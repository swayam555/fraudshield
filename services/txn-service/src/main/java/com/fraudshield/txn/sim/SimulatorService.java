package com.fraudshield.txn.sim;

import com.fraudshield.contracts.Decision;
import com.fraudshield.txn.payment.PaymentResult;
import com.fraudshield.txn.payment.PaymentService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Drives synthetic UPI traffic through the real payment pipeline (risk call, database, outbox,
 * Kafka) and keeps a scoreboard of how well each attack type is caught.
 */
@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);
    private static final int POPULATION = 1000;
    private static final int HISTORY_PER_CUSTOMER = 8;

    private final PaymentService payments;
    private final Population population = new Population(POPULATION, 2026);
    private final TrafficGenerator generator = new TrafficGenerator(population, System.nanoTime());
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore inFlight = new Semaphore(32);
    private final AtomicBoolean warmedUp = new AtomicBoolean(false);
    private final AtomicBoolean warming = new AtomicBoolean(false);
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final Map<Scenario, Map<Decision, LongAdder>> scoreboard = new ConcurrentHashMap<>();

    private volatile ScheduledFuture<?> loop;
    private volatile double tps = 8;
    private volatile double fraudRate = 0.04;
    private double carry;
    private boolean demoSeeded;

    public SimulatorService(PaymentService payments) {
        this.payments = payments;
    }

    public synchronized Status start(double tps, double fraudRate) {
        this.tps = Math.max(0.5, Math.min(tps, 200));
        this.fraudRate = Math.max(0, Math.min(fraudRate, 0.5));
        if (loop == null || loop.isDone()) {
            loop = ticker.scheduleAtFixedRate(this::tick, 0, 100, TimeUnit.MILLISECONDS);
            if (!warmedUp.get()) workers.submit(this::warmUp);
            log.info("Simulator started at {} tps, fraud rate {}", this.tps, this.fraudRate);
        }
        return status();
    }

    public synchronized Status stop() {
        if (loop != null) loop.cancel(false);
        loop = null;
        return status();
    }

    public Status inject(Scenario scenario) {
        generator.inject(scenario);
        if (loop == null || loop.isDone()) {
            // Not running: play the attack out on its own, a step every 300 ms.
            workers.submit(() -> {
                while (generator.pendingCampaignSteps() > 0) {
                    send(generator.next(0));
                    sleep(300);
                }
            });
        }
        return status();
    }

    private void tick() {
        if (warming.get()) return;
        carry += tps / 10.0;
        int n = (int) carry;
        carry -= n;
        for (int i = 0; i < n; i++) {
            if (!inFlight.tryAcquire()) {
                return; // back-pressure: never queue more than 32 payments in flight
            }
            SimulatedPayment p = generator.next(fraudRate);
            workers.submit(() -> {
                try {
                    send(p);
                } finally {
                    inFlight.release();
                }
            });
        }
    }

    private void send(SimulatedPayment p) {
        try {
            PaymentResult r = payments.process(p.request(), null, p.at(), p.label().name());
            sent.incrementAndGet();
            scoreboard.computeIfAbsent(p.label(), k -> new EnumMap<>(Decision.class));
            scoreboard.get(p.label()).computeIfAbsent(r.decision(), k -> new LongAdder()).increment();
        } catch (RuntimeException e) {
            errors.incrementAndGet();
            log.debug("Simulated payment failed: {}", e.getMessage());
        }
    }

    /**
     * Back-fills 30 days of ordinary history so customers have established spending profiles,
     * known devices and known payees before live traffic starts. Each customer's history is
     * replayed in order; customers run in parallel.
     */
    public void warmUp() {
        if (!warming.compareAndSet(false, true)) return;
        long start = System.currentTimeMillis();
        try {
            List<Future<?>> jobs = new ArrayList<>();
            Semaphore parallel = new Semaphore(8);
            for (Population.Customer c : population.customers) {
                List<SimulatedPayment> history = generator.history(c, HISTORY_PER_CUSTOMER);
                jobs.add(workers.submit(() -> {
                    parallel.acquireUninterruptibly();
                    try {
                        history.forEach(this::send);
                    } finally {
                        parallel.release();
                    }
                }));
            }
            for (Future<?> f : jobs) f.get();
            warmedUp.set(true);
            log.info("Warm-up finished: {} historical payments in {} ms", POPULATION * HISTORY_PER_CUSTOMER,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Warm-up failed", e);
        } finally {
            warming.set(false);
        }
    }

    /**
     * A real customer from the synthetic population, with an established 30-day history, for the
     * "Try a payment" presets. If the simulator has not back-filled yet, this customer's history is
     * back-filled now, so a "stolen phone" payment is compared against genuine past behaviour.
     */
    public synchronized DemoCustomer demoCustomer() {
        Population.Customer c = population.customers.get(7);
        if (!warmedUp.get() && !demoSeeded) {
            generator.history(c, HISTORY_PER_CUSTOMER).forEach(this::send);
            demoSeeded = true;
        }
        Population.City far = Population.CITIES.stream()
                .filter(city -> !city.equals(c.city()))
                .max(java.util.Comparator.comparingDouble(city -> Math.abs(city.lat() - c.lat()) + Math.abs(city.lon() - c.lon())))
                .orElseThrow();
        String merchant = c.favourites().stream().filter(Population.MERCHANTS::contains).findFirst()
                .orElse(Population.MERCHANTS.get(0));
        return new DemoCustomer(c.vpa(), c.device(), c.city().name(), round4(c.lat()), round4(c.lon()),
                Math.round(c.typicalAmount()), merchant, far.name(), far.lat(), far.lon());
    }

    private static double round4(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }

    public record DemoCustomer(String vpa, String deviceId, String homeCity, double lat, double lon, long typicalAmount,
                               String favouriteMerchant, String farCity, double farLat, double farLon) {
    }

    public Status status() {
        Map<String, Map<String, Long>> board = new LinkedHashMap<>();
        for (Scenario s : Scenario.values()) {
            Map<Decision, LongAdder> row = scoreboard.get(s);
            if (row == null) continue;
            Map<String, Long> counts = new LinkedHashMap<>();
            for (Decision d : Decision.values()) counts.put(d.name(), row.containsKey(d) ? row.get(d).sum() : 0);
            board.put(s.name(), counts);
        }
        return new Status(loop != null && !loop.isDone(), warming.get(), warmedUp.get(), tps, fraudRate, sent.get(),
                errors.get(), generator.pendingCampaignSteps(), board);
    }

    public synchronized void resetScoreboard() {
        scoreboard.clear();
        sent.set(0);
        errors.set(0);
    }

    @PreDestroy
    void shutdown() {
        ticker.shutdownNow();
        workers.shutdownNow();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Status(boolean running, boolean warmingUp, boolean warmedUp, double tps, double fraudRate,
                         long sent, long errors, int pendingAttackSteps, Map<String, Map<String, Long>> scoreboard) {
    }
}

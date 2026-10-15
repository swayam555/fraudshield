package com.fraudshield.txn.sim;

import com.fraudshield.contracts.Channel;
import com.fraudshield.txn.payment.PaymentRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Produces a stream of payments: mostly ordinary spending, interleaved with multi-step fraud
 * campaigns. Each campaign unfolds over several ticks the way real attacks do, so velocity and
 * graph signals build up gradually instead of arriving all at once.
 */
final class TrafficGenerator {

    private final Population population;
    private final Random rnd;
    private final Deque<SimulatedPayment> campaignQueue = new ArrayDeque<>();
    private int campaignSeq;

    TrafficGenerator(Population population, long seed) {
        this.population = population;
        this.rnd = new Random(seed);
    }

    /** One payment per call. With probability {@code fraudRate} a new campaign is started. */
    synchronized SimulatedPayment next(double fraudRate) {
        if (!campaignQueue.isEmpty() && rnd.nextDouble() < 0.5) {
            return campaignQueue.poll().withTime(Instant.now());
        }
        if (rnd.nextDouble() < fraudRate / 6) {
            startCampaign();
        }
        return legit(Instant.now());
    }

    /** Queues a specific attack so it plays out over the next few seconds (used by the demo buttons). */
    synchronized void inject(Scenario scenario) {
        campaignSeq++;
        switch (scenario) {
            case MULE_RING -> muleRing();
            case ACCOUNT_TAKEOVER -> accountTakeover();
            case DIGITAL_ARREST -> digitalArrest();
            case UPI_PROBING -> probing();
            case LEGIT -> throw new IllegalArgumentException("LEGIT is not an attack");
        }
    }

    synchronized int pendingCampaignSteps() {
        return campaignQueue.size();
    }

    /** Historical payments for one customer, spread over the past 30 days, oldest first. */
    List<SimulatedPayment> history(Population.Customer c, int count) {
        // Independent random times per customer, so back-filled history doesn't pile into the
        // same hours (which would make every merchant look like a mule collection account).
        long now = System.currentTimeMillis();
        long span = 30L * 86_400_000L;
        List<Long> times = new ArrayList<>();
        synchronized (this) {
            for (int i = 0; i < count; i++) times.add(now - span + (long) (rnd.nextDouble() * (span - 7_200_000L)));
        }
        times.sort(null);
        List<SimulatedPayment> out = new ArrayList<>();
        for (long t : times) out.add(legitFrom(c, Instant.ofEpochMilli(t)));
        return out;
    }

    private SimulatedPayment legit(Instant at) {
        Population.Customer c = population.customers.get(rnd.nextInt(population.customers.size()));
        if (rnd.nextDouble() < 0.01) {
            // Legitimately unusual: rent or a deposit to a brand-new payee. Keeps the model honest
            // about false positives.
            String landlord = "landlord." + Integer.toHexString(rnd.nextInt(0xffff)) + "@okhdfcbank";
            return payment(c.vpa(), landlord, round(15_000 + rnd.nextInt(20_000)), c.device(), c.lat(), c.lon(),
                    Scenario.LEGIT, at, "Rent");
        }
        return legitFrom(c, at);
    }

    private synchronized SimulatedPayment legitFrom(Population.Customer c, Instant at) {
        String payee = c.favourites().get(rnd.nextInt(c.favourites().size()));
        if (payee.equals(c.vpa())) payee = Population.MERCHANTS.get(0);
        double amount = Math.max(10, c.typicalAmount() * Math.exp(rnd.nextGaussian() * 0.5));
        return payment(c.vpa(), payee, round(amount), c.device(),
                c.lat() + rnd.nextGaussian() * 0.01, c.lon() + rnd.nextGaussian() * 0.01, Scenario.LEGIT, at, null);
    }

    private void startCampaign() {
        campaignSeq++;
        switch (rnd.nextInt(4)) {
            case 0 -> muleRing();
            case 1 -> accountTakeover();
            case 2 -> digitalArrest();
            default -> probing();
        }
    }

    private void muleRing() {
        String tag = Integer.toHexString(0x1000 + campaignSeq * 7919 % 0xefff);
        String farmDevice = "dev-farm-" + tag;
        List<String> mules = List.of("quickcash" + tag + "@ybl", "earnhome" + tag + "@paytm");
        String collector = "agent" + tag + "@axl";
        Population.City city = Population.CITIES.get(rnd.nextInt(Population.CITIES.size()));
        int victims = 10 + rnd.nextInt(6);
        List<SimulatedPayment> steps = new ArrayList<>();
        double[] received = new double[mules.size()];
        for (int v = 0; v < victims; v++) {
            Population.Customer victim = population.customers.get(rnd.nextInt(population.customers.size()));
            int m = v % mules.size();
            double amount = round(2_000 + rnd.nextInt(13_000)).doubleValue();
            received[m] += amount;
            steps.add(payment(victim.vpa(), mules.get(m), BigDecimal.valueOf(amount), victim.device(),
                    victim.lat(), victim.lon(), Scenario.MULE_RING, null, "Task reward deposit"));
            // After every few deposits, the mule forwards most of what it holds to the collector.
            if (v % 4 == 3) {
                for (int k = 0; k < mules.size(); k++) {
                    if (received[k] > 0) {
                        steps.add(payment(mules.get(k), collector, round(received[k] * 0.92), farmDevice,
                                city.lat(), city.lon(), Scenario.MULE_RING, null, null));
                        received[k] = 0;
                    }
                }
            }
        }
        campaignQueue.addAll(steps);
    }

    private void accountTakeover() {
        Population.Customer victim = population.customers.get(rnd.nextInt(population.customers.size()));
        Population.City far;
        do {
            far = Population.CITIES.get(rnd.nextInt(Population.CITIES.size()));
        } while (far.equals(victim.city()));
        String device = "dev-ato-" + Integer.toHexString(rnd.nextInt(0xfffff));
        int steps = 2 + rnd.nextInt(3);
        for (int i = 0; i < steps; i++) {
            String payee = "fastpay" + Integer.toHexString(rnd.nextInt(0xfffff)) + "@ybl";
            campaignQueue.add(payment(victim.vpa(), payee, round(15_000 + rnd.nextInt(75_000)), device,
                    far.lat(), far.lon(), Scenario.ACCOUNT_TAKEOVER, null, null));
        }
    }

    private void digitalArrest() {
        Population.Customer victim = population.customers.stream().filter(Population.Customer::senior)
                .skip(rnd.nextInt(10)).findFirst().orElse(population.customers.get(0));
        String scammer = "cyber.cell.verification" + rnd.nextInt(999) + "@okaxis";
        double[] amounts = {48_000, 95_000, 1_99_000};
        for (double a : amounts) {
            campaignQueue.add(payment(victim.vpa(), scammer, round(a), victim.device(), victim.lat(), victim.lon(),
                    Scenario.DIGITAL_ARREST, null, "Security deposit - case clearance"));
        }
    }

    private void probing() {
        String attacker = "user" + Integer.toHexString(rnd.nextInt(0xfffff)) + "@paytm";
        String device = "dev-probe-" + Integer.toHexString(rnd.nextInt(0xfffff));
        Population.City city = Population.CITIES.get(rnd.nextInt(Population.CITIES.size()));
        for (int i = 0; i < 8; i++) {
            Population.Customer target = population.customers.get(rnd.nextInt(population.customers.size()));
            campaignQueue.add(payment(attacker, target.vpa(), BigDecimal.ONE, device, city.lat(), city.lon(),
                    Scenario.UPI_PROBING, null, null));
        }
    }

    private SimulatedPayment payment(String from, String to, BigDecimal amount, String device, double lat, double lon,
                                     Scenario label, Instant at, String note) {
        Channel channel = Population.MERCHANTS.contains(to) ? Channel.QR_SCAN : Channel.UPI_INTENT;
        return new SimulatedPayment(new PaymentRequest(from, to, amount, channel, device,
                round4(lat), round4(lon), note), label, at);
    }

    private static BigDecimal round(double amount) {
        return BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP);
    }

    private static double round4(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}

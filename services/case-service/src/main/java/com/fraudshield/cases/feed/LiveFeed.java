package com.fraudshield.cases.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pushes payments, new cases and rolling KPIs to every open dashboard over Server-Sent Events.
 * SSE fits this one-way stream better than WebSockets: it is plain HTTP, passes through the
 * gateway and proxies untouched, and the browser reconnects on its own.
 */
@Component
public class LiveFeed {

    private static final Logger log = LoggerFactory.getLogger(LiveFeed.class);
    private static final int MAX_PAYMENT_EVENTS_PER_SECOND = 40;

    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger sentThisSecond = new AtomicInteger();
    private final StatsWindow stats;

    public LiveFeed(StatsWindow stats) {
        this.stats = stats;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter));
        send(emitter, "stats", stats.snapshot());
        return emitter;
    }

    /** Payment events are sampled above 40/s: a dashboard cannot usefully show more than that. */
    public void publishPayment(Object payment) {
        if (sentThisSecond.incrementAndGet() <= MAX_PAYMENT_EVENTS_PER_SECOND) {
            broadcast("payment", payment);
        }
    }

    public void publishCase(long id, String subject, String typology, String priority) {
        broadcast("case", Map.of("id", id, "subject", subject, "typology", typology, "priority", priority));
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        sentThisSecond.set(0);
        if (!clients.isEmpty()) broadcast("stats", stats.snapshot());
    }

    public int clientCount() {
        return clients.size();
    }

    private void broadcast(String event, Object data) {
        for (SseEmitter c : clients) send(c, event, data);
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            }
        } catch (IOException | IllegalStateException e) {
            clients.remove(emitter);
            log.debug("Dropped SSE client: {}", e.getMessage());
        }
    }
}

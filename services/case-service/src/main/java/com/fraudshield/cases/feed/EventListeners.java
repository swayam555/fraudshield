package com.fraudshield.cases.feed;

import com.fraudshield.cases.cases.CaseService;
import com.fraudshield.cases.config.JsonCodec;
import com.fraudshield.contracts.MuleAlert;
import com.fraudshield.contracts.PaymentScored;
import com.fraudshield.contracts.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EventListeners {

    /** Back-filled history (simulator warm-up) is persisted as cases but kept off the live view. */
    private static final Duration LIVE_HORIZON = Duration.ofMinutes(2);

    private final CaseService cases;
    private final LiveFeed feed;
    private final StatsWindow stats;
    private final JsonCodec json;

    public EventListeners(CaseService cases, LiveFeed feed, StatsWindow stats, JsonCodec json) {
        this.cases = cases;
        this.feed = feed;
        this.stats = stats;
        this.json = json;
    }

    @KafkaListener(topics = Topics.PAYMENTS_SCORED, groupId = "case-service", concurrency = "3")
    public void onPayment(String message) {
        PaymentScored p = json.read(message, PaymentScored.class);
        cases.onPayment(p);
        if (p.timestamp().isAfter(Instant.now().minus(LIVE_HORIZON))) {
            stats.record(p);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("txnId", p.txnId());
            view.put("payer", p.payerVpa());
            view.put("payee", p.payeeVpa());
            view.put("amount", p.amount());
            view.put("decision", p.decision());
            view.put("riskScore", p.riskScore());
            view.put("reasons", p.reasons().stream().limit(3).toList());
            view.put("latencyMs", p.latencyMillis());
            view.put("degraded", p.degraded());
            view.put("simLabel", p.simLabel());
            view.put("at", p.timestamp());
            feed.publishPayment(view);
        }
    }

    @KafkaListener(topics = Topics.RISK_ALERTS, groupId = "case-service")
    public void onAlert(String message) {
        cases.onAlert(json.read(message, MuleAlert.class));
    }
}

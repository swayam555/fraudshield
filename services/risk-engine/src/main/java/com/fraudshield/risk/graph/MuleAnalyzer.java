package com.fraudshield.risk.graph;

import com.fraudshield.contracts.Decision;
import com.fraudshield.contracts.MuleAlert;
import com.fraudshield.contracts.PaymentScored;
import com.fraudshield.contracts.Topics;
import com.fraudshield.risk.config.JsonCodec;
import com.fraudshield.risk.config.RiskProperties;
import com.fraudshield.risk.feature.FeatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Asynchronous, cross-payment analysis. A single payment rarely proves an account is a mule; the
 * pattern only shows up across many payments: money fanning in from strangers, then leaving
 * again within minutes. This consumer maintains a payment graph and a mule score for every
 * account it sees, and raises an alert the moment an account crosses the threshold.
 */
@Component
public class MuleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(MuleAnalyzer.class);
    private static final Duration GRAPH_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;
    private final JsonCodec json;
    private final RiskProperties props;

    public MuleAnalyzer(StringRedisTemplate redis, KafkaTemplate<String, String> kafka, JsonCodec json,
                        RiskProperties props) {
        this.redis = redis;
        this.kafka = kafka;
        this.json = json;
        this.props = props;
    }

    @KafkaListener(topics = Topics.PAYMENTS_SCORED, groupId = "risk-graph")
    public void onPayment(String message) {
        PaymentScored p = json.read(message, PaymentScored.class);
        // The outbox guarantees at-least-once delivery, so de-duplicate before mutating the graph.
        Boolean first = redis.opsForValue().setIfAbsent("graph:seen:" + p.txnId(), "1", Duration.ofDays(1));
        if (!Boolean.TRUE.equals(first)) {
            return;
        }
        recordEdge(p);
        for (String account : List.of(p.payeeVpa(), p.payerVpa())) {
            MuleAssessment a = assess(account);
            if (a.score() >= 0.3) {
                redis.opsForZSet().add(FeatureStore.MULE_WATCH, account, a.score());
            } else {
                redis.opsForZSet().remove(FeatureStore.MULE_WATCH, account);
            }
            if (a.score() >= props.muleAlertScore()) {
                raiseOnce(account, a);
            }
        }
    }

    private void recordEdge(PaymentScored p) {
        long ts = p.timestamp().toEpochMilli();
        String out = "g:out:" + p.payerVpa();
        String in = "g:in:" + p.payeeVpa();
        String amt = "g:amt:" + p.payerVpa();
        String cnt = "g:cnt:" + p.payerVpa();
        redis.opsForZSet().add(out, p.payeeVpa(), ts);
        redis.opsForZSet().add(in, p.payerVpa(), ts);
        redis.opsForHash().increment(amt, p.payeeVpa(), p.amount().doubleValue());
        redis.opsForHash().increment(cnt, p.payeeVpa(), 1);
        for (String k : List.of(out, in, amt, cnt)) redis.expire(k, GRAPH_TTL);
        if (p.decision() != Decision.APPROVE) {
            String flagged = "flagged:in:" + p.payeeVpa();
            redis.opsForZSet().add(flagged, p.txnId(), ts);
            redis.opsForZSet().removeRangeByScore(flagged, 0, ts - 86_400_000L);
            redis.expire(flagged, Duration.ofDays(1));
        }
    }

    public MuleAssessment assess(String vpa) {
        long now = System.currentTimeMillis();
        long hour = now / 3_600_000L;
        Long fanIn = redis.opsForHyperLogLog().size("hll:in:" + vpa + ":" + hour, "hll:in:" + vpa + ":" + (hour - 1));
        double inflow = windowSum("inflow:" + vpa);
        double outflow = windowSum("outflow:" + vpa);
        double passThrough = inflow > 0 ? Math.min(1, outflow / inflow) : 0;
        Long flagged = redis.opsForZSet().count("flagged:in:" + vpa, now - 86_400_000L, Double.MAX_VALUE);
        Object first = redis.opsForHash().get("prof:" + vpa, "first");
        boolean young = first == null || now - (long) Double.parseDouble(first.toString()) < 86_400_000L;
        long maxDeviceAccounts = 0;
        Set<Object> devices = redis.opsForHash().keys("devseen:" + vpa);
        if (devices != null) {
            for (Object d : devices) {
                Long n = redis.opsForSet().size("devacc:" + d);
                if (n != null) maxDeviceAccounts = Math.max(maxDeviceAccounts, n);
            }
        }
        long fi = fanIn == null ? 0 : fanIn;
        long fl = flagged == null ? 0 : flagged;

        // Fan-in alone is normal for a merchant; it only counts when the account also behaves
        // like a conduit (new, or forwarding what it receives). This is the negative control
        // that keeps the corner chai shop off the watch-list.
        double conduit = Math.max(passThrough, young ? 0.5 : 0);
        double z = 0.35 * Math.min(Math.max(0, fi - 3), 20) * (0.05 + conduit)
                + 2.5 * passThrough * (inflow >= 2000 ? 1 : 0.3)
                + 0.6 * Math.min(fl, 5) * (0.3 + conduit)
                + 0.4 * Math.max(0, maxDeviceAccounts - 2);
        double score = 1 - Math.exp(-z / 2);

        Map<String, Double> signals = new LinkedHashMap<>();
        signals.put("distinctPayersLastHour", (double) fi);
        signals.put("inflowLastHour", round(inflow));
        signals.put("outflowLastHour", round(outflow));
        signals.put("passThroughRatio", round(passThrough));
        signals.put("flaggedInboundPayments24h", (double) fl);
        signals.put("accountYoungerThan24h", young ? 1.0 : 0.0);
        signals.put("maxAccountsOnSameDevice", (double) maxDeviceAccounts);
        return new MuleAssessment(vpa, round(score), signals);
    }

    private void raiseOnce(String vpa, MuleAssessment a) {
        Boolean fresh = redis.opsForValue().setIfAbsent("alerted:" + vpa, "1", Duration.ofMinutes(30));
        if (!Boolean.TRUE.equals(fresh)) return;
        List<String> txnIds = new ArrayList<>();
        for (String key : List.of("inflow:" + vpa, "outflow:" + vpa)) {
            Set<String> members = redis.opsForZSet().reverseRange(key, 0, 9);
            if (members != null) members.forEach(m -> txnIds.add(m.substring(0, m.indexOf('|'))));
        }
        MuleAlert alert = new MuleAlert(UUID.randomUUID().toString(), vpa, a.score(), a.signals(), txnIds, Instant.now());
        kafka.send(Topics.RISK_ALERTS, vpa, json.write(alert));
        log.info("Mule alert for {} (score {})", vpa, a.score());
    }

    private double windowSum(String key) {
        long from = System.currentTimeMillis() - 3_600_000L;
        Set<String> members = redis.opsForZSet().rangeByScore(key, from, Double.MAX_VALUE);
        double total = 0;
        if (members != null) {
            for (String m : members) total += Double.parseDouble(m.substring(m.indexOf('|') + 1));
        }
        return total;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    public record MuleAssessment(String vpa, double score, Map<String, Double> signals) {
    }
}

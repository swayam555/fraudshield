package com.fraudshield.risk.feedback;

import com.fraudshield.contracts.Decision;
import com.fraudshield.contracts.PaymentScored;
import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.contracts.Topics;
import com.fraudshield.risk.config.JsonCodec;
import com.fraudshield.risk.feature.FeatureStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * When the risk engine was unreachable, txn-service decided the payment with its fallback policy
 * and the payment never reached the feature store. Left alone, every outage would punch a hole in
 * customers' behavioural history (velocity, usual amount, known devices), weakening scoring for
 * days afterwards. This consumer replays those payments into the feature store from Kafka.
 */
@Component
public class DegradedBackfill {

    private final FeatureStore features;
    private final StringRedisTemplate redis;
    private final JsonCodec json;

    public DegradedBackfill(FeatureStore features, StringRedisTemplate redis, JsonCodec json) {
        this.features = features;
        this.redis = redis;
        this.json = json;
    }

    @KafkaListener(topics = Topics.PAYMENTS_SCORED, groupId = "risk-backfill")
    public void onPayment(String message) {
        PaymentScored p = json.read(message, PaymentScored.class);
        if (!p.degraded()) return;
        Boolean first = redis.opsForValue().setIfAbsent("backfill:" + p.txnId(), "1", Duration.ofDays(2));
        if (!Boolean.TRUE.equals(first)) return;
        ScoreRequest r = new ScoreRequest(p.txnId(), p.payerVpa(), p.payeeVpa(), p.amount(), p.channel(),
                p.deviceId(), p.lat(), p.lon(), p.timestamp(), null);
        features.readAndRecord(r);
        if (p.decision() == Decision.APPROVE) features.learnApproved(r);
    }
}

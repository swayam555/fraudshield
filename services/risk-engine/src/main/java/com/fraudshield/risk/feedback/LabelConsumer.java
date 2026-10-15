package com.fraudshield.risk.feedback;

import com.fraudshield.contracts.CaseLabel;
import com.fraudshield.contracts.Topics;
import com.fraudshield.risk.config.JsonCodec;
import com.fraudshield.risk.feature.FeatureStore;
import com.fraudshield.risk.feature.FeatureVector;
import com.fraudshield.risk.model.ModelRegistry;
import com.fraudshield.risk.model.TrainingSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Closes the loop between investigators and the model: a FRAUD verdict deny-lists the account
 * immediately and turns the case's payments into positive training samples; a LEGIT verdict
 * clears the account and teaches the model what a false positive looked like.
 */
@Component
public class LabelConsumer {

    private static final Logger log = LoggerFactory.getLogger(LabelConsumer.class);

    private final StringRedisTemplate redis;
    private final ModelRegistry models;
    private final JsonCodec json;

    public LabelConsumer(StringRedisTemplate redis, ModelRegistry models, JsonCodec json) {
        this.redis = redis;
        this.models = models;
        this.json = json;
    }

    @KafkaListener(topics = Topics.CASE_LABELS, groupId = "risk-feedback")
    public void onLabel(String message) {
        CaseLabel label = json.read(message, CaseLabel.class);
        boolean fraud = label.verdict() == CaseLabel.Verdict.FRAUD;
        if (fraud) {
            label.denyVpas().forEach(vpa -> redis.opsForSet().add(FeatureStore.DENY_LIST, vpa));
        } else {
            redis.opsForSet().remove(FeatureStore.DENY_LIST, label.subjectVpa());
            redis.opsForZSet().remove(FeatureStore.MULE_WATCH, label.subjectVpa());
        }
        int added = 0;
        for (String txnId : label.txnIds()) {
            String csv = redis.opsForValue().get("fv:" + txnId);
            if (csv != null) {
                models.recordSample(new TrainingSample(FeatureVector.fromCsv(csv), fraud ? 1 : 0));
                added++;
            }
        }
        log.info("Case {} labelled {} for {}: {} training samples added",
                label.caseId(), label.verdict(), label.subjectVpa(), added);
    }
}

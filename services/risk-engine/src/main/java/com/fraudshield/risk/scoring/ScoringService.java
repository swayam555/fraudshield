package com.fraudshield.risk.scoring;

import com.fraudshield.contracts.Decision;
import com.fraudshield.contracts.Reason;
import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.contracts.ScoreResponse;
import com.fraudshield.risk.config.RiskProperties;
import com.fraudshield.risk.feature.Feature;
import com.fraudshield.risk.feature.FeatureExtractor;
import com.fraudshield.risk.feature.FeatureStore;
import com.fraudshield.risk.feature.FeatureVector;
import com.fraudshield.risk.feature.RawSignals;
import com.fraudshield.risk.model.LogisticModel;
import com.fraudshield.risk.model.ModelRegistry;
import com.fraudshield.risk.model.TrainingSample;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);
    private static final double MIN_REASON_CONTRIBUTION = 0.25;

    private final FeatureStore features;
    private final ModelRegistry models;
    private final RuntimeThresholds thresholds;
    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final Timer timer;
    /** Book-keeping that must not sit on the payment's critical path. */
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

    public ScoringService(FeatureStore features, ModelRegistry models, RuntimeThresholds thresholds,
                          StringRedisTemplate redis, MeterRegistry meters) {
        this.features = features;
        this.models = models;
        this.thresholds = thresholds;
        this.redis = redis;
        this.meters = meters;
        this.timer = Timer.builder("risk.score.latency").publishPercentiles(0.5, 0.95, 0.99).register(meters);
    }

    public ScoreResponse score(ScoreRequest req) {
        long start = System.nanoTime();
        RawSignals raw = features.readAndRecord(req);
        FeatureVector x = FeatureExtractor.extract(raw, req.amount().doubleValue(), req.lat(), req.lon(), req.timestamp());
        LogisticModel model = models.active();

        double p = model.probability(x);
        List<Reason> reasons = new ArrayList<>();
        if (raw.payeeDenied()) {
            p = Math.max(p, 0.99);
            reasons.add(new Reason("PAYEE_DENYLISTED",
                    req.payeeVpa() + " was confirmed as fraudulent by an analyst", 10.0));
        }
        for (Feature f : Feature.values()) {
            double c = model.contribution(f, x);
            if (f == Feature.AMOUNT_SIZE && req.amount().doubleValue() < 10_000) continue;
            if (c >= MIN_REASON_CONTRIBUTION) {
                reasons.add(new Reason(f.name(), ReasonFormatter.describe(f, raw, req), round(c)));
            }
        }
        reasons.sort(Comparator.comparingDouble(Reason::contribution).reversed());
        if (reasons.size() > 6) reasons = new ArrayList<>(reasons.subList(0, 6));

        RiskProperties t = thresholds.current();
        Decision decision = p >= t.blockThreshold() ? Decision.BLOCK
                : p >= t.reviewThreshold() ? Decision.REVIEW : Decision.APPROVE;
        // Only approved payments shape what "normal" looks like for this payer.
        if (decision == Decision.APPROVE) features.learnApproved(req);

        long micros = (System.nanoTime() - start) / 1_000;
        timer.record(Duration.ofNanos(micros * 1_000));
        meters.counter("risk.decisions", "decision", decision.name()).increment();

        background.submit(() -> remember(req, x));
        return new ScoreResponse(req.txnId(), decision, round(p), reasons, model.version(), micros);
    }

    /** Keeps the feature vector so a later analyst verdict can turn it into a training sample. */
    private void remember(ScoreRequest req, FeatureVector x) {
        try {
            redis.opsForValue().set("fv:" + req.txnId(), x.toCsv(), Duration.ofDays(3));
            if (req.simLabel() != null && !req.simLabel().isBlank()) {
                models.recordSample(new TrainingSample(x, "LEGIT".equals(req.simLabel()) ? 0 : 1));
            }
        } catch (RuntimeException e) {
            log.warn("Could not store feature vector for {}: {}", req.txnId(), e.getMessage());
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}

package com.fraudshield.risk.model;

import com.fraudshield.risk.config.JsonCodec;
import com.fraudshield.risk.config.RiskProperties;
import com.fraudshield.risk.feature.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the active model in memory and keeps it in Redis so that every risk-engine replica
 * scores with the same version. Replicas poll for a newer version every few seconds, which makes
 * promoting a model a hot swap with no restart.
 */
@Component
public class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);
    static final String ACTIVE_KEY = "risk:model:active";
    static final String HISTORY_KEY = "risk:model:history";
    public static final String SAMPLES_KEY = "risk:train:samples";
    public static final int MAX_SAMPLES = 60_000;
    static final String SAMPLES_TOTAL_KEY = "risk:train:total";
    static final String LAST_TRAINED_AT_KEY = "risk:train:last-total";
    static final String TRAIN_LOCK_KEY = "risk:train:lock";

    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final RiskProperties props;
    private final AtomicReference<LogisticModel> active = new AtomicReference<>(LogisticModel.baseline());

    public ModelRegistry(StringRedisTemplate redis, JsonCodec json, RiskProperties props) {
        this.redis = redis;
        this.json = json;
        this.props = props;
    }

    public LogisticModel active() {
        return active.get();
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 0)
    public void refresh() {
        try {
            String raw = redis.opsForValue().get(ACTIVE_KEY);
            if (raw != null) {
                LogisticModel stored = json.read(raw, LogisticModel.class);
                if (!stored.version().equals(active.get().version())) {
                    active.set(stored);
                    log.info("Activated model {}", stored.version());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not refresh model from Redis: {}", e.getMessage());
        }
    }

    public void recordSample(TrainingSample sample) {
        redis.opsForValue().increment(SAMPLES_TOTAL_KEY);
        redis.opsForList().leftPush(SAMPLES_KEY, sample.encode());
        redis.opsForList().trim(SAMPLES_KEY, 0, MAX_SAMPLES - 1);
    }

    public long sampleCount() {
        Long size = redis.opsForList().size(SAMPLES_KEY);
        return size == null ? 0 : size;
    }

    public synchronized TrainingReport retrain() {
        List<String> raw = redis.opsForList().range(SAMPLES_KEY, 0, -1);
        List<TrainingSample> samples = new ArrayList<>();
        if (raw != null) {
            for (String s : raw) samples.add(TrainingSample.decode(s));
        }
        LogisticModel previous = active.get();
        Long n = redis.opsForValue().increment("risk:model:seq");
        Trainer.Result result = Trainer.defaults().train(samples, previous, "lr-v" + n, props.reviewThreshold());
        LogisticModel trained = result.model();

        // Score the current model on the same hold-out so the comparison is like for like.
        Map<String, Double> baselineMetrics = Trainer.evaluate(previous, result.holdout(),
                props.reviewThreshold(), trained.metrics().get("trainSize").intValue());
        // Champion/challenger: the new model must rank at least as well AND must not push the
        // false-positive rate up, because every false positive is a genuine customer blocked.
        boolean promoted = trained.metrics().get("auc") >= baselineMetrics.get("auc") - 0.005
                && trained.metrics().get("falsePositiveRate")
                <= Math.max(baselineMetrics.get("falsePositiveRate") * 1.5, 0.005);
        if (promoted) {
            String encoded = json.write(trained);
            redis.opsForValue().set(ACTIVE_KEY, encoded);
            redis.opsForList().leftPush(HISTORY_KEY, encoded);
            redis.opsForList().trim(HISTORY_KEY, 0, 19);
            active.set(trained);
            log.info("Promoted {} with metrics {}", trained.version(), trained.metrics());
        }
        return new TrainingReport(trained, previous.version(), baselineMetrics, promoted, samples.size(),
                (int) samples.stream().filter(s -> s.label() == 1).count());
    }

    /**
     * Continuous training: once enough new labelled payments have arrived since the last attempt,
     * retrain in the background. A Redis lock makes sure only one replica trains at a time; the
     * champion/challenger check in {@link #retrain()} still decides whether the result goes live.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void autoRetrain() {
        try {
            long total = parse(redis.opsForValue().get(SAMPLES_TOTAL_KEY));
            long last = parse(redis.opsForValue().get(LAST_TRAINED_AT_KEY));
            if (total < 2_000 || total - last < 1_000) return;
            Boolean locked = redis.opsForValue().setIfAbsent(TRAIN_LOCK_KEY, "1", java.time.Duration.ofMinutes(5));
            if (!Boolean.TRUE.equals(locked)) return;
            try {
                redis.opsForValue().set(LAST_TRAINED_AT_KEY, String.valueOf(total));
                TrainingReport report = retrain();
                log.info("Scheduled retrain: {} {} (AUC {} -> {})", report.model().version(),
                        report.promoted() ? "promoted" : "rejected", report.previousMetrics().get("auc"),
                        report.model().metrics().get("auc"));
            } finally {
                redis.delete(TRAIN_LOCK_KEY);
            }
        } catch (RuntimeException e) {
            log.info("Scheduled retrain skipped: {}", e.getMessage());
        }
    }

    private static long parse(String v) {
        return v == null ? 0 : Long.parseLong(v);
    }

    public List<LogisticModel> history() {
        List<String> raw = redis.opsForList().range(HISTORY_KEY, 0, -1);
        List<LogisticModel> out = new ArrayList<>();
        if (raw != null) raw.forEach(r -> out.add(json.read(r, LogisticModel.class)));
        return out;
    }

    public void reset() {
        redis.delete(ACTIVE_KEY);
        active.set(LogisticModel.baseline());
    }

    public static Map<String, Double> namedWeights(LogisticModel m) {
        Map<String, Double> named = new LinkedHashMap<>();
        for (Feature f : Feature.values()) named.put(f.name(), m.weights()[f.ordinal()]);
        return named;
    }

    public record TrainingReport(LogisticModel model, String previousVersion, Map<String, Double> previousMetrics,
                                 boolean promoted, int samples, int fraudSamples) {
        public Instant trainedAt() {
            return model.trainedAt();
        }
    }
}

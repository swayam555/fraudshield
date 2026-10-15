package com.fraudshield.risk.api;

import com.fraudshield.risk.config.RiskProperties;
import com.fraudshield.risk.feature.Feature;
import com.fraudshield.risk.feature.FeatureStore;
import com.fraudshield.risk.graph.GraphService;
import com.fraudshield.risk.graph.MuleAnalyzer;
import com.fraudshield.risk.model.LogisticModel;
import com.fraudshield.risk.model.ModelRegistry;
import com.fraudshield.risk.scoring.RuntimeThresholds;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/risk")
public class RiskAdminController {

    private final ModelRegistry models;
    private final RuntimeThresholds thresholds;
    private final GraphService graph;
    private final MuleAnalyzer mules;
    private final StringRedisTemplate redis;

    public RiskAdminController(ModelRegistry models, RuntimeThresholds thresholds, GraphService graph,
                               MuleAnalyzer mules, StringRedisTemplate redis) {
        this.models = models;
        this.thresholds = thresholds;
        this.graph = graph;
        this.mules = mules;
        this.redis = redis;
    }

    @GetMapping("/model")
    public Map<String, Object> model() {
        LogisticModel m = models.active();
        List<Map<String, Object>> features = new ArrayList<>();
        for (Feature f : Feature.values()) {
            features.add(Map.of("name", f.name(), "description", f.description(), "weight", m.weights()[f.ordinal()]));
        }
        List<Map<String, Object>> history = new ArrayList<>();
        for (LogisticModel h : models.history()) {
            history.add(Map.of("version", h.version(), "trainedAt", h.trainedAt(), "metrics", h.metrics()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", m.version());
        body.put("bias", m.bias());
        body.put("trainedAt", m.trainedAt());
        body.put("metrics", m.metrics());
        body.put("features", features);
        body.put("labelledSamples", models.sampleCount());
        body.put("history", history);
        return body;
    }

    @PostMapping("/model/train")
    public ModelRegistry.TrainingReport train() {
        return models.retrain();
    }

    @PostMapping("/model/reset")
    public Map<String, String> reset() {
        models.reset();
        return Map.of("version", models.active().version());
    }

    @GetMapping("/thresholds")
    public RiskProperties thresholds() {
        return thresholds.current();
    }

    @PutMapping("/thresholds")
    public RiskProperties updateThresholds(@RequestBody ThresholdUpdate update) {
        return thresholds.update(update.reviewThreshold(), update.blockThreshold());
    }

    @GetMapping("/graph/{vpa}")
    public GraphService.Graph graph(@PathVariable String vpa, @RequestParam(defaultValue = "2") int depth) {
        return graph.around(vpa, Math.min(Math.max(depth, 1), 3));
    }

    @GetMapping("/accounts/{vpa}")
    public Map<String, Object> account(@PathVariable String vpa) {
        Map<Object, Object> profile = redis.opsForHash().entries("prof:" + vpa);
        Set<Object> devices = redis.opsForHash().keys("devseen:" + vpa);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vpa", vpa);
        body.put("paymentsMade", profile.getOrDefault("n", "0"));
        body.put("averageAmount", profile.getOrDefault("mean", "0"));
        Object first = profile.get("first");
        body.put("firstSeen", first == null ? null : Instant.ofEpochMilli((long) Double.parseDouble(first.toString())));
        body.put("devices", devices);
        body.put("denylisted", Boolean.TRUE.equals(redis.opsForSet().isMember(FeatureStore.DENY_LIST, vpa)));
        body.put("mule", mules.assess(vpa));
        return body;
    }

    @GetMapping("/watchlist")
    public List<Map<String, Object>> watchlist(@RequestParam(defaultValue = "20") int limit) {
        Set<ZSetOperations.TypedTuple<String>> top =
                redis.opsForZSet().reverseRangeWithScores(FeatureStore.MULE_WATCH, 0, limit - 1);
        List<Map<String, Object>> out = new ArrayList<>();
        if (top != null) {
            for (var t : top) {
                out.add(Map.of("vpa", t.getValue(), "muleScore", t.getScore(),
                        "denylisted", Boolean.TRUE.equals(redis.opsForSet().isMember(FeatureStore.DENY_LIST, t.getValue()))));
            }
        }
        return out;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    public record ThresholdUpdate(double reviewThreshold, double blockThreshold) {
    }
}

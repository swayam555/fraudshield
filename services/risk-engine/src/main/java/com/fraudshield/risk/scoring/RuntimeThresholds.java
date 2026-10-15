package com.fraudshield.risk.scoring;

import com.fraudshield.risk.config.RiskProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Decision thresholds that risk operations can tune at runtime (for example, tightening during a
 * festival-season scam wave). Stored in Redis so all replicas agree.
 */
@Component
public class RuntimeThresholds {

    private static final String KEY = "risk:thresholds";
    private final StringRedisTemplate redis;
    private final RiskProperties defaults;
    private final AtomicReference<RiskProperties> current;

    public RuntimeThresholds(StringRedisTemplate redis, RiskProperties defaults) {
        this.redis = redis;
        this.defaults = defaults;
        this.current = new AtomicReference<>(defaults);
    }

    public RiskProperties current() {
        return current.get();
    }

    public RiskProperties update(double review, double block) {
        if (!(0 < review && review < block && block <= 1)) {
            throw new IllegalArgumentException("Thresholds must satisfy 0 < review < block <= 1");
        }
        redis.opsForHash().putAll(KEY, Map.of("review", String.valueOf(review), "block", String.valueOf(block)));
        RiskProperties updated = with(review, block);
        current.set(updated);
        return updated;
    }

    @Scheduled(fixedDelay = 5000)
    public void refresh() {
        Object review = redis.opsForHash().get(KEY, "review");
        Object block = redis.opsForHash().get(KEY, "block");
        if (review != null && block != null) {
            current.set(with(Double.parseDouble(review.toString()), Double.parseDouble(block.toString())));
        }
    }

    private RiskProperties with(double review, double block) {
        return new RiskProperties(review, block, defaults.microAmount(), defaults.muleAlertScore());
    }
}

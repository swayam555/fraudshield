package com.fraudshield.risk.scoring;

import com.fraudshield.contracts.Channel;
import com.fraudshield.contracts.ScoreRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores a few hundred throw-away payments before the instance reports ready, so the JIT, the
 * Lua script cache and the connection pool are hot. Without this the first real payments after
 * a deploy blow the 300 ms latency budget and fall back to the degraded policy.
 */
@Component
public class WarmUp implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WarmUp.class);
    private static final String PREFIX = "warmup.";

    private final ScoringService scoring;
    private final StringRedisTemplate redis;

    public WarmUp(ScoringService scoring, StringRedisTemplate redis) {
        this.scoring = scoring;
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            scoring.score(new ScoreRequest("warmup-txn-" + i, PREFIX + (i % 7) + "@fs", PREFIX + (i % 11) + "@fs",
                    BigDecimal.valueOf(100 + i), Channel.UPI_INTENT, "warmup-device-" + (i % 3), 19.0, 72.8,
                    Instant.now(), null));
        }
        try {
            Thread.sleep(500); // let the asynchronous feature-vector writes land before cleaning up
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<String> keys = new ArrayList<>();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match("*warmup*").count(1000).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) redis.delete(keys);
        log.info("Warm-up done in {} ms ({} scratch keys removed)", System.currentTimeMillis() - start, keys.size());
    }
}

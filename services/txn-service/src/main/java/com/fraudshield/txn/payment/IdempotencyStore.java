package com.fraudshield.txn.payment;

import com.fraudshield.txn.config.JsonCodec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Makes {@code POST /api/payments} safe to retry. A mobile app on a flaky 4G connection will
 * resend the same request; with an {@code Idempotency-Key} header the customer is charged once
 * and every retry gets the original answer back.
 */
@Component
public class IdempotencyStore {

    private static final String PENDING = "__PENDING__";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final JsonCodec json;

    public IdempotencyStore(StringRedisTemplate redis, JsonCodec json) {
        this.redis = redis;
        this.json = json;
    }

    /** Claims the key. Empty means "go ahead"; otherwise the earlier outcome. */
    public Optional<Outcome> claim(String key) {
        Boolean claimed = redis.opsForValue().setIfAbsent(redisKey(key), PENDING, TTL);
        if (Boolean.TRUE.equals(claimed)) return Optional.empty();
        String stored = redis.opsForValue().get(redisKey(key));
        if (stored == null || PENDING.equals(stored)) return Optional.of(new Outcome(null, true));
        return Optional.of(new Outcome(json.read(stored, PaymentResult.class), false));
    }

    public void complete(String key, PaymentResult result) {
        redis.opsForValue().set(redisKey(key), json.write(result), TTL);
    }

    public void release(String key) {
        redis.delete(redisKey(key));
    }

    private static String redisKey(String key) {
        return "idem:" + key;
    }

    public record Outcome(PaymentResult previous, boolean inFlight) {
    }
}

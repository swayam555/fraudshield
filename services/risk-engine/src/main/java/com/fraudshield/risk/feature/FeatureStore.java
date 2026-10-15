package com.fraudshield.risk.feature;

import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.risk.config.RiskProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Real-time feature store on Redis.
 *
 * <p>All reads and writes for one payment happen inside a single Lua script, so a payment costs one
 * network round trip and concurrent payments from the same account are serialised by Redis.
 */
@Component
public class FeatureStore {

    public static final String DENY_LIST = "risk:deny";
    public static final String MULE_WATCH = "risk:watch";

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;
    private final RedisScript<Long> learn;
    private final StringRedisTemplate redis;
    private final RiskProperties props;

    public FeatureStore(StringRedisTemplate redis, RiskProperties props) throws IOException {
        this.redis = redis;
        this.props = props;
        this.script = new DefaultRedisScript<>(
                new ClassPathResource("scripts/features.lua").getContentAsString(StandardCharsets.UTF_8), List.class);
        this.learn = new DefaultRedisScript<>(
                new ClassPathResource("scripts/learn.lua").getContentAsString(StandardCharsets.UTF_8), Long.class);
    }

    /**
     * Learns the payer's trusted profile (usual amount, last location, known devices and payees)
     * from a payment that was approved. Never call it for blocked or held payments: their device,
     * location and amount must stay "unusual" for the next attempt.
     */
    public void learnApproved(ScoreRequest r) {
        redis.execute(learn, List.of("prof:" + r.payerVpa(), "devseen:" + r.payerVpa(), "payeeseen:" + r.payerVpa()),
                String.valueOf(r.timestamp().toEpochMilli()),
                r.amount().toPlainString(),
                r.deviceId(),
                r.lat() == null ? "" : r.lat().toString(),
                r.lon() == null ? "" : r.lon().toString(),
                r.payeeVpa());
    }

    public RawSignals readAndRecord(ScoreRequest r) {
        long now = r.timestamp().toEpochMilli();
        long hour = now / 3_600_000L;
        List<String> keys = List.of(
                "vel:" + r.payerVpa(),
                "prof:" + r.payerVpa(),
                "devseen:" + r.payerVpa(),
                "payeeseen:" + r.payerVpa(),
                "hll:in:" + r.payeeVpa() + ":" + hour,
                "hll:in:" + r.payeeVpa() + ":" + (hour - 1),
                "hll:out:" + r.payerVpa() + ":" + hour,
                "hll:out:" + r.payerVpa() + ":" + (hour - 1),
                "inflow:" + r.payerVpa(),
                "inflow:" + r.payeeVpa(),
                "outflow:" + r.payerVpa(),
                "devacc:" + r.deviceId(),
                "micro:" + r.payerVpa(),
                "prof:" + r.payeeVpa(),
                DENY_LIST,
                MULE_WATCH,
                "outflow:" + r.payeeVpa());

        @SuppressWarnings("unchecked")
        List<String> out = redis.execute(script, keys,
                r.txnId(),
                String.valueOf(now),
                r.amount().toPlainString(),
                r.deviceId(),
                r.lat() == null ? "" : r.lat().toString(),
                r.lon() == null ? "" : r.lon().toString(),
                r.payerVpa(),
                r.payeeVpa(),
                String.valueOf(props.microAmount()));

        return new RawSignals(
                l(out.get(0)), l(out.get(1)), l(out.get(2)), d(out.get(3)), d(out.get(4)),
                nd(out.get(5)), nd(out.get(6)), nl(out.get(7)),
                nl(out.get(8)), l(out.get(9)), nl(out.get(10)),
                l(out.get(11)), l(out.get(12)), d(out.get(13)), d(out.get(14)),
                l(out.get(15)), l(out.get(16)), nl(out.get(17)), nl(out.get(18)),
                "1".equals(out.get(19)), d(out.get(20)), d(out.get(21)));
    }

    private static long l(String s) {
        return (long) Double.parseDouble(s);
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    private static Double nd(String s) {
        return s == null || s.isEmpty() ? null : Double.valueOf(s);
    }

    private static Long nl(String s) {
        return s == null || s.isEmpty() ? null : (long) Double.parseDouble(s);
    }
}

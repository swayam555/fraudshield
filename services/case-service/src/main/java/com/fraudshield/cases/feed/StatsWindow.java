package com.fraudshield.cases.feed;

import com.fraudshield.contracts.Decision;
import com.fraudshield.contracts.PaymentScored;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rolling 60-second window of decision KPIs, bucketed per second. Cheap enough to update on
 * every event and to snapshot every second.
 */
@Component
public class StatsWindow {

    private static final int SECONDS = 60;
    private final Bucket[] buckets = new Bucket[SECONDS];
    private long totalPayments;
    private double totalBlockedAmount;
    private long fraudCaught;
    private long fraudMissed;
    private long legitFlagged;
    private long legitPassed;

    public StatsWindow() {
        for (int i = 0; i < SECONDS; i++) buckets[i] = new Bucket();
    }

    public synchronized void record(PaymentScored p) {
        long second = System.currentTimeMillis() / 1000;
        Bucket b = buckets[(int) (second % SECONDS)];
        if (b.second != second) b.reset(second);
        b.count[p.decision().ordinal()]++;
        b.amount[p.decision().ordinal()] += p.amount().doubleValue();
        if (b.latencies.size() < 2000) b.latencies.add(p.latencyMillis());
        if (p.degraded()) b.degraded++;

        totalPayments++;
        if (p.decision() == Decision.BLOCK) totalBlockedAmount += p.amount().doubleValue();
        if (p.simLabel() != null) {
            boolean fraud = !"LEGIT".equals(p.simLabel());
            boolean flagged = p.decision() != Decision.APPROVE;
            if (fraud && flagged) fraudCaught++;
            else if (fraud) fraudMissed++;
            else if (flagged) legitFlagged++;
            else legitPassed++;
        }
    }

    public synchronized Map<String, Object> snapshot() {
        long now = System.currentTimeMillis() / 1000;
        long[] count = new long[Decision.values().length];
        double[] amount = new double[Decision.values().length];
        List<Long> latencies = new ArrayList<>();
        long degraded = 0;
        List<Map<String, Object>> series = new ArrayList<>();
        for (long s = now - SECONDS + 1; s <= now; s++) {
            Bucket b = buckets[(int) (s % SECONDS)];
            boolean live = b.second == s;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("t", s * 1000);
            for (Decision d : Decision.values()) {
                long c = live ? b.count[d.ordinal()] : 0;
                point.put(d.name(), c);
                count[d.ordinal()] += c;
                if (live) amount[d.ordinal()] += b.amount[d.ordinal()];
            }
            if (live) {
                latencies.addAll(b.latencies);
                degraded += b.degraded;
            }
            series.add(point);
        }
        long total = Arrays.stream(count).sum();
        long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowSeconds", SECONDS);
        out.put("tps", Math.round(total / (double) SECONDS * 10) / 10.0);
        out.put("approved", count[Decision.APPROVE.ordinal()]);
        out.put("reviewed", count[Decision.REVIEW.ordinal()]);
        out.put("blocked", count[Decision.BLOCK.ordinal()]);
        out.put("blockedAmount", Math.round(amount[Decision.BLOCK.ordinal()]));
        out.put("reviewedAmount", Math.round(amount[Decision.REVIEW.ordinal()]));
        out.put("p50LatencyMs", percentile(sorted, 0.50));
        out.put("p95LatencyMs", percentile(sorted, 0.95));
        out.put("p99LatencyMs", percentile(sorted, 0.99));
        out.put("degraded", degraded);
        out.put("series", series);
        out.put("totalPayments", totalPayments);
        out.put("totalBlockedAmount", Math.round(totalBlockedAmount));
        long fraudTotal = fraudCaught + fraudMissed;
        long legitTotal = legitFlagged + legitPassed;
        out.put("groundTruth", Map.of(
                "fraudCaught", fraudCaught,
                "fraudMissed", fraudMissed,
                "legitFlagged", legitFlagged,
                "legitPassed", legitPassed,
                "detectionRate", fraudTotal == 0 ? 0 : Math.round(1000.0 * fraudCaught / fraudTotal) / 10.0,
                "falsePositiveRate", legitTotal == 0 ? 0 : Math.round(10000.0 * legitFlagged / legitTotal) / 100.0));
        return out;
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
    }

    private static final class Bucket {
        long second = -1;
        final long[] count = new long[Decision.values().length];
        final double[] amount = new double[Decision.values().length];
        final List<Long> latencies = new ArrayList<>();
        long degraded;

        void reset(long s) {
            second = s;
            Arrays.fill(count, 0);
            Arrays.fill(amount, 0);
            latencies.clear();
            degraded = 0;
        }
    }
}

package com.fraudshield.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Raised when an account's behaviour across many payments looks like a money mule. */
public record MuleAlert(
        String alertId,
        String vpa,
        double muleScore,
        Map<String, Double> signals,
        List<String> recentTxnIds,
        Instant raisedAt) {
}

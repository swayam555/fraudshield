package com.fraudshield.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Published to {@link Topics#PAYMENTS_SCORED} through the transactional outbox. */
public record PaymentScored(
        String txnId,
        String payerVpa,
        String payeeVpa,
        BigDecimal amount,
        Channel channel,
        String deviceId,
        Double lat,
        Double lon,
        Instant timestamp,
        Decision decision,
        double riskScore,
        List<Reason> reasons,
        String modelVersion,
        long latencyMillis,
        boolean degraded,
        String simLabel) {
}

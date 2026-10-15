package com.fraudshield.txn.payment;

import com.fraudshield.contracts.Decision;
import com.fraudshield.contracts.Reason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentResult(
        String txnId,
        String payerVpa,
        String payeeVpa,
        BigDecimal amount,
        Decision decision,
        double riskScore,
        List<Reason> reasons,
        String modelVersion,
        long latencyMs,
        boolean degraded,
        Instant createdAt) {
}

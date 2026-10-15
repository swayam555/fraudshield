package com.fraudshield.contracts;

import java.math.BigDecimal;
import java.time.Instant;

/** Synchronous scoring request sent from txn-service to risk-engine on the payment's hot path. */
public record ScoreRequest(
        String txnId,
        String payerVpa,
        String payeeVpa,
        BigDecimal amount,
        Channel channel,
        String deviceId,
        Double lat,
        Double lon,
        Instant timestamp,
        /* Ground truth for synthetic traffic only; used to label training samples, never to score. */
        String simLabel) {
}

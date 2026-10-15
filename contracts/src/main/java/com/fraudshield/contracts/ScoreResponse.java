package com.fraudshield.contracts;

import java.util.List;

public record ScoreResponse(
        String txnId,
        Decision decision,
        double riskScore,
        List<Reason> reasons,
        String modelVersion,
        long engineMicros) {
}

package com.fraudshield.cases.cases;

import com.fraudshield.contracts.Reason;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CaseModels {

    private CaseModels() {
    }

    public record CaseRow(long id, String subjectVpa, String caseType, String typology, String status, String priority,
                          double riskScore, BigDecimal totalAmount, int paymentCount, String assignee,
                          String groundTruth, String aiSummary, String aiSummaryBy, OffsetDateTime aiSummaryAt,
                          String resolution, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                          OffsetDateTime slaDueAt) {
    }

    public record CasePaymentRow(String txnId, String payerVpa, String payeeVpa, BigDecimal amount, String decision,
                                 double riskScore, String reasons, String deviceId, String simLabel,
                                 OffsetDateTime paidAt) {
    }

    public record CasePayment(String txnId, String payerVpa, String payeeVpa, BigDecimal amount, String decision,
                              double riskScore, List<Reason> reasons, String deviceId, String simLabel,
                              OffsetDateTime paidAt) {
    }

    public record CaseEvent(long id, OffsetDateTime at, String actor, String eventType, String message) {
    }

    public record CaseDetail(CaseRow kase, String typologyLabel, List<CasePayment> payments, List<CaseEvent> timeline) {
    }

    public record QueueStats(long open, long inReview, long resolvedFraud, long resolvedLegit, long p1Open,
                             long breachedSla) {
    }
}

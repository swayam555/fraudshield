package com.fraudshield.contracts;

/** Kafka topic names. Every topic is keyed by the VPA it is about, so per-account ordering is preserved. */
public final class Topics {
    /** Every payment after it has been scored (key: payer VPA). */
    public static final String PAYMENTS_SCORED = "payments.scored";
    /** Mule-account alerts raised by the graph analyser (key: suspect VPA). */
    public static final String RISK_ALERTS = "risk.alerts";
    /** Analyst verdicts fed back into the risk engine (key: subject VPA). */
    public static final String CASE_LABELS = "case.labels";

    private Topics() {
    }
}

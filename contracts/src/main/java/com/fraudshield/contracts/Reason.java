package com.fraudshield.contracts;

/**
 * One explainable signal behind a risk score.
 *
 * @param code         stable machine-readable code, e.g. {@code PAYEE_FAN_IN}
 * @param description  human-readable explanation shown to analysts
 * @param contribution how much this signal moved the log-odds of fraud
 */
public record Reason(String code, String description, double contribution) {
}

package com.fraudshield.contracts;

import java.time.Instant;
import java.util.List;

/**
 * Analyst verdict on a case, consumed by the risk engine to update deny-lists and training labels.
 *
 * @param subjectVpa the account the case is about (a mule, or a victim whose account was taken over)
 * @param denyVpas   accounts to deny-list on a FRAUD verdict: the mule itself, or the beneficiaries
 *                   that received a victim's money. Never the victim.
 */
public record CaseLabel(
        String caseId,
        String subjectVpa,
        List<String> denyVpas,
        List<String> txnIds,
        Verdict verdict,
        String analyst,
        Instant labelledAt) {

    public enum Verdict { FRAUD, LEGIT }
}

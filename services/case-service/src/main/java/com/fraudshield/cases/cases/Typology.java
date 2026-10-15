package com.fraudshield.cases.cases;

import com.fraudshield.contracts.Reason;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps the risk engine's reason codes to the fraud pattern an investigator should work from. */
public enum Typology {
    MONEY_MULE("Money mule / layering", Set.of("PAYEE_FAN_IN", "PASS_THROUGH", "PAYEE_WATCHLIST",
            "PAYEE_DENYLISTED", "SHARED_DEVICE", "PAYEE_NEW_ACCOUNT")),
    ACCOUNT_TAKEOVER("Account takeover", Set.of("NEW_DEVICE", "IMPOSSIBLE_TRAVEL", "PAYER_VELOCITY_1M",
            "PAYER_VELOCITY_1H")),
    ACCOUNT_PROBING("UPI ID probing", Set.of("MICRO_PROBING", "PAYER_FAN_OUT")),
    SOCIAL_ENGINEERING_SCAM("Social-engineering scam", Set.of("AMOUNT_ANOMALY", "NEW_PAYEE", "ODD_HOUR",
            "AMOUNT_SIZE")),
    SUSPICIOUS_ACTIVITY("Suspicious activity", Set.of());

    private static final Set<String> PAYEE_CENTRIC = Set.of("PAYEE_FAN_IN", "PAYEE_WATCHLIST", "PAYEE_DENYLISTED");

    private final String label;
    private final Set<String> codes;

    Typology(String label, Set<String> codes) {
        this.label = label;
        this.codes = codes;
    }

    public String label() {
        return label;
    }

    public static Typology classify(List<Reason> reasons) {
        Map<Typology, Double> weight = new EnumMap<>(Typology.class);
        for (Reason r : reasons) {
            for (Typology t : values()) {
                if (t.codes.contains(r.code())) weight.merge(t, r.contribution(), Double::sum);
            }
        }
        return weight.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(SUSPICIOUS_ACTIVITY);
    }

    /** Payee-side signals make the payee the subject (a suspected mule); otherwise the payer is. */
    public static boolean payeeIsSubject(List<Reason> reasons) {
        return reasons.stream().anyMatch(r -> PAYEE_CENTRIC.contains(r.code()));
    }

    /** In these typologies the case subject is the victim, so a FRAUD verdict deny-lists the beneficiaries. */
    public boolean subjectIsVictim() {
        return this == ACCOUNT_TAKEOVER || this == SOCIAL_ENGINEERING_SCAM;
    }
}

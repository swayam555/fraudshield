package com.fraudshield.cases;

import com.fraudshield.cases.cases.Typology;
import com.fraudshield.contracts.Reason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaseRulesTest {

    private static Reason r(String code, double c) {
        return new Reason(code, code, c);
    }

    @Test
    void classifiesByStrongestSignalGroup() {
        assertThat(Typology.classify(List.of(r("NEW_DEVICE", 1.4), r("IMPOSSIBLE_TRAVEL", 3), r("NEW_PAYEE", 0.8))))
                .isEqualTo(Typology.ACCOUNT_TAKEOVER);
        assertThat(Typology.classify(List.of(r("AMOUNT_ANOMALY", 3.9), r("NEW_PAYEE", 0.8))))
                .isEqualTo(Typology.SOCIAL_ENGINEERING_SCAM);
        assertThat(Typology.classify(List.of(r("MICRO_PROBING", 2), r("PAYER_FAN_OUT", 1))))
                .isEqualTo(Typology.ACCOUNT_PROBING);
        assertThat(Typology.classify(List.of())).isEqualTo(Typology.SUSPICIOUS_ACTIVITY);
    }

    @Test
    void payeeSignalsMakeThePayeeTheSubject() {
        assertThat(Typology.payeeIsSubject(List.of(r("PAYEE_FAN_IN", 2)))).isTrue();
        assertThat(Typology.payeeIsSubject(List.of(r("NEW_DEVICE", 2)))).isFalse();
    }

    @Test
    void victimTypologiesNeverDenyListTheVictim() {
        assertThat(Typology.ACCOUNT_TAKEOVER.subjectIsVictim()).isTrue();
        assertThat(Typology.SOCIAL_ENGINEERING_SCAM.subjectIsVictim()).isTrue();
        assertThat(Typology.MONEY_MULE.subjectIsVictim()).isFalse();
    }
}

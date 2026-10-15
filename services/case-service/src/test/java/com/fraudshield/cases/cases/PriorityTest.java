package com.fraudshield.cases.cases;

import com.fraudshield.contracts.Decision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityTest {

    @Test
    void largeBlocksAndLargeScamsArePageWorthy() {
        assertThat(CaseService.priority(Decision.BLOCK, new BigDecimal("75000"), 0.9, Typology.MONEY_MULE)).isEqualTo("P1");
        assertThat(CaseService.priority(Decision.REVIEW, new BigDecimal("95000"), 0.6,
                Typology.SOCIAL_ENGINEERING_SCAM)).isEqualTo("P1");
    }

    @Test
    void smallerRiskIsQueued() {
        assertThat(CaseService.priority(Decision.BLOCK, new BigDecimal("500"), 0.99, Typology.ACCOUNT_PROBING)).isEqualTo("P2");
        assertThat(CaseService.priority(Decision.REVIEW, new BigDecimal("12000"), 0.6, Typology.MONEY_MULE)).isEqualTo("P2");
        assertThat(CaseService.priority(Decision.REVIEW, new BigDecimal("900"), 0.55, Typology.MONEY_MULE)).isEqualTo("P3");
    }
}

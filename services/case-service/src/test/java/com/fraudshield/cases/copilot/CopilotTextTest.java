package com.fraudshield.cases.copilot;

import com.fraudshield.cases.kb.Passage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotTextTest {

    @Test
    void sentenceSplitterKeepsDomainsIntact() {
        String text = "Report on cybercrime.gov.in as soon as possible. Speed matters.";
        assertThat(CopilotService.firstSentences(text, 1)).isEqualTo("Report on cybercrime.gov.in as soon as possible.");
    }

    @Test
    void extractsNumberedAndBulletedSteps() {
        assertThat(CopilotService.listItems("Intro\n1. Do this.\n2. Then that.\n- And this."))
                .containsExactly("Do this.", "Then that.", "And this.");
    }

    @Test
    void extractiveAnswerCitesTheMostRelevantPassage() {
        List<Passage> passages = List.of(
                new Passage(1, "04", "Reporting", "STR", "A Suspicious Transaction Report must be filed with "
                        + "FIU-IND within seven working days of the suspicion being established.", null, 1, 1, 0.03),
                new Passage(2, "08", "Limits", "UPI", "The standard UPI limit is one lakh rupees per transaction.",
                        null, 2, null, 0.01));
        String answer = CopilotService.extractiveAnswer("When must the suspicious transaction report be filed?", passages);
        assertThat(answer).contains("seven working days").contains("[1]");
    }

    @Test
    void knowledgeBlockNumbersPassagesForCitation() {
        List<Passage> passages = List.of(new Passage(9, "d", "Doc", "Sec", "Body", null, 1, null, 0.016));
        assertThat(CopilotService.knowledgeBlock(passages)).startsWith("[1] Doc - Sec\nBody");
        assertThat(CopilotService.citations(passages).get(0).n()).isEqualTo(1);
    }
}

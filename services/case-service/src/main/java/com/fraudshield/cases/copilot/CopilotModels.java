package com.fraudshield.cases.copilot;

import java.util.List;

public final class CopilotModels {

    private CopilotModels() {
    }

    public record Citation(int n, String docId, String docTitle, String section, String snippet, String source,
                           Integer denseRank, Integer lexicalRank, double score) {
    }

    /**
     * @param generatedBy which model wrote the answer, or "rule-based fallback"
     * @param llm         true when a language model produced the text
     */
    public record Answer(String answer, List<Citation> citations, String generatedBy, boolean llm, long latencyMs) {
    }

    public record AskRequest(String question, Long caseId) {
    }
}

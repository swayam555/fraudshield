package com.fraudshield.cases.copilot;

import com.fraudshield.cases.kb.Embedder;
import com.fraudshield.cases.kb.HybridRetriever;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/copilot")
public class CopilotController {

    private final CopilotService copilot;
    private final LlmClient llm;
    private final HybridRetriever retriever;

    public CopilotController(CopilotService copilot, LlmClient llm, HybridRetriever retriever) {
        this.copilot = copilot;
        this.llm = llm;
        this.retriever = retriever;
    }

    @PostMapping("/cases/{id}/summary")
    public CopilotModels.Answer summary(@PathVariable long id,
                                        @RequestHeader(value = "X-User-Id", defaultValue = "system") String user) {
        return copilot.summarize(id, user);
    }

    @PostMapping("/ask")
    public CopilotModels.Answer ask(@RequestBody CopilotModels.AskRequest req) {
        return copilot.ask(req.question(), req.caseId());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("llmEnabled", llm.enabled());
        body.put("chatModel", llm.description());
        body.put("embeddingModel", Embedder.MODEL_NAME);
        body.put("retrieval", "hybrid: pgvector HNSW (cosine) + Postgres full-text, fused with RRF (k=60)");
        body.put("knowledgeChunks", retriever.chunkCount());
        return body;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail bad(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}

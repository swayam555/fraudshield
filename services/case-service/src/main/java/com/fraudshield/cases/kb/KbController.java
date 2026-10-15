package com.fraudshield.cases.kb;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final HybridRetriever retriever;

    public KbController(HybridRetriever retriever) {
        this.retriever = retriever;
    }

    @GetMapping("/search")
    public List<Passage> search(@RequestParam String q, @RequestParam(defaultValue = "6") int k) {
        return retriever.search(q, Math.min(Math.max(k, 1), 20));
    }

    @GetMapping("/documents")
    public List<Map<String, Object>> documents() {
        return retriever.documents();
    }
}

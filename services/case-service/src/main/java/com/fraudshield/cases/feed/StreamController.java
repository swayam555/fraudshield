package com.fraudshield.cases.feed;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final LiveFeed feed;
    private final StatsWindow stats;

    public StreamController(LiveFeed feed, StatsWindow stats) {
        this.feed = feed;
        this.stats = stats;
    }

    @GetMapping(path = "/live", produces = "text/event-stream")
    public SseEmitter live() {
        return feed.subscribe();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return stats.snapshot();
    }
}

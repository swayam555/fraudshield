package com.fraudshield.risk.api;

import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.contracts.ScoreResponse;
import com.fraudshield.risk.scoring.ScoringService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Hot-path scoring endpoint. Internal only: the gateway does not route {@code /internal/**}. */
@RestController
public class ScoreController {

    private final ScoringService scoring;

    public ScoreController(ScoringService scoring) {
        this.scoring = scoring;
    }

    @PostMapping("/internal/score")
    public ScoreResponse score(@RequestBody ScoreRequest request) {
        return scoring.score(request);
    }
}

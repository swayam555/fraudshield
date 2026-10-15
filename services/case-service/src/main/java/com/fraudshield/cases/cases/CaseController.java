package com.fraudshield.cases.cases;

import com.fraudshield.contracts.CaseLabel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/cases")
@Validated
public class CaseController {

    private final CaseService cases;
    private final CaseRepository repo;

    public CaseController(CaseService cases, CaseRepository repo) {
        this.cases = cases;
        this.repo = repo;
    }

    @GetMapping
    public List<CaseModels.CaseRow> list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String priority,
                                         @RequestParam(required = false) String typology,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "50") int limit,
                                         @RequestParam(defaultValue = "0") int offset) {
        return repo.list(status, priority, typology, q, Math.min(limit, 200), Math.max(offset, 0));
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("queue", repo.stats(), "typologies24h", repo.typologyBreakdown());
    }

    @GetMapping("/{id}")
    public CaseModels.CaseDetail detail(@PathVariable long id) {
        return cases.detail(id);
    }

    @PostMapping("/{id}/assign")
    public CaseModels.CaseDetail assign(@PathVariable long id, @RequestHeader("X-User-Id") String user) {
        return cases.assign(id, user);
    }

    @PostMapping("/{id}/notes")
    public CaseModels.CaseDetail note(@PathVariable long id, @RequestHeader("X-User-Id") String user,
                                      @RequestBody NoteRequest req) {
        return cases.note(id, user, req.text());
    }

    @PostMapping("/{id}/resolve")
    public CaseModels.CaseDetail resolve(@PathVariable long id, @RequestHeader("X-User-Id") String user,
                                         @RequestBody ResolveRequest req) {
        return cases.resolve(id, req.verdict(), user, req.note());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    public record NoteRequest(@NotBlank String text) {
    }

    public record ResolveRequest(@NotNull CaseLabel.Verdict verdict, String note) {
    }
}

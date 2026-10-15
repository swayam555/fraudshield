package com.fraudshield.txn.api;

import com.fraudshield.contracts.Decision;
import com.fraudshield.txn.outbox.OutboxRepository;
import com.fraudshield.txn.payment.PaymentRepository;
import com.fraudshield.txn.payment.PaymentRequest;
import com.fraudshield.txn.payment.PaymentResult;
import com.fraudshield.txn.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;
    private final PaymentRepository payments;
    private final OutboxRepository outbox;

    public PaymentController(PaymentService service, PaymentRepository payments, OutboxRepository outbox) {
        this.service = service;
        this.payments = payments;
        this.outbox = outbox;
    }

    @Operation(summary = "Submit a UPI payment for a real-time fraud decision")
    @PostMapping
    public ResponseEntity<PaymentResult> submit(@Valid @RequestBody PaymentRequest request,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        PaymentResult result = service.submit(request, key);
        HttpStatus status = result.decision() == Decision.APPROVE ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping
    public List<PaymentResult> recent(@RequestParam(defaultValue = "50") int limit) {
        return payments.findByCreatedAtAfterOrderByCreatedAtDesc(Instant.now().minus(Duration.ofDays(1)),
                        PageRequest.of(0, Math.min(limit, 500)))
                .stream().map(service::toResult).toList();
    }

    @GetMapping("/{txnId}")
    public ResponseEntity<PaymentResult> one(@PathVariable String txnId) {
        return payments.findById(txnId).map(service::toResult).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/account/{vpa}")
    public List<PaymentResult> byAccount(@PathVariable String vpa, @RequestParam(defaultValue = "50") int limit) {
        return payments.findByAccount(vpa.toLowerCase(), PageRequest.of(0, Math.min(limit, 200)))
                .stream().map(service::toResult).toList();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(defaultValue = "15") int minutes) {
        Map<String, Object> byDecision = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : payments.decisionStats(Instant.now().minus(Duration.ofMinutes(minutes)))) {
            long count = ((Number) row[1]).longValue();
            total += count;
            byDecision.put((String) row[0], Map.of(
                    "count", count,
                    "amount", new BigDecimal(row[2].toString()),
                    "avgLatencyMs", ((Number) row[3]).doubleValue(),
                    "p95LatencyMs", ((Number) row[4]).doubleValue()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windowMinutes", minutes);
        body.put("total", total);
        body.put("byDecision", byDecision);
        body.put("outboxBacklog", outbox.countByPublishedAtIsNull());
        return body;
    }
}

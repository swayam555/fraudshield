package com.fraudshield.txn.payment;

import com.fraudshield.contracts.Channel;
import com.fraudshield.contracts.PaymentScored;
import com.fraudshield.contracts.Reason;
import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.contracts.ScoreResponse;
import com.fraudshield.contracts.Topics;
import com.fraudshield.txn.config.JsonCodec;
import com.fraudshield.txn.outbox.OutboxEvent;
import com.fraudshield.txn.outbox.OutboxRepository;
import com.fraudshield.txn.risk.RiskClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final TypeReference<List<Reason>> REASONS = new TypeReference<>() {
    };

    private final RiskClient risk;
    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final IdempotencyStore idempotency;
    private final TransactionTemplate tx;
    private final JsonCodec json;
    private final Timer timer;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(RiskClient risk, PaymentRepository payments, OutboxRepository outbox,
                          IdempotencyStore idempotency, TransactionTemplate tx, JsonCodec json, MeterRegistry meters) {
        this.risk = risk;
        this.payments = payments;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.tx = tx;
        this.json = json;
        this.timer = Timer.builder("payments.decision.latency").publishPercentiles(0.5, 0.95, 0.99).register(meters);
    }

    public PaymentResult submit(PaymentRequest req, String idempotencyKey) {
        if (idempotencyKey == null) {
            return process(req, null, Instant.now(), null);
        }
        var previous = idempotency.claim(idempotencyKey);
        if (previous.isPresent()) {
            if (previous.get().inFlight()) throw new DuplicateInFlightException(idempotencyKey);
            return previous.get().previous();
        }
        try {
            PaymentResult result = process(req, idempotencyKey, Instant.now(), null);
            idempotency.complete(idempotencyKey, result);
            return result;
        } catch (RuntimeException e) {
            idempotency.release(idempotencyKey);
            throw e;
        }
    }

    /**
     * Scores and records one payment. The risk call happens outside the database transaction so
     * a slow dependency never holds a connection; the payment row and its outbox event are then
     * committed atomically.
     */
    public PaymentResult process(PaymentRequest req, String idempotencyKey, Instant at, String simLabel) {
        if (req.payerVpa().equalsIgnoreCase(req.payeeVpa())) {
            throw new IllegalArgumentException("Payer and payee must be different accounts");
        }
        long start = System.nanoTime();
        String txnId = newTxnId();
        Channel channel = req.channel() == null ? Channel.UPI_INTENT : req.channel();

        RiskClient.Scored scored = risk.score(new ScoreRequest(txnId, req.payerVpa().toLowerCase(),
                req.payeeVpa().toLowerCase(), req.amount(), channel, req.deviceId(), req.lat(), req.lon(), at, simLabel));
        ScoreResponse r = scored.response();
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        Payment payment = new Payment(txnId, idempotencyKey, req.payerVpa().toLowerCase(), req.payeeVpa().toLowerCase(),
                req.amount(), channel, req.deviceId(), req.lat(), req.lon(), req.note(), r.decision(), r.riskScore(),
                json.write(r.reasons()), r.modelVersion(), latencyMs, scored.degraded(), simLabel, at);
        PaymentScored event = new PaymentScored(txnId, payment.getPayerVpa(), payment.getPayeeVpa(), req.amount(),
                channel, req.deviceId(), req.lat(), req.lon(), at, r.decision(), r.riskScore(), r.reasons(),
                r.modelVersion(), latencyMs, scored.degraded(), simLabel);

        tx.executeWithoutResult(status -> {
            payments.save(payment);
            outbox.save(new OutboxEvent(Topics.PAYMENTS_SCORED, payment.getPayerVpa(), json.write(event)));
        });
        timer.record(java.time.Duration.ofNanos(System.nanoTime() - start));
        return toResult(payment);
    }

    public PaymentResult toResult(Payment p) {
        return new PaymentResult(p.getTxnId(), p.getPayerVpa(), p.getPayeeVpa(), p.getAmount(), p.getDecision(),
                p.getRiskScore(), json.read(p.getReasons(), REASONS), p.getModelVersion(), p.getLatencyMs(),
                p.isDegraded(), p.getCreatedAt());
    }

    /** Sortable, URL-safe id: 9 chars of millisecond time + 7 random chars (Crockford base32). */
    String newTxnId() {
        StringBuilder sb = new StringBuilder("TXN");
        long t = System.currentTimeMillis();
        char[] time = new char[9];
        for (int i = 8; i >= 0; i--) {
            time[i] = ALPHABET[(int) (t & 31)];
            t >>>= 5;
        }
        sb.append(time);
        for (int i = 0; i < 7; i++) sb.append(ALPHABET[random.nextInt(32)]);
        return sb.toString();
    }

    public static class DuplicateInFlightException extends RuntimeException {
        public DuplicateInFlightException(String key) {
            super("A payment with Idempotency-Key " + key + " is already being processed");
        }
    }
}

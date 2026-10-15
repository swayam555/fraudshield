package com.fraudshield.cases.cases;

import com.fraudshield.cases.config.JsonCodec;
import com.fraudshield.cases.feed.LiveFeed;
import com.fraudshield.contracts.CaseLabel;
import com.fraudshield.contracts.Decision;
import com.fraudshield.contracts.MuleAlert;
import com.fraudshield.contracts.PaymentScored;
import com.fraudshield.contracts.Reason;
import com.fraudshield.contracts.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private static final TypeReference<List<Reason>> REASONS = new TypeReference<>() {
    };
    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private static final BigDecimal P1_AMOUNT = new BigDecimal("50000");
    private static final BigDecimal P2_AMOUNT = new BigDecimal("10000");

    private final CaseRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final JsonCodec json;
    private final LiveFeed feed;

    public CaseService(CaseRepository repo, KafkaTemplate<String, String> kafka, JsonCodec json, LiveFeed feed) {
        this.repo = repo;
        this.kafka = kafka;
        this.json = json;
        this.feed = feed;
    }

    /** Every held or blocked payment becomes evidence in a case about the account that matters. */
    @Transactional
    public void onPayment(PaymentScored p) {
        if (p.decision() == Decision.APPROVE) return;
        // Payee-side network signals mean the case is about the receiving account, which is then a
        // suspected mule regardless of how unusual the amount looked from the victim's side.
        boolean payeeSubject = Typology.payeeIsSubject(p.reasons());
        Typology typology = payeeSubject ? Typology.MONEY_MULE : Typology.classify(p.reasons());
        String subject = payeeSubject ? p.payeeVpa() : p.payerVpa();
        String priority = priority(p.decision(), p.amount(), p.riskScore(), typology);
        String type = p.decision() == Decision.BLOCK ? "PAYMENT_BLOCKED" : "PAYMENT_REVIEW";

        CaseRepository.Upserted c = repo.upsertOpenCase(subject, type, typology, priority, p.riskScore(),
                p.simLabel(), slaFor(priority));
        boolean added = repo.addPayment(c.id(), p.txnId(), p.payerVpa(), p.payeeVpa(), p.amount(),
                p.decision().name(), p.riskScore(), json.write(p.reasons()), p.deviceId(), p.simLabel(),
                p.timestamp().atOffset(ZoneOffset.UTC));
        if (!added) return; // duplicate delivery

        String verb = p.decision() == Decision.BLOCK ? "Blocked" : "Held for review";
        String top = p.reasons().isEmpty() ? "" : " Top signal: " + p.reasons().get(0).description() + ".";
        if (c.created()) {
            repo.addEvent(c.id(), "system", "CREATED", "Case opened (" + typology.label() + ", " + priority + "). "
                    + verb + " " + INR.format(p.amount()) + " from " + p.payerVpa() + " to " + p.payeeVpa()
                    + " at risk " + String.format("%.2f", p.riskScore()) + "." + top);
            feed.publishCase(c.id(), subject, typology.name(), priority);
        } else {
            repo.addEvent(c.id(), "system", "PAYMENT_ADDED", verb + " " + INR.format(p.amount()) + " from "
                    + p.payerVpa() + " to " + p.payeeVpa() + " (risk " + String.format("%.2f", p.riskScore()) + ")."
                    + top);
        }
    }

    @Transactional
    public void onAlert(MuleAlert a) {
        String priority = a.muleScore() >= 0.85 ? "P1" : "P2";
        CaseRepository.Upserted c = repo.upsertOpenCase(a.vpa(), "MULE_ALERT", Typology.MONEY_MULE, priority,
                a.muleScore(), null, slaFor(priority));
        String signals = a.signals().entrySet().stream()
                .map(e -> e.getKey() + "=" + trim(e.getValue()))
                .collect(Collectors.joining(", "));
        repo.addEvent(c.id(), "graph-analyser", "ALERT", "Mule score " + String.format("%.2f", a.muleScore())
                + " across recent payments (" + signals + ").");
        if (c.created()) feed.publishCase(c.id(), a.vpa(), Typology.MONEY_MULE.name(), priority);
    }

    public CaseModels.CaseDetail detail(long id) {
        CaseModels.CaseRow row = repo.find(id).orElseThrow(() -> new NoSuchElementException("Case " + id + " not found"));
        List<CaseModels.CasePayment> payments = repo.payments(id).stream()
                .map(r -> new CaseModels.CasePayment(r.txnId(), r.payerVpa(), r.payeeVpa(), r.amount(), r.decision(),
                        r.riskScore(), json.read(r.reasons(), REASONS), r.deviceId(), r.simLabel(), r.paidAt()))
                .toList();
        return new CaseModels.CaseDetail(row, Typology.valueOf(row.typology()).label(), payments, repo.events(id));
    }

    @Transactional
    public CaseModels.CaseDetail assign(long id, String analyst) {
        if (repo.assign(id, analyst) == 0) throw new IllegalStateException("Case " + id + " is not open");
        repo.addEvent(id, analyst, "ASSIGNED", "Picked up by " + analyst + ".");
        return detail(id);
    }

    @Transactional
    public CaseModels.CaseDetail note(long id, String analyst, String text) {
        repo.find(id).orElseThrow(() -> new NoSuchElementException("Case " + id + " not found"));
        repo.addEvent(id, analyst, "NOTE", text);
        return detail(id);
    }

    /**
     * Records the verdict and feeds it back to the risk engine: a FRAUD verdict deny-lists the
     * bad actors in real time and both verdicts become labelled training data.
     */
    @Transactional
    public CaseModels.CaseDetail resolve(long id, CaseLabel.Verdict verdict, String analyst, String note) {
        CaseModels.CaseDetail before = detail(id);
        String status = verdict == CaseLabel.Verdict.FRAUD ? "RESOLVED_FRAUD" : "RESOLVED_LEGIT";
        if (repo.resolve(id, status, verdict.name(), analyst) == 0) {
            throw new IllegalStateException("Case " + id + " is already resolved");
        }
        Typology typology = Typology.valueOf(before.kase().typology());
        String subject = before.kase().subjectVpa();
        Set<String> deny = new LinkedHashSet<>();
        if (verdict == CaseLabel.Verdict.FRAUD) {
            if (typology.subjectIsVictim()) {
                before.payments().stream().filter(p -> p.payerVpa().equals(subject))
                        .forEach(p -> deny.add(p.payeeVpa()));
            } else {
                deny.add(subject);
            }
        }
        List<String> txnIds = before.payments().stream().map(CaseModels.CasePayment::txnId).toList();
        CaseLabel label = new CaseLabel(String.valueOf(id), subject, List.copyOf(deny), txnIds, verdict, analyst,
                Instant.now());
        try {
            // Wait for the broker: if the verdict cannot reach the risk engine, the transaction
            // rolls back and the analyst sees an error, instead of the two silently diverging.
            kafka.send(Topics.CASE_LABELS, subject, json.write(label)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish verdict to the risk engine, please retry", e);
        }

        String outcome = verdict == CaseLabel.Verdict.FRAUD
                ? "Confirmed FRAUD. Deny-listed: " + (deny.isEmpty() ? "none" : String.join(", ", deny)) + "."
                : "Marked LEGITIMATE (false positive). Account cleared from watch-lists.";
        repo.addEvent(id, analyst, "RESOLVED", outcome + " " + txnIds.size() + " payments sent to the model as "
                + "labelled training data." + (note == null || note.isBlank() ? "" : " Note: " + note));
        return detail(id);
    }

    static String priority(Decision decision, BigDecimal amount, double score, Typology typology) {
        if (decision == Decision.BLOCK && amount.compareTo(P1_AMOUNT) >= 0) return "P1";
        if (typology == Typology.SOCIAL_ENGINEERING_SCAM && amount.compareTo(P1_AMOUNT) >= 0) return "P1";
        if (decision == Decision.BLOCK || amount.compareTo(P2_AMOUNT) >= 0) return "P2";
        return "P3";
    }

    static OffsetDateTime slaFor(String priority) {
        Duration sla = switch (priority) {
            case "P1" -> Duration.ofMinutes(15);
            case "P2" -> Duration.ofHours(1);
            default -> Duration.ofHours(4);
        };
        return OffsetDateTime.now(ZoneOffset.UTC).plus(sla);
    }

    private static String trim(Double v) {
        return v == Math.rint(v) ? String.valueOf(v.longValue()) : String.format("%.2f", v);
    }
}

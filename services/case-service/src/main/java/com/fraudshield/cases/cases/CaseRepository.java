package com.fraudshield.cases.cases;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CaseRepository {

    private static final String COLUMNS = """
            id, subject_vpa, case_type, typology, status, priority, risk_score, total_amount, payment_count,
            assignee, ground_truth, ai_summary, ai_summary_by, ai_summary_at, resolution, created_at, updated_at,
            sla_due_at""";

    private final JdbcClient jdbc;

    public CaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Opens a case for the subject, or folds the new evidence into the subject's existing open
     * case. The partial unique index makes this race-free across consumer threads and replicas.
     */
    public Upserted upsertOpenCase(String subject, String type, Typology typology, String priority, double score,
                                   String groundTruth, OffsetDateTime slaDue) {
        return jdbc.sql("""
                        INSERT INTO cases (subject_vpa, case_type, typology, status, priority, risk_score, ground_truth,
                                           sla_due_at)
                        VALUES (:subject, :type, :typology, 'OPEN', :priority, :score, :truth, :sla)
                        ON CONFLICT (subject_vpa) WHERE status IN ('OPEN', 'IN_REVIEW') DO UPDATE SET
                            risk_score   = GREATEST(cases.risk_score, EXCLUDED.risk_score),
                            typology     = CASE WHEN EXCLUDED.risk_score > cases.risk_score
                                                THEN EXCLUDED.typology ELSE cases.typology END,
                            priority     = LEAST(cases.priority, EXCLUDED.priority),
                            sla_due_at   = LEAST(cases.sla_due_at, EXCLUDED.sla_due_at),
                            ground_truth = COALESCE(NULLIF(cases.ground_truth, 'LEGIT'), EXCLUDED.ground_truth),
                            updated_at   = now()
                        RETURNING id, (xmax = 0) AS created""")
                .param("subject", subject)
                .param("type", type)
                .param("typology", typology.name())
                .param("priority", priority)
                .param("score", score)
                .param("truth", groundTruth)
                .param("sla", slaDue)
                .query((rs, n) -> new Upserted(rs.getLong("id"), rs.getBoolean("created")))
                .single();
    }

    public boolean addPayment(long caseId, String txnId, String payer, String payee, BigDecimal amount,
                              String decision, double score, String reasonsJson, String device, String simLabel,
                              OffsetDateTime paidAt) {
        int inserted = jdbc.sql("""
                        INSERT INTO case_payments (case_id, txn_id, payer_vpa, payee_vpa, amount, decision, risk_score,
                                                   reasons, device_id, sim_label, paid_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING""")
                .params(caseId, txnId, payer, payee, amount, decision, score, reasonsJson, device, simLabel, paidAt)
                .update();
        if (inserted == 1) {
            jdbc.sql("UPDATE cases SET total_amount = total_amount + ?, payment_count = payment_count + 1 WHERE id = ?")
                    .params(amount, caseId)
                    .update();
        }
        return inserted == 1;
    }

    public void addEvent(long caseId, String actor, String type, String message) {
        jdbc.sql("INSERT INTO case_events (case_id, actor, event_type, message) VALUES (?, ?, ?, ?)")
                .params(caseId, actor, type, message)
                .update();
    }

    public Optional<CaseModels.CaseRow> find(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cases WHERE id = ?").param(id)
                .query(CaseModels.CaseRow.class).optional();
    }

    public List<CaseModels.CaseRow> list(String status, String priority, String typology, String q, int limit,
                                         int offset) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM cases WHERE 1=1");
        var params = new java.util.HashMap<String, Object>();
        if (status != null && !status.isBlank()) {
            if (status.equals("ACTIVE")) {
                sql.append(" AND status IN ('OPEN','IN_REVIEW')");
            } else if (status.equals("RESOLVED")) {
                sql.append(" AND status IN ('RESOLVED_FRAUD','RESOLVED_LEGIT')");
            } else {
                sql.append(" AND status = :status");
                params.put("status", status);
            }
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND priority = :priority");
            params.put("priority", priority);
        }
        if (typology != null && !typology.isBlank()) {
            sql.append(" AND typology = :typology");
            params.put("typology", typology);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (subject_vpa ILIKE :q OR CAST(id AS TEXT) = :exact)");
            params.put("q", "%" + q.trim() + "%");
            params.put("exact", q.trim());
        }
        sql.append(" ORDER BY CASE WHEN status IN ('OPEN','IN_REVIEW') THEN 0 ELSE 1 END, priority, risk_score DESC,")
                .append(" created_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(sql.toString()).params(params).query(CaseModels.CaseRow.class).list();
    }

    public List<CaseModels.CasePaymentRow> payments(long caseId) {
        return jdbc.sql("""
                        SELECT txn_id, payer_vpa, payee_vpa, amount, decision, risk_score, reasons, device_id,
                               sim_label, paid_at
                        FROM case_payments WHERE case_id = ? ORDER BY paid_at DESC LIMIT 200""")
                .param(caseId).query(CaseModels.CasePaymentRow.class).list();
    }

    public List<CaseModels.CaseEvent> events(long caseId) {
        return jdbc.sql("SELECT id, at, actor, event_type, message FROM case_events WHERE case_id = ? ORDER BY at, id")
                .param(caseId).query(CaseModels.CaseEvent.class).list();
    }

    public int assign(long id, String analyst) {
        return jdbc.sql("""
                        UPDATE cases SET assignee = ?, status = CASE WHEN status = 'OPEN' THEN 'IN_REVIEW' ELSE status END,
                                         updated_at = now()
                        WHERE id = ? AND status IN ('OPEN','IN_REVIEW')""")
                .params(analyst, id).update();
    }

    public int resolve(long id, String status, String resolution, String analyst) {
        return jdbc.sql("""
                        UPDATE cases SET status = ?, resolution = ?, assignee = COALESCE(assignee, ?), updated_at = now()
                        WHERE id = ? AND status IN ('OPEN','IN_REVIEW')""")
                .params(status, resolution, analyst, id).update();
    }

    public void saveSummary(long id, String summary, String by) {
        jdbc.sql("UPDATE cases SET ai_summary = ?, ai_summary_by = ?, ai_summary_at = now() WHERE id = ?")
                .params(summary, by, id).update();
    }

    public CaseModels.QueueStats stats() {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE status = 'OPEN')                                  AS open,
                               count(*) FILTER (WHERE status = 'IN_REVIEW')                             AS in_review,
                               count(*) FILTER (WHERE status = 'RESOLVED_FRAUD')                        AS resolved_fraud,
                               count(*) FILTER (WHERE status = 'RESOLVED_LEGIT')                        AS resolved_legit,
                               count(*) FILTER (WHERE status IN ('OPEN','IN_REVIEW') AND priority = 'P1') AS p1_open,
                               count(*) FILTER (WHERE status IN ('OPEN','IN_REVIEW') AND sla_due_at < now()) AS breached_sla
                        FROM cases""")
                .query(CaseModels.QueueStats.class).single();
    }

    public List<Map<String, Object>> typologyBreakdown() {
        return jdbc.sql("""
                        SELECT typology, count(*) AS cases, coalesce(sum(total_amount), 0) AS amount
                        FROM cases WHERE created_at > now() - interval '24 hours'
                        GROUP BY typology ORDER BY cases DESC""")
                .query().listOfRows();
    }

    public record Upserted(long id, boolean created) {
    }
}

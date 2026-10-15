package com.fraudshield.txn.payment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByCreatedAtAfterOrderByCreatedAtDesc(Instant after, Pageable page);

    @Query("select p from Payment p where p.payerVpa = :vpa or p.payeeVpa = :vpa order by p.createdAt desc")
    List<Payment> findByAccount(@Param("vpa") String vpa, Pageable page);

    @Query(value = """
            SELECT decision,
                   count(*)                                                  AS cnt,
                   coalesce(sum(amount), 0)                                  AS total,
                   coalesce(avg(latency_ms), 0)                              AS avg_latency,
                   coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) AS p95_latency
            FROM payments
            WHERE created_at > :since
            GROUP BY decision
            """, nativeQuery = true)
    List<Object[]> decisionStats(@Param("since") Instant since);
}

package com.fraudshield.txn.payment;

import com.fraudshield.contracts.Channel;
import com.fraudshield.contracts.Decision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private String txnId;
    private String idempotencyKey;
    private String payerVpa;
    private String payeeVpa;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private Channel channel;
    private String deviceId;
    private Double lat;
    private Double lon;
    private String note;
    @Enumerated(EnumType.STRING)
    private Decision decision;
    private double riskScore;
    @Column(columnDefinition = "text")
    private String reasons;
    private String modelVersion;
    private long latencyMs;
    private boolean degraded;
    private String simLabel;
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(String txnId, String idempotencyKey, String payerVpa, String payeeVpa, BigDecimal amount,
                   Channel channel, String deviceId, Double lat, Double lon, String note, Decision decision,
                   double riskScore, String reasons, String modelVersion, long latencyMs, boolean degraded,
                   String simLabel, Instant createdAt) {
        this.txnId = txnId;
        this.idempotencyKey = idempotencyKey;
        this.payerVpa = payerVpa;
        this.payeeVpa = payeeVpa;
        this.amount = amount;
        this.channel = channel;
        this.deviceId = deviceId;
        this.lat = lat;
        this.lon = lon;
        this.note = note;
        this.decision = decision;
        this.riskScore = riskScore;
        this.reasons = reasons;
        this.modelVersion = modelVersion;
        this.latencyMs = latencyMs;
        this.degraded = degraded;
        this.simLabel = simLabel;
        this.createdAt = createdAt;
    }

    public String getTxnId() { return txnId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayerVpa() { return payerVpa; }
    public String getPayeeVpa() { return payeeVpa; }
    public BigDecimal getAmount() { return amount; }
    public Channel getChannel() { return channel; }
    public String getDeviceId() { return deviceId; }
    public Double getLat() { return lat; }
    public Double getLon() { return lon; }
    public String getNote() { return note; }
    public Decision getDecision() { return decision; }
    public double getRiskScore() { return riskScore; }
    public String getReasons() { return reasons; }
    public String getModelVersion() { return modelVersion; }
    public long getLatencyMs() { return latencyMs; }
    public boolean isDegraded() { return degraded; }
    public String getSimLabel() { return simLabel; }
    public Instant getCreatedAt() { return createdAt; }
}

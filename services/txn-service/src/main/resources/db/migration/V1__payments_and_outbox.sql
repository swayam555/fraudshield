CREATE TABLE payments (
    txn_id          VARCHAR(32) PRIMARY KEY,
    idempotency_key VARCHAR(80) UNIQUE,
    payer_vpa       VARCHAR(100) NOT NULL,
    payee_vpa       VARCHAR(100) NOT NULL,
    amount          NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    channel         VARCHAR(20) NOT NULL,
    device_id       VARCHAR(64) NOT NULL,
    lat             DOUBLE PRECISION,
    lon             DOUBLE PRECISION,
    note            VARCHAR(140),
    decision        VARCHAR(10) NOT NULL,
    risk_score      DOUBLE PRECISION NOT NULL,
    reasons         TEXT NOT NULL,
    model_version   VARCHAR(40) NOT NULL,
    latency_ms      BIGINT NOT NULL,
    degraded        BOOLEAN NOT NULL DEFAULT FALSE,
    sim_label       VARCHAR(30),
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payments_created ON payments (created_at DESC);
CREATE INDEX idx_payments_payer ON payments (payer_vpa, created_at DESC);
CREATE INDEX idx_payments_payee ON payments (payee_vpa, created_at DESC);

-- Transactional outbox: written in the same database transaction as the payment, relayed to
-- Kafka afterwards. A crash between "saved" and "published" can no longer lose an event.
CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

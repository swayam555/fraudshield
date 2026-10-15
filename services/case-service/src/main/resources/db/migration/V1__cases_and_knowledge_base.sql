CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE analysts (
    username      VARCHAR(60) PRIMARY KEY,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20) NOT NULL
);

CREATE TABLE cases (
    id            BIGSERIAL PRIMARY KEY,
    subject_vpa   VARCHAR(100) NOT NULL,
    case_type     VARCHAR(30) NOT NULL,
    typology      VARCHAR(40) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    priority      VARCHAR(2) NOT NULL,
    risk_score    DOUBLE PRECISION NOT NULL,
    total_amount  NUMERIC(16, 2) NOT NULL DEFAULT 0,
    payment_count INT NOT NULL DEFAULT 0,
    assignee      VARCHAR(60),
    ground_truth  VARCHAR(30),
    ai_summary    TEXT,
    ai_summary_by VARCHAR(80),
    ai_summary_at TIMESTAMPTZ,
    resolution    VARCHAR(10),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    sla_due_at    TIMESTAMPTZ NOT NULL
);

-- At most one open case per account: concurrent consumers upsert into the same row instead of
-- racing to create duplicates.
CREATE UNIQUE INDEX uq_cases_open_subject ON cases (subject_vpa) WHERE status IN ('OPEN', 'IN_REVIEW');
CREATE INDEX idx_cases_queue ON cases (status, priority, created_at DESC);

CREATE TABLE case_payments (
    case_id    BIGINT NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    txn_id     VARCHAR(32) NOT NULL,
    payer_vpa  VARCHAR(100) NOT NULL,
    payee_vpa  VARCHAR(100) NOT NULL,
    amount     NUMERIC(14, 2) NOT NULL,
    decision   VARCHAR(10) NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    reasons    TEXT NOT NULL,
    device_id  VARCHAR(64),
    sim_label  VARCHAR(30),
    paid_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (case_id, txn_id)
);

CREATE TABLE case_events (
    id         BIGSERIAL PRIMARY KEY,
    case_id    BIGINT NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor      VARCHAR(60) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    message    TEXT NOT NULL
);
CREATE INDEX idx_case_events_case ON case_events (case_id, at);

-- Knowledge base for retrieval-augmented generation.
CREATE TABLE kb_chunks (
    id           BIGSERIAL PRIMARY KEY,
    doc_id       VARCHAR(80) NOT NULL,
    doc_title    VARCHAR(200) NOT NULL,
    section      VARCHAR(200) NOT NULL,
    content      TEXT NOT NULL,
    source       VARCHAR(300),
    content_hash VARCHAR(64) NOT NULL,
    embedding    vector(384) NOT NULL,
    tsv          tsvector GENERATED ALWAYS AS (
                     setweight(to_tsvector('english', coalesce(section, '')), 'A') ||
                     setweight(to_tsvector('english', content), 'B')) STORED
);
CREATE INDEX idx_kb_embedding ON kb_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_kb_tsv ON kb_chunks USING gin (tsv);
CREATE INDEX idx_kb_doc ON kb_chunks (doc_id);

-- Platform tables: file metadata (Cloudinary), transactional outbox, append-only audit log.

CREATE TABLE file_assets (
    id            UUID PRIMARY KEY,
    owner_user_id UUID         NOT NULL REFERENCES users (id),
    event_id      UUID         REFERENCES events (id),
    purpose       VARCHAR(30)  NOT NULL
                  CHECK (purpose IN ('EVENT_BANNER', 'PROFILE_IMAGE', 'EXPORT')),
    public_id     VARCHAR(255) NOT NULL, -- random Cloudinary key, never the original filename
    mime          VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL CHECK (size_bytes >= 0),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'READY', 'DELETED')),
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_file_assets_public_id UNIQUE (public_id)
);

CREATE INDEX ix_file_assets_owner ON file_assets (owner_user_id);
CREATE INDEX ix_file_assets_event ON file_assets (event_id);
CREATE INDEX ix_file_assets_pending_cleanup ON file_assets (created_at) WHERE status = 'PENDING';

-- deferred FK from V2: events.banner_file_id -> file_assets
ALTER TABLE events
    ADD CONSTRAINT fk_events_banner_file FOREIGN KEY (banner_file_id) REFERENCES file_assets (id);

CREATE TABLE outbox_jobs (
    id              UUID PRIMARY KEY,
    job_type        VARCHAR(40)  NOT NULL,
    payload         JSONB        NOT NULL,
    job_key         VARCHAR(160) NOT NULL, -- e.g. ORDER_CONFIRMATION:{orderId}; blocks duplicate enqueues
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'SENT', 'DEAD')),
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_outbox_jobs_key UNIQUE (job_key)
);

CREATE INDEX ix_outbox_jobs_claim ON outbox_jobs (status, next_attempt_at, id);

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY,
    actor_user_id UUID        REFERENCES users (id), -- NULL for system actions
    action        VARCHAR(60) NOT NULL,
    entity_type   VARCHAR(40),
    entity_id     UUID,
    detail        JSONB,
    request_id    VARCHAR(60),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_created ON audit_logs (created_at DESC, id);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- Tickets & check-ins. event_id is deliberately denormalized onto both tables for check-in speed.

CREATE TABLE tickets (
    id                    UUID PRIMARY KEY,
    public_code           VARCHAR(20)  NOT NULL,
    order_id              UUID         NOT NULL REFERENCES orders (id),
    order_item_id         UUID         NOT NULL REFERENCES order_items (id),
    event_id              UUID         NOT NULL REFERENCES events (id),
    ticket_type_id        UUID         NOT NULL REFERENCES ticket_types (id),
    owner_user_id         UUID         NOT NULL REFERENCES users (id),
    attendee_name         VARCHAR(120) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'VALID'
                          CHECK (status IN ('VALID', 'USED', 'CANCELLED')),
    validation_token_hash VARCHAR(64)  NOT NULL, -- SHA-256 hex; the raw token exists only inside the QR
    issued_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancelled_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_tickets_public_code UNIQUE (public_code),
    CONSTRAINT ux_tickets_token_hash UNIQUE (validation_token_hash)
);

CREATE INDEX ix_tickets_owner_issued ON tickets (owner_user_id, issued_at DESC, id);
CREATE INDEX ix_tickets_event_status ON tickets (event_id, status);

CREATE TABLE check_ins (
    id            UUID PRIMARY KEY,
    ticket_id     UUID        NOT NULL REFERENCES tickets (id),
    event_id      UUID        NOT NULL REFERENCES events (id),
    staff_user_id UUID        NOT NULL REFERENCES users (id),
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    method        VARCHAR(10) NOT NULL CHECK (method IN ('QR', 'MANUAL')),
    device_ref    VARCHAR(120),
    -- the final duplicate-check-in defense: one row per ticket, ever (docs/architecture.md §4.6)
    CONSTRAINT ux_check_ins_ticket UNIQUE (ticket_id)
);

CREATE INDEX ix_check_ins_event_time ON check_ins (event_id, checked_in_at DESC);

-- Orders & payments. Financial rows are never deleted — explicit statuses only (docs/architecture.md §5).

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    order_number    VARCHAR(30)    NOT NULL,
    user_id         UUID           NOT NULL REFERENCES users (id),
    event_id        UUID           NOT NULL REFERENCES events (id),
    status          VARCHAR(30)    NOT NULL
                    CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    currency        CHAR(3)        NOT NULL DEFAULT 'LKR',
    subtotal        NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    fees            NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (fees >= 0),
    grand_total     NUMERIC(12, 2) NOT NULL CHECK (grand_total >= 0),
    idempotency_key VARCHAR(80)    NOT NULL,
    expires_at      TIMESTAMPTZ,
    confirmed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ux_orders_number UNIQUE (order_number),
    -- retry with the same key returns the original order instead of double-charging
    CONSTRAINT ux_orders_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC, id);
CREATE INDEX ix_orders_event_status_created ON orders (event_id, status, created_at DESC);
CREATE INDEX ix_orders_expiration_sweep ON orders (expires_at) WHERE status = 'PENDING_PAYMENT';

CREATE TABLE order_items (
    id               UUID PRIMARY KEY,
    order_id         UUID           NOT NULL REFERENCES orders (id),
    ticket_type_id   UUID           NOT NULL REFERENCES ticket_types (id),
    ticket_type_name VARCHAR(100)   NOT NULL, -- snapshot: preserves financial history on rename
    unit_price       NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0), -- snapshot
    quantity         INTEGER        NOT NULL CHECK (quantity > 0),
    line_total       NUMERIC(12, 2) NOT NULL CHECK (line_total >= 0),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    order_id            UUID           NOT NULL REFERENCES orders (id),
    provider            VARCHAR(30)    NOT NULL,
    provider_payment_id VARCHAR(120),
    provider_event_id   VARCHAR(120),
    status              VARCHAR(30)    NOT NULL
                        CHECK (status IN ('CREATED', 'SUCCEEDED', 'FAILED')),
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency            CHAR(3)        NOT NULL DEFAULT 'LKR',
    failure_code        VARCHAR(80),
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- webhook replay defense (docs/architecture.md §4.6)
    CONSTRAINT ux_payments_provider_payment UNIQUE (provider, provider_payment_id)
);

CREATE INDEX ix_payments_order ON payments (order_id);

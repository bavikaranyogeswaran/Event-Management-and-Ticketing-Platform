-- Event catalog: categories, events (approval lifecycle per ADR-0007), ticket types, staff assignments.

CREATE TABLE categories (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80)  NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_categories_slug UNIQUE (slug)
);

CREATE TABLE events (
    id                      UUID PRIMARY KEY,
    organizer_id            UUID         NOT NULL REFERENCES organizer_profiles (id),
    category_id             UUID         NOT NULL REFERENCES categories (id),
    slug                    VARCHAR(160) NOT NULL,
    title                   VARCHAR(160) NOT NULL,
    description             TEXT,
    event_type              VARCHAR(20)  NOT NULL
                            CHECK (event_type IN ('PHYSICAL', 'ONLINE')),
    venue_name              VARCHAR(160),
    address_line            VARCHAR(255),
    city                    VARCHAR(100),
    online_url              VARCHAR(500),
    timezone                VARCHAR(60)  NOT NULL,
    starts_at               TIMESTAMPTZ  NOT NULL,
    ends_at                 TIMESTAMPTZ  NOT NULL,
    registration_opens_at   TIMESTAMPTZ  NOT NULL,
    registration_closes_at  TIMESTAMPTZ  NOT NULL,
    capacity                INTEGER      NOT NULL CHECK (capacity > 0),
    status                  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED',
                                              'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    submitted_at            TIMESTAMPTZ,
    published_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    rejection_reason        TEXT,
    banner_file_id          UUID, -- FK added in V5 after file_assets exists
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_events_slug UNIQUE (slug),
    CONSTRAINT ck_events_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_events_registration_window CHECK (registration_closes_at <= starts_at),
    CONSTRAINT ck_events_physical_venue CHECK (event_type <> 'PHYSICAL' OR venue_name IS NOT NULL)
);

CREATE INDEX ix_events_status_starts ON events (status, starts_at, id);
CREATE INDEX ix_events_organizer_created ON events (organizer_id, created_at DESC);
CREATE INDEX ix_events_category_status_starts ON events (category_id, status, starts_at);
CREATE INDEX ix_events_review_queue ON events (submitted_at) WHERE status = 'PENDING_REVIEW';

CREATE TABLE ticket_types (
    id             UUID PRIMARY KEY,
    event_id       UUID           NOT NULL REFERENCES events (id),
    name           VARCHAR(100)   NOT NULL,
    description    TEXT,
    price          NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    currency       CHAR(3)        NOT NULL DEFAULT 'LKR',
    quantity_total INTEGER        NOT NULL CHECK (quantity_total > 0),
    quantity_sold  INTEGER        NOT NULL DEFAULT 0,
    max_per_order  INTEGER        NOT NULL DEFAULT 4 CHECK (max_per_order > 0),
    sales_start_at TIMESTAMPTZ    NOT NULL,
    sales_end_at   TIMESTAMPTZ    NOT NULL,
    status         VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0,
    -- the oversell backstop: the conditional UPDATE is the primary defense (docs/architecture.md §6.1)
    CONSTRAINT ck_ticket_types_inventory CHECK (quantity_sold >= 0 AND quantity_sold <= quantity_total),
    CONSTRAINT ck_ticket_types_sales_window CHECK (sales_end_at > sales_start_at)
);

CREATE INDEX ix_ticket_types_event_status ON ticket_types (event_id, status);

CREATE TABLE event_staff_assignments (
    id          UUID PRIMARY KEY,
    event_id    UUID        NOT NULL REFERENCES events (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    assigned_by UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_staff_assignment UNIQUE (event_id, user_id)
);

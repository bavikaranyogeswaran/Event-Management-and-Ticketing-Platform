-- Identity & access: users, roles, auth tokens, organizer profiles.
-- IDs are app-generated UUIDv4 (no DB defaults); updated_at is app-managed (JPA @PreUpdate).

CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    email              VARCHAR(320) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(120) NOT NULL,
    phone              VARCHAR(30),
    status             VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    email_verified_at  TIMESTAMPTZ,
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_status_created ON users (status, created_at DESC);

CREATE TABLE user_roles (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL
               CHECK (role IN ('ATTENDEE', 'ORGANIZER', 'STAFF', 'ADMIN')),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_user_roles_user_role UNIQUE (user_id, role)
);

CREATE TABLE auth_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    purpose    VARCHAR(30)  NOT NULL
               CHECK (purpose IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION')),
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_auth_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_auth_tokens_user_purpose ON auth_tokens (user_id, purpose);

CREATE TABLE organizer_profiles (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id),
    org_name      VARCHAR(150) NOT NULL,
    description   TEXT,
    contact_email VARCHAR(320),
    status        VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_organizer_profiles_user UNIQUE (user_id)
);

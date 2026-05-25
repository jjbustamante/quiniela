-- V002: User table.
--
-- Thin schema deliberately — Google OIDC handles auth, so we only persist
-- what's needed to attach quinielas to a stable identity. No passwords,
-- session tokens, login counters, etc. (vs. the 15+ auth columns the
-- legacy authlogic-based Rails User table had — see legacy/CLAUDE.md.)

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    google_sub      VARCHAR(255) NOT NULL UNIQUE,  -- Google's stable user ID (`sub` claim)
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    avatar_url      VARCHAR(1024),
    is_admin        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

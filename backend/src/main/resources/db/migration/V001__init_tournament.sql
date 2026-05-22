-- V001: Foundational tournament + stage tables.
-- Schema is designed multi-tournament from day one (FIFA 2026 first,
-- UEFA Champions League and others planned for v2). See repo root CLAUDE.md.

CREATE TABLE tournament (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    external_id     VARCHAR(64),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'UPCOMING',
    group_stage_deadline   TIMESTAMPTZ,
    knockout_deadline      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE stage (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT NOT NULL REFERENCES tournament(id) ON DELETE CASCADE,
    code            VARCHAR(32) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    sequence        INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tournament_id, code),
    UNIQUE (tournament_id, sequence)
);

CREATE INDEX idx_stage_tournament ON stage(tournament_id);

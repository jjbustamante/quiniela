-- V003: Roles, invite tree, pool, prize split, pool membership.
--
-- - Replaces users.is_admin (boolean) with users.role (enum-as-VARCHAR).
-- - Adds the 2-level invite tree: invited_by_user_id + invite_path.
-- - Introduces pool, prize_split, pool_membership — multi-pool-ready schema
--   even though v1 ships a single hard-coded pool.
-- - Seeds the default pool (id=1) and the 80/15/5 prize split.

-- ── users upgrade ──────────────────────────────────────────────────────────

ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'player';
UPDATE users SET role = 'admin' WHERE is_admin = true;
ALTER TABLE users DROP COLUMN is_admin;
ALTER TABLE users ADD CONSTRAINT users_role_chk
  CHECK (role IN ('admin', 'captain', 'player'));

ALTER TABLE users ADD COLUMN invited_by_user_id BIGINT
  REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE users ADD COLUMN invite_path VARCHAR(64) UNIQUE;
CREATE INDEX idx_users_invite_path ON users(invite_path);

-- ── pool ───────────────────────────────────────────────────────────────────

CREATE TABLE pool (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT NOT NULL REFERENCES tournament(id),
    name            VARCHAR(255) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    entry_fee_cents INT NOT NULL DEFAULT 2000,
    locked_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pool_tournament ON pool(tournament_id);

-- ── prize_split ────────────────────────────────────────────────────────────

CREATE TABLE prize_split (
    pool_id    BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    rank       INT NOT NULL,
    percentage INT NOT NULL,
    PRIMARY KEY (pool_id, rank),
    CHECK (rank >= 1),
    CHECK (percentage > 0 AND percentage <= 100)
);

-- ── pool_membership ────────────────────────────────────────────────────────

CREATE TABLE pool_membership (
    pool_id    BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (pool_id, user_id)
);

CREATE INDEX idx_pool_membership_user ON pool_membership(user_id);

-- ── seed default pool + prize split ────────────────────────────────────────

INSERT INTO tournament (id, slug, name, start_date, end_date, status)
VALUES (1, 'fifa-wc-2026', 'Copa Mundial FIFA 2026', '2026-06-11', '2026-07-19', 'UPCOMING')
ON CONFLICT (id) DO NOTHING;

SELECT setval('tournament_id_seq', GREATEST(1, (SELECT MAX(id) FROM tournament)));

INSERT INTO pool (id, tournament_id, name, currency, entry_fee_cents)
VALUES (1, 1, 'Quiniela Panas', 'USD', 2000)
ON CONFLICT (id) DO NOTHING;

SELECT setval('pool_id_seq', GREATEST(1, (SELECT MAX(id) FROM pool)));

INSERT INTO prize_split (pool_id, rank, percentage) VALUES
    (1, 1, 80),
    (1, 2, 15),
    (1, 3, 5)
ON CONFLICT (pool_id, rank) DO NOTHING;

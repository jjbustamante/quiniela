-- V011: Two-tier payments ledger.
--
-- `paid`    — the member handed their entry fee to the person who invited them
--             (their captain, or the admin). Set by that inviter or the admin.
-- `settled` — a captain remitted their collected pile to the admin. Set by the
--             admin only; meaningless (stays false) for non-captains.
--
-- The pot is NOT derived from this table — PublicSummaryController keeps
-- pot = members × entry_fee. This ledger is an accountability layer only.
--
-- Rows are created lazily on first toggle (LEFT JOIN on read; absent = unpaid).

CREATE TABLE payment (
    pool_id           BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    paid              BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at           TIMESTAMPTZ,
    marked_paid_by    BIGINT REFERENCES users(id),
    amount_cents      INT,            -- NULL = use pool.entry_fee_cents
    note              TEXT,
    settled           BOOLEAN NOT NULL DEFAULT FALSE,
    settled_at        TIMESTAMPTZ,
    marked_settled_by BIGINT REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (pool_id, user_id),
    CHECK (amount_cents IS NULL OR amount_cents >= 0)
);

CREATE INDEX idx_payment_pool ON payment(pool_id);

-- V018: Admin-configurable organizer (house) cut.
--
-- The organizer can take a percentage off the top of the pot (e.g. to recover
-- the laquinieladelospanas.com domain cost) BEFORE the remainder is split
-- among the winners by prize_split. Default 0 keeps the prior behaviour: the
-- whole pot goes to the winners.
--
-- entry_fee_cents (pool) and prize_split percentages already exist and are
-- per-pool; this migration just adds the missing house-cut knob so the admin
-- money-config UI can drive all three.

ALTER TABLE pool
    ADD COLUMN house_cut_percentage INT NOT NULL DEFAULT 0
        CHECK (house_cut_percentage >= 0 AND house_cut_percentage <= 100);

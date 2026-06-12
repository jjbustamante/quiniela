-- V022: Frozen per-match points in bet.points + kickoff-lock in the scoring trigger.
--
-- New column bet.points stores the points each bet ACTUALLY scored, frozen at
-- scoring time. The trigger is updated to:
--   (a) only credit bets placed BEFORE the match kicked off (b.created_at <= NEW.kickoff_at),
--       de-arming the landmine where post-kickoff bets could be scored on a re-score;
--   (b) write bet.points = new_points for each scored bet.
--
-- A one-time backfill then reconciles the two already-played prod matches, and an
-- in-migration assertion guarantees SUM(bet.points over played) = quiniela.points
-- for every quiniela. The migration is a no-op on fresh/test DBs (assertion trivially
-- passes when no matches are played).

-- ── §1: Add the frozen-points column ────────────────────────────────────────────

ALTER TABLE bet ADD COLUMN points INT NOT NULL DEFAULT 0;

-- ── §2: Update the scoring trigger function ─────────────────────────────────────
--
-- Copy of the V019 update_players_score() body with exactly two changes:
--   1. Bet loop: WHERE b.match_id = NEW.id → WHERE b.match_id = NEW.id AND b.created_at <= NEW.kickoff_at
--   2. Inside loop after new_points is computed: UPDATE bet SET points = new_points …
-- Everything else is preserved verbatim (advanced_team_id maintenance, early RETURN NEW
-- no-change guard, multiplier lookup, UPDATE quiniela delta, winner_id derivation).
-- The trigger binding (matches_score_update_trigger from V017) stays — only the function.

CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
    is_knockout BOOLEAN;
    multiplier_val INT;
    bet_row RECORD;
    old_points INT;
    new_points INT;
    delta INT;
BEGIN
    NEW.advanced_team_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NEW.advanced_team_id
    END;

    IF NEW.score_t1 IS NOT DISTINCT FROM OLD.score_t1
       AND NEW.score_t2 IS NOT DISTINCT FROM OLD.score_t2
       AND NEW.advanced_team_id IS NOT DISTINCT FROM OLD.advanced_team_id THEN
        RETURN NEW;
    END IF;

    SELECT r.code <> 'GROUP', r.points_multiplier
      INTO is_knockout, multiplier_val
      FROM round r WHERE r.id = NEW.round_id;
    IF is_knockout IS NULL THEN is_knockout := FALSE; END IF;
    -- Defensive only: points_multiplier is NOT NULL DEFAULT 1, so this fires
    -- solely if NEW.round_id points at no round row (which FKs already prevent).
    IF multiplier_val IS NULL THEN
        multiplier_val := CASE WHEN is_knockout THEN 2 ELSE 1 END;
    END IF;

    FOR bet_row IN
        SELECT b.quiniela_id,
               b.score_t1            AS bet_t1,
               b.score_t2            AS bet_t2,
               b.predicted_winner_id AS predicted_winner_id
        FROM bet b
        WHERE b.match_id = NEW.id AND b.created_at <= NEW.kickoff_at
    LOOP
        old_points := score_match_for_bet(
            is_knockout,
            bet_row.bet_t1, bet_row.bet_t2,
            OLD.score_t1, OLD.score_t2,
            bet_row.predicted_winner_id, OLD.advanced_team_id,
            multiplier_val
        );
        new_points := score_match_for_bet(
            is_knockout,
            bet_row.bet_t1, bet_row.bet_t2,
            NEW.score_t1, NEW.score_t2,
            bet_row.predicted_winner_id, NEW.advanced_team_id,
            multiplier_val
        );
        UPDATE bet SET points = new_points
        WHERE quiniela_id = bet_row.quiniela_id AND match_id = NEW.id;
        delta := new_points - old_points;
        IF delta <> 0 THEN
            UPDATE quiniela SET points = points + delta, updated_at = NOW()
            WHERE id = bet_row.quiniela_id;
        END IF;
    END LOOP;

    NEW.updated_at := NOW();

    NEW.winner_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NULL
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── §3: One-time backfill for already-played matches ─────────────────────────────

-- (a) Recompute every bet on an already-played match (correct for the 40 clean bettors).
UPDATE bet b
SET points = score_match_for_bet(
      (r.code <> 'GROUP'), b.score_t1, b.score_t2,
      m.score_t1, m.score_t2, b.predicted_winner_id, m.advanced_team_id,
      r.points_multiplier)
FROM match m JOIN round r ON r.id = m.round_id
WHERE b.match_id = m.id AND m.played;

-- (b) Bets that did NOT exist when their match was scored → 0.
--     (created after the match's scoring time, i.e. match.updated_at). Covers Daneff & Juan M.
UPDATE bet b SET points = 0
FROM match m
WHERE b.match_id = m.id AND m.played AND b.created_at > m.updated_at;

-- (c) Bets that existed at scoring but were EDITED afterward → restore the frozen original
--     = quiniela.points minus the player's other (already correct) bet points.
--     Covers Artistic. Runs after (a)+(b).
UPDATE bet b SET points = q.points - COALESCE((
        SELECT SUM(b2.points) FROM bet b2
        WHERE b2.quiniela_id = b.quiniela_id AND b2.match_id <> b.match_id), 0)
FROM match m, quiniela q
WHERE b.match_id = m.id AND m.played AND q.id = b.quiniela_id
  AND b.created_at <= m.updated_at AND b.updated_at > m.updated_at;

-- ── §3: In-migration assertion ────────────────────────────────────────────────────
--
-- Fail the migration if any quiniela's SUM(bet.points over played) ≠ quiniela.points.
-- Trivially passes on fresh/test DBs where no matches are played.

DO $$
DECLARE bad INT;
BEGIN
  SELECT count(*) INTO bad FROM (
    SELECT q.id, q.points,
           COALESCE(SUM(b.points) FILTER (WHERE m.played), 0) AS sum_bet
    FROM quiniela q
    LEFT JOIN bet b ON b.quiniela_id = q.id
    LEFT JOIN match m ON m.id = b.match_id
    GROUP BY q.id, q.points
  ) t WHERE t.points <> t.sum_bet;
  IF bad > 0 THEN
    RAISE EXCEPTION 'bet.points backfill mismatch for % quiniela(s)', bad;
  END IF;
END $$;

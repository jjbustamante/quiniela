-- V019: configurable per-round score multiplier.
--
-- Replaces the hardcoded "knockout = ×2" inside score_match_for_bet with a
-- per-round points_multiplier (a column on `round`). The trigger looks it up
-- and passes it through; group rounds carry 1 (unchanged), knockout rounds seed
-- to 2 (unchanged) and become admin-editable in a later phase. The Java mirror
-- in CompareService reads the same value; ScoringDivergenceTest pins the two.

ALTER TABLE round
    ADD COLUMN points_multiplier INT NOT NULL DEFAULT 1
        CHECK (points_multiplier >= 1);

-- Seed: GROUP stays ×1 (the column default); every knockout round to ×2,
-- exactly today's behaviour.
UPDATE round SET points_multiplier = 2 WHERE code <> 'GROUP';

-- Add a trailing `multiplier` arg to the scoring function. NULL preserves the
-- old "knockout ? ×2 : ×1" for any caller that omits it; the trigger always
-- passes the round's configured value. Drop the V016 7-arg overload first so
-- the new 8-arg form (callable with 5/7/8 args) is unambiguous.
DROP FUNCTION IF EXISTS score_match_for_bet(boolean, integer, integer, integer, integer, bigint, bigint);

CREATE OR REPLACE FUNCTION score_match_for_bet(
    is_knockout BOOLEAN,
    bet_t1 INT, bet_t2 INT,
    actual_t1 INT, actual_t2 INT,
    predicted_winner_id BIGINT DEFAULT NULL,
    actual_winner_id    BIGINT DEFAULT NULL,
    multiplier          INT DEFAULT NULL
) RETURNS INT AS $$
DECLARE
    is_exact      BOOLEAN;
    bet_winner    INT;
    actual_winner INT;
    outcome_pts   INT := 0;
    t1_pts        INT := 0;
    t2_pts        INT := 0;
    diff_pts      INT := 0;
    total         INT;
BEGIN
    IF actual_t1 IS NULL OR actual_t2 IS NULL THEN RETURN 0; END IF;

    is_exact := (bet_t1 = actual_t1 AND bet_t2 = actual_t2);

    bet_winner := CASE
        WHEN bet_t1 > bet_t2 THEN 1
        WHEN bet_t1 < bet_t2 THEN -1
        ELSE 0
    END;
    actual_winner := CASE
        WHEN actual_t1 > actual_t2 THEN 1
        WHEN actual_t1 < actual_t2 THEN -1
        ELSE 0
    END;

    IF bet_winner = actual_winner THEN
        IF is_knockout AND bet_winner = 0 AND actual_winner = 0
           AND predicted_winner_id IS NOT NULL AND actual_winner_id IS NOT NULL THEN
            outcome_pts := CASE WHEN predicted_winner_id = actual_winner_id THEN 3 ELSE 0 END;
        ELSE
            outcome_pts := 3;
        END IF;
    END IF;

    IF bet_t1 = actual_t1 THEN t1_pts := 2; END IF;
    IF bet_t2 = actual_t2 THEN t2_pts := 2; END IF;

    IF NOT is_exact AND (bet_t1 - bet_t2) = (actual_t1 - actual_t2) THEN
        diff_pts := 1;
    END IF;

    total := outcome_pts + t1_pts + t2_pts + diff_pts;

    RETURN total * COALESCE(multiplier, CASE WHEN is_knockout THEN 2 ELSE 1 END);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Recreate the trigger function (V017 shape, which maintains advanced_team_id)
-- to look up the round multiplier and pass it through. Only the function body
-- changes; the BEFORE UPDATE trigger binding from V017 stays in place.
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
    IF multiplier_val IS NULL THEN
        multiplier_val := CASE WHEN is_knockout THEN 2 ELSE 1 END;
    END IF;

    FOR bet_row IN
        SELECT b.quiniela_id,
               b.score_t1            AS bet_t1,
               b.score_t2            AS bet_t2,
               b.predicted_winner_id AS predicted_winner_id
        FROM bet b
        WHERE b.match_id = NEW.id
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

-- V016: predicted_winner_id on knockout draw bets.
--
-- When a player predicts a draw in a knockout match they must also pick
-- which team advances (via penalties / extra time). This column stores
-- that pick so the scoring engine can award outcome points correctly.
--
-- For group-stage bets and non-draw knockout bets the column is NULL and
-- the scoring behaviour is identical to V010.

ALTER TABLE bet ADD COLUMN predicted_winner_id BIGINT REFERENCES team(id);

-- Drop the old 5-param overload so the new 7-param function (with 2 defaults)
-- is the sole overload. Without this, calls with 5 args are ambiguous.
DROP FUNCTION IF EXISTS score_match_for_bet(boolean, integer, integer, integer, integer);

-- Update score_match_for_bet to accept the predicted/actual winner ids.
-- For knockout regulation draws (both scores equal) the outcome component
-- is based on predicted_winner_id vs actual_winner_id instead of the
-- score comparison (which would always give 3 pts for matching draws).
-- When predicted_winner_id is NULL the old behaviour is preserved.
CREATE OR REPLACE FUNCTION score_match_for_bet(
    is_knockout BOOLEAN,
    bet_t1 INT, bet_t2 INT,
    actual_t1 INT, actual_t2 INT,
    predicted_winner_id BIGINT DEFAULT NULL,
    actual_winner_id    BIGINT DEFAULT NULL
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
        -- Knockout regulation draw: use predicted winner for outcome points.
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

    IF is_knockout THEN
        RETURN total * 2;
    ELSE
        RETURN total;
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Recreate the trigger function to pass predicted_winner_id and actual_winner_id.
CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
    is_knockout BOOLEAN;
    bet_row RECORD;
    old_points INT;
    new_points INT;
    delta INT;
BEGIN
    IF NEW.score_t1 IS NOT DISTINCT FROM OLD.score_t1
       AND NEW.score_t2 IS NOT DISTINCT FROM OLD.score_t2 THEN
        RETURN NEW;
    END IF;

    SELECT r.code <> 'GROUP' INTO is_knockout FROM round r WHERE r.id = NEW.round_id;
    IF is_knockout IS NULL THEN is_knockout := FALSE; END IF;

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
            bet_row.predicted_winner_id, OLD.winner_id
        );
        new_points := score_match_for_bet(
            is_knockout,
            bet_row.bet_t1, bet_row.bet_t2,
            NEW.score_t1, NEW.score_t2,
            bet_row.predicted_winner_id, NEW.winner_id
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

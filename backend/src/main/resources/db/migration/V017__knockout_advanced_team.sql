-- V017: advanced_team_id — the team that actually progresses from a knockout.
--
-- winner_id is score-derived and forced NULL on any draw (V016 lines 126-131),
-- so a penalty-shootout result (returned by football-data.org as an on-pitch
-- draw + a `winner` field) loses the progressing team. advanced_team_id stores
-- it and is NOT nulled on draws. Scoring uses it as actual_winner_id so the
-- knockout advancement bonus is awarded; the matches UI uses it to show who
-- advanced. winner_id keeps its existing meaning (used by test-mode advancement).

ALTER TABLE match ADD COLUMN advanced_team_id BIGINT REFERENCES team(id);

-- Backfill decisive played matches: the progressing team equals the scoreboard
-- winner. Draws have winner_id NULL and stay NULL (no historical penalty data).
UPDATE match SET advanced_team_id = winner_id
WHERE played = TRUE AND winner_id IS NOT NULL AND advanced_team_id IS NULL;

-- Rewrite the trigger function: maintain advanced_team_id, score off it.
CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
    is_knockout BOOLEAN;
    bet_row RECORD;
    old_points INT;
    new_points INT;
    delta INT;
BEGIN
    -- Compute the progressing team BEFORE the no-op guard so a decisive result
    -- always derives it, and a draw keeps whatever the writer supplied (the
    -- penalty winner) rather than discarding it.
    NEW.advanced_team_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NEW.advanced_team_id   -- draw: trust the supplied penalty winner
    END;

    -- No-op short-circuit: nothing changed that affects scoring or advancement.
    IF NEW.score_t1 IS NOT DISTINCT FROM OLD.score_t1
       AND NEW.score_t2 IS NOT DISTINCT FROM OLD.score_t2
       AND NEW.advanced_team_id IS NOT DISTINCT FROM OLD.advanced_team_id THEN
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
            bet_row.predicted_winner_id, OLD.advanced_team_id
        );
        new_points := score_match_for_bet(
            is_knockout,
            bet_row.bet_t1, bet_row.bet_t2,
            NEW.score_t1, NEW.score_t2,
            bet_row.predicted_winner_id, NEW.advanced_team_id
        );
        delta := new_points - old_points;
        IF delta <> 0 THEN
            UPDATE quiniela SET points = points + delta, updated_at = NOW()
            WHERE id = bet_row.quiniela_id;
        END IF;
    END LOOP;

    NEW.updated_at := NOW();

    -- Keep winner_id score-derived (legacy: test-mode advancement reads it).
    NEW.winner_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NULL
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- The trigger must also fire when only advanced_team_id changes (penalty-winner
-- correction with an unchanged on-pitch draw). Recreate with the wider column list.
DROP TRIGGER IF EXISTS matches_score_update_trigger ON match;
CREATE TRIGGER matches_score_update_trigger
BEFORE UPDATE OF score_t1, score_t2, advanced_team_id ON match
FOR EACH ROW EXECUTE FUNCTION update_players_score();

-- V010: Replace V005's scoring ladder with a 4-component additive shape.
--
-- Components, group / knockout (×2):
--   Outcome (winner-or-draw bucket):  3 / 6
--   Team 1 exact score:               2 / 4
--   Team 2 exact score:               2 / 4
--   Goal difference (signed):         1 / 2   (suppressed when exact)
--
-- The trigger function `update_players_score` is unchanged. It calls
-- `score_match_for_bet` and the new behavior is contained there.
--
-- After replacing the function, a one-time UPDATE recomputes
-- `quinielas.points` for any quinielas whose bets cover at least one
-- played match.

CREATE OR REPLACE FUNCTION score_match_for_bet(
    is_knockout BOOLEAN,
    bet_t1 INT, bet_t2 INT,
    actual_t1 INT, actual_t2 INT
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
        outcome_pts := 3;
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

-- Recompute cached points for quinielas with at least one played-match bet.
-- The WHERE EXISTS clause skips quinielas with no relevant bets so they
-- don't get a spurious updated_at bump.
UPDATE quiniela q
SET points = COALESCE((
        SELECT SUM(score_match_for_bet(
            (SELECT r.code <> 'GROUP' FROM round r WHERE r.id = m.round_id),
            b.score_t1, b.score_t2, m.score_t1, m.score_t2
        ))
        FROM bet b
        JOIN match m ON m.id = b.match_id
        WHERE b.quiniela_id = q.id
          AND m.played = true
    ), 0),
    updated_at = NOW()
WHERE EXISTS (
    SELECT 1 FROM bet b
    JOIN match m ON m.id = b.match_id
    WHERE b.quiniela_id = q.id
      AND m.played = true
);

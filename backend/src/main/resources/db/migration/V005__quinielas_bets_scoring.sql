-- V005: Player picks + scoring engine.
--
-- One quiniela per (pool, user) — the player's bracket. Bets are scored
-- by a PL/pgSQL trigger that fires when match results are entered or
-- corrected. quiniela.points is denormalized for fast leaderboard reads.
--
-- Point rules (group stage / knockout):
--   exact score:         5  / 10
--   winner + goal diff:  3  / 6
--   correct winner:      2  / 4
--   correct draw:        2  / 4   (predicted draw, any draw outcome)
--   miss:                0
--
-- The trigger subtracts the old contribution before adding the new so
-- result corrections produce the correct delta.

CREATE TABLE quiniela (
    id          BIGSERIAL PRIMARY KEY,
    pool_id     BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    points      INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (pool_id, user_id)
);

CREATE INDEX idx_quiniela_pool_points ON quiniela(pool_id, points DESC);

CREATE TABLE bet (
    quiniela_id BIGINT NOT NULL REFERENCES quiniela(id) ON DELETE CASCADE,
    match_id    BIGINT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    score_t1    INT NOT NULL,
    score_t2    INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (quiniela_id, match_id),
    CHECK (score_t1 >= 0 AND score_t1 <= 30),
    CHECK (score_t2 >= 0 AND score_t2 <= 30)
);

CREATE INDEX idx_bet_match ON bet(match_id);

-- ── Scoring engine ─────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION score_match_for_bet(
    is_knockout BOOLEAN,
    bet_t1 INT, bet_t2 INT,
    actual_t1 INT, actual_t2 INT
) RETURNS INT AS $$
DECLARE
    base INT := 0;
    bet_winner INT;     -- 1 = team1, 2 = team2, 0 = draw
    actual_winner INT;
BEGIN
    IF actual_t1 IS NULL OR actual_t2 IS NULL THEN RETURN 0; END IF;

    -- Exact score.
    IF bet_t1 = actual_t1 AND bet_t2 = actual_t2 THEN
        base := 5;
    ELSE
        bet_winner := CASE
            WHEN bet_t1 > bet_t2 THEN 1
            WHEN bet_t1 < bet_t2 THEN 2
            ELSE 0
        END;
        actual_winner := CASE
            WHEN actual_t1 > actual_t2 THEN 1
            WHEN actual_t1 < actual_t2 THEN 2
            ELSE 0
        END;

        IF bet_winner = actual_winner THEN
            IF bet_winner = 0 THEN
                -- Correct draw outcome (any draw vs predicted draw).
                base := 2;
            ELSIF (bet_t1 - bet_t2) = (actual_t1 - actual_t2) THEN
                -- Correct winner AND correct goal difference.
                base := 3;
            ELSE
                -- Correct winner only.
                base := 2;
            END IF;
        END IF;
    END IF;

    -- Knockout matches double everything.
    IF is_knockout THEN
        base := base * 2;
    END IF;

    RETURN base;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
    is_knockout BOOLEAN;
    bet_row RECORD;
    old_points INT;
    new_points INT;
    delta INT;
BEGIN
    -- Only react when score columns actually change.
    IF NEW.score_t1 IS NOT DISTINCT FROM OLD.score_t1
       AND NEW.score_t2 IS NOT DISTINCT FROM OLD.score_t2 THEN
        RETURN NEW;
    END IF;

    -- Knockout = any round whose code starts with 'R', 'QF', 'SF', 'THIRD', or 'FINAL'.
    -- Group-stage round has code 'GROUP'.
    SELECT r.code <> 'GROUP' INTO is_knockout FROM round r WHERE r.id = NEW.round_id;
    IF is_knockout IS NULL THEN is_knockout := FALSE; END IF;

    FOR bet_row IN
        SELECT b.quiniela_id, b.score_t1 AS bet_t1, b.score_t2 AS bet_t2
        FROM bet b
        WHERE b.match_id = NEW.id
    LOOP
        old_points := score_match_for_bet(is_knockout, bet_row.bet_t1, bet_row.bet_t2, OLD.score_t1, OLD.score_t2);
        new_points := score_match_for_bet(is_knockout, bet_row.bet_t1, bet_row.bet_t2, NEW.score_t1, NEW.score_t2);
        delta := new_points - old_points;
        IF delta <> 0 THEN
            UPDATE quiniela SET points = points + delta, updated_at = NOW()
            WHERE id = bet_row.quiniela_id;
        END IF;
    END LOOP;

    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER matches_score_update_trigger
BEFORE UPDATE OF score_t1, score_t2 ON match
FOR EACH ROW EXECUTE FUNCTION update_players_score();

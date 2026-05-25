-- V004: Tournament fixture data — teams, rounds, matches.
--
-- Multi-tournament-ready (every row carries tournament_id). The match
-- table self-references via match_parent_1_id / match_parent_2_id so the
-- knockout bracket can be modeled as a tree: a Round of 16 match has two
-- R32 parents, an R16 advances to a QF, etc. Group-stage matches have
-- NULL parents.
--
-- `winner_id` is denormalized on match for fast scoring trigger access
-- (instead of computing from score_t1/score_t2 on every UPDATE).

CREATE TABLE team (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT NOT NULL REFERENCES tournament(id) ON DELETE CASCADE,
    code            VARCHAR(8) NOT NULL,
    name            VARCHAR(64) NOT NULL,
    group_code      CHAR(1),
    flag_emoji      VARCHAR(8),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tournament_id, code)
);

CREATE INDEX idx_team_group ON team(tournament_id, group_code);

CREATE TABLE round (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT NOT NULL REFERENCES tournament(id) ON DELETE CASCADE,
    code            VARCHAR(16) NOT NULL,
    name            VARCHAR(64) NOT NULL,
    sequence        INT NOT NULL,
    UNIQUE (tournament_id, code),
    UNIQUE (tournament_id, sequence)
);

CREATE TABLE match (
    id                  BIGSERIAL PRIMARY KEY,
    tournament_id       BIGINT NOT NULL REFERENCES tournament(id) ON DELETE CASCADE,
    round_id            BIGINT NOT NULL REFERENCES round(id),
    group_code          CHAR(1),                              -- non-NULL only for group-stage matches
    team_1_id           BIGINT REFERENCES team(id),           -- nullable: knockout matches before parents resolve
    team_2_id           BIGINT REFERENCES team(id),
    score_t1            INT,
    score_t2            INT,
    winner_id           BIGINT REFERENCES team(id),
    played              BOOLEAN NOT NULL DEFAULT FALSE,
    kickoff_at          TIMESTAMPTZ NOT NULL,
    match_parent_1_id   BIGINT REFERENCES match(id),          -- parent matches in the bracket tree
    match_parent_2_id   BIGINT REFERENCES match(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (score_t1 IS NULL OR score_t1 >= 0),
    CHECK (score_t2 IS NULL OR score_t2 >= 0)
);

CREATE INDEX idx_match_tournament ON match(tournament_id);
CREATE INDEX idx_match_round ON match(round_id);
CREATE INDEX idx_match_group ON match(tournament_id, group_code) WHERE group_code IS NOT NULL;
CREATE INDEX idx_match_kickoff ON match(kickoff_at);

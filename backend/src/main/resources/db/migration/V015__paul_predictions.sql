-- V015: Paul's AI predictions + identity-based pot eligibility.

-- Pot eligibility: bots never win money. ADMIN role is excluded at query time;
-- is_bot excludes Paul (who is role 'player' so he shows on the leaderboard).
ALTER TABLE users ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT FALSE;

-- Candidate (one per model) + official (ensemble) predictions per match.
CREATE TABLE paul_prediction (
    id             BIGSERIAL PRIMARY KEY,
    match_id       BIGINT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    provider       VARCHAR(32)  NOT NULL,
    model          VARCHAR(64)  NOT NULL,
    kind           VARCHAR(16)  NOT NULL DEFAULT 'CANDIDATE',
    score_t1       INT NOT NULL,
    score_t2       INT NOT NULL,
    confidence     NUMERIC(3,2),
    reasoning      TEXT,
    reasoning_lang VARCHAR(8) NOT NULL DEFAULT 'es',
    source         VARCHAR(16) NOT NULL DEFAULT 'AI',
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (score_t1 >= 0 AND score_t2 >= 0),
    CHECK (kind IN ('CANDIDATE', 'OFFICIAL')),
    CHECK (source IN ('AI', 'FALLBACK')),
    UNIQUE (match_id, model, kind)
);
CREATE INDEX idx_paul_prediction_match ON paul_prediction(match_id);

-- Paul bot user. NO quiniela yet — absence of a quiniela means "not competing".
-- Created only at reveal (PaulService.reveal). role 'player' so he shows on the
-- leaderboard once revealed; is_bot TRUE so he is never prize-eligible.
INSERT INTO users (google_sub, email, display_name, role, is_bot)
VALUES ('paul-bot-oracle', 'paul@laquinieladelospanas.com', 'Pulpo Paul 🐙', 'player', TRUE)
ON CONFLICT (google_sub) DO NOTHING;

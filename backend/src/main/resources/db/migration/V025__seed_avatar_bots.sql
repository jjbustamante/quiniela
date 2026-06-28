-- V025: seed the two avatar bot users (same pattern as Paul's V015 seed).
-- role 'player' so they show on the leaderboard; is_bot TRUE so never prize-eligible.
INSERT INTO users (google_sub, email, display_name, role, is_bot) VALUES
  ('otto-bot-oracle',    'otto@laquinieladelospanas.com',    'Otto la Nutria 🦦',    'player', TRUE),
  ('chitara-bot-oracle', 'chitara@laquinieladelospanas.com', 'Chitara la Leoparda 🐆','player', TRUE)
ON CONFLICT (google_sub) DO NOTHING;

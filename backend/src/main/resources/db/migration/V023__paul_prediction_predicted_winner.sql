-- V023: Paul's predicted advancing team for knockout draws.
--
-- Mirrors bet.predicted_winner_id (V016). NULL for group predictions and for
-- decisive knockout scores; set only when Paul predicts a regulation draw and
-- names which team advances on penalties.
ALTER TABLE paul_prediction ADD COLUMN predicted_winner_id BIGINT REFERENCES team(id);

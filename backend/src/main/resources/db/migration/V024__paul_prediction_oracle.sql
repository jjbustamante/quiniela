-- V024: attribute each Paul prediction to an oracle bot (Paul, Otto, Chitara, …).
-- Existing rows backfill to 'paul' via the column default, so Paul's data is unchanged.
ALTER TABLE paul_prediction ADD COLUMN oracle VARCHAR(32) NOT NULL DEFAULT 'paul';

ALTER TABLE paul_prediction DROP CONSTRAINT paul_prediction_match_id_model_kind_key;
ALTER TABLE paul_prediction
    ADD CONSTRAINT paul_prediction_oracle_match_model_kind_key
    UNIQUE (oracle, match_id, model, kind);

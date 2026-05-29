-- V012: Per-user display timezone (IANA zone id). Instant fields (match
-- kickoffs, deadlines) are rendered in this zone. Default America/Bogota so
-- existing rows + users who never open settings get a correct-for-most zone
-- rather than raw UTC.

ALTER TABLE users ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/Bogota';

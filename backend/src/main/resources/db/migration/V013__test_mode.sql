-- V013: Admin test mode flag. Default true so the app launches in test mode;
-- the admin flips it false to "go live". Gates the not-real-data banner +
-- the admin test tools (clean / simulate / editable deadlines).

ALTER TABLE tournament ADD COLUMN test_mode BOOLEAN NOT NULL DEFAULT true;

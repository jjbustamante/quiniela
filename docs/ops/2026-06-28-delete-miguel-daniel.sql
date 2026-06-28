-- One-off prod data op (run manually via the Cloud SQL proxy as quiniela_app).
-- Removes two abandoned competitors so Otto & Chitara take their spots.
-- Blast radius verified 2026-06-28: no invitees reference them, 0 payments, 0 bets,
-- captain is a role (not an FK). DRY-RUN FIRST: run with ROLLBACK, check counts, then COMMIT.
--
--   Miguel Angel Tona Gené  id 13  (captain)  migueltona@gmail.com
--   Daniel                  id 24  (player)   daniel.art.diaz05@gmail.com

BEGIN;

DELETE FROM bet            WHERE quiniela_id IN (SELECT id FROM quiniela WHERE user_id IN (13,24));
DELETE FROM quiniela       WHERE user_id IN (13,24);
DELETE FROM pool_membership WHERE user_id IN (13,24);
DELETE FROM users          WHERE id IN (13,24);

-- Sanity: expect 0 rows remaining for both ids across all of these.
SELECT 'users' t, count(*) FROM users WHERE id IN (13,24)
UNION ALL SELECT 'quiniela', count(*) FROM quiniela WHERE user_id IN (13,24)
UNION ALL SELECT 'pool_membership', count(*) FROM pool_membership WHERE user_id IN (13,24);

-- ROLLBACK;   -- use this on the dry run
-- COMMIT;     -- use this once the dry run looks correct

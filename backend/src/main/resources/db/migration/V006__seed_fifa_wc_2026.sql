-- V006: Seed FIFA World Cup 2026 — rounds + tournament deadlines only.
--
-- Teams + matches are loaded at startup from football-data.org by
-- FootballDataLoader (ApplicationRunner). This migration intentionally
-- omits team and match rows so the loader can own that data canonically.

-- ── Rounds ─────────────────────────────────────────────────────────────────

INSERT INTO round (id, tournament_id, code, name, sequence) VALUES
    (1, 1, 'GROUP',        'Fase de grupos', 1),
    (2, 1, 'R32',          'Dieciseisavos',  2),
    (3, 1, 'R16',          'Octavos',        3),
    (4, 1, 'QF',           'Cuartos',        4),
    (5, 1, 'SF',           'Semifinales',    5),
    (6, 1, 'THIRD_PLACE',  'Tercer puesto',  6),
    (7, 1, 'FINAL',        'Final',          7);

SELECT setval('round_id_seq', (SELECT MAX(id) FROM round));

-- ── Tournament deadlines ────────────────────────────────────────────────────
-- Lock enforcement compares against these timestamps.

UPDATE tournament
SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC',
    knockout_deadline    = TIMESTAMPTZ '2026-06-28 17:00 UTC'
WHERE id = 1;

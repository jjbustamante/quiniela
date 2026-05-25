-- V007: Test-only fixture seed (applied only in the test Postgres container).
--
-- Restores the 48 teams + 104 matches that V006 used to seed directly.
-- Production relies on FootballDataLoader; tests need deterministic fixture
-- data so they do not call the real football-data.org API.

-- ── Teams (48) — group_code A through L, 4 teams each ─────────────────────

INSERT INTO team (id, tournament_id, code, name, group_code, flag_emoji) VALUES
    -- Group A
    ( 1, 1, 'MEX', 'México',         'A', '🇲🇽'),
    ( 2, 1, 'CRC', 'Costa Rica',     'A', '🇨🇷'),
    ( 3, 1, 'NOR', 'Noruega',        'A', '🇳🇴'),
    ( 4, 1, 'SEN', 'Senegal',        'A', '🇸🇳'),
    -- Group B
    ( 5, 1, 'CAN', 'Canadá',         'B', '🇨🇦'),
    ( 6, 1, 'SUI', 'Suiza',          'B', '🇨🇭'),
    ( 7, 1, 'IRN', 'Irán',           'B', '🇮🇷'),
    ( 8, 1, 'HON', 'Honduras',       'B', '🇭🇳'),
    -- Group C
    ( 9, 1, 'USA', 'Estados Unidos', 'C', '🇺🇸'),
    (10, 1, 'CRO', 'Croacia',        'C', '🇭🇷'),
    (11, 1, 'GHA', 'Ghana',          'C', '🇬🇭'),
    (12, 1, 'IRQ', 'Iraq',           'C', '🇮🇶'),
    -- Group D
    (13, 1, 'ARG', 'Argentina',      'D', '🇦🇷'),
    (14, 1, 'JPN', 'Japón',          'D', '🇯🇵'),
    (15, 1, 'MAR', 'Marruecos',      'D', '🇲🇦'),
    (16, 1, 'JAM', 'Jamaica',        'D', '🇯🇲'),
    -- Group E
    (17, 1, 'BRA', 'Brasil',         'E', '🇧🇷'),
    (18, 1, 'GER', 'Alemania',       'E', '🇩🇪'),
    (19, 1, 'KOR', 'Corea del Sur',  'E', '🇰🇷'),
    (20, 1, 'NZL', 'Nueva Zelanda',  'E', '🇳🇿'),
    -- Group F
    (21, 1, 'ESP', 'España',         'F', '🇪🇸'),
    (22, 1, 'POR', 'Portugal',       'F', '🇵🇹'),
    (23, 1, 'EGY', 'Egipto',         'F', '🇪🇬'),
    (24, 1, 'AUS', 'Australia',      'F', '🇦🇺'),
    -- Group G
    (25, 1, 'FRA', 'Francia',        'G', '🇫🇷'),
    (26, 1, 'COL', 'Colombia',       'G', '🇨🇴'),
    (27, 1, 'KSA', 'Arabia Saudí',   'G', '🇸🇦'),
    (28, 1, 'PAN', 'Panamá',         'G', '🇵🇦'),
    -- Group H
    (29, 1, 'ENG', 'Inglaterra',     'H', '🇬🇧'),
    (30, 1, 'NED', 'Países Bajos',   'H', '🇳🇱'),
    (31, 1, 'NGA', 'Nigeria',        'H', '🇳🇬'),
    (32, 1, 'CUR', 'Curazao',        'H', '🇨🇼'),
    -- Group I
    (33, 1, 'BEL', 'Bélgica',        'I', '🇧🇪'),
    (34, 1, 'URU', 'Uruguay',        'I', '🇺🇾'),
    (35, 1, 'CIV', 'Costa de Marfil','I', '🇨🇮'),
    (36, 1, 'QAT', 'Catar',          'I', '🇶🇦'),
    -- Group J
    (37, 1, 'ITA', 'Italia',         'J', '🇮🇹'),
    (38, 1, 'ECU', 'Ecuador',        'J', '🇪🇨'),
    (39, 1, 'TUN', 'Túnez',          'J', '🇹🇳'),
    (40, 1, 'CPV', 'Cabo Verde',     'J', '🇨🇻'),
    -- Group K
    (41, 1, 'TBD_K1', 'Países K1',   'K', NULL),
    (42, 1, 'TBD_K2', 'Países K2',   'K', NULL),
    (43, 1, 'TBD_K3', 'Países K3',   'K', NULL),
    (44, 1, 'TBD_K4', 'Países K4',   'K', NULL),
    -- Group L
    (45, 1, 'TBD_L1', 'Países L1',   'L', NULL),
    (46, 1, 'TBD_L2', 'Países L2',   'L', NULL),
    (47, 1, 'TBD_L3', 'Países L3',   'L', NULL),
    (48, 1, 'TBD_L4', 'Países L4',   'L', NULL);

UPDATE team SET group_code = 'K' WHERE id IN (41, 42, 43, 44) AND tournament_id = 1;
UPDATE team SET group_code = 'L' WHERE id IN (45, 46, 47, 48) AND tournament_id = 1;

SELECT setval('team_id_seq', (SELECT MAX(id) FROM team));

-- ── Group matches (72) ─────────────────────────────────────────────────────

INSERT INTO match (tournament_id, round_id, group_code, team_1_id, team_2_id, kickoff_at)
SELECT
    1,
    1,
    g.group_code,
    t1.id,
    t2.id,
    TIMESTAMPTZ '2026-06-11 17:00 UTC'
        + (ROW_NUMBER() OVER (ORDER BY g.group_code, p.match_no) - 1) * INTERVAL '3 hours'
FROM (
    SELECT DISTINCT group_code
    FROM team
    WHERE tournament_id = 1 AND group_code IS NOT NULL
) g
CROSS JOIN LATERAL (
    SELECT 1 AS match_no, 1 AS a, 2 AS b UNION ALL
    SELECT 2,             3,      4       UNION ALL
    SELECT 3,             1,      3       UNION ALL
    SELECT 4,             2,      4       UNION ALL
    SELECT 5,             1,      4       UNION ALL
    SELECT 6,             2,      3
) p
JOIN team t1
    ON  t1.group_code   = g.group_code
    AND t1.tournament_id = 1
    AND t1.id = (
        SELECT id FROM team
        WHERE group_code = g.group_code AND tournament_id = 1
        ORDER BY id
        LIMIT 1 OFFSET (p.a - 1)
    )
JOIN team t2
    ON  t2.group_code   = g.group_code
    AND t2.tournament_id = 1
    AND t2.id = (
        SELECT id FROM team
        WHERE group_code = g.group_code AND tournament_id = 1
        ORDER BY id
        LIMIT 1 OFFSET (p.b - 1)
    );

-- ── Knockout matches (32) ─────────────────────────────────────────────────

INSERT INTO match (tournament_id, round_id, kickoff_at)
SELECT 1, 2,
    TIMESTAMPTZ '2026-06-28 17:00 UTC' + (n - 1) * INTERVAL '4 hours'
FROM generate_series(1, 16) AS n;

WITH r32 AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match
    WHERE round_id = 2 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT
    1, 3,
    TIMESTAMPTZ '2026-07-03 17:00 UTC' + (p1.rn / 2 - 1) * INTERVAL '4 hours',
    p1.id,
    p2.id
FROM r32 p1
JOIN r32 p2 ON p2.rn = p1.rn + 1
WHERE p1.rn % 2 = 1;

WITH r16 AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match
    WHERE round_id = 3 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT
    1, 4,
    TIMESTAMPTZ '2026-07-09 17:00 UTC' + (p1.rn / 2 - 1) * INTERVAL '4 hours',
    p1.id,
    p2.id
FROM r16 p1
JOIN r16 p2 ON p2.rn = p1.rn + 1
WHERE p1.rn % 2 = 1;

WITH qf AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match
    WHERE round_id = 4 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT
    1, 5,
    TIMESTAMPTZ '2026-07-14 17:00 UTC' + (p1.rn / 2 - 1) * INTERVAL '24 hours',
    p1.id,
    p2.id
FROM qf p1
JOIN qf p2 ON p2.rn = p1.rn + 1
WHERE p1.rn % 2 = 1;

WITH sf AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match
    WHERE round_id = 5 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT
    1, 6,
    TIMESTAMPTZ '2026-07-18 17:00 UTC',
    p1.id,
    p2.id
FROM sf p1
JOIN sf p2 ON p1.rn = 1 AND p2.rn = 2;

WITH sf AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match
    WHERE round_id = 5 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT
    1, 7,
    TIMESTAMPTZ '2026-07-19 17:00 UTC',
    p1.id,
    p2.id
FROM sf p1
JOIN sf p2 ON p1.rn = 1 AND p2.rn = 2;

SELECT setval('match_id_seq', (SELECT MAX(id) FROM match));

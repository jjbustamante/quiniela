# Plan 2 — Bracket data + Fill UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the bracket data model and the fill UX. After this plan, a signed-in player can navigate from `/home` into any group, see 6 real matches with team names + kickoff times, tap a score cell to open a numpad with quick presets, accept Paul's suggestion, and have every change autosaved. The 32 knockout matches exist in the schema with bracket parent-pointers; the UI for them is reachable but locked until the group stage finishes.

**Architecture:** Three Flyway migrations land the schema in stages — V004 adds the read-side tournament data (`team`, `round`, `match`), V005 adds the write side (`quiniela`, `bet`) plus the PL/pgSQL scoring trigger ported and modernized from the legacy 2014 app, and V006 seeds the 2026 World Cup fixtures. Backend grows new feature packages `team/`, `match/`, `quiniela/`, `bet/`, `paul/`. The bracket-write endpoint enforces the group-stage deadline server-side. Frontend adds `/group/[groupId]` and `/knockout/[roundId]` routes plus a `NumpadScoreInput` client component; the lobby graduates from skeleton cards to live progress, and the Paul-fill-all button works end-to-end.

**Tech Stack:** Same as Plan 1. Server actions for autosave (POST per cell change). PL/pgSQL trigger for points computation. No new third-party dependencies on the frontend.

---

## Reference

- Spec: [`docs/superpowers/specs/2026-05-25-quiniela-mvp-ui-design.md`](../specs/2026-05-25-quiniela-mvp-ui-design.md)
- Plan 1 (foundation): [`docs/superpowers/plans/2026-05-25-quiniela-plan-1-foundation-auth-invite.md`](2026-05-25-quiniela-plan-1-foundation-auth-invite.md)
- Legacy scoring trigger (rules guidance): `legacy/db/scripts/update_players_score_trigger.sql`
- Legacy schema (entity shape guidance): `legacy/db/schema.rb`

## Scoring rules (documented in V005 SQL too)

These point values are baked into the V005 trigger. **Edit V005 before applying the migration** if you want different values — they are not configurable at runtime in v1.

**Group stage match (one bet, one match):**

| Outcome | Points |
|---------|--------|
| Exact score (e.g. bet 2-1, actual 2-1) | 5 |
| Correct winner + goal difference (e.g. bet 2-1, actual 3-2) | 3 |
| Correct winner (e.g. bet 2-1, actual 4-0) | 2 |
| Correct draw outcome (e.g. bet 1-1, actual 0-0) | 2 |
| Miss | 0 |

**Knockout match:** same rules, doubled (10 / 6 / 4 / 4 / 0). Picking which teams advance is implicit in picking the winner.

`quinielas.points` is recomputed by the trigger on every `UPDATE` of `matches.score_t1` / `matches.score_t2`. The trigger subtracts the old contribution and adds the new one so a result correction yields the right delta.

## File Structure

### Backend (`backend/`)

Migrations (`src/main/resources/db/migration/`):
- Create: `V004__tournament_fixtures.sql` — `team`, `round`, `match` tables with FKs and bracket-parent self-references on `match`.
- Create: `V005__quinielas_bets_scoring.sql` — `quiniela`, `bet` tables; PL/pgSQL `update_players_score()` function + trigger.
- Create: `V006__seed_fifa_wc_2026.sql` — 7 round rows, 48 team rows, 72 group fixtures, 32 knockout fixtures (placeholders for advancing teams; admin populates later).

Domain (`src/main/java/io/quiniela/api/`):
- Create: `team/Team.java`, `team/TeamRepository.java`
- Create: `match/Round.java`, `match/RoundRepository.java`, `match/Match.java`, `match/MatchRepository.java`
- Create: `quiniela/Quiniela.java`, `quiniela/QuinielaRepository.java`, `quiniela/QuinielaService.java`
- Create: `bet/Bet.java`, `bet/BetId.java`, `bet/BetRepository.java`
- Create: `bracket/BracketController.java`, `bracket/BracketService.java` — read + write endpoints
- Create: `paul/PaulController.java`, `paul/PaulService.java` — stub suggest + fill endpoints

Tests (`src/test/java/io/quiniela/api/`):
- `team/TeamRepositoryIT.java`
- `match/MatchRepositoryIT.java`
- `quiniela/QuinielaRepositoryIT.java`
- `quiniela/ScoringTriggerIT.java` — exact-score, winner-only, draw, miss, correction-delta cases
- `bracket/BracketControllerIT.java` — read + write, lock enforcement
- `paul/PaulControllerIT.java` — suggest + fill

### Frontend (`frontend/`)

- Modify: `lib/api/me.ts` — extend or pair with new `lib/api/bracket.ts`
- Create: `lib/api/bracket.ts` — `getMyBracket()`, `saveBet(matchId, scoreT1, scoreT2)`
- Create: `lib/api/paul.ts` — `suggestForMatch(matchId)`, `fillAll()`
- Modify: `components/lobby/GroupCardSkeleton.tsx` — accept live `{ filled, total }` props, rename to `GroupCard.tsx`
- Modify: `components/lobby/KnockoutLockedCard.tsx` — already correct, no changes
- Create: `components/lobby/PaulFillAllButton.tsx` — client component, calls `paul.fillAll()` + refetches
- Create: `components/group/MatchRow.tsx` — server component: team names + score cells + Paul icon
- Create: `components/group/NumpadScoreInput.tsx` — client component, slide-up numpad with preset chips
- Create: `components/group/PaulSuggestionInline.tsx` — client expand-in-place
- Create: `app/group/[groupId]/page.tsx`
- Create: `app/group/[groupId]/actions.ts` — `saveBet`, `acceptPaulSuggestion` server actions
- Create: `app/knockout/[roundId]/page.tsx` — shell with lock UX
- Modify: `app/home/page.tsx` — replace skeleton with live data, add Paul-fill-all button
- Tests:
  - `components/group/NumpadScoreInput.test.tsx`
  - `components/lobby/GroupCard.test.tsx` (rename + new props)
  - `e2e/group-drill-in.e2e.ts` (skipped pending fixture seed in CI)

---

## Task 1: V004 migration — Team + Round + Match schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V004__tournament_fixtures.sql`
- Create: `backend/src/test/java/io/quiniela/api/support/V004MigrationTest.java`

- [ ] **Step 1: Write the failing migration test**

Create `backend/src/test/java/io/quiniela/api/support/V004MigrationTest.java`:

```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V004MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void teamTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'team' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("id", "tournament_id", "code", "name", "group_code");
  }

  @Test
  void roundTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'round' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("id", "tournament_id", "code", "name", "sequence");
  }

  @Test
  void matchTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'match' AND table_schema = 'public'",
            String.class);
    assertThat(columns)
        .contains(
            "id",
            "tournament_id",
            "round_id",
            "team_1_id",
            "team_2_id",
            "score_t1",
            "score_t2",
            "winner_id",
            "played",
            "kickoff_at",
            "match_parent_1_id",
            "match_parent_2_id");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL — tables don't exist.

- [ ] **Step 3: Write the V004 migration**

Create `backend/src/main/resources/db/migration/V004__tournament_fixtures.sql`:

```sql
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

CREATE INDEX idx_team_tournament ON team(tournament_id);
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

CREATE INDEX idx_round_tournament ON round(tournament_id);

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
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && ./mvnw verify
```

Expected: PASS — all V004 column-presence assertions green; all prior tests (20 from Plan 1) still green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V004__tournament_fixtures.sql \
        backend/src/test/java/io/quiniela/api/support/V004MigrationTest.java
git commit -m "feat(backend): V004 migration — team, round, match schema"
```

---

## Task 2: V005 migration — Quiniela + Bet schema + scoring trigger

**Files:**
- Create: `backend/src/main/resources/db/migration/V005__quinielas_bets_scoring.sql`
- Create: `backend/src/test/java/io/quiniela/api/support/V005MigrationTest.java`

- [ ] **Step 1: Write the failing migration test**

Create `backend/src/test/java/io/quiniela/api/support/V005MigrationTest.java`:

```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V005MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void quinielaTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'quiniela' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("id", "pool_id", "user_id", "points");
  }

  @Test
  void betTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'bet' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("quiniela_id", "match_id", "score_t1", "score_t2");
  }

  @Test
  void scoringTriggerExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_trigger "
                + "WHERE tgname = 'matches_score_update_trigger' AND NOT tgisinternal",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL — `quiniela` / `bet` tables and trigger don't exist.

- [ ] **Step 3: Write the V005 migration**

Create `backend/src/main/resources/db/migration/V005__quinielas_bets_scoring.sql`:

```sql
-- V005: Player picks + scoring engine.
--
-- One quiniela per (pool, user) — the player's bracket. Bets are scored
-- by a PL/pgSQL trigger that fires when match results are entered or
-- corrected. quiniela.points is denormalized for fast leaderboard reads.
--
-- Point rules (group stage / knockout):
--   exact score:         5  / 10
--   winner + goal diff:  3  / 6
--   correct winner:      2  / 4
--   correct draw:        2  / 4   (predicted draw, any draw outcome)
--   miss:                0
--
-- The trigger subtracts the old contribution before adding the new so
-- result corrections produce the correct delta.

CREATE TABLE quiniela (
    id          BIGSERIAL PRIMARY KEY,
    pool_id     BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    points      INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (pool_id, user_id)
);

CREATE INDEX idx_quiniela_pool_points ON quiniela(pool_id, points DESC);

CREATE TABLE bet (
    quiniela_id BIGINT NOT NULL REFERENCES quiniela(id) ON DELETE CASCADE,
    match_id    BIGINT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    score_t1    INT NOT NULL,
    score_t2    INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (quiniela_id, match_id),
    CHECK (score_t1 >= 0 AND score_t1 <= 30),
    CHECK (score_t2 >= 0 AND score_t2 <= 30)
);

CREATE INDEX idx_bet_match ON bet(match_id);

-- ── Scoring engine ─────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION score_match_for_bet(
    is_knockout BOOLEAN,
    bet_t1 INT, bet_t2 INT,
    actual_t1 INT, actual_t2 INT
) RETURNS INT AS $$
DECLARE
    base INT := 0;
    bet_winner INT;     -- 1 = team1, 2 = team2, 0 = draw
    actual_winner INT;
BEGIN
    IF actual_t1 IS NULL OR actual_t2 IS NULL THEN RETURN 0; END IF;

    -- Exact score.
    IF bet_t1 = actual_t1 AND bet_t2 = actual_t2 THEN
        base := 5;
    ELSE
        bet_winner := CASE
            WHEN bet_t1 > bet_t2 THEN 1
            WHEN bet_t1 < bet_t2 THEN 2
            ELSE 0
        END;
        actual_winner := CASE
            WHEN actual_t1 > actual_t2 THEN 1
            WHEN actual_t1 < actual_t2 THEN 2
            ELSE 0
        END;

        IF bet_winner = actual_winner THEN
            IF bet_winner = 0 THEN
                -- Correct draw outcome (any draw vs predicted draw).
                base := 2;
            ELSIF (bet_t1 - bet_t2) = (actual_t1 - actual_t2) THEN
                -- Correct winner AND correct goal difference.
                base := 3;
            ELSE
                -- Correct winner only.
                base := 2;
            END IF;
        END IF;
    END IF;

    -- Knockout matches double everything.
    IF is_knockout THEN
        base := base * 2;
    END IF;

    RETURN base;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
    is_knockout BOOLEAN;
    bet_row RECORD;
    old_points INT;
    new_points INT;
    delta INT;
BEGIN
    -- Only react when score columns actually change.
    IF NEW.score_t1 IS NOT DISTINCT FROM OLD.score_t1
       AND NEW.score_t2 IS NOT DISTINCT FROM OLD.score_t2 THEN
        RETURN NEW;
    END IF;

    -- Knockout = any round whose code starts with 'R', 'QF', 'SF', 'THIRD', or 'FINAL'.
    -- Group-stage round has code 'GROUP'.
    SELECT r.code <> 'GROUP' INTO is_knockout FROM round r WHERE r.id = NEW.round_id;
    IF is_knockout IS NULL THEN is_knockout := FALSE; END IF;

    FOR bet_row IN
        SELECT b.quiniela_id, b.score_t1 AS bet_t1, b.score_t2 AS bet_t2
        FROM bet b
        WHERE b.match_id = NEW.id
    LOOP
        old_points := score_match_for_bet(is_knockout, bet_row.bet_t1, bet_row.bet_t2, OLD.score_t1, OLD.score_t2);
        new_points := score_match_for_bet(is_knockout, bet_row.bet_t1, bet_row.bet_t2, NEW.score_t1, NEW.score_t2);
        delta := new_points - old_points;
        IF delta <> 0 THEN
            UPDATE quiniela SET points = points + delta, updated_at = NOW()
            WHERE id = bet_row.quiniela_id;
        END IF;
    END LOOP;

    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER matches_score_update_trigger
BEFORE UPDATE OF score_t1, score_t2 ON match
FOR EACH ROW EXECUTE FUNCTION update_players_score();
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && ./mvnw verify
```

Expected: PASS — V005 tables + trigger exist; all 23 prior tests still green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V005__quinielas_bets_scoring.sql \
        backend/src/test/java/io/quiniela/api/support/V005MigrationTest.java
git commit -m "feat(backend): V005 migration — quiniela, bet, scoring trigger"
```

---

## Task 3: V006 seed migration — FIFA WC 2026 fixtures

**Files:**
- Create: `backend/src/main/resources/db/migration/V006__seed_fifa_wc_2026.sql`
- Create: `backend/src/test/java/io/quiniela/api/support/V006SeedTest.java`

> **Note for the engineer:** The 48 team names and group assignments below are a representative placeholder set as of plan-writing time. The real FIFA WC 2026 qualification + draw outcome may differ. Before deploying, run a `psql` UPDATE to correct any mismatches — the schema doesn't care, only the visible names do. The 12 groups × 6 matches structure stays the same regardless.

- [ ] **Step 1: Write the failing seed test**

Create `backend/src/test/java/io/quiniela/api/support/V006SeedTest.java`:

```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V006SeedTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void sevenRoundsSeeded() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM round WHERE tournament_id = 1", Long.class);
    assertThat(n).isEqualTo(7L);
  }

  @Test
  void fortyEightTeamsSeeded() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE tournament_id = 1", Long.class);
    assertThat(n).isEqualTo(48L);
  }

  @Test
  void twelveGroupsOfFour() {
    var jdbc = new JdbcTemplate(dataSource);
    var rows =
        jdbc.queryForList(
            "SELECT group_code, COUNT(*) AS n FROM team "
                + "WHERE tournament_id = 1 AND group_code IS NOT NULL "
                + "GROUP BY group_code ORDER BY group_code");
    assertThat(rows).hasSize(12);
    rows.forEach(r -> assertThat(r.get("n")).isEqualTo(4L));
  }

  @Test
  void seventyTwoGroupMatches() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id = m.round_id "
                + "WHERE m.tournament_id = 1 AND r.code = 'GROUP'",
            Long.class);
    assertThat(n).isEqualTo(72L);
  }

  @Test
  void thirtyTwoKnockoutMatches() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id = m.round_id "
                + "WHERE m.tournament_id = 1 AND r.code <> 'GROUP'",
            Long.class);
    assertThat(n).isEqualTo(32L);
  }

  @Test
  void totalMatchesIs104() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n =
        jdbc.queryForObject("SELECT COUNT(*) FROM match WHERE tournament_id = 1", Long.class);
    assertThat(n).isEqualTo(104L);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL — seed migration doesn't exist yet.

- [ ] **Step 3: Write the V006 seed migration**

Create `backend/src/main/resources/db/migration/V006__seed_fifa_wc_2026.sql`:

```sql
-- V006: Seed FIFA World Cup 2026.
--
-- 7 rounds, 48 teams, 12 groups, 72 group matches, 32 knockout matches.
-- Total = 104 matches.
--
-- Team names + group assignments are illustrative — verify against the
-- actual qualified teams + final group draw before going live.
-- Kickoff timestamps are illustrative — group matches stretch 2026-06-11
-- through 2026-06-27, knockouts run through 2026-07-19. Real schedule is
-- published by FIFA.

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

-- ── Teams (48) — group_code A through L, 4 teams each ─────────────────────

INSERT INTO team (id, tournament_id, code, name, group_code, flag_emoji) VALUES
    -- Group A
    ( 1, 1, 'MEX', 'México',        'A', '🇲🇽'),
    ( 2, 1, 'CRC', 'Costa Rica',    'A', '🇨🇷'),
    ( 3, 1, 'NOR', 'Noruega',       'A', '🇳🇴'),
    ( 4, 1, 'SEN', 'Senegal',       'A', '🇸🇳'),
    -- Group B
    ( 5, 1, 'CAN', 'Canadá',        'B', '🇨🇦'),
    ( 6, 1, 'SUI', 'Suiza',         'B', '🇨🇭'),
    ( 7, 1, 'IRN', 'Irán',          'B', '🇮🇷'),
    ( 8, 1, 'HON', 'Honduras',      'B', '🇭🇳'),
    -- Group C
    ( 9, 1, 'USA', 'Estados Unidos','C', '🇺🇸'),
    (10, 1, 'CRO', 'Croacia',       'C', '🇭🇷'),
    (11, 1, 'GHA', 'Ghana',         'C', '🇬🇭'),
    (12, 1, 'IRQ', 'Iraq',          'C', '🇮🇶'),
    -- Group D
    (13, 1, 'ARG', 'Argentina',     'D', '🇦🇷'),
    (14, 1, 'JPN', 'Japón',         'D', '🇯🇵'),
    (15, 1, 'MAR', 'Marruecos',     'D', '🇲🇦'),
    (16, 1, 'JAM', 'Jamaica',       'D', '🇯🇲'),
    -- Group E
    (17, 1, 'BRA', 'Brasil',        'E', '🇧🇷'),
    (18, 1, 'GER', 'Alemania',      'E', '🇩🇪'),
    (19, 1, 'KOR', 'Corea del Sur', 'E', '🇰🇷'),
    (20, 1, 'NZL', 'Nueva Zelanda', 'E', '🇳🇿'),
    -- Group F
    (21, 1, 'ESP', 'España',        'F', '🇪🇸'),
    (22, 1, 'POR', 'Portugal',      'F', '🇵🇹'),
    (23, 1, 'EGY', 'Egipto',        'F', '🇪🇬'),
    (24, 1, 'AUS', 'Australia',     'F', '🇦🇺'),
    -- Group G
    (25, 1, 'FRA', 'Francia',       'G', '🇫🇷'),
    (26, 1, 'COL', 'Colombia',      'G', '🇨🇴'),
    (27, 1, 'KSA', 'Arabia Saudí',  'G', '🇸🇦'),
    (28, 1, 'PAN', 'Panamá',        'G', '🇵🇦'),
    -- Group H
    (29, 1, 'ENG', 'Inglaterra',    'H', '🇬🇧'),
    (30, 1, 'NED', 'Países Bajos',  'H', '🇳🇱'),
    (31, 1, 'NGA', 'Nigeria',       'H', '🇳🇬'),
    (32, 1, 'CUR', 'Curazao',       'H', '🇨🇼'),
    -- Group I
    (33, 1, 'BEL', 'Bélgica',       'I', '🇧🇪'),
    (34, 1, 'URU', 'Uruguay',       'I', '🇺🇾'),
    (35, 1, 'CIV', 'Costa de Marfil','I', '🇨🇮'),
    (36, 1, 'QAT', 'Catar',         'I', '🇶🇦'),
    -- Group J
    (37, 1, 'ITA', 'Italia',        'J', '🇮🇹'),
    (38, 1, 'ECU', 'Ecuador',       'J', '🇪🇨'),
    (39, 1, 'TUN', 'Túnez',         'J', '🇹🇳'),
    (40, 1, 'CPV', 'Cabo Verde',    'J', '🇨🇻'),
    -- Group K
    (41, 1, 'NEX', 'Países K1',     'K', NULL),  -- placeholder until real draw
    (42, 1, 'TBD', 'Países K2',     'K', NULL),
    (43, 1, 'TBD', 'Países K3',     'K', NULL),
    (44, 1, 'TBD', 'Países K4',     'K', NULL),
    -- Group L
    (45, 1, 'TBD', 'Países L1',     'L', NULL),
    (46, 1, 'TBD', 'Países L2',     'L', NULL),
    (47, 1, 'TBD', 'Países L3',     'L', NULL),
    (48, 1, 'TBD', 'Países L4',     'L', NULL);

-- Reapply group_code to the K/L placeholder rows (UPDATE because of NULL above).
UPDATE team SET group_code = 'K' WHERE id IN (41, 42, 43, 44);
UPDATE team SET group_code = 'L' WHERE id IN (45, 46, 47, 48);

SELECT setval('team_id_seq', (SELECT MAX(id) FROM team));

-- ── Group matches (72) ─────────────────────────────────────────────────────
-- Six matches per group: every pair plays once.
-- Pairings for a group of 4 teams (T1, T2, T3, T4): T1-T2, T3-T4, T1-T3, T2-T4, T1-T4, T2-T3.

INSERT INTO match (tournament_id, round_id, group_code, team_1_id, team_2_id, kickoff_at)
SELECT
    1,                                          -- tournament_id
    1,                                          -- round_id (group)
    g.group_code,
    t1.id, t2.id,
    -- Stagger kickoffs across the 2-week group window starting 2026-06-11 17:00 UTC.
    TIMESTAMPTZ '2026-06-11 17:00 UTC'
        + (ROW_NUMBER() OVER (ORDER BY g.group_code, p.match_no) - 1) * INTERVAL '3 hours'
FROM (
    SELECT DISTINCT group_code FROM team WHERE tournament_id = 1 AND group_code IS NOT NULL
) g
CROSS JOIN LATERAL (
    SELECT 1 AS match_no, 1 AS a, 2 AS b UNION ALL
    SELECT 2,            3,      4 UNION ALL
    SELECT 3,            1,      3 UNION ALL
    SELECT 4,            2,      4 UNION ALL
    SELECT 5,            1,      4 UNION ALL
    SELECT 6,            2,      3
) p
JOIN team t1 ON t1.group_code = g.group_code AND t1.tournament_id = 1
    AND t1.id = (SELECT id FROM team WHERE group_code = g.group_code AND tournament_id = 1 ORDER BY id LIMIT 1 OFFSET (p.a - 1))
JOIN team t2 ON t2.group_code = g.group_code AND t2.tournament_id = 1
    AND t2.id = (SELECT id FROM team WHERE group_code = g.group_code AND tournament_id = 1 ORDER BY id LIMIT 1 OFFSET (p.b - 1));

-- ── Knockout matches (32) — placeholders, team_1_id / team_2_id NULL until
--    admin populates after groups resolve. match_parent_*_id wired so the
--    bracket tree is queryable.
-- Round-of-32: 16 matches (no parents — teams come from groups).
-- Round-of-16: 8 matches (parents = R32).
-- QF: 4 matches (parents = R16).
-- SF: 2 matches.
-- Third place: 1 match (parents = SF losers).
-- Final: 1 match (parents = SF winners).
-- Kickoffs stagger from 2026-06-28 onward.

-- R32 (16 matches)
INSERT INTO match (tournament_id, round_id, kickoff_at)
SELECT 1, 2, TIMESTAMPTZ '2026-06-28 17:00 UTC' + (n - 1) * INTERVAL '4 hours'
FROM generate_series(1, 16) AS n;

-- R16 (8 matches) — parents are the first 16 R32 matches paired.
WITH r32 AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match WHERE round_id = 2 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT 1, 3,
    TIMESTAMPTZ '2026-07-03 17:00 UTC' + (p1.rn / 2 - 1) * INTERVAL '4 hours',
    p1.id, p2.id
FROM r32 p1
JOIN r32 p2 ON p2.rn = p1.rn + 1
WHERE p1.rn % 2 = 1;

-- QF (4 matches)
WITH r16 AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match WHERE round_id = 3 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT 1, 4,
    TIMESTAMPTZ '2026-07-09 17:00 UTC' + (p1.rn / 2 - 1) * INTERVAL '4 hours',
    p1.id, p2.id
FROM r16 p1
JOIN r16 p2 ON p2.rn = p1.rn + 1
WHERE p1.rn % 2 = 1;

-- SF (2 matches)
WITH qf AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match WHERE round_id = 4 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT 1, 5,
    TIMESTAMPTZ '2026-07-14 17:00 UTC' + (p1.rn / 2 - 1) * INTERVAL '24 hours',
    p1.id, p2.id
FROM qf p1
JOIN qf p2 ON p2.rn = p1.rn + 1
WHERE p1.rn % 2 = 1;

-- Third-place + Final (each has both SF as parents)
WITH sf AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match WHERE round_id = 5 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT 1, 6,
    TIMESTAMPTZ '2026-07-18 17:00 UTC',
    p1.id, p2.id
FROM sf p1 JOIN sf p2 ON p1.rn = 1 AND p2.rn = 2;

WITH sf AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM match WHERE round_id = 5 AND tournament_id = 1
)
INSERT INTO match (tournament_id, round_id, kickoff_at, match_parent_1_id, match_parent_2_id)
SELECT 1, 7,
    TIMESTAMPTZ '2026-07-19 17:00 UTC',
    p1.id, p2.id
FROM sf p1 JOIN sf p2 ON p1.rn = 1 AND p2.rn = 2;

-- Set tournament deadlines so the lock enforcement (Task 7) has values to compare against.
UPDATE tournament
SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC',
    knockout_deadline    = TIMESTAMPTZ '2026-06-28 17:00 UTC'
WHERE id = 1;
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && ./mvnw verify
```

Expected: PASS — 7 rounds, 48 teams, 12 groups × 4, 72 group matches, 32 knockout matches, 104 total. All 28 prior tests still green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V006__seed_fifa_wc_2026.sql \
        backend/src/test/java/io/quiniela/api/support/V006SeedTest.java
git commit -m "feat(backend): V006 seed — FIFA WC 2026 (48 teams, 7 rounds, 104 matches)"
```

---

## Task 4: Team + Round + Match entities + repositories

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/team/Team.java`, `TeamRepository.java`
- Create: `backend/src/main/java/io/quiniela/api/match/Round.java`, `RoundRepository.java`
- Create: `backend/src/main/java/io/quiniela/api/match/Match.java`, `MatchRepository.java`
- Test: `backend/src/test/java/io/quiniela/api/match/MatchRepositoryIT.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/match/MatchRepositoryIT.java`:

```java
package io.quiniela.api.match;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MatchRepositoryIT extends AbstractIntegrationTest {

  @Autowired MatchRepository matches;
  @Autowired RoundRepository rounds;
  @Autowired TeamRepository teams;

  @Test
  void groupAHasSixMatches() {
    var groupAMatches =
        matches.findByTournamentIdAndGroupCodeOrderByKickoffAtAsc(1L, "A");
    assertThat(groupAMatches).hasSize(6);
    assertThat(groupAMatches).allMatch(m -> "A".equals(m.getGroupCode()));
  }

  @Test
  void groupRoundExists() {
    var round = rounds.findByTournamentIdAndCode(1L, "GROUP").orElseThrow();
    assertThat(round.getName()).isEqualTo("Fase de grupos");
    assertThat(round.getSequence()).isEqualTo(1);
  }

  @Test
  void teamHasFlagEmoji() {
    var spain = teams.findByTournamentIdAndCode(1L, "ESP").orElseThrow();
    assertThat(spain.getName()).isEqualTo("España");
    assertThat(spain.getFlagEmoji()).isEqualTo("🇪🇸");
    assertThat(spain.getGroupCode()).isEqualTo("F");
  }
}
```

- [ ] **Step 2: Run to see failure**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL — entities don't exist.

- [ ] **Step 3: Create the Team entity + repository**

Create `backend/src/main/java/io/quiniela/api/team/Team.java`:

```java
package io.quiniela.api.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "team")
public class Team {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(nullable = false, length = 8)
  private String code;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(name = "group_code", length = 1)
  private String groupCode;

  @Column(name = "flag_emoji", length = 8)
  private String flagEmoji;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Team() {}

  public Long getId() { return id; }
  public Long getTournamentId() { return tournamentId; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getGroupCode() { return groupCode; }
  public String getFlagEmoji() { return flagEmoji; }
}
```

Create `backend/src/main/java/io/quiniela/api/team/TeamRepository.java`:

```java
package io.quiniela.api.team;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

  Optional<Team> findByTournamentIdAndCode(Long tournamentId, String code);
}
```

- [ ] **Step 4: Create the Round entity + repository**

Create `backend/src/main/java/io/quiniela/api/match/Round.java`:

```java
package io.quiniela.api.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "round")
public class Round {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(nullable = false, length = 16)
  private String code;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false)
  private Integer sequence;

  protected Round() {}

  public Long getId() { return id; }
  public Long getTournamentId() { return tournamentId; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public Integer getSequence() { return sequence; }
}
```

Create `backend/src/main/java/io/quiniela/api/match/RoundRepository.java`:

```java
package io.quiniela.api.match;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRepository extends JpaRepository<Round, Long> {

  Optional<Round> findByTournamentIdAndCode(Long tournamentId, String code);
}
```

- [ ] **Step 5: Create the Match entity + repository**

Create `backend/src/main/java/io/quiniela/api/match/Match.java`:

```java
package io.quiniela.api.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "match")
public class Match {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(name = "round_id", nullable = false)
  private Long roundId;

  @Column(name = "group_code", length = 1)
  private String groupCode;

  @Column(name = "team_1_id")
  private Long team1Id;

  @Column(name = "team_2_id")
  private Long team2Id;

  @Column(name = "score_t1")
  private Integer scoreT1;

  @Column(name = "score_t2")
  private Integer scoreT2;

  @Column(name = "winner_id")
  private Long winnerId;

  @Column(nullable = false)
  private Boolean played;

  @Column(name = "kickoff_at", nullable = false)
  private Instant kickoffAt;

  @Column(name = "match_parent_1_id")
  private Long matchParent1Id;

  @Column(name = "match_parent_2_id")
  private Long matchParent2Id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Match() {}

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.played == null) this.played = false;
  }

  @PreUpdate
  void onUpdate() { this.updatedAt = Instant.now(); }

  public Long getId() { return id; }
  public Long getTournamentId() { return tournamentId; }
  public Long getRoundId() { return roundId; }
  public String getGroupCode() { return groupCode; }
  public Long getTeam1Id() { return team1Id; }
  public Long getTeam2Id() { return team2Id; }
  public Integer getScoreT1() { return scoreT1; }
  public void setScoreT1(Integer s) { this.scoreT1 = s; }
  public Integer getScoreT2() { return scoreT2; }
  public void setScoreT2(Integer s) { this.scoreT2 = s; }
  public Long getWinnerId() { return winnerId; }
  public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
  public Boolean getPlayed() { return played; }
  public void setPlayed(Boolean played) { this.played = played; }
  public Instant getKickoffAt() { return kickoffAt; }
  public Long getMatchParent1Id() { return matchParent1Id; }
  public Long getMatchParent2Id() { return matchParent2Id; }
}
```

Create `backend/src/main/java/io/quiniela/api/match/MatchRepository.java`:

```java
package io.quiniela.api.match;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

  List<Match> findByTournamentIdAndGroupCodeOrderByKickoffAtAsc(Long tournamentId, String groupCode);

  List<Match> findByTournamentIdAndRoundIdOrderByKickoffAtAsc(Long tournamentId, Long roundId);
}
```

- [ ] **Step 6: Run tests**

```bash
cd backend && ./mvnw verify
```

Expected: PASS — repository test green; all 28 prior tests still green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/team/ \
        backend/src/main/java/io/quiniela/api/match/ \
        backend/src/test/java/io/quiniela/api/match/MatchRepositoryIT.java
git commit -m "feat(backend): Team, Round, Match entities + repositories"
```

---

## Task 5: Quiniela + Bet entities + repositories

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/quiniela/Quiniela.java`, `QuinielaRepository.java`
- Create: `backend/src/main/java/io/quiniela/api/bet/Bet.java`, `BetId.java`, `BetRepository.java`
- Test: `backend/src/test/java/io/quiniela/api/quiniela/QuinielaRepositoryIT.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/quiniela/QuinielaRepositoryIT.java`:

```java
package io.quiniela.api.quiniela;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuinielaRepositoryIT extends AbstractIntegrationTest {

  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;

  @Test
  void canPersistQuinielaAndBet() {
    var u = new User("g-q1", "qtest@example.com", "QTest", null, UserRole.CAPTAIN);
    u.setInvitePath("qtest-abc");
    u = users.save(u);

    var q = new Quiniela(1L, u.getId());
    q = quinielas.save(q);
    assertThat(q.getId()).isNotNull();
    assertThat(q.getPoints()).isEqualTo(0);

    // match 1 is the first seeded group-stage match.
    var b = new Bet(q.getId(), 1L, 2, 1);
    bets.save(b);

    var fetched = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    assertThat(fetched.getId()).isEqualTo(q.getId());

    var matchBet = bets.findByQuinielaIdAndMatchId(q.getId(), 1L).orElseThrow();
    assertThat(matchBet.getScoreT1()).isEqualTo(2);
    assertThat(matchBet.getScoreT2()).isEqualTo(1);
  }
}
```

- [ ] **Step 2: Run to see failure**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL — entities don't exist.

- [ ] **Step 3: Create Quiniela entity + repository**

Create `backend/src/main/java/io/quiniela/api/quiniela/Quiniela.java`:

```java
package io.quiniela.api.quiniela;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "quiniela")
public class Quiniela {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pool_id", nullable = false)
  private Long poolId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Integer points;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Quiniela() {}

  public Quiniela(Long poolId, Long userId) {
    this.poolId = poolId;
    this.userId = userId;
    this.points = 0;
  }

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.points == null) this.points = 0;
  }

  @PreUpdate
  void onUpdate() { this.updatedAt = Instant.now(); }

  public Long getId() { return id; }
  public Long getPoolId() { return poolId; }
  public Long getUserId() { return userId; }
  public Integer getPoints() { return points; }
}
```

Create `backend/src/main/java/io/quiniela/api/quiniela/QuinielaRepository.java`:

```java
package io.quiniela.api.quiniela;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuinielaRepository extends JpaRepository<Quiniela, Long> {

  Optional<Quiniela> findByPoolIdAndUserId(Long poolId, Long userId);
}
```

- [ ] **Step 4: Create Bet entity + composite ID + repository**

Create `backend/src/main/java/io/quiniela/api/bet/BetId.java`:

```java
package io.quiniela.api.bet;

import java.io.Serializable;
import java.util.Objects;

public class BetId implements Serializable {
  private Long quinielaId;
  private Long matchId;

  public BetId() {}
  public BetId(Long quinielaId, Long matchId) {
    this.quinielaId = quinielaId;
    this.matchId = matchId;
  }

  public Long getQuinielaId() { return quinielaId; }
  public Long getMatchId() { return matchId; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BetId other)) return false;
    return Objects.equals(quinielaId, other.quinielaId) && Objects.equals(matchId, other.matchId);
  }

  @Override
  public int hashCode() { return Objects.hash(quinielaId, matchId); }
}
```

Create `backend/src/main/java/io/quiniela/api/bet/Bet.java`:

```java
package io.quiniela.api.bet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bet")
@IdClass(BetId.class)
public class Bet {

  @Id
  @Column(name = "quiniela_id")
  private Long quinielaId;

  @Id
  @Column(name = "match_id")
  private Long matchId;

  @Column(name = "score_t1", nullable = false)
  private Integer scoreT1;

  @Column(name = "score_t2", nullable = false)
  private Integer scoreT2;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Bet() {}

  public Bet(Long quinielaId, Long matchId, Integer scoreT1, Integer scoreT2) {
    this.quinielaId = quinielaId;
    this.matchId = matchId;
    this.scoreT1 = scoreT1;
    this.scoreT2 = scoreT2;
  }

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() { this.updatedAt = Instant.now(); }

  public Long getQuinielaId() { return quinielaId; }
  public Long getMatchId() { return matchId; }
  public Integer getScoreT1() { return scoreT1; }
  public void setScoreT1(Integer scoreT1) { this.scoreT1 = scoreT1; }
  public Integer getScoreT2() { return scoreT2; }
  public void setScoreT2(Integer scoreT2) { this.scoreT2 = scoreT2; }
}
```

Create `backend/src/main/java/io/quiniela/api/bet/BetRepository.java`:

```java
package io.quiniela.api.bet;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetRepository extends JpaRepository<Bet, BetId> {

  Optional<Bet> findByQuinielaIdAndMatchId(Long quinielaId, Long matchId);

  List<Bet> findByQuinielaId(Long quinielaId);
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && ./mvnw verify
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/quiniela/ \
        backend/src/main/java/io/quiniela/api/bet/ \
        backend/src/test/java/io/quiniela/api/quiniela/QuinielaRepositoryIT.java
git commit -m "feat(backend): Quiniela + Bet entities + repositories"
```

---

## Task 6: GET /api/bracket/me — read the player's bracket

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/bracket/BracketController.java`
- Create: `backend/src/main/java/io/quiniela/api/bracket/BracketService.java`
- Test: `backend/src/test/java/io/quiniela/api/bracket/BracketControllerIT.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/bracket/BracketControllerIT.java`:

```java
package io.quiniela.api.bracket;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BracketControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void getMeBracketReturnsAllGroupsAndKnockouts() throws Exception {
    var u = new User("g-br1", "br1@example.com", "BR1", null, UserRole.CAPTAIN);
    u.setInvitePath("br1-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(get("/api/bracket/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMatches").value(104))
        .andExpect(jsonPath("$.totalBets").value(0))
        .andExpect(jsonPath("$.groups.length()").value(12))
        .andExpect(jsonPath("$.groups[0].code").value("A"))
        .andExpect(jsonPath("$.groups[0].matches.length()").value(6));
  }

  @Test
  void getMeBracketRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/bracket/me")).andExpect(status().isUnauthorized());
  }
}
```

- [ ] **Step 2: Run to see failure**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL.

- [ ] **Step 3: Implement BracketService**

Create `backend/src/main/java/io/quiniela/api/bracket/BracketService.java`:

```java
package io.quiniela.api.bracket;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.team.Team;
import io.quiniela.api.team.TeamRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BracketService {

  private static final Long DEFAULT_POOL_ID = 1L;
  private static final Long DEFAULT_TOURNAMENT_ID = 1L;

  private final QuinielaRepository quinielas;
  private final BetRepository bets;
  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final TeamRepository teams;

  public BracketService(
      QuinielaRepository quinielas,
      BetRepository bets,
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams) {
    this.quinielas = quinielas;
    this.bets = bets;
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
  }

  public record MatchView(
      Long id,
      String team1Code, String team1Name, String team1Flag,
      String team2Code, String team2Name, String team2Flag,
      String kickoffAt,
      Integer betScoreT1, Integer betScoreT2,
      Integer actualScoreT1, Integer actualScoreT2,
      boolean played) {}

  public record GroupView(String code, int filled, int total, List<MatchView> matches) {}

  public record KnockoutRoundView(
      String code, String name, int filled, int total, boolean unlocked, List<MatchView> matches) {}

  public record BracketView(
      Long quinielaId,
      int totalMatches,
      int totalBets,
      List<GroupView> groups,
      List<KnockoutRoundView> knockouts) {}

  @Transactional
  public BracketView getMyBracket(Long userId) {
    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, userId)
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, userId)));

    List<Bet> myBets = bets.findByQuinielaId(q.getId());
    Map<Long, Bet> betByMatch =
        myBets.stream().collect(Collectors.toMap(Bet::getMatchId, b -> b));

    Map<Long, Team> teamById = new HashMap<>();
    teams.findAll().forEach(t -> teamById.put(t.getId(), t));

    List<GroupView> groups = new java.util.ArrayList<>();
    for (String code : List.of("A","B","C","D","E","F","G","H","I","J","K","L")) {
      List<Match> ms =
          matches.findByTournamentIdAndGroupCodeOrderByKickoffAtAsc(DEFAULT_TOURNAMENT_ID, code);
      List<MatchView> mvs = ms.stream().map(m -> toView(m, teamById, betByMatch)).toList();
      int filled = (int) mvs.stream().filter(v -> v.betScoreT1() != null).count();
      groups.add(new GroupView(code, filled, ms.size(), mvs));
    }

    List<KnockoutRoundView> ko = new java.util.ArrayList<>();
    boolean unlocked = false; // toggled true by the lock logic once group stage closes.
    for (Round r : rounds.findAll()) {
      if ("GROUP".equals(r.getCode())) continue;
      List<Match> ms =
          matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(DEFAULT_TOURNAMENT_ID, r.getId());
      List<MatchView> mvs = ms.stream().map(m -> toView(m, teamById, betByMatch)).toList();
      int filled = (int) mvs.stream().filter(v -> v.betScoreT1() != null).count();
      ko.add(new KnockoutRoundView(r.getCode(), r.getName(), filled, ms.size(), unlocked, mvs));
    }

    int totalMatches = (int) matches.count();
    return new BracketView(q.getId(), totalMatches, myBets.size(), groups, ko);
  }

  private MatchView toView(Match m, Map<Long, Team> teamById, Map<Long, Bet> betByMatch) {
    Team t1 = m.getTeam1Id() == null ? null : teamById.get(m.getTeam1Id());
    Team t2 = m.getTeam2Id() == null ? null : teamById.get(m.getTeam2Id());
    Bet b = betByMatch.get(m.getId());
    return new MatchView(
        m.getId(),
        t1 == null ? null : t1.getCode(),
        t1 == null ? null : t1.getName(),
        t1 == null ? null : t1.getFlagEmoji(),
        t2 == null ? null : t2.getCode(),
        t2 == null ? null : t2.getName(),
        t2 == null ? null : t2.getFlagEmoji(),
        m.getKickoffAt().toString(),
        b == null ? null : b.getScoreT1(),
        b == null ? null : b.getScoreT2(),
        m.getScoreT1(),
        m.getScoreT2(),
        Boolean.TRUE.equals(m.getPlayed()));
  }
}
```

- [ ] **Step 4: Implement BracketController**

Create `backend/src/main/java/io/quiniela/api/bracket/BracketController.java`:

```java
package io.quiniela.api.bracket;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bracket")
public class BracketController {

  private final BracketService service;

  public BracketController(BracketService service) { this.service = service; }

  @GetMapping("/me")
  public ResponseEntity<BracketService.BracketView> getMyBracket(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getMyBracket(userId));
  }
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && ./mvnw verify
```

Expected: PASS — both controller scenarios green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/bracket/ \
        backend/src/test/java/io/quiniela/api/bracket/BracketControllerIT.java
git commit -m "feat(backend): GET /api/bracket/me — read the player's bracket"
```

---

## Task 7: POST /api/bracket/bet — save a bet with lock enforcement

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/bracket/BracketService.java`
- Modify: `backend/src/main/java/io/quiniela/api/bracket/BracketController.java`
- Test: extend `backend/src/test/java/io/quiniela/api/bracket/BracketControllerIT.java`

- [ ] **Step 1: Add failing tests to the existing controller IT**

Append to `BracketControllerIT.java`:

```java
  @Test
  void saveBetUpsertsAndReturns200() throws Exception {
    var u = new User("g-br2", "br2@example.com", "BR2", null, UserRole.CAPTAIN);
    u.setInvitePath("br2-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    // Group-stage match id=1 (first seeded).
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/bracket/bet")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"matchId\":1,\"scoreT1\":2,\"scoreT2\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(1))
        .andExpect(jsonPath("$.scoreT1").value(2))
        .andExpect(jsonPath("$.scoreT2").value(1));

    // Second POST overwrites.
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/bracket/bet")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"matchId\":1,\"scoreT1\":3,\"scoreT2\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scoreT1").value(3));
  }

  @Test
  void saveBetRejectsAfterGroupStageDeadline() throws Exception {
    // Move the deadline into the past for this test by direct SQL.
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");

    var u = new User("g-br3", "br3@example.com", "BR3", null, UserRole.CAPTAIN);
    u.setInvitePath("br3-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/bracket/bet")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"matchId\":1,\"scoreT1\":2,\"scoreT2\":1}"))
        .andExpect(status().isLocked()); // 423 Locked

    // Restore for other tests.
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC' WHERE id = 1");
  }
```

Add `@Autowired javax.sql.DataSource dataSource;` to the class.

- [ ] **Step 2: Run to see failure**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL — `/api/bracket/bet` doesn't exist.

- [ ] **Step 3: Add the save method to BracketService**

Append to `BracketService.java` (inside the class):

```java
  public static class BracketLockedException extends RuntimeException {
    public BracketLockedException(String msg) { super(msg); }
  }

  public record SaveBetRequest(Long matchId, Integer scoreT1, Integer scoreT2) {}

  public record BetView(Long matchId, Integer scoreT1, Integer scoreT2) {}

  @Transactional
  public BetView saveBet(Long userId, SaveBetRequest req) {
    if (req.matchId() == null || req.scoreT1() == null || req.scoreT2() == null) {
      throw new IllegalArgumentException("matchId, scoreT1, scoreT2 required");
    }
    if (req.scoreT1() < 0 || req.scoreT2() < 0 || req.scoreT1() > 30 || req.scoreT2() > 30) {
      throw new IllegalArgumentException("scores out of range");
    }

    io.quiniela.api.match.Match match =
        matches.findById(req.matchId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown match"));

    // Lock check: group-stage matches lock at tournament.group_stage_deadline.
    // Knockout matches use tournament.knockout_deadline (per-round refinement is
    // a v1.1 improvement — kept coarse here).
    io.quiniela.api.match.Round round =
        rounds.findById(match.getRoundId()).orElseThrow();
    var t = lockClock.fetchTournamentDeadlines(match.getTournamentId());
    java.time.Instant now = java.time.Instant.now();
    boolean isGroup = "GROUP".equals(round.getCode());
    java.time.Instant deadline = isGroup ? t.groupStageDeadline() : t.knockoutDeadline();
    if (deadline != null && now.isAfter(deadline)) {
      throw new BracketLockedException("Bets locked for this round");
    }

    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, userId)
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, userId)));

    Bet bet =
        bets
            .findByQuinielaIdAndMatchId(q.getId(), req.matchId())
            .orElseGet(() -> new Bet(q.getId(), req.matchId(), req.scoreT1(), req.scoreT2()));
    bet.setScoreT1(req.scoreT1());
    bet.setScoreT2(req.scoreT2());
    bets.save(bet);
    return new BetView(req.matchId(), req.scoreT1(), req.scoreT2());
  }
```

Add a `LockClock` companion to look up deadlines from the tournament row. Create `backend/src/main/java/io/quiniela/api/bracket/LockClock.java`:

```java
package io.quiniela.api.bracket;

import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LockClock {

  private final JdbcTemplate jdbc;

  public LockClock(DataSource ds) { this.jdbc = new JdbcTemplate(ds); }

  public record TournamentDeadlines(Instant groupStageDeadline, Instant knockoutDeadline) {}

  public TournamentDeadlines fetchTournamentDeadlines(Long tournamentId) {
    return jdbc.queryForObject(
        "SELECT group_stage_deadline, knockout_deadline FROM tournament WHERE id = ?",
        (rs, n) -> {
          var gs = rs.getTimestamp("group_stage_deadline");
          var ko = rs.getTimestamp("knockout_deadline");
          return new TournamentDeadlines(
              gs == null ? null : gs.toInstant(), ko == null ? null : ko.toInstant());
        },
        tournamentId);
  }
}
```

Inject `LockClock` into `BracketService`'s constructor (add as a final field + constructor parameter named `lockClock`).

- [ ] **Step 4: Add the POST endpoint to BracketController**

Replace the entire `BracketController.java` body to add the POST handler + exception mapping:

```java
package io.quiniela.api.bracket;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bracket")
public class BracketController {

  private final BracketService service;

  public BracketController(BracketService service) { this.service = service; }

  @GetMapping("/me")
  public ResponseEntity<BracketService.BracketView> getMyBracket(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getMyBracket(userId));
  }

  @PostMapping("/bet")
  public ResponseEntity<BracketService.BetView> saveBet(
      @AuthenticationPrincipal Jwt jwt, @RequestBody BracketService.SaveBetRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.saveBet(userId, req));
  }

  @ExceptionHandler(BracketService.BracketLockedException.class)
  public ResponseEntity<String> handleLocked(BracketService.BracketLockedException e) {
    return ResponseEntity.status(HttpStatus.LOCKED).body(e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && ./mvnw verify
```

Expected: PASS — save + lock tests both green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/bracket/ \
        backend/src/test/java/io/quiniela/api/bracket/BracketControllerIT.java
git commit -m "feat(backend): POST /api/bracket/bet — upsert with lock enforcement"
```

---

## Task 8: Scoring trigger integration test

**Files:**
- Create: `backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java`

The trigger is already in place from Task 2 (V005). This task verifies its behavior end-to-end against a real Postgres container.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java`:

```java
package io.quiniela.api.quiniela;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ScoringTriggerIT extends AbstractIntegrationTest {

  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired MatchRepository matches;
  @Autowired UserRepository users;
  @Autowired DataSource dataSource;

  /** Helper: bet on match 1 (group A first match) and update its result. */
  private Long setupBetOnMatch1(int betT1, int betT2) {
    var u = new User("g-sc-" + System.nanoTime(), "sc@example.com", "Sc", null, UserRole.PLAYER);
    u = users.save(u);
    var q = quinielas.save(new Quiniela(1L, u.getId()));
    bets.save(new Bet(q.getId(), 1L, betT1, betT2));
    return q.getId();
  }

  private void setMatchResult(Long matchId, int t1, int t2) {
    new JdbcTemplate(dataSource)
        .update("UPDATE match SET score_t1 = ?, score_t2 = ? WHERE id = ?", t1, t2, matchId);
  }

  private int pointsOf(Long qId) {
    return quinielas.findById(qId).orElseThrow().getPoints();
  }

  @Test
  void exactScoreAwardsFivePoints() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(5);
  }

  @Test
  void correctWinnerAndGoalDifferenceAwardsThree() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 3, 2);
    assertThat(pointsOf(q)).isEqualTo(3);
  }

  @Test
  void correctWinnerOnlyAwardsTwo() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 4, 0);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void correctDrawAwardsTwo() {
    var q = setupBetOnMatch1(1, 1);
    setMatchResult(1L, 0, 0);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void wrongWinnerAwardsZero() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 0, 3);
    assertThat(pointsOf(q)).isEqualTo(0);
  }

  @Test
  void resultCorrectionUpdatesPointsDelta() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 1); // exact — +5
    assertThat(pointsOf(q)).isEqualTo(5);
    setMatchResult(1L, 3, 2); // winner + diff — should become +3 (delta -2)
    assertThat(pointsOf(q)).isEqualTo(3);
    setMatchResult(1L, 0, 3); // miss — delta -3
    assertThat(pointsOf(q)).isEqualTo(0);
  }
}
```

- [ ] **Step 2: Run the tests**

```bash
cd backend && ./mvnw verify
```

Expected: PASS — all 6 scoring scenarios green. If any fail, the trigger SQL in V005 has a bug. Fix it before continuing.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java
git commit -m "test(backend): scoring trigger covers exact/winner+diff/winner/draw/miss/correction"
```

---

## Task 9: Ask Paul stub endpoints

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulService.java`
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulController.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulControllerIT.java`

Paul is a v1 stub. He returns deterministic-ish predictions seeded from `match_id`. v1.1 will swap in a real LLM call.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/paul/PaulControllerIT.java`:

```java
package io.quiniela.api.paul;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaulControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired JwtService jwt;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void suggestReturnsScoreAndReasoning() throws Exception {
    var u = new User("g-paul1", "p1@example.com", "P1", null, UserRole.PLAYER);
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(post("/api/paul/suggest?matchId=1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scoreT1").isNumber())
        .andExpect(jsonPath("$.scoreT2").isNumber())
        .andExpect(jsonPath("$.reasoning").isString());
  }

  @Test
  void fillAllPopulatesEveryUnsetGroupBet() throws Exception {
    var u = new User("g-paul2", "p2@example.com", "P2", null, UserRole.PLAYER);
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(post("/api/paul/fill").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(72)); // all 72 group matches

    Quiniela q = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    assertThat(bets.findByQuinielaId(q.getId())).hasSize(72);
  }
}
```

- [ ] **Step 2: Run to see failure**

```bash
cd backend && ./mvnw verify
```

Expected: FAIL.

- [ ] **Step 3: Implement PaulService**

Create `backend/src/main/java/io/quiniela/api/paul/PaulService.java`:

```java
package io.quiniela.api.paul;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaulService {

  private static final Long DEFAULT_POOL_ID = 1L;

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final QuinielaRepository quinielas;
  private final BetRepository bets;

  public PaulService(
      MatchRepository matches,
      RoundRepository rounds,
      QuinielaRepository quinielas,
      BetRepository bets) {
    this.matches = matches;
    this.rounds = rounds;
    this.quinielas = quinielas;
    this.bets = bets;
  }

  public record Suggestion(Integer scoreT1, Integer scoreT2, String reasoning) {}

  public record FillResult(int created) {}

  /**
   * Deterministic stub: scores derive from match id so the same match always
   * gets the same prediction (helps reproducible debugging). Distribution
   * skews toward low-scoring realistic results: 0-0 .. 3-2.
   */
  public Suggestion suggestForMatch(Long matchId) {
    Match m = matches.findById(matchId).orElseThrow();
    long seed = matchId * 17L + 11L;
    int t1 = (int) (seed % 4);
    int t2 = (int) ((seed / 5) % 3);
    return new Suggestion(t1, t2, "Paul cree que es un partido " + (t1 + t2 > 2 ? "abierto" : "cerrado") + ".");
  }

  @Transactional
  public FillResult fillAllForUser(Long userId) {
    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, userId)
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, userId)));

    List<Bet> existing = bets.findByQuinielaId(q.getId());
    Set<Long> alreadyBet = new HashSet<>();
    existing.forEach(b -> alreadyBet.add(b.getMatchId()));

    // For v1, only fill group-stage matches. Knockout matches don't have
    // resolved teams yet — Paul can't reason about "Group A winner vs Group B
    // runner-up" placeholders. v1.1 fills knockouts after group stage closes.
    Long groupRoundId = rounds.findByTournamentIdAndCode(1L, "GROUP").orElseThrow().getId();
    List<Match> ms = matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(1L, groupRoundId);

    int created = 0;
    for (Match m : ms) {
      if (alreadyBet.contains(m.getId())) continue;
      Suggestion s = suggestForMatch(m.getId());
      bets.save(new Bet(q.getId(), m.getId(), s.scoreT1(), s.scoreT2()));
      created++;
    }
    return new FillResult(created);
  }
}
```

- [ ] **Step 4: Implement PaulController**

Create `backend/src/main/java/io/quiniela/api/paul/PaulController.java`:

```java
package io.quiniela.api.paul;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paul")
public class PaulController {

  private final PaulService service;

  public PaulController(PaulService service) { this.service = service; }

  @PostMapping("/suggest")
  public ResponseEntity<PaulService.Suggestion> suggest(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("matchId") Long matchId) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.suggestForMatch(matchId));
  }

  @PostMapping("/fill")
  public ResponseEntity<PaulService.FillResult> fillAll(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.fillAllForUser(userId));
  }
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && ./mvnw verify
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/ \
        backend/src/test/java/io/quiniela/api/paul/PaulControllerIT.java
git commit -m "feat(backend): /api/paul/suggest + /api/paul/fill stub endpoints"
```

---

## Task 10: Frontend — bracket + paul API clients

**Files:**
- Create: `frontend/lib/api/bracket.ts`
- Create: `frontend/lib/api/paul.ts`
- Modify: `frontend/mocks/handlers.ts`

- [ ] **Step 1: Add the typed wrappers**

Create `frontend/lib/api/bracket.ts`:

```ts
import { api } from "./client";

export type MatchView = {
  id: number;
  team1Code: string | null;
  team1Name: string | null;
  team1Flag: string | null;
  team2Code: string | null;
  team2Name: string | null;
  team2Flag: string | null;
  kickoffAt: string;
  betScoreT1: number | null;
  betScoreT2: number | null;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  played: boolean;
};

export type GroupView = {
  code: string;
  filled: number;
  total: number;
  matches: MatchView[];
};

export type KnockoutRoundView = {
  code: string;
  name: string;
  filled: number;
  total: number;
  unlocked: boolean;
  matches: MatchView[];
};

export type BracketView = {
  quinielaId: number;
  totalMatches: number;
  totalBets: number;
  groups: GroupView[];
  knockouts: KnockoutRoundView[];
};

export type BetView = {
  matchId: number;
  scoreT1: number;
  scoreT2: number;
};

export async function getMyBracket(): Promise<BracketView> {
  return api<BracketView>("/api/bracket/me");
}

export async function saveBet(
  matchId: number,
  scoreT1: number,
  scoreT2: number,
): Promise<BetView> {
  return api<BetView>("/api/bracket/bet", {
    method: "POST",
    body: JSON.stringify({ matchId, scoreT1, scoreT2 }),
  });
}
```

Create `frontend/lib/api/paul.ts`:

```ts
import { api } from "./client";

export type PaulSuggestion = {
  scoreT1: number;
  scoreT2: number;
  reasoning: string;
};

export type PaulFillResult = { created: number };

export async function suggestForMatch(matchId: number): Promise<PaulSuggestion> {
  return api<PaulSuggestion>(`/api/paul/suggest?matchId=${matchId}`, { method: "POST" });
}

export async function fillAll(): Promise<PaulFillResult> {
  return api<PaulFillResult>("/api/paul/fill", { method: "POST" });
}
```

- [ ] **Step 2: Add MSW handlers for unit tests**

Append to `frontend/mocks/handlers.ts`:

```ts
  http.get(`${process.env.API_URL ?? "http://localhost:8080"}/api/bracket/me`, () =>
    HttpResponse.json({
      quinielaId: 1,
      totalMatches: 104,
      totalBets: 0,
      groups: ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"].map((code) => ({
        code,
        filled: 0,
        total: 6,
        matches: [],
      })),
      knockouts: [
        { code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, matches: [] },
        { code: "R16", name: "Octavos", filled: 0, total: 8, unlocked: false, matches: [] },
        { code: "QF", name: "Cuartos", filled: 0, total: 4, unlocked: false, matches: [] },
        { code: "SF", name: "Semifinales", filled: 0, total: 2, unlocked: false, matches: [] },
        { code: "THIRD_PLACE", name: "Tercer puesto", filled: 0, total: 1, unlocked: false, matches: [] },
        { code: "FINAL", name: "Final", filled: 0, total: 1, unlocked: false, matches: [] },
      ],
    }),
  ),
  http.post(`${process.env.API_URL ?? "http://localhost:8080"}/api/bracket/bet`, async ({ request }) => {
    const body = (await request.json()) as { matchId: number; scoreT1: number; scoreT2: number };
    return HttpResponse.json({ matchId: body.matchId, scoreT1: body.scoreT1, scoreT2: body.scoreT2 });
  }),
  http.post(`${process.env.API_URL ?? "http://localhost:8080"}/api/paul/suggest`, ({ request }) => {
    const url = new URL(request.url);
    const matchId = Number(url.searchParams.get("matchId"));
    return HttpResponse.json({
      scoreT1: (matchId * 17) % 4,
      scoreT2: (matchId * 3) % 3,
      reasoning: "Mock Paul",
    });
  }),
  http.post(`${process.env.API_URL ?? "http://localhost:8080"}/api/paul/fill`, () =>
    HttpResponse.json({ created: 72 }),
  ),
```

(Append inside the existing `handlers` array, keeping prior entries.)

- [ ] **Step 3: Run frontend checks**

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm test --run
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/api/bracket.ts frontend/lib/api/paul.ts frontend/mocks/handlers.ts
git commit -m "feat(frontend): typed bracket + paul API client wrappers"
```

---

## Task 11: NumpadScoreInput client component

**Files:**
- Create: `frontend/components/group/NumpadScoreInput.tsx`
- Test: `frontend/components/group/NumpadScoreInput.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/components/group/NumpadScoreInput.test.tsx`:

```tsx
import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import { NumpadScoreInput } from "./NumpadScoreInput";

const messages = {
  numpad: {
    title: "Marca el resultado",
    presets: "Marcadores frecuentes",
    confirm: "Confirmar",
    cancel: "Cancelar",
  },
};

describe("NumpadScoreInput", () => {
  it("renders all digits 0-9 and confirms a single-side score", () => {
    const onConfirm = vi.fn();
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <NumpadScoreInput side="t1" onConfirm={onConfirm} onCancel={() => {}} />
      </NextIntlClientProvider>,
    );
    for (let n = 0; n <= 9; n++) {
      expect(screen.getByRole("button", { name: String(n) })).toBeInTheDocument();
    }
    fireEvent.click(screen.getByRole("button", { name: "2" }));
    fireEvent.click(screen.getByRole("button", { name: /confirmar/i }));
    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("applies a preset score and confirms both sides", () => {
    const onConfirm = vi.fn();
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <NumpadScoreInput side="both" onConfirm={onConfirm} onCancel={() => {}} />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "2-1" }));
    expect(onConfirm).toHaveBeenCalledWith({ t1: 2, t2: 1 });
  });
});
```

- [ ] **Step 2: Run to see failure**

```bash
cd frontend && pnpm test NumpadScoreInput
```

Expected: FAIL.

- [ ] **Step 3: Implement the component**

Create `frontend/components/group/NumpadScoreInput.tsx`:

```tsx
"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";

const PRESETS = ["1-0", "2-1", "0-0", "1-1", "2-0", "0-1"] as const;

type Props =
  | { side: "t1" | "t2"; onConfirm: (n: number) => void; onCancel: () => void }
  | { side: "both"; onConfirm: (s: { t1: number; t2: number }) => void; onCancel: () => void };

export function NumpadScoreInput(props: Props) {
  const t = useTranslations("numpad");
  const locale = useLocale();
  const [val, setVal] = useState<number | null>(null);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") props.onCancel();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [props]);

  function pickPreset(preset: string) {
    const [a, b] = preset.split("-").map(Number);
    if (props.side === "both") props.onConfirm({ t1: a, t2: b });
    else if (props.side === "t1") props.onConfirm(a);
    else props.onConfirm(b);
  }

  function confirmDigit() {
    if (val == null) return;
    if (props.side === "both") props.onConfirm({ t1: val, t2: 0 });
    else props.onConfirm(val);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t("title")}
      lang={locale}
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/80 backdrop-blur-sm sm:items-center"
      onClick={props.onCancel}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-t-2xl border-t-2 border-[var(--color-border-accent)] bg-[#111c2e] p-5 shadow-2xl shadow-[var(--color-accent-cyan)]/20 sm:rounded-2xl sm:border-2 sm:border-t-2"
      >
        <h2 className="chrome-label text-[var(--color-accent-cyan)]">{t("title")}</h2>

        <div className="mt-4">
          <div className="chrome-label">{t("presets")}</div>
          <div className="mt-1 flex flex-wrap gap-2">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => pickPreset(p)}
                className="rounded-md border border-[var(--color-border-subtle)] px-3 py-1 font-mono-num text-sm text-[var(--color-text-primary)] hover:border-[var(--color-accent-cyan)]"
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-4">
          <div
            aria-live="polite"
            className="mb-2 text-center font-mono-num text-3xl font-bold text-[var(--color-accent-cyan)]"
          >
            {val ?? "_"}
          </div>
          <div className="grid grid-cols-3 gap-2">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setVal(n)}
                className="rounded-md bg-[var(--color-bg-primary)] py-3 font-mono-num text-lg text-[var(--color-text-primary)] hover:bg-[var(--color-bg-elevated)]"
              >
                {n}
              </button>
            ))}
            <button
              type="button"
              onClick={props.onCancel}
              className="rounded-md bg-[var(--color-bg-primary)] py-3 text-sm text-[var(--color-text-muted)] hover:bg-[var(--color-bg-elevated)]"
            >
              {t("cancel")}
            </button>
            <button
              type="button"
              onClick={() => setVal(0)}
              className="rounded-md bg-[var(--color-bg-primary)] py-3 font-mono-num text-lg text-[var(--color-text-primary)] hover:bg-[var(--color-bg-elevated)]"
            >
              0
            </button>
            <button
              type="button"
              onClick={confirmDigit}
              disabled={val == null}
              className="rounded-md bg-[var(--color-accent-cyan)] py-3 text-sm font-bold text-black disabled:opacity-50"
            >
              {t("confirm")}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Add numpad keys to message catalogs**

Append to `frontend/messages/es-CO.json` (inside the root object):

```json
  "numpad": {
    "title": "Marca el resultado",
    "presets": "Marcadores frecuentes",
    "confirm": "Confirmar",
    "cancel": "Cancelar"
  },
```

Append to `frontend/messages/en.json`:

```json
  "numpad": {
    "title": "Tap the score",
    "presets": "Common scores",
    "confirm": "Confirm",
    "cancel": "Cancel"
  },
```

- [ ] **Step 5: Run the test**

```bash
cd frontend && pnpm test NumpadScoreInput
```

Expected: PASS — both tests green.

- [ ] **Step 6: Commit**

```bash
git add frontend/components/group/ frontend/messages/
git commit -m "feat(frontend): NumpadScoreInput component with presets + numpad"
```

---

## Task 12: /group/[groupId] drill-in page + autosave

**Files:**
- Create: `frontend/app/group/[groupId]/page.tsx`
- Create: `frontend/app/group/[groupId]/actions.ts`
- Create: `frontend/components/group/MatchRow.tsx`
- Create: `frontend/components/group/GroupDrillIn.tsx` (client orchestrator)

- [ ] **Step 1: Create the server action**

Create `frontend/app/group/[groupId]/actions.ts`:

```ts
"use server";

import { revalidatePath } from "next/cache";
import { saveBet } from "@/lib/api/bracket";
import { suggestForMatch } from "@/lib/api/paul";

export async function saveBetAction(
  matchId: number,
  scoreT1: number,
  scoreT2: number,
  groupId: string,
) {
  await saveBet(matchId, scoreT1, scoreT2);
  revalidatePath(`/group/${groupId}`);
}

export async function acceptPaulSuggestionAction(matchId: number, groupId: string) {
  const s = await suggestForMatch(matchId);
  await saveBet(matchId, s.scoreT1, s.scoreT2);
  revalidatePath(`/group/${groupId}`);
}
```

- [ ] **Step 2: Create the MatchRow server component**

Create `frontend/components/group/MatchRow.tsx`:

```tsx
import type { MatchView } from "@/lib/api/bracket";

export function MatchRow({
  match,
  onTapScore,
  onAskPaul,
}: {
  match: MatchView;
  onTapScore: () => void;
  onAskPaul: () => void;
}) {
  const filled = match.betScoreT1 != null && match.betScoreT2 != null;
  return (
    <div className="flex items-center gap-3 rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] p-3">
      <div className="flex-1 text-right text-sm">
        <span className="mr-1">{match.team1Flag}</span>
        {match.team1Name}
      </div>
      <button
        type="button"
        onClick={onTapScore}
        className={`min-w-[70px] rounded-md border px-3 py-2 font-mono-num text-base ${
          filled
            ? "border-[var(--color-accent-cyan)] text-[var(--color-accent-cyan)]"
            : "border-[var(--color-border-subtle)] text-[var(--color-text-muted)]"
        }`}
      >
        {filled ? `${match.betScoreT1}-${match.betScoreT2}` : "_ - _"}
      </button>
      <div className="flex-1 text-left text-sm">
        {match.team2Name}
        <span className="ml-1">{match.team2Flag}</span>
      </div>
      <button
        type="button"
        onClick={onAskPaul}
        aria-label="Ask Paul"
        title="Ask Paul"
        className="flex h-9 w-9 items-center justify-center rounded-md bg-[var(--color-accent-purple)]/20 text-base hover:bg-[var(--color-accent-purple)]/40"
      >
        🐙
      </button>
    </div>
  );
}
```

- [ ] **Step 3: Create the client orchestrator**

Create `frontend/components/group/GroupDrillIn.tsx`:

```tsx
"use client";

import { useState, useTransition } from "react";
import type { MatchView } from "@/lib/api/bracket";
import { MatchRow } from "./MatchRow";
import { NumpadScoreInput } from "./NumpadScoreInput";

export function GroupDrillIn({
  matches,
  groupId,
  saveBetAction,
  acceptPaulAction,
}: {
  matches: MatchView[];
  groupId: string;
  saveBetAction: (matchId: number, t1: number, t2: number, gid: string) => Promise<void>;
  acceptPaulAction: (matchId: number, gid: string) => Promise<void>;
}) {
  const [editing, setEditing] = useState<{ matchId: number } | null>(null);
  const [, startTransition] = useTransition();

  return (
    <>
      <div className="space-y-2">
        {matches.map((m) => (
          <MatchRow
            key={m.id}
            match={m}
            onTapScore={() => setEditing({ matchId: m.id })}
            onAskPaul={() =>
              startTransition(() => {
                acceptPaulAction(m.id, groupId);
              })
            }
          />
        ))}
      </div>
      {editing && (
        <NumpadScoreInput
          side="both"
          onConfirm={(s) =>
            startTransition(() => {
              saveBetAction(editing.matchId, s.t1, s.t2, groupId);
              setEditing(null);
            })
          }
          onCancel={() => setEditing(null)}
        />
      )}
    </>
  );
}
```

- [ ] **Step 4: Create the group page**

Create `frontend/app/group/[groupId]/page.tsx`:

```tsx
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { GroupDrillIn } from "@/components/group/GroupDrillIn";
import { saveBetAction, acceptPaulSuggestionAction } from "./actions";

const VALID_GROUPS = ["A","B","C","D","E","F","G","H","I","J","K","L"];

export default async function GroupPage({
  params,
}: {
  params: Promise<{ groupId: string }>;
}) {
  const { groupId } = await params;
  const upperId = groupId.toUpperCase();
  if (!VALID_GROUPS.includes(upperId)) notFound();

  const session = await auth();
  if (!session?.userId) redirect("/");

  const bracket = await getMyBracket();
  const group = bracket.groups.find((g) => g.code === upperId);
  if (!group) notFound();

  const tNav = await getTranslations("nav");

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={`${tNav("myQuiniela")} · Grupo ${upperId}`} meta={`${group.filled}/${group.total}`} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 py-3">
          <Link
            href="/home"
            className="chrome-label inline-block text-[var(--color-accent-cyan)] hover:underline"
          >
            ← {tNav("myQuiniela")}
          </Link>
        </div>
        <div className="px-3">
          <GroupDrillIn
            matches={group.matches}
            groupId={upperId}
            saveBetAction={saveBetAction}
            acceptPaulAction={acceptPaulSuggestionAction}
          />
        </div>
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}
```

- [ ] **Step 5: Verify**

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm test --run
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/app/group/ frontend/components/group/MatchRow.tsx frontend/components/group/GroupDrillIn.tsx
git commit -m "feat(frontend): /group/[groupId] drill-in with autosave + Ask Paul"
```

---

## Task 13: Lobby — live progress + Paul fill-all

**Files:**
- Rename: `frontend/components/lobby/GroupCardSkeleton.tsx` → `GroupCard.tsx` (with live props)
- Create: `frontend/components/lobby/PaulFillAllButton.tsx`
- Create: `frontend/app/home/actions.ts`
- Modify: `frontend/app/home/page.tsx`

- [ ] **Step 1: Replace GroupCardSkeleton with a live GroupCard**

Delete `frontend/components/lobby/GroupCardSkeleton.tsx`. Create `frontend/components/lobby/GroupCard.tsx`:

```tsx
import Link from "next/link";

export function GroupCard({
  letter,
  filled,
  total,
}: {
  letter: string;
  filled: number;
  total: number;
}) {
  const pct = total === 0 ? 0 : Math.round((filled / total) * 100);
  const complete = filled === total && total > 0;
  return (
    <Link
      href={`/group/${letter}`}
      className="flex items-center justify-between rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-4 py-3 transition-colors hover:border-[var(--color-border-accent)]/40"
    >
      <div>
        <div className="text-sm font-bold text-[var(--color-text-primary)]">Grupo {letter}</div>
        <div className="mt-0.5 text-xs text-[var(--color-text-muted)]">
          {filled} / {total}
        </div>
      </div>
      <div className="h-1.5 w-16 overflow-hidden rounded-full bg-[var(--color-border-subtle)]">
        <div
          className={`h-full ${complete ? "bg-[var(--color-state-good)]" : "bg-[var(--color-accent-cyan)]"}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </Link>
  );
}
```

- [ ] **Step 2: Create the Paul fill-all button**

Create `frontend/app/home/actions.ts`:

```ts
"use server";

import { revalidatePath } from "next/cache";
import { fillAll } from "@/lib/api/paul";

export async function paulFillAllAction() {
  await fillAll();
  revalidatePath("/home");
}
```

Create `frontend/components/lobby/PaulFillAllButton.tsx`:

```tsx
"use client";

import { useTransition } from "react";
import { useTranslations } from "next-intl";
import { paulFillAllAction } from "@/app/home/actions";

export function PaulFillAllButton() {
  const t = useTranslations("lobby");
  const [pending, start] = useTransition();
  return (
    <button
      type="button"
      onClick={() => start(() => paulFillAllAction())}
      disabled={pending}
      className="w-full rounded-md border border-[var(--color-accent-purple)] bg-[var(--color-accent-purple)]/10 py-3 chrome-label text-[var(--color-accent-purple)] hover:bg-[var(--color-accent-purple)]/20 disabled:opacity-50"
    >
      {t("askPaulFillAll")}
    </button>
  );
}
```

- [ ] **Step 3: Update /home/page.tsx to use live data**

Replace `frontend/app/home/page.tsx`:

```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CountdownChip } from "@/components/lobby/CountdownChip";
import { PotChip } from "@/components/lobby/PotChip";
import { GroupCard } from "@/components/lobby/GroupCard";
import { KnockoutLockedCard } from "@/components/lobby/KnockoutLockedCard";
import { PaulFillAllButton } from "@/components/lobby/PaulFillAllButton";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";

export default async function HomePage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket] = await Promise.all([getMe(), getMyBracket()]);
  const t = await getTranslations("lobby");

  const knockoutsUnlocked = bracket.knockouts.some((k) => k.unlocked);

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={t("title")} meta={`${me.displayName} · ${bracket.totalBets}/${bracket.totalMatches}`} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="flex flex-wrap gap-2 px-3 py-3">
          <CountdownChip />
          <PotChip potCents={0} paidCount={0} />
        </div>

        <section className="px-3">
          <span className="chrome-label">{t("groupsHeading")}</span>
          <div className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2 sm:gap-2 lg:grid-cols-3">
            {bracket.groups.map((g) => (
              <GroupCard key={g.code} letter={g.code} filled={g.filled} total={g.total} />
            ))}
          </div>
        </section>

        <section className="px-3 py-3">
          <span className="chrome-label">{t("knockoutsHeading")}</span>
          <div className="mt-2">
            {knockoutsUnlocked ? (
              <div className="text-sm text-[var(--color-text-muted)]">
                Disponible — entra a /knockout/R32, /knockout/R16, etc.
              </div>
            ) : (
              <KnockoutLockedCard />
            )}
          </div>
        </section>

        <section className="px-3 py-3 space-y-2">
          <PaulFillAllButton />
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}
```

- [ ] **Step 4: Verify**

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm test --run
```

Expected: PASS. Any tests that previously imported `GroupCardSkeleton` need updating; the lobby tests in Plan 1 didn't import it directly, so this should be clean.

- [ ] **Step 5: Commit**

```bash
git rm frontend/components/lobby/GroupCardSkeleton.tsx
git add frontend/components/lobby/GroupCard.tsx \
        frontend/components/lobby/PaulFillAllButton.tsx \
        frontend/app/home/actions.ts \
        frontend/app/home/page.tsx
git commit -m "feat(frontend): lobby uses live bracket data + Paul fill-all wired up"
```

---

## Task 14: /knockout/[roundId] shell + lock UX

**Files:**
- Create: `frontend/app/knockout/[roundId]/page.tsx`
- Modify: `frontend/messages/es-CO.json`, `en.json` (add `lobby.knockoutsUnlockedHint` if needed)

- [ ] **Step 1: Create the knockout page shell**

Create `frontend/app/knockout/[roundId]/page.tsx`:

```tsx
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";

const VALID = ["R32", "R16", "QF", "SF", "THIRD_PLACE", "FINAL"];

export default async function KnockoutPage({
  params,
}: {
  params: Promise<{ roundId: string }>;
}) {
  const { roundId } = await params;
  const upper = roundId.toUpperCase();
  if (!VALID.includes(upper)) notFound();

  const session = await auth();
  if (!session?.userId) redirect("/");

  const bracket = await getMyBracket();
  const round = bracket.knockouts.find((k) => k.code === upper);
  if (!round) notFound();

  const tLobby = await getTranslations("lobby");
  const tNav = await getTranslations("nav");

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={round.name} meta={`${round.filled}/${round.total}`} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 py-3">
          <Link
            href="/home"
            className="chrome-label inline-block text-[var(--color-accent-cyan)] hover:underline"
          >
            ← {tNav("myQuiniela")}
          </Link>
        </div>
        {!round.unlocked ? (
          <div className="mx-3 mt-6 rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] p-6 text-center">
            <div className="text-2xl">🔒</div>
            <h1 className="mt-2 text-lg font-bold">{round.name}</h1>
            <p className="mt-1 text-sm text-[var(--color-text-muted)]">
              {tLobby("knockoutsLocked")}
            </p>
          </div>
        ) : (
          <div className="px-3 text-sm text-[var(--color-text-muted)]">
            {/* Active round UI lives here — same MatchRow pattern as the group drill-in.
                Implemented incrementally as each round resolves in production. */}
            {round.matches.length} matches available.
          </div>
        )}
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}
```

- [ ] **Step 2: Verify**

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm test --run
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add frontend/app/knockout/
git commit -m "feat(frontend): /knockout/[roundId] shell with lock UX"
```

---

## Self-Review

**1. Spec coverage:**

Plan 2 implements these spec items (the bracket-fill section):
- **Item 4 (fill bracket — group stage)** → Tasks 1, 2, 3, 6, 7, 11, 12
- **Item 5 (knockout drill-in, 6 sub-cards)** → Task 14 (shell + lock); active round UI deferred to v1 patch once group stage resolves
- **Item 6 (score input pattern A — tap-to-numpad + presets)** → Task 11
- **Item 7 (Ask Paul: per-match icon + fill-all)** → Tasks 9, 12 (per-match), 13 (fill-all)
- **Item 8 (bracket lock at kickoff)** → Task 7 (server enforcement)
- Lobby progress → Task 13

Out of Plan 2 scope (Plan 3 territory): admin results screen, payments, XLSX import/export, public pot display values (replacing the hardcoded $0), spectator screens (ranking, schedule, compare).

**2. Placeholder scan:** No "TBD" / "TODO" / "implement later" in instructions. Two intentional placeholders documented:

- V006 seed contains illustrative team names (rows 41-48 are explicit placeholders). Plan documents this and says "edit before deploying."
- Task 14's knockout active-round UI is a stub (the file ships, the round renders count, full UI is deferred). The spec specified knockouts unlock after group stage closes (June 27), so this is acceptable for the June 11 ship deadline — the shell is in place and the active UI can land as a follow-up.

**3. Type consistency:**

- `MatchView` field names match between backend `BracketService.MatchView` (Java record) and frontend `lib/api/bracket.ts` (TS type): `id, team1Code, team1Name, team1Flag, team2Code, team2Name, team2Flag, kickoffAt, betScoreT1, betScoreT2, actualScoreT1, actualScoreT2, played`.
- `BetView`, `GroupView`, `KnockoutRoundView`, `BracketView` all aligned.
- `PaulSuggestion` aligned: `scoreT1, scoreT2, reasoning`.
- `NumpadScoreInput`'s `side: "both"` returns `{ t1, t2 }` (object); `side: "t1" | "t2"` returns a plain `number`. Consistent with `GroupDrillIn`'s `side="both"` usage.
- `saveBetAction(matchId, t1, t2, groupId)` signature matches in `actions.ts` definition and `GroupDrillIn` prop type.

No drift found.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-25-quiniela-plan-2-bracket-data-fill.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

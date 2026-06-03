# Knockout Penalty Results — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ingest real knockout results from football-data.org so a penalty-shootout match (returned as an on-pitch draw plus a `winner` field) records the team that actually advanced — making scoring award the advancement bonus AND the matches page show the advancing team on a draw.

**Architecture:** football-data.org returns a penalty-decided knockout as `score.fullTime = {1,1}` (the regulation/ET draw) with `score.winner = "HOME_TEAM" | "AWAY_TEAM"` naming who progressed. Today the loader reads only `fullTime`, and the V016 `match` trigger forces `winner_id = NULL` on any draw — so the advancing team is lost and `actual_winner_id` reaches the scorer as NULL. We add a dedicated `match.advanced_team_id` column that the trigger does **not** null on draws, source it from the provider's `winner`, feed it to the existing `score_match_for_bet(...)` as `actual_winner_id`, and surface it on the matches API/UI. We also switch the loader's match write from `ON CONFLICT DO NOTHING` to an UPSERT so re-sync actually lands results (the open idempotency gap noted in the knockout-seeding spec).

**Tech Stack:** Spring Boot 4 + Java 25, Flyway (plain SQL, next migration = **V017**), PL/pgSQL trigger on `match`, JdbcTemplate, RestClient DTOs, WireMock + Testcontainers for the loader IT, Next.js 16 + Vitest/RTL for the UI.

---

## Reference

- Provider score shape: <https://docs.football-data.org/general/v4/match.html> + <https://docs.football-data.org/general/v4/overtime.html> — `score.winner` ∈ {HOME_TEAM, AWAY_TEAM, DRAW}; `score.duration` ∈ {REGULAR, EXTRA_TIME, PENALTY_SHOOTOUT}; `fullTime` excludes shootout goals; `penalties` holds shootout goals.
- Current ingest: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataClient.java` (DTOs) + `FootballDataLoader.java:159-199` (match write).
- Scoring trigger: `backend/src/main/resources/db/migration/V016__knockout_predicted_winner.sql` (function `update_players_score`, `score_match_for_bet`); trigger defined in `V005__quinielas_bets_scoring.sql:142-144` as `BEFORE UPDATE OF score_t1, score_t2`.
- Matches API: `backend/src/main/java/io/quiniela/api/matches/MatchesService.java`; frontend `frontend/lib/api/matches.ts` + `frontend/components/matches/MatchListItem.tsx`.
- Already shipped (this session): the **pick-winner** chip (`bet.predicted_winner_id` → `pickWinner` on the matches API + UI). This plan adds the symmetric **actual-winner** half.

## Design — the column and the trigger

`match.winner_id` keeps its existing meaning (score-derived scoreboard winner, NULL on draw) so no existing reader breaks — notably `AdminTestService.advanceFromRound` (test-mode bracket advancement, which never produces draws). We add:

- `match.advanced_team_id BIGINT REFERENCES team(id)` — the team that actually progresses. Decisive knockout → scoreboard winner; penalty draw → the `winner`-field team; group/unplayed → NULL.

The trigger (`update_players_score`) is rewritten to:
1. Compute `NEW.advanced_team_id` **at the top, before the no-op guard**: decisive → derive from score; draw → **keep** the value the writer supplied (the penalty winner); unplayed → NULL.
2. Extend the no-op guard to also short-circuit only when `advanced_team_id` is unchanged (so a penalty-winner correction on an unchanged 1–1 still re-scores).
3. Pass `NEW.advanced_team_id` / `OLD.advanced_team_id` as `actual_winner_id` to `score_match_for_bet(...)` (instead of `winner_id`).
4. Keep maintaining `winner_id` exactly as before.

The trigger's column list grows to `BEFORE UPDATE OF score_t1, score_t2, advanced_team_id` so a winner-only correction fires it.

## Out of scope (follow-ups)

- Bracket page (`group/MatchRow`) showing the **actual** advancing team on a played draw — this plan covers the matches page (where it was reported) + scoring. (The bracket already shows the *pick* advancing team.)
- Displaying the penalty score itself (e.g. "(4–2 pen)"). The DTO will parse `penalties` in Task 2 so this is cheap later, but no UI renders it here.
- Test-mode simulator changes — it deliberately avoids draws and is unaffected.
- Scoring on a *fresh INSERT* of an already-finished match. Production's first load is pre-tournament (all SCHEDULED); real results arrive via the re-sync UPDATE path (Task 3), which fires the trigger. Documented, not handled.

---

## Task 1: V017 migration — `advanced_team_id` + trigger

**Files:**
- Create: `backend/src/main/resources/db/migration/V017__knockout_advanced_team.sql`
- Test: `backend/src/test/java/io/quiniela/api/scoring/AdvancedTeamScoringIT.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/scoring/AdvancedTeamScoringIT.java`:

```java
package io.quiniela.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V017: a knockout penalty draw stores advanced_team_id (survives the draw-nulling that hits
 * winner_id), and the predicted-winner bonus is scored off advanced_team_id.
 */
class AdvancedTeamScoringIT extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;
  JdbcTemplate jdbc;

  // A real knockout match from the seed (round R32). Resolved in setUp.
  Long koMatchId;
  Long koTeam1;
  Long koTeam2;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    koMatchId =
        jdbc.queryForObject(
            "SELECT m.id FROM match m JOIN round r ON r.id = m.round_id"
                + " WHERE r.code = 'R32' ORDER BY m.id LIMIT 1",
            Long.class);
    // The V007 test seed inserts R32 matches with NULL team slots (teams fill in
    // later from group standings). Assign two real teams so we can record a result
    // with a concrete advancing team. Restored to NULL in @AfterEach because the
    // Testcontainers Postgres is a shared singleton (see AbstractIntegrationTest).
    var teamIds = jdbc.queryForList("SELECT id FROM team ORDER BY id LIMIT 2", Long.class);
    koTeam1 = teamIds.get(0);
    koTeam2 = teamIds.get(1);
    jdbc.update(
        "UPDATE match SET team_1_id = ?, team_2_id = ? WHERE id = ?", koTeam1, koTeam2, koMatchId);
  }

  @AfterEach
  void restore() {
    jdbc.update("DELETE FROM bet WHERE match_id = ?", koMatchId);
    jdbc.update("DELETE FROM quiniela WHERE user_id < 0");
    jdbc.update(
        "UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE, winner_id = NULL,"
            + " advanced_team_id = NULL, team_1_id = NULL, team_2_id = NULL WHERE id = ?",
        koMatchId);
  }

  @Test
  void penaltyDrawKeepsAdvancedTeamAndAwardsBonus() {
    // A quiniela that bet a 1-1 draw and predicted koTeam2 advances.
    Long quinielaId =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at)"
                + " VALUES (1, -1, 0, NOW(), NOW()) RETURNING id",
            Long.class);
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, predicted_winner_id,"
            + " created_at, updated_at) VALUES (?, ?, 1, 1, ?, NOW(), NOW())",
        quinielaId,
        koMatchId,
        koTeam2);

    // Record a 1-1 penalty draw, koTeam2 advanced.
    jdbc.update(
        "UPDATE match SET score_t1 = 1, score_t2 = 1, played = TRUE, advanced_team_id = ?"
            + " WHERE id = ?",
        koTeam2,
        koMatchId);

    // advanced_team_id persisted (NOT nulled like winner_id is on a draw).
    Long advanced =
        jdbc.queryForObject(
            "SELECT advanced_team_id FROM match WHERE id = ?", Long.class, koMatchId);
    assertThat(advanced).isEqualTo(koTeam2);
    Long winnerId = jdbc.queryForObject("SELECT winner_id FROM match WHERE id = ?", Long.class, koMatchId);
    assertThat(winnerId).isNull(); // score-derived, still null on a draw

    // Knockout exact 1-1 (2+2) + correct advancement outcome (3) = 7, doubled = 14.
    Long points = jdbc.queryForObject("SELECT points FROM quiniela WHERE id = ?", Long.class, quinielaId);
    assertThat(points).isEqualTo(14L);
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AdvancedTeamScoringIT`
Expected: FAIL — `column "advanced_team_id" does not exist` (migration not written yet).

- [ ] **Step 3: Write the migration**

Create `backend/src/main/resources/db/migration/V017__knockout_advanced_team.sql`:

```sql
-- V017: advanced_team_id — the team that actually progresses from a knockout.
--
-- winner_id is score-derived and forced NULL on any draw (V016 lines 126-131),
-- so a penalty-shootout result (returned by football-data.org as an on-pitch
-- draw + a `winner` field) loses the progressing team. advanced_team_id stores
-- it and is NOT nulled on draws. Scoring uses it as actual_winner_id so the
-- knockout advancement bonus is awarded; the matches UI uses it to show who
-- advanced. winner_id keeps its existing meaning (used by test-mode advancement).

ALTER TABLE match ADD COLUMN advanced_team_id BIGINT REFERENCES team(id);

-- Backfill decisive played matches: the progressing team equals the scoreboard
-- winner. Draws have winner_id NULL and stay NULL (no historical penalty data).
UPDATE match SET advanced_team_id = winner_id WHERE played = TRUE AND winner_id IS NOT NULL;

-- Rewrite the trigger function: maintain advanced_team_id, score off it.
CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
    is_knockout BOOLEAN;
    bet_row RECORD;
    old_points INT;
    new_points INT;
    delta INT;
BEGIN
    -- Compute the progressing team BEFORE the no-op guard so a decisive result
    -- always derives it, and a draw keeps whatever the writer supplied (the
    -- penalty winner) rather than discarding it.
    NEW.advanced_team_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NEW.advanced_team_id   -- draw: trust the supplied penalty winner
    END;

    -- No-op short-circuit: nothing changed that affects scoring or advancement.
    IF NEW.score_t1 IS NOT DISTINCT FROM OLD.score_t1
       AND NEW.score_t2 IS NOT DISTINCT FROM OLD.score_t2
       AND NEW.advanced_team_id IS NOT DISTINCT FROM OLD.advanced_team_id THEN
        RETURN NEW;
    END IF;

    SELECT r.code <> 'GROUP' INTO is_knockout FROM round r WHERE r.id = NEW.round_id;
    IF is_knockout IS NULL THEN is_knockout := FALSE; END IF;

    FOR bet_row IN
        SELECT b.quiniela_id,
               b.score_t1            AS bet_t1,
               b.score_t2            AS bet_t2,
               b.predicted_winner_id AS predicted_winner_id
        FROM bet b
        WHERE b.match_id = NEW.id
    LOOP
        old_points := score_match_for_bet(
            is_knockout,
            bet_row.bet_t1, bet_row.bet_t2,
            OLD.score_t1, OLD.score_t2,
            bet_row.predicted_winner_id, OLD.advanced_team_id
        );
        new_points := score_match_for_bet(
            is_knockout,
            bet_row.bet_t1, bet_row.bet_t2,
            NEW.score_t1, NEW.score_t2,
            bet_row.predicted_winner_id, NEW.advanced_team_id
        );
        delta := new_points - old_points;
        IF delta <> 0 THEN
            UPDATE quiniela SET points = points + delta, updated_at = NOW()
            WHERE id = bet_row.quiniela_id;
        END IF;
    END LOOP;

    NEW.updated_at := NOW();

    -- Keep winner_id score-derived (legacy: test-mode advancement reads it).
    NEW.winner_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NULL
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- The trigger must also fire when only advanced_team_id changes (penalty-winner
-- correction with an unchanged on-pitch draw). Recreate with the wider column list.
DROP TRIGGER IF EXISTS matches_score_update_trigger ON match;
CREATE TRIGGER matches_score_update_trigger
BEFORE UPDATE OF score_t1, score_t2, advanced_team_id ON match
FOR EACH ROW EXECUTE FUNCTION update_players_score();
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AdvancedTeamScoringIT`
Expected: PASS (1 test).

- [ ] **Step 5: Run the existing scoring + matches suites to confirm no regression**

Run: `cd backend && ./mvnw test -Dtest='ScoringIT,MatchesControllerIT,AdminResultsServiceIT'`
Expected: PASS. (If a class name differs, run `./mvnw test` for the full suite.)

- [ ] **Step 6: Commit**

```bash
cd backend && ./mvnw spotless:apply
git add backend/src/main/resources/db/migration/V017__knockout_advanced_team.sql \
        backend/src/test/java/io/quiniela/api/scoring/AdvancedTeamScoringIT.java
git commit -m "feat(scoring): add advanced_team_id so penalty draws keep the progressing team"
```

---

## Task 2: Parse `winner` / `penalties` from the provider

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataClient.java:23-37`
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataLoader.java` (add static `advancingTeamId`)
- Test: `backend/src/test/java/io/quiniela/api/footballdata/FootballDataLoaderTest.java`

- [ ] **Step 1: Write the failing test**

Append to `FootballDataLoaderTest.java` (inside the existing class):

```java
  @org.junit.jupiter.api.Test
  void advancingTeamIdReadsPenaltyWinnerFromWinnerField() {
    var home = new FootballDataClient.MatchTeam(1001L, "Home");
    var away = new FootballDataClient.MatchTeam(1002L, "Away");
    var drawScore =
        new FootballDataClient.MatchScore(
            "AWAY_TEAM",
            "PENALTY_SHOOTOUT",
            new FootballDataClient.MatchScoreFull(1, 1),
            new FootballDataClient.MatchScoreFull(3, 5));
    var m =
        new FootballDataClient.MatchApi(
            7001L, "2026-07-01T18:00:00Z", "FINISHED", "LAST_32", null, home, away, drawScore);

    org.junit.jupiter.api.Assertions.assertEquals(1002L, FootballDataLoader.advancingTeamId(m));
  }

  @org.junit.jupiter.api.Test
  void advancingTeamIdIsNullForGroupDraw() {
    var home = new FootballDataClient.MatchTeam(1001L, "Home");
    var away = new FootballDataClient.MatchTeam(1002L, "Away");
    var drawScore =
        new FootballDataClient.MatchScore(
            "DRAW", "REGULAR", new FootballDataClient.MatchScoreFull(1, 1), null);
    var m =
        new FootballDataClient.MatchApi(
            8001L, "2026-06-15T18:00:00Z", "FINISHED", "GROUP_STAGE", "Group A", home, away, drawScore);

    org.junit.jupiter.api.Assertions.assertNull(FootballDataLoader.advancingTeamId(m));
  }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=FootballDataLoaderTest`
Expected: FAIL — the `MatchScore` constructor takes 1 arg (`fullTime`) and `advancingTeamId` does not exist (compile error).

- [ ] **Step 3: Widen the `MatchScore` DTO**

In `FootballDataClient.java` replace line 25:

```java
  public record MatchScore(MatchScoreFull fullTime) {}
```

with:

```java
  // winner ∈ {HOME_TEAM, AWAY_TEAM, DRAW}; duration ∈ {REGULAR, EXTRA_TIME, PENALTY_SHOOTOUT}.
  // fullTime excludes shootout goals; penalties holds the shootout score (null when not applicable).
  public record MatchScore(
      String winner, String duration, MatchScoreFull fullTime, MatchScoreFull penalties) {}
```

Jackson maps by field name and ignores unknown JSON properties by default, and missing properties deserialize to null — so existing stubs that send only `fullTime` still parse.

- [ ] **Step 4: Add the resolver to `FootballDataLoader`**

In `FootballDataLoader.java`, add this static method next to `mapStageToRoundCode` (after line 227):

```java
  /**
   * The team that progresses from a finished knockout, per football-data.org's score.winner.
   * Works for both regulation/ET wins and penalty shootouts (winner names the progressing side).
   * Returns null for a true draw (group stage) or missing data.
   */
  static Long advancingTeamId(FootballDataClient.MatchApi m) {
    if (m.score() == null || m.score().winner() == null) return null;
    return switch (m.score().winner()) {
      case "HOME_TEAM" -> m.homeTeam() != null ? m.homeTeam().id() : null;
      case "AWAY_TEAM" -> m.awayTeam() != null ? m.awayTeam().id() : null;
      default -> null; // DRAW or unrecognized
    };
  }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=FootballDataLoaderTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd backend && ./mvnw spotless:apply
git add backend/src/main/java/io/quiniela/api/footballdata/FootballDataClient.java \
        backend/src/main/java/io/quiniela/api/footballdata/FootballDataLoader.java \
        backend/src/test/java/io/quiniela/api/footballdata/FootballDataLoaderTest.java
git commit -m "feat(footballdata): parse winner/penalties and resolve the advancing team"
```

---

## Task 3: Loader writes `advanced_team_id` and re-syncs results (UPSERT)

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataLoader.java:170-195`
- Test: `backend/src/test/java/io/quiniela/api/footballdata/FootballDataLoaderIT.java`

- [ ] **Step 1: Write the failing test**

Append to `FootballDataLoaderIT.java` (inside the class):

```java
  @Test
  void resyncUpdatesResultAndStoresPenaltyAdvancingTeam() {
    // Teams 1001/1002 exist via standings + teams stubs (reuse the shapes from the
    // first test). Minimal standings/teams so the loader inserts both teams.
    stubFor(
        get(urlEqualTo("/v4/competitions/WC/standings"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"standings\":[{\"group\":\"Group A\",\"type\":\"TOTAL\","
                            + "\"table\":[{\"team\":{\"id\":1001,\"name\":\"Uno\"}},"
                            + "{\"team\":{\"id\":1002,\"name\":\"Dos\"}}]}]}")));
    stubFor(
        get(urlEqualTo("/v4/competitions/WC/teams"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"teams\":[{\"id\":1001,\"name\":\"Uno\",\"tla\":\"UNO\"},"
                            + "{\"id\":1002,\"name\":\"Dos\",\"tla\":\"DOS\"}]}")));

    // First sync: a scheduled R32 match, no result yet.
    stubFor(
        get(urlEqualTo("/v4/competitions/WC/matches"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"matches\":[{\"id\":9001,\"utcDate\":\"2026-07-01T18:00:00Z\","
                            + "\"status\":\"SCHEDULED\",\"stage\":\"LAST_32\","
                            + "\"homeTeam\":{\"id\":1001,\"name\":\"Uno\"},"
                            + "\"awayTeam\":{\"id\":1002,\"name\":\"Dos\"},"
                            + "\"score\":{\"winner\":null,\"duration\":\"REGULAR\","
                            + "\"fullTime\":{\"home\":null,\"away\":null}}}]}")));
    loader.run(null);

    var jdbc = new JdbcTemplate(dataSource);
    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 9001", Boolean.class))
        .isFalse();

    // Second sync: same match, now a 1-1 penalty win for the away team (1002).
    wm.resetAll();
    WireMock.configureFor("localhost", wm.port());
    stubFor(
        get(urlEqualTo("/v4/competitions/WC/standings"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"standings\":[]}")));
    stubFor(
        get(urlEqualTo("/v4/competitions/WC/teams"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"teams\":[]}")));
    stubFor(
        get(urlEqualTo("/v4/competitions/WC/matches"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"matches\":[{\"id\":9001,\"utcDate\":\"2026-07-01T18:00:00Z\","
                            + "\"status\":\"FINISHED\",\"stage\":\"LAST_32\","
                            + "\"homeTeam\":{\"id\":1001,\"name\":\"Uno\"},"
                            + "\"awayTeam\":{\"id\":1002,\"name\":\"Dos\"},"
                            + "\"score\":{\"winner\":\"AWAY_TEAM\",\"duration\":\"PENALTY_SHOOTOUT\","
                            + "\"fullTime\":{\"home\":1,\"away\":1},"
                            + "\"penalties\":{\"home\":3,\"away\":5}}}]}")));

    // teams table is non-empty, so call load() directly to force the matches re-sync.
    loader.load();

    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 9001", Boolean.class)).isTrue();
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 9001", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT score_t2 FROM match WHERE id = 9001", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT advanced_team_id FROM match WHERE id = 9001", Long.class))
        .isEqualTo(1002L);
    // winner_id stays null on a draw (score-derived).
    assertThat(jdbc.queryForObject("SELECT winner_id FROM match WHERE id = 9001", Long.class)).isNull();
  }
```

Note: this calls `loader.load()` directly on the second sync (the public `run` early-returns once the team table is populated). `load()` is package-private and the test is in the same package — no visibility change needed.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=FootballDataLoaderIT`
Expected: FAIL — after the second sync `played` is still false / `advanced_team_id` is null, because the current write is `ON CONFLICT (id) DO NOTHING`.

- [ ] **Step 3: Make the match write an UPSERT that sets `advanced_team_id`**

In `FootballDataLoader.java`, replace the score-extraction + insert block (lines 173-195) with:

```java
        Integer scoreT1 =
            m.score() != null && m.score().fullTime() != null ? m.score().fullTime().home() : null;
        Integer scoreT2 =
            m.score() != null && m.score().fullTime() != null ? m.score().fullTime().away() : null;
        boolean played = "FINISHED".equals(m.status());
        Long advancedTeamId = advancingTeamId(m);

        // UPSERT so a re-sync lands real results onto the row inserted at first load.
        // Updating score_t1/score_t2/advanced_team_id fires the BEFORE UPDATE trigger,
        // which recomputes points. team ids use COALESCE so a knockout slot already
        // filled isn't blanked by a later TBD payload. group_code/round_id never change.
        jdbc.update(
            "INSERT INTO match "
                + "(id, tournament_id, round_id, group_code, team_1_id, team_2_id, "
                + " score_t1, score_t2, advanced_team_id, played, kickoff_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "  team_1_id = COALESCE(EXCLUDED.team_1_id, match.team_1_id), "
                + "  team_2_id = COALESCE(EXCLUDED.team_2_id, match.team_2_id), "
                + "  score_t1 = EXCLUDED.score_t1, "
                + "  score_t2 = EXCLUDED.score_t2, "
                + "  advanced_team_id = EXCLUDED.advanced_team_id, "
                + "  played = EXCLUDED.played, "
                + "  kickoff_at = EXCLUDED.kickoff_at",
            m.id(),
            TOURNAMENT_ID,
            roundId,
            groupCode,
            team1Id,
            team2Id,
            scoreT1,
            scoreT2,
            advancedTeamId,
            played,
            java.sql.Timestamp.from(kickoff));
        matchesInserted++;
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=FootballDataLoaderIT`
Expected: PASS (both the original test and the new one).

- [ ] **Step 5: Commit**

```bash
cd backend && ./mvnw spotless:apply
git add backend/src/main/java/io/quiniela/api/footballdata/FootballDataLoader.java \
        backend/src/test/java/io/quiniela/api/footballdata/FootballDataLoaderIT.java
git commit -m "feat(footballdata): upsert results on re-sync, persist advanced_team_id"
```

---

## Task 4: Surface the actual advancing team on the matches API + UI

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/matches/MatchesService.java`
- Modify: `backend/src/test/java/io/quiniela/api/matches/MatchesControllerIT.java`
- Modify: `frontend/lib/api/matches.ts`
- Modify: `frontend/components/matches/MatchListItem.tsx`
- Modify: `frontend/components/matches/MatchListItem.test.tsx`

- [ ] **Step 1: Write the failing backend test**

In `MatchesControllerIT.java`, first extend `restoreMatch1` to also clear the new column — change its UPDATE to:

```java
    jdbc.update(
        "UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE, winner_id = NULL,"
            + " advanced_team_id = NULL,"
            + " kickoff_at = TIMESTAMPTZ '2026-06-11 17:00 UTC' WHERE id = 1");
```

Then add this test:

```java
  @Test
  void drawResultSurfacesActualAdvancingTeam() throws Exception {
    var u = saveUser("mt-adv", "Advancing Caller");
    String token = jwt.issue(u);

    Long team2Id = jdbc.queryForObject("SELECT team_2_id FROM match WHERE id = 1", Long.class);

    // 1-1 result, team2 advanced (penalties). Push to the past.
    jdbc.update(
        "UPDATE match SET kickoff_at = NOW() - INTERVAL '2 days', score_t1 = 1, score_t2 = 1,"
            + " played = TRUE, advanced_team_id = ? WHERE id = 1",
        team2Id);

    String advCode =
        jdbc.queryForObject("SELECT code FROM team WHERE id = ?", String.class, team2Id);

    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.past[0].winner.code").value(advCode))
        .andExpect(jsonPath("$.past[0].winner.flag").exists());
  }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=MatchesControllerIT`
Expected: FAIL — `No value at JSON path "$.past[0].winner.code"` (the API has no `winner` field).

- [ ] **Step 3: Add `winner` to `MatchesService`**

In `MatchesService.java`:

(a) Add to the `MatchRow` record (after `pickWinner`):

```java
      ScorePair yourPick,
      Integer pointsEarned,
      TeamRef pickWinner,
      TeamRef winner) {}
```

(b) In the SELECT, after the `pw_flag` column add:

```sql
              bw.flag_emoji     AS pw_flag,
              aw.code           AS aw_code,
              aw.name           AS aw_name,
              aw.flag_emoji     AS aw_flag,
```

(c) After the `bw` join add:

```sql
            LEFT JOIN team bw ON bw.id = b.predicted_winner_id
            LEFT JOIN team aw ON aw.id = m.advanced_team_id
```

(d) In the row mapper, after the `pickWinner` block add:

```java
              TeamRef winner =
                  rs.getString("aw_code") == null
                      ? null
                      : new TeamRef(
                          rs.getString("aw_code"),
                          rs.getString("aw_name"),
                          rs.getString("aw_flag"));
```

(e) In the `new MatchRow(...)` constructor call, append `winner` as the last argument:

```java
                  yourPick,
                  (Integer) rs.getObject("points_earned"),
                  pickWinner,
                  winner);
```

- [ ] **Step 4: Run the backend test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=MatchesControllerIT`
Expected: PASS (all tests).

- [ ] **Step 5: Write the failing frontend test**

In `frontend/components/matches/MatchListItem.test.tsx`, add `winner: null` to the `match()` factory defaults (next to `pickWinner: null`), then add:

```tsx
  it("shows the actual advancing team when a knockout result is a draw", () => {
    render(
      <MatchListItem
        match={match({
          score: { t1: 1, t2: 1 },
          winner: { code: "PAR", name: "Paraguay", flag: "🇵🇾" },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    expect(screen.getAllByText(/Paraguay/i).length).toBeGreaterThan(1);
  });

  it("does not show an advancing chip for a decisive result", () => {
    render(
      <MatchListItem
        match={match({
          score: { t1: 2, t2: 1 },
          winner: { code: "PAR", name: "Paraguay", flag: "🇵🇾" },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    expect(screen.getAllByText(/Paraguay/i).length).toBe(1);
  });
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd frontend && npx vitest run components/matches/MatchListItem.test.tsx`
Expected: FAIL — "shows the actual advancing team" expects >1 but gets 1 (no chip rendered).

- [ ] **Step 7: Add `winner` to the frontend type**

In `frontend/lib/api/matches.ts`, inside `MatchView` add (after `pickWinner`):

```ts
  pickWinner: TeamRef | null;
  /**
   * Team that actually advanced when a knockout result is a draw (penalties).
   * Null for group matches and decisive results (the score already names the winner).
   */
  winner: TeamRef | null;
```

- [ ] **Step 8: Render the actual-advancing chip in the score cell**

In `frontend/components/matches/MatchListItem.tsx`, add this just below the existing `pickAdvancingTeam` computation:

```tsx
  const pickAdvancingTeam = pickIsDraw ? match.pickWinner : null;

  // Knockout draws are decided on penalties — the score hides who advanced, so
  // surface it. Only for draws; a decisive score already names its winner.
  const resultIsDraw =
    match.played && match.score != null && match.score.t1 === match.score.t2;
  const advancingTeam = resultIsDraw ? match.winner : null;
```

Then change the score-display branch from:

```tsx
          {match.played && match.score ? (
            <span className="font-display text-[26px] font-black leading-none tracking-[-0.04em] text-[var(--color-text-primary)]">
              {match.score.t1}–{match.score.t2}
            </span>
          ) : isLive ? (
```

to:

```tsx
          {match.played && match.score ? (
            <>
              <span className="font-display text-[26px] font-black leading-none tracking-[-0.04em] text-[var(--color-text-primary)]">
                {match.score.t1}–{match.score.t2}
              </span>
              {advancingTeam && (
                <span className="flex items-center gap-0.5 font-mono text-[9px] font-bold tracking-[0.06em] text-[var(--color-text-muted)]">
                  <span className="text-[11px] leading-none">{advancingTeam.flag}</span>
                  <span className="max-w-[64px] truncate">{advancingTeam.name}</span>
                </span>
              )}
            </>
          ) : isLive ? (
```

- [ ] **Step 9: Run the frontend test + typecheck to verify**

Run: `cd frontend && npx vitest run components/matches/MatchListItem.test.tsx && npx tsc --noEmit`
Expected: PASS, no type errors.

- [ ] **Step 10: Commit**

```bash
cd backend && ./mvnw spotless:apply
cd ..
git add backend/src/main/java/io/quiniela/api/matches/MatchesService.java \
        backend/src/test/java/io/quiniela/api/matches/MatchesControllerIT.java \
        frontend/lib/api/matches.ts \
        frontend/components/matches/MatchListItem.tsx \
        frontend/components/matches/MatchListItem.test.tsx
git commit -m "feat(matches): show the actual advancing team on knockout draws"
```

---

## Task 5: Admin manual entry of the advancing team (correction path)

> The provider (Tasks 2-3) is the primary source. This adds the manual override an admin uses to record/correct a penalty result when the feed is wrong or late.

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/admin/AdminResultsService.java`
- Test: `backend/src/test/java/io/quiniela/api/admin/AdminResultsServiceIT.java` (create if absent; otherwise add the test)

- [ ] **Step 1: Write the failing test**

Add to the admin results IT (mirror the existing record-result test's setup; this test asserts the new field plumbs through). If no IT exists, create `backend/src/test/java/io/quiniela/api/admin/AdminResultsServiceIT.java` extending `AbstractIntegrationTest`, autowiring `AdminResultsService` and a `JdbcTemplate`, seeding an ADMIN user, then:

```java
  @Test
  void recordingADrawWithAdvancingTeamStoresAdvancedTeamId() {
    Long matchId =
        jdbc.queryForObject(
            "SELECT m.id FROM match m JOIN round r ON r.id = m.round_id"
                + " WHERE r.code = 'R32' ORDER BY m.id LIMIT 1",
            Long.class);
    // Seed leaves R32 teams NULL — assign two real teams (restore to NULL in @AfterEach).
    var teamIds = jdbc.queryForList("SELECT id FROM team ORDER BY id LIMIT 2", Long.class);
    Long team2 = teamIds.get(1);
    jdbc.update(
        "UPDATE match SET team_1_id = ?, team_2_id = ? WHERE id = ?",
        teamIds.get(0), team2, matchId);

    service.record(new AdminResultsService.RecordResultCommand(adminUserId, matchId, 1, 1, team2));

    assertThat(jdbc.queryForObject("SELECT advanced_team_id FROM match WHERE id = ?", Long.class, matchId))
        .isEqualTo(team2);
  }
```

Add a matching `@AfterEach` that restores `team_1_id = NULL, team_2_id = NULL, score_t1 = NULL, score_t2 = NULL, played = FALSE, winner_id = NULL, advanced_team_id = NULL` on `matchId`, since the Postgres container is a shared singleton.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AdminResultsServiceIT`
Expected: FAIL — `RecordResultCommand` has no 5-arg constructor.

- [ ] **Step 3: Extend `RecordResultCommand` and `record(...)`**

In `AdminResultsService.java`:

(a) Replace the `RecordResultCommand` record (line 51) with a nullable advancing-team field:

```java
  public record RecordResultCommand(
      Long callerUserId, Long matchId, int scoreT1, int scoreT2, Long advancingTeamId) {}
```

(b) In `record(...)`, replace the score/winner write block (lines 140-143) with:

```java
    m.setScoreT1(cmd.scoreT1);
    m.setScoreT2(cmd.scoreT2);
    m.setPlayed(true);
    m.setWinnerId(winnerOf(m, cmd.scoreT1, cmd.scoreT2));
    // On a draw the progressing team can't be derived from the score; trust the
    // admin's pick. On a decisive score the trigger overwrites this anyway.
    m.setAdvancedTeamId(
        cmd.scoreT1 == cmd.scoreT2 ? cmd.advancingTeamId : winnerOf(m, cmd.scoreT1, cmd.scoreT2));
```

(c) Add the `advancedTeamId` field + getter/setter to the `Match` entity (`backend/src/main/java/io/quiniela/api/match/Match.java`), mirroring `winnerId`:

```java
  @Column(name = "advanced_team_id")
  private Long advancedTeamId;
```
```java
  public Long getAdvancedTeamId() {
    return advancedTeamId;
  }

  public void setAdvancedTeamId(Long advancedTeamId) {
    this.advancedTeamId = advancedTeamId;
  }
```

(d) Update the controller call site. In `AdminResultsController.java:45` change:

```java
            new RecordResultCommand(callerUserId, matchId, req.scoreT1(), req.scoreT2()));
```

to:

```java
            new RecordResultCommand(
                callerUserId, matchId, req.scoreT1(), req.scoreT2(), req.advancingTeamId()));
```

and add `Long advancingTeamId` to the request record that backs `req` (same file — find the `record ... Req(` declaration with `scoreT1`/`scoreT2`). A missing JSON property deserializes to null, so existing clients that don't send it still work. Then re-check for any other call sites:

Run: `cd backend && grep -rn "new RecordResultCommand\|new AdminResultsService.RecordResultCommand" src`
Pass `null` as the final argument anywhere else (e.g. existing tests).

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AdminResultsServiceIT`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
cd backend && ./mvnw spotless:apply
git add backend/src/main/java/io/quiniela/api/admin/AdminResultsService.java \
        backend/src/main/java/io/quiniela/api/match/Match.java \
        backend/src/test/java/io/quiniela/api/admin/AdminResultsServiceIT.java
git commit -m "feat(admin): let admins record the advancing team on a knockout draw"
```

---

## Final verification

- [ ] `cd backend && ./mvnw verify` — full backend build + all ITs green (Spotless enforced at compile).
- [ ] `cd frontend && pnpm test && npx tsc --noEmit` — frontend unit tests + typecheck green.
- [ ] Manual smoke (optional, real key): set `app.football-data.enabled=true` against a finished knockout fixture and confirm `match.advanced_team_id` populates and the Partidos page shows the advancing team on a 1–1.

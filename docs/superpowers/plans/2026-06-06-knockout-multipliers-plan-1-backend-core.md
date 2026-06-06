# Knockout Multipliers — Plan 1 of 3: Backend Scoring Core

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the score multiplier per-round and configurable in the DB (`round.points_multiplier`), replacing the hardcoded `×2`, with both the DB scoring function and its Java mirror reading the value — fully test-pinned. No admin UI yet; this phase ships a configurable-but-unchanged scoring engine (all knockout rounds still seed to ×2).

**Architecture:** Add `points_multiplier` to `round`. `score_match_for_bet` gains a trailing `multiplier` arg returning `total * COALESCE(multiplier, knockout?2:1)`. The trigger looks up the round's multiplier and passes it. `CompareService.scoreMatchForBet` takes the multiplier (from a `round`-joined projection). `ScoringDivergenceTest` is extended to drive multipliers so Java and the DB can't drift.

**Tech Stack:** Spring Boot 4 + Java 25 + Maven + Flyway + PL/pgSQL on Postgres; Testcontainers integration tests (Docker available). Run backend commands from `.worktrees/knockout-multipliers/backend`.

**This is Plan 1 of 3.** Plan 2 = admin read/write API for the multipliers; Plan 3 = admin UI panel + scoring-explainer page + i18n. Each is its own spec-derived plan.

**Phasing note:** Plan 1 is independently mergeable — it changes the scoring engine to be config-driven while preserving today's exact behavior (knockouts ×2). Nothing user-visible changes until Plan 2/3.

---

## File Structure

**Create**
- `backend/src/main/resources/db/migration/V019__round_points_multiplier.sql` — column + seed + function/trigger replacement
- `backend/src/test/java/io/quiniela/api/support/V019MigrationTest.java` — column + seed assertions

**Modify**
- `backend/src/main/java/io/quiniela/api/compare/CompareService.java` — `MatchMeta.pointsMultiplier`, projection join, `scoreMatchForBet(int multiplier, …)`, caller
- `backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java` — reset multipliers in `@BeforeEach`; add a custom-multiplier test
- `backend/src/test/java/io/quiniela/api/compare/ScoringDivergenceTest.java` — adapt to the new Java signature; add multiplier coverage

---

## Task 1: Migration — column, seed, function, trigger

**Files:**
- Create: `backend/src/main/resources/db/migration/V019__round_points_multiplier.sql`
- Create: `backend/src/test/java/io/quiniela/api/support/V019MigrationTest.java`

First confirm `V019` is free: `ls backend/src/main/resources/db/migration/ | sort | tail -3` must show `V018__pool_house_cut.sql` as the head and **no** `V019__*`. If a `V019__*` exists (a concurrent branch landed one — e.g. the deferred bilingual-Paul work), STOP and report NEEDS_CONTEXT so we bump to the next free number across this file, the test class name, and all command invocations.

- [ ] **Step 1: Write the failing migration test**

Create `backend/src/test/java/io/quiniela/api/support/V019MigrationTest.java`:

```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V019MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void roundHasPointsMultiplierColumn() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'round' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("points_multiplier");
  }

  @Test
  void groupSeedsToOneAndKnockoutsToTwo() {
    var jdbc = new JdbcTemplate(dataSource);
    Integer group =
        jdbc.queryForObject(
            "SELECT points_multiplier FROM round WHERE code = 'GROUP'", Integer.class);
    assertThat(group).isEqualTo(1);

    var knockoutMultipliers =
        jdbc.queryForList(
            "SELECT points_multiplier FROM round WHERE code <> 'GROUP'", Integer.class);
    assertThat(knockoutMultipliers).isNotEmpty().allMatch(m -> m == 2);
  }
}
```

- [ ] **Step 2: Run it, expect FAIL** (column missing)

Run: `./mvnw -q -Dtest=V019MigrationTest test`
Expected: FAIL — `columns` does not contain `points_multiplier`. (Docker required; available.)

- [ ] **Step 3: Write the migration**

Create `backend/src/main/resources/db/migration/V019__round_points_multiplier.sql`:

```sql
-- V019: configurable per-round score multiplier.
--
-- Replaces the hardcoded "knockout = ×2" inside score_match_for_bet with a
-- per-round points_multiplier (a column on `round`). The trigger looks it up
-- and passes it through; group rounds carry 1 (unchanged), knockout rounds seed
-- to 2 (unchanged) and become admin-editable in a later phase. The Java mirror
-- in CompareService reads the same value; ScoringDivergenceTest pins the two.

ALTER TABLE round
    ADD COLUMN points_multiplier INT NOT NULL DEFAULT 1
        CHECK (points_multiplier >= 1);

-- Seed: GROUP stays ×1 (the column default); every knockout round to ×2,
-- exactly today's behaviour.
UPDATE round SET points_multiplier = 2 WHERE code <> 'GROUP';

-- Add a trailing `multiplier` arg to the scoring function. NULL preserves the
-- old "knockout ? ×2 : ×1" for any caller that omits it; the trigger always
-- passes the round's configured value. Drop the V016 7-arg overload first so
-- the new 8-arg form (callable with 5/7/8 args) is unambiguous.
DROP FUNCTION IF EXISTS score_match_for_bet(boolean, integer, integer, integer, integer, bigint, bigint);

CREATE OR REPLACE FUNCTION score_match_for_bet(
    is_knockout BOOLEAN,
    bet_t1 INT, bet_t2 INT,
    actual_t1 INT, actual_t2 INT,
    predicted_winner_id BIGINT DEFAULT NULL,
    actual_winner_id    BIGINT DEFAULT NULL,
    multiplier          INT DEFAULT NULL
) RETURNS INT AS $$
DECLARE
    is_exact      BOOLEAN;
    bet_winner    INT;
    actual_winner INT;
    outcome_pts   INT := 0;
    t1_pts        INT := 0;
    t2_pts        INT := 0;
    diff_pts      INT := 0;
    total         INT;
BEGIN
    IF actual_t1 IS NULL OR actual_t2 IS NULL THEN RETURN 0; END IF;

    is_exact := (bet_t1 = actual_t1 AND bet_t2 = actual_t2);

    bet_winner := CASE
        WHEN bet_t1 > bet_t2 THEN 1
        WHEN bet_t1 < bet_t2 THEN -1
        ELSE 0
    END;
    actual_winner := CASE
        WHEN actual_t1 > actual_t2 THEN 1
        WHEN actual_t1 < actual_t2 THEN -1
        ELSE 0
    END;

    IF bet_winner = actual_winner THEN
        IF is_knockout AND bet_winner = 0 AND actual_winner = 0
           AND predicted_winner_id IS NOT NULL AND actual_winner_id IS NOT NULL THEN
            outcome_pts := CASE WHEN predicted_winner_id = actual_winner_id THEN 3 ELSE 0 END;
        ELSE
            outcome_pts := 3;
        END IF;
    END IF;

    IF bet_t1 = actual_t1 THEN t1_pts := 2; END IF;
    IF bet_t2 = actual_t2 THEN t2_pts := 2; END IF;

    IF NOT is_exact AND (bet_t1 - bet_t2) = (actual_t1 - actual_t2) THEN
        diff_pts := 1;
    END IF;

    total := outcome_pts + t1_pts + t2_pts + diff_pts;

    RETURN total * COALESCE(multiplier, CASE WHEN is_knockout THEN 2 ELSE 1 END);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Recreate the trigger function (V017 shape, which maintains advanced_team_id)
-- to look up the round multiplier and pass it through. Only the function body
-- changes; the BEFORE UPDATE trigger binding from V017 stays in place.
CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
    is_knockout BOOLEAN;
    multiplier_val INT;
    bet_row RECORD;
    old_points INT;
    new_points INT;
    delta INT;
BEGIN
    NEW.advanced_team_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NEW.advanced_team_id
    END;

    IF NEW.score_t1 IS NOT DISTINCT FROM OLD.score_t1
       AND NEW.score_t2 IS NOT DISTINCT FROM OLD.score_t2
       AND NEW.advanced_team_id IS NOT DISTINCT FROM OLD.advanced_team_id THEN
        RETURN NEW;
    END IF;

    SELECT r.code <> 'GROUP', r.points_multiplier
      INTO is_knockout, multiplier_val
      FROM round r WHERE r.id = NEW.round_id;
    IF is_knockout IS NULL THEN is_knockout := FALSE; END IF;
    IF multiplier_val IS NULL THEN
        multiplier_val := CASE WHEN is_knockout THEN 2 ELSE 1 END;
    END IF;

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
            bet_row.predicted_winner_id, OLD.advanced_team_id,
            multiplier_val
        );
        new_points := score_match_for_bet(
            is_knockout,
            bet_row.bet_t1, bet_row.bet_t2,
            NEW.score_t1, NEW.score_t2,
            bet_row.predicted_winner_id, NEW.advanced_team_id,
            multiplier_val
        );
        delta := new_points - old_points;
        IF delta <> 0 THEN
            UPDATE quiniela SET points = points + delta, updated_at = NOW()
            WHERE id = bet_row.quiniela_id;
        END IF;
    END LOOP;

    NEW.updated_at := NOW();

    NEW.winner_id := CASE
        WHEN NEW.score_t1 IS NULL OR NEW.score_t2 IS NULL THEN NULL
        WHEN NEW.score_t1 > NEW.score_t2 THEN NEW.team_1_id
        WHEN NEW.score_t2 > NEW.score_t1 THEN NEW.team_2_id
        ELSE NULL
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 4: Run the migration test, expect PASS**

Run: `./mvnw -q -Dtest=V019MigrationTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Confirm existing scoring tests still pass (no behavior change)**

Run: `./mvnw -q -Dtest=ScoringTriggerIT test`
Expected: PASS — all existing group + knockout (×2) assertions hold (knockout rounds still seed to 2).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V019__round_points_multiplier.sql backend/src/test/java/io/quiniela/api/support/V019MigrationTest.java
git commit -m "feat(scoring): per-round points_multiplier column + multiplier-aware scoring fn"
```

---

## Task 2: Trigger honours a non-default multiplier (end-to-end IT)

**Files:**
- Modify: `backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java`

This proves the trigger actually reads `round.points_multiplier` and scales stored points. We also harden `@BeforeEach` to reset multipliers so a custom value never leaks into other tests (the Testcontainer is shared across the JVM; `round` is not in the per-test cleanup).

- [ ] **Step 1: Add a multiplier reset to `@BeforeEach`**

In `ScoringTriggerIT.java`, replace the existing `resetMatchScores` method body so it also resets round multipliers to their seeded defaults:

```java
  @BeforeEach
  void resetMatchScores() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // Resets matches 1 (group-stage) and 73, 74 (knockout) before each test.
    jdbc.update("UPDATE match SET score_t1 = NULL, score_t2 = NULL WHERE id IN (1, 73, 74)");
    // Restore seeded multipliers — a test below changes one, and `round` is not
    // in AbstractIntegrationTest's per-test cleanup (shared container).
    jdbc.update("UPDATE round SET points_multiplier = CASE WHEN code = 'GROUP' THEN 1 ELSE 2 END");
  }
```

- [ ] **Step 2: Write the failing custom-multiplier test**

Add this test method to `ScoringTriggerIT` (match 73 is in round_id 2 = R32):

```java
  @Test
  void knockoutMultiplierScalesStoredPoints() {
    // Set R32 to ×3, then bet 2-1 on a R32 match and result 2-1:
    // additive base (3 + 2 + 2) = 7; with ×3 -> 21.
    new JdbcTemplate(dataSource)
        .update("UPDATE round SET points_multiplier = 3 WHERE code = 'R32'");
    var q = setupBetOnKnockoutMatch(73L, 2, 1);
    setMatchResult(73L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(21);
  }
```

- [ ] **Step 3: Run it, expect PASS**

Run: `./mvnw -q -Dtest=ScoringTriggerIT test`
Expected: PASS — the new test scores 21, and every pre-existing test still passes (the `@BeforeEach` reset keeps `×2` for them).

> If the new test FAILS with 14 (i.e. `×2`), the trigger isn't reading the column — re-check Task 1's trigger body (the `SELECT … r.points_multiplier INTO … multiplier_val` and the `multiplier_val` arg on both `score_match_for_bet` calls).

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java
git commit -m "test(scoring): trigger honours a non-default round multiplier"
```

---

## Task 3: Java mirror reads the multiplier

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`

The Java `scoreMatchForBet` currently takes a `boolean knockout` whose only effect is `× (knockout ? 2 : 1)`. Replace it with an explicit `int multiplier`, sourced from a new `MatchMeta.pointsMultiplier` joined from `round`.

- [ ] **Step 1: Add `pointsMultiplier` to the `MatchMeta` record**

In `CompareService.java`, change the `MatchMeta` record (it ends with `boolean played) {}`) to add the field:

```java
  private record MatchMeta(
      long id,
      String roundCode,
      String t1Code,
      String t1Flag,
      String t2Code,
      String t2Flag,
      String kickoffAt,
      Integer actualT1,
      Integer actualT2,
      boolean played,
      int pointsMultiplier) {}
```

- [ ] **Step 2: Join the multiplier in `fetchMatchMeta`**

In `fetchMatchMeta()`, add `r.points_multiplier` to the SELECT and the constructor. The SELECT's first line becomes:

```java
        SELECT m.id, r.code AS round_code, m.kickoff_at, m.score_t1, m.score_t2, m.played,
               r.points_multiplier AS points_multiplier,
               t1.code AS t1_code, t1.flag_emoji AS t1_flag,
               t2.code AS t2_code, t2.flag_emoji AS t2_flag
```

And the row mapper's `new MatchMeta(...)` gains a trailing arg (after `rs.getBoolean("played")`):

```java
                rs.getBoolean("played"),
                rs.getInt("points_multiplier")),
```

- [ ] **Step 3: Change `scoreMatchForBet` to take the multiplier**

Replace the method signature + return. The body's additive computation is unchanged; only the `knockout` boolean → `multiplier` int and the final line change. Also update the Javadoc's first line. New method:

```java
  /**
   * Java mirror of the DB scoring function {@code score_match_for_bet} (current shape: the V010
   * additive model with the V019 per-round {@code multiplier}). Used for the head-to-head points
   * tally previewed in the Duelos view.
   *
   * <p>Additive components (before the round multiplier): outcome bucket 3, each-team exact 2,
   * signed goal difference 1 (suppressed when exact). The result is multiplied by the round's
   * {@code points_multiplier} (1 for the group stage, configurable for knockout rounds).
   *
   * <p>SCOPE: this form omits V016's knockout-regulation-draw refinement (predicted/advanced winner
   * ids), which the H2H preview does not model — the H2H call sites pass no winner ids, exactly the
   * DB function invoked with NULL winner args. {@code ScoringDivergenceTest} pins this contract.
   */
  static int scoreMatchForBet(
      int multiplier, int betT1, int betT2, Integer actualT1, Integer actualT2) {
    if (actualT1 == null || actualT2 == null) return 0;

    boolean exact = betT1 == actualT1 && betT2 == actualT2;
    int betWinner = Integer.compare(betT1, betT2);
    int actualWinner = Integer.compare(actualT1, actualT2);

    int total = 0;
    if (betWinner == actualWinner) total += 3; // outcome bucket (includes correct draw)
    if (betT1 == actualT1) total += 2; // team-1 exact
    if (betT2 == actualT2) total += 2; // team-2 exact
    if (!exact && (betT1 - betT2) == (actualT1 - actualT2)) total += 1; // signed goal difference

    return total * multiplier;
  }
```

- [ ] **Step 4: Update the caller in `getH2H`**

Find the block that computes points (currently builds `boolean knockout = !"GROUP".equals(m.roundCode());` then calls `scoreMatchForBet(knockout, …)`). Replace it with the multiplier-driven version:

```java
      if (revealed && m.played() && m.actualT1() != null && m.actualT2() != null) {
        if (mine != null) {
          myPoints += scoreMatchForBet(m.pointsMultiplier(), mine[0], mine[1], m.actualT1(), m.actualT2());
        }
        if (theirs != null) {
          rivalPoints +=
              scoreMatchForBet(m.pointsMultiplier(), theirs[0], theirs[1], m.actualT1(), m.actualT2());
        }
      }
```

(Delete the now-unused `boolean knockout = …;` line if it sits inside this block.)

- [ ] **Step 5: Compile**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS. (`ScoringDivergenceTest` will not compile yet — it still calls the old boolean signature — but it's test code; it's fixed in Task 4. `compile` skips test sources.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/compare/CompareService.java
git commit -m "feat(compare): H2H scoring reads the per-round multiplier"
```

---

## Task 4: Pin Java ↔ DB across multipliers

**Files:**
- Modify: `backend/src/test/java/io/quiniela/api/compare/ScoringDivergenceTest.java`

The existing exhaustive test calls `CompareService.scoreMatchForBet(ko, …)` (boolean) and the DB `score_match_for_bet(ko, …)` (5-arg, multiplier defaults via COALESCE to `ko?2:1`). Adapt the Java call to the new `int multiplier` signature (mapping `ko → ko?2:1`), then add explicit-multiplier coverage.

- [ ] **Step 1: Adapt the exhaustive test to the new Java signature**

In `javaMirrorMatchesDbScoringFunctionForEveryScoreline`, change the Java call line from:

```java
          int java = CompareService.scoreMatchForBet(ko, b1, b2, a1, a2);
```
to:

```java
          // DB 5-arg call defaults multiplier to COALESCE(NULL, ko?2:1); mirror that here.
          int java = CompareService.scoreMatchForBet(ko ? 2 : 1, b1, b2, a1, a2);
```

(The DB query line `score_match_for_bet(ko, b1, b2, a1, a2)` stays — it uses the COALESCE default.)

- [ ] **Step 2: Update the unplayed-match test for the new signature**

In `unplayedMatchScoresZeroInBothImpls`, replace the two Java assertions:

```java
    assertThat(CompareService.scoreMatchForBet(false, 1, 0, null, null)).isZero();
    assertThat(CompareService.scoreMatchForBet(true, 1, 0, null, null)).isZero();
```
with multiplier-form calls:

```java
    assertThat(CompareService.scoreMatchForBet(1, 1, 0, null, null)).isZero();
    assertThat(CompareService.scoreMatchForBet(2, 1, 0, null, null)).isZero();
```

- [ ] **Step 3: Add explicit-multiplier divergence coverage**

Add this test method to `ScoringDivergenceTest`:

```java
  /**
   * Pins Java and DB across explicit multipliers (1..4) for every scoreline, passing the multiplier
   * to the DB function directly so the two scale identically.
   */
  @Test
  void javaMirrorMatchesDbScoringAcrossMultipliers() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    List<String> mismatches = new ArrayList<>();
    jdbc.query(
        """
        SELECT mult, b1, b2, a1, a2,
               score_match_for_bet(true, b1, b2, a1, a2, NULL, NULL, mult) AS pts
        FROM generate_series(0, 5) AS b1,
             generate_series(0, 5) AS b2,
             generate_series(0, 5) AS a1,
             generate_series(0, 5) AS a2,
             generate_series(1, 4) AS mult
        """,
        rs -> {
          int mult = rs.getInt("mult");
          int b1 = rs.getInt("b1");
          int b2 = rs.getInt("b2");
          int a1 = rs.getInt("a1");
          int a2 = rs.getInt("a2");
          int db = rs.getInt("pts");
          int java = CompareService.scoreMatchForBet(mult, b1, b2, a1, a2);
          if (db != java) {
            mismatches.add(
                String.format(
                    "mult=%d bet=%d-%d actual=%d-%d -> java=%d db=%d", mult, b1, b2, a1, a2, java, db));
          }
        });

    assertThat(mismatches)
        .as("Java and DB scoring must agree across multipliers; divergences:\n%s",
            String.join("\n", mismatches))
        .isEmpty();
  }
```

- [ ] **Step 4: Run the divergence test, expect PASS**

Run: `./mvnw -q -Dtest=ScoringDivergenceTest test`
Expected: PASS (3 tests — the two adapted + the new multiplier sweep).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/io/quiniela/api/compare/ScoringDivergenceTest.java
git commit -m "test(scoring): pin Java vs DB scoring across per-round multipliers"
```

---

## Task 5: Full backend verify

**Files:** none (gate only).

- [ ] **Step 1: Full build + all tests (unit + ITs)**

Run: `./mvnw -q verify`
Expected: BUILD SUCCESS. (Slow — boots Spring + a Postgres Testcontainer.) This exercises every scoring IT (`ScoringTriggerIT`, `AdvancedTeamScoringIT`, `CompareH2HIT`, `RankingServiceIT`, `ScoringDivergenceTest`, the migration tests) against the new function.

If a formatting check (Spotless) fails: `./mvnw -q spotless:apply` then re-run.
If `AdvancedTeamScoringIT` or `CompareH2HIT` fail: confirm the additive math is unchanged for `×2` (the migration only generalizes the multiplier; knockout rounds still seed to 2, so all existing expectations must hold).

- [ ] **Step 2: Commit any fixups**

```bash
git add -A && git commit -m "chore(scoring): backend verify fixups" || echo "clean"
```

---

## Self-Review (completed during planning)

- **Spec coverage (Plan 1 portion):** `points_multiplier` column on `round` + seed (Task 1) ✓; DB function gains `multiplier` with `COALESCE(multiplier, knockout?2:1)` preserving legacy (Task 1) ✓; trigger looks up + passes the round multiplier, based on the latest V017 body incl. `advanced_team_id` (Task 1) ✓; `is_knockout` retained for draw logic (Task 1) ✓; Java mirror reads the multiplier via the `round`-joined projection (Task 3) ✓; `ScoringDivergenceTest` extended across multipliers (Task 4) ✓; trigger end-to-end honour test (Task 2) ✓; `CHECK (>= 1)` (Task 1) ✓; no retroactive rescore — migration only seeds, no recompute, existing points untouched ✓. Admin API (Plan 2) and admin UI + scoring page + i18n (Plan 3) are explicitly out of this plan.
- **Placeholder scan:** none — every code step is complete.
- **Type consistency:** DB `multiplier` (8th param) ↔ trigger `multiplier_val` ↔ Java `int multiplier` / `MatchMeta.pointsMultiplier` / `r.points_multiplier`. The Java `scoreMatchForBet(int multiplier, …)` signature is used identically in Task 3 (caller) and Task 4 (tests). Migration drops the V016 7-arg overload before creating the 8-arg form (avoids the ambiguity the V016 migration itself called out).

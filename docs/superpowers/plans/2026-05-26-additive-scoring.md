# 4-Component Additive Scoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace V005's `score_match_for_bet` ladder with a 4-component additive shape (outcome 3, T1 score 2, T2 score 2, diff 1 — knockouts ×2; diff suppressed when exact).

**Architecture:** One Flyway migration `V010__additive_scoring.sql` that does `CREATE OR REPLACE FUNCTION score_match_for_bet(...)` and a one-time `UPDATE quiniela SET points = ...` recompute. The `update_players_score()` trigger is unchanged.

**Tech Stack:** PostgreSQL PL/pgSQL + Spring Boot 4 / JPA + JUnit IT via Testcontainers.

---

## Reference

- Spec: [`docs/superpowers/specs/2026-05-26-additive-scoring.md`](../specs/2026-05-26-additive-scoring.md)
- Existing V005 function: `backend/src/main/resources/db/migration/V005__quinielas_bets_scoring.sql`
- Existing IT: `backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java`

## File Structure

- **Create:** `backend/src/main/resources/db/migration/V010__additive_scoring.sql`
- **Modify:** `backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java` — rewrite the 5 existing cases with new numbers AND add 7 new cases covering the additive shape.

That's it. No other files. No Java code changes (the trigger function is unchanged). No frontend changes.

---

## Task 1: Rewrite ScoringTriggerIT for the new shape (TDD red)

**Files:**
- Modify: `backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java`

Rewrites the 5 existing tests to assert the new values AND adds 7 new tests. Existing tests will FAIL against V005 (different numbers); new tests will FAIL against V005 too. Both will PASS after V010 lands in Task 2.

- [ ] **Step 1: Replace the entire test body section with this content**

Read the current file first. Then replace EVERYTHING from `@Test void exactScoreAwardsFivePoints() {` through the last test in the file (preserving the helper methods at the top: `@BeforeEach resetMatchScores`, `setupBetOnMatch1`, `setupBetOnKnockoutMatch`, `setMatchResult`, `pointsOf`). The test methods to replace are everything from line ~55 onwards (after the helpers).

Note: at the time of this plan, the helper `setupBetOnKnockoutMatch` does NOT exist in the file yet (it lived in the now-discarded commits). Add it back as part of this step. The reset @BeforeEach should also handle match ids 73 and 74 — update it accordingly.

The complete replacement section:

```java
  @BeforeEach
  void resetMatchScores() {
    // Resets matches 1 (group-stage) and 73, 74 (knockout) before each test.
    new JdbcTemplate(dataSource)
        .update("UPDATE match SET score_t1 = NULL, score_t2 = NULL WHERE id IN (1, 73, 74)");
  }

  /** Create a user + quiniela + bet on match 1 (the first seeded group-stage match). */
  private Long setupBetOnMatch1(int betT1, int betT2) {
    var u = new User("g-sc-" + System.nanoTime(), "sc@example.com", "Sc", null, UserRole.PLAYER);
    u = users.save(u);
    var q = quinielas.save(new Quiniela(1L, u.getId()));
    bets.save(new Bet(q.getId(), 1L, betT1, betT2));
    return q.getId();
  }

  /** Create a user + quiniela + bet on a knockout match (round_id = 2, R32). */
  private Long setupBetOnKnockoutMatch(Long matchId, int betT1, int betT2) {
    var u =
        users.save(
            new User("g-sck-" + System.nanoTime(), "sck@example.com", "Sck", null, UserRole.PLAYER));
    var q = quinielas.save(new Quiniela(1L, u.getId()));
    bets.save(new Bet(q.getId(), matchId, betT1, betT2));
    return q.getId();
  }

  private void setMatchResult(Long matchId, int t1, int t2) {
    new JdbcTemplate(dataSource)
        .update("UPDATE match SET score_t1 = ?, score_t2 = ? WHERE id = ?", t1, t2, matchId);
  }

  private int pointsOf(Long qId) {
    return quinielas.findById(qId).orElseThrow().getPoints();
  }

  // ── Group-stage scoring ──────────────────────────────────────────────────

  @Test
  void exactScoreAwardsSeven() {
    // bet 2-1, actual 2-1: outcome (3) + T1 (2) + T2 (2) + diff suppressed = 7.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(7);
  }

  @Test
  void winnerAndGoalDifferenceAwardsFour() {
    // bet 2-1, actual 3-2: outcome (3) + diff (1) = 4. No team-score match.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 3, 2);
    assertThat(pointsOf(q)).isEqualTo(4);
  }

  @Test
  void winnerWithOneTeamExactAwardsFive() {
    // bet 2-1, actual 2-0: outcome (3) + T1 exact (2) = 5.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 0);
    assertThat(pointsOf(q)).isEqualTo(5);
  }

  @Test
  void winnerOnlyNoTeamScoreAwardsThree() {
    // bet 2-1, actual 5-0: outcome only (3). No T1, no T2, no diff.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 5, 0);
    assertThat(pointsOf(q)).isEqualTo(3);
  }

  @Test
  void correctDrawWithDifferentExactAwardsFour() {
    // bet 1-1, actual 0-0: outcome draw (3) + diff (1, both diffs are 0) = 4.
    var q = setupBetOnMatch1(1, 1);
    setMatchResult(1L, 0, 0);
    assertThat(pointsOf(q)).isEqualTo(4);
  }

  @Test
  void predictedDrawActualNotDrawWithOneTeamExactAwardsTwo() {
    // bet 1-1, actual 1-3: wrong outcome (draw vs team2-win), T1 exact (2) = 2.
    var q = setupBetOnMatch1(1, 1);
    setMatchResult(1L, 1, 3);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void wrongWinnerWithOneTeamExactAwardsTwo() {
    // bet 2-1, actual 0-1: wrong outcome (T1-win vs T2-win), T2 exact (2) = 2.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 0, 1);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void wrongWinnerNoPartialCreditAwardsZero() {
    // bet 2-1, actual 3-4: wrong outcome, no team-score match, wrong diff sign.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 3, 4);
    assertThat(pointsOf(q)).isEqualTo(0);
  }

  // ── Knockout scoring (×2 on every component) ─────────────────────────────

  @Test
  void exactScoreKnockoutAwardsFourteen() {
    // bet 2-1, actual 2-1 on knockout: (3+2+2) * 2 = 14.
    var q = setupBetOnKnockoutMatch(73L, 2, 1);
    setMatchResult(73L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(14);
  }

  @Test
  void winnerAndGoalDifferenceKnockoutAwardsEight() {
    // bet 2-1, actual 3-2 on knockout: (3+1) * 2 = 8.
    var q = setupBetOnKnockoutMatch(73L, 2, 1);
    setMatchResult(73L, 3, 2);
    assertThat(pointsOf(q)).isEqualTo(8);
  }

  @Test
  void winnerWithOneTeamExactKnockoutAwardsTen() {
    // bet 2-1, actual 2-0 on knockout: (3+2) * 2 = 10.
    var q = setupBetOnKnockoutMatch(73L, 2, 1);
    setMatchResult(73L, 2, 0);
    assertThat(pointsOf(q)).isEqualTo(10);
  }

  @Test
  void wrongWinnerWithOneTeamExactKnockoutAwardsFour() {
    // bet 2-1, actual 0-1 on knockout: T2 only (2) * 2 = 4.
    var q = setupBetOnKnockoutMatch(74L, 2, 1);
    setMatchResult(74L, 0, 1);
    assertThat(pointsOf(q)).isEqualTo(4);
  }
```

That is 12 test methods total: 8 group + 4 knockout.

- [ ] **Step 2: Run the IT to confirm the new + rewritten cases FAIL against V005**

From `/home/juan/Workspace/jjbustamante/quiniela/backend/`:

```bash
./mvnw -q spotless:apply
./mvnw -Dit.test=ScoringTriggerIT verify 2>&1 | tail -30
```

Expected: most tests fail with `expected: X but was: Y` patterns where Y is the V005 value and X is the new shape's value. Specifically:
- `exactScoreAwardsSeven`: expected 7, was 5
- `winnerAndGoalDifferenceAwardsFour`: expected 4, was 3
- `winnerWithOneTeamExactAwardsFive`: expected 5, was 2
- `winnerOnlyNoTeamScoreAwardsThree`: expected 3, was 2
- `correctDrawWithDifferentExactAwardsFour`: expected 4, was 2
- `predictedDrawActualNotDrawWithOneTeamExactAwardsTwo`: expected 2, was 0
- `wrongWinnerWithOneTeamExactAwardsTwo`: expected 2, was 0
- `wrongWinnerNoPartialCreditAwardsZero`: expected 0, was 0 (this one PASSES under V005 too)
- `exactScoreKnockoutAwardsFourteen`: expected 14, was 10
- `winnerAndGoalDifferenceKnockoutAwardsEight`: expected 8, was 6
- `winnerWithOneTeamExactKnockoutAwardsTen`: expected 10, was 4
- `wrongWinnerWithOneTeamExactKnockoutAwardsFour`: expected 4, was 0

So 11 failures + 1 passing under V005. The 1 passing (`wrongWinnerNoPartialCredit`) is fine — its value is 0 in both shapes.

If you see compilation errors instead of assertion failures, STOP and report BLOCKED.

- [ ] **Step 3: Commit**

```bash
cd /home/juan/Workspace/jjbustamante/quiniela
git add backend/src/test/java/io/quiniela/api/quiniela/ScoringTriggerIT.java
git commit -m "test(scoring): rewrite IT for 4-component additive shape"
```

---

## Task 2: Write the V010 migration (TDD green)

**Files:**
- Create: `backend/src/main/resources/db/migration/V010__additive_scoring.sql`

- [ ] **Step 1: Create the migration with this EXACT content**

```sql
-- V010: Replace V005's scoring ladder with a 4-component additive shape.
--
-- Components, group / knockout (×2):
--   Outcome (winner-or-draw bucket):  3 / 6
--   Team 1 exact score:               2 / 4
--   Team 2 exact score:               2 / 4
--   Goal difference (signed):         1 / 2   (suppressed when exact)
--
-- The trigger function `update_players_score` is unchanged. It calls
-- `score_match_for_bet` and the new behavior is contained there.
--
-- After replacing the function, a one-time UPDATE recomputes
-- `quinielas.points` for any quinielas whose bets cover at least one
-- played match.

CREATE OR REPLACE FUNCTION score_match_for_bet(
    is_knockout BOOLEAN,
    bet_t1 INT, bet_t2 INT,
    actual_t1 INT, actual_t2 INT
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
        outcome_pts := 3;
    END IF;

    IF bet_t1 = actual_t1 THEN t1_pts := 2; END IF;
    IF bet_t2 = actual_t2 THEN t2_pts := 2; END IF;

    IF NOT is_exact AND (bet_t1 - bet_t2) = (actual_t1 - actual_t2) THEN
        diff_pts := 1;
    END IF;

    total := outcome_pts + t1_pts + t2_pts + diff_pts;

    IF is_knockout THEN
        RETURN total * 2;
    ELSE
        RETURN total;
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Recompute cached points for quinielas with at least one played-match bet.
-- The WHERE EXISTS clause skips quinielas with no relevant bets so they
-- don't get a spurious updated_at bump.
UPDATE quiniela q
SET points = COALESCE((
        SELECT SUM(score_match_for_bet(
            (SELECT r.code <> 'GROUP' FROM round r WHERE r.id = m.round_id),
            b.score_t1, b.score_t2, m.score_t1, m.score_t2
        ))
        FROM bet b
        JOIN match m ON m.id = b.match_id
        WHERE b.quiniela_id = q.id
          AND m.played = true
    ), 0),
    updated_at = NOW()
WHERE EXISTS (
    SELECT 1 FROM bet b
    JOIN match m ON m.id = b.match_id
    WHERE b.quiniela_id = q.id
      AND m.played = true
);
```

- [ ] **Step 2: Run ScoringTriggerIT and confirm all 12 tests pass**

```bash
cd /home/juan/Workspace/jjbustamante/quiniela/backend
./mvnw -Dit.test=ScoringTriggerIT verify 2>&1 | grep -E "Tests run|FAIL|BUILD" | tail -10
```

Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

If any tests fail, STOP and report the failures. The values in the function MUST produce the spec's worked-example outputs.

- [ ] **Step 3: Run the full backend verify**

```bash
./mvnw verify 2>&1 | grep -E "Tests run:|BUILD" | tail -10
```

Expected: `BUILD SUCCESS`. All ITs across the suite pass — in particular `AdminResultsControllerIT` (which exercises the trigger end-to-end) must still pass since the trigger contract didn't change.

- [ ] **Step 4: Commit**

```bash
cd /home/juan/Workspace/jjbustamante/quiniela
git add backend/src/main/resources/db/migration/V010__additive_scoring.sql
git commit -m "feat(scoring): V010 — 4-component additive scoring (replaces 5/3/2/2/0 ladder)"
```

---

## Task 3: Apply to dev DB + sanity check

**Files:** none.

- [ ] **Step 1: Restart the backend so Flyway picks up V010**

```bash
cd /home/juan/Workspace/jjbustamante/quiniela
lsof -ti:8080 2>/dev/null | xargs -r kill ; sleep 2 && bin/dev-backend.sh > /tmp/quiniela-backend.log 2>&1 &
disown
until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do sleep 2; done
echo "backend ready"
```

- [ ] **Step 2: Verify Flyway applied V010**

```bash
docker exec -i quiniela-postgres psql -U quiniela -d quiniela -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"
```

Expected top row: `010 | additive scoring | t`.

- [ ] **Step 3: Sanity-check Juan's existing bet**

In the local DB, Juan has 72 bets and the MEX-RSA 3-0 match is the only played one. Compute his expected points by hand using the new shape, then query:

```bash
docker exec -i quiniela-postgres psql -U quiniela -d quiniela -c "
SELECT u.display_name, q.points,
       (SELECT b.score_t1 || '-' || b.score_t2 FROM bet b WHERE b.quiniela_id = q.id AND b.match_id = 537327) AS mex_rsa_bet
FROM quiniela q JOIN users u ON u.id = q.user_id;"
```

Apply the formula to the bet shown:
- If bet is `3-0` exact → 7 pts
- If bet is `(>0)-0` with right T2 score (0) → outcome (3) + T2 (2) = 5
- If bet is `(>0)-(0+)` with right T1 score (3) → outcome (3) + T1 (2) = 5 (no diff because not exact, unless diff also matches)
- If bet is some other team1-wins → outcome only = 3
- If bet predicted MEX losing → 0 or partial credit on team-scores only

Verify the value matches the formula. If it doesn't, the recompute is wrong — STOP and investigate.

---

## Task 4: Push + watch CI

- [ ] **Step 1: Push to origin/master**

```bash
cd /home/juan/Workspace/jjbustamante/quiniela
git push 2>&1 | tail -3
```

Expected: `master -> master`.

- [ ] **Step 2: Wait for CI**

```bash
gh run list --limit 2 --json status,conclusion,workflowName,headSha
```

Both backend-ci and frontend-ci should land green. The backend deploy applies V010 to Cloud SQL at startup.

---

## Self-Review

- [x] Spec coverage: every section in the spec is covered.
  - In-scope: V010 migration → Task 2. Test rewrite → Task 1. Function pseudocode → Task 2 SQL.
  - Out-of-scope: not implemented (correctly).
  - Acceptance: Task 2 Steps 2-3 satisfy the IT + verify gates.
- [x] Placeholder scan: no TBDs, all code blocks complete.
- [x] Type consistency: function name `score_match_for_bet` matches V005 + recompute query + spec. Parameter order matches V005 (BOOLEAN, INT, INT, INT, INT).
- [x] Helper names: `setupBetOnMatch1`, `setupBetOnKnockoutMatch`, `resetMatchScores` consistent with what was in the discarded commits — the test file is being rebuilt from scratch as part of Task 1 Step 1.
- [x] Worked-example check: each IT case maps to a row in the spec's worked-example table.

# Player Scorecard — Plan 1 of 2: Backend (ScoreBreakdown + scorecard API)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `GET /api/ranking/{userId}/scorecard` endpoint returning a player's points grouped by stage, with a per-match point breakdown (outcome / team-exact / goal-diff × multiplier) computed by a new `ScoreBreakdown` that is pinned to the live DB scoring function.

**Architecture:** A pure `ScoreBreakdown.of(...)` replicates the V016/V019 `score_match_for_bet` components (and is pinned against the DB so its total can't drift). A `ScorecardService` loads the user's played, bet-on matches, computes a breakdown per match, and groups by round. A thin controller exposes it.

**Tech Stack:** Spring Boot 4 + Java 25 + Maven + Postgres; Testcontainers ITs (Docker available). Run from `.worktrees/player-scorecard/backend`.

**This is Plan 1 of 2.** Plan 2 = the frontend `/ranking/[userId]` scorecard page + tappable ranking rows.

---

## File Structure

**Create**
- `backend/src/main/java/io/quiniela/api/scoring/ScoreBreakdown.java` — pure component breakdown
- `backend/src/main/java/io/quiniela/api/ranking/ScorecardService.java` — builds the scorecard
- `backend/src/main/java/io/quiniela/api/ranking/ScorecardView.java` — response records
- `backend/src/main/java/io/quiniela/api/ranking/ScorecardController.java` — `GET /{userId}/scorecard`
- `backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownTest.java` — unit
- `backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownDivergenceIT.java` — pin to DB
- `backend/src/test/java/io/quiniela/api/ranking/ScorecardControllerIT.java` — endpoint IT

---

## Task 1: `ScoreBreakdown` pure helper

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/scoring/ScoreBreakdown.java`
- Create: `backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownTest.java`:

```java
package io.quiniela.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScoreBreakdownTest {

  @Test
  void exactGroupScore() {
    // bet 2-1, actual 2-1, group (mult 1): outcome 3 + t1 2 + t2 2 + diff 0 = 7.
    var b = ScoreBreakdown.of(false, 2, 1, 2, 1, null, null, 1);
    assertThat(b.outcome()).isEqualTo(3);
    assertThat(b.team1Exact()).isEqualTo(2);
    assertThat(b.team2Exact()).isEqualTo(2);
    assertThat(b.goalDiff()).isZero();
    assertThat(b.multiplier()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(7);
  }

  @Test
  void winnerAndGoalDifference() {
    // bet 2-1, actual 3-2: outcome 3 + diff 1 = 4 (no team exact).
    var b = ScoreBreakdown.of(false, 2, 1, 3, 2, null, null, 1);
    assertThat(b.outcome()).isEqualTo(3);
    assertThat(b.team1Exact()).isZero();
    assertThat(b.team2Exact()).isZero();
    assertThat(b.goalDiff()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(4);
  }

  @Test
  void knockoutMultiplierScalesTotal() {
    // bet 2-1, actual 2-1 knockout mult 3: (3+2+2) * 3 = 21.
    assertThat(ScoreBreakdown.of(true, 2, 1, 2, 1, null, null, 3).total()).isEqualTo(21);
  }

  @Test
  void knockoutDrawAwardsOutcomeWhenAdvancingTeamMatches() {
    // bet 1-1, actual 0-0 knockout, predicted advancer == actual advancer (5):
    // outcome 3 + diff 1 = 4, × mult 2 = 8.
    var b = ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 5L, 2);
    assertThat(b.outcome()).isEqualTo(3);
    assertThat(b.goalDiff()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(8);
  }

  @Test
  void knockoutDrawZeroOutcomeWhenAdvancingTeamWrong() {
    // same but predicted (5) != actual advancer (7): outcome 0 + diff 1 = 1, × 2 = 2.
    var b = ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 7L, 2);
    assertThat(b.outcome()).isZero();
    assertThat(b.goalDiff()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(2);
  }

  @Test
  void unplayedMatchIsAllZero() {
    var b = ScoreBreakdown.of(false, 2, 1, null, null, null, null, 1);
    assertThat(b.outcome()).isZero();
    assertThat(b.team1Exact()).isZero();
    assertThat(b.team2Exact()).isZero();
    assertThat(b.goalDiff()).isZero();
    assertThat(b.total()).isZero();
  }
}
```

- [ ] **Step 2: Run it, expect FAIL** (module missing)

Run: `./mvnw -q -Dtest=ScoreBreakdownTest test`
Expected: FAIL — `ScoreBreakdown` does not exist.

- [ ] **Step 3: Implement**

Create `backend/src/main/java/io/quiniela/api/scoring/ScoreBreakdown.java`:

```java
package io.quiniela.api.scoring;

/**
 * Additive point components for one bet-vs-result, matching the DB
 * {@code score_match_for_bet} (V016/V019) exactly:
 *
 * <ul>
 *   <li>{@code outcome}: 3 when the predicted winner/draw matches (with the
 *       knockout-regulation-draw refinement: a predicted draw scores via the
 *       advancing team, not the scoreline);
 *   <li>{@code team1Exact} / {@code team2Exact}: 2 each for an exact team score;
 *   <li>{@code goalDiff}: 1 for the right signed goal difference (suppressed on an
 *       exact score).
 * </ul>
 *
 * {@code total = (outcome + team1Exact + team2Exact + goalDiff) * multiplier}.
 * {@code ScoreBreakdownDivergenceIT} pins {@code total()} against the live DB
 * function so the breakdown always sums to a player's real points.
 */
public record ScoreBreakdown(
    int outcome, int team1Exact, int team2Exact, int goalDiff, int multiplier, int total) {

  public static ScoreBreakdown of(
      boolean knockout,
      int betT1,
      int betT2,
      Integer actualT1,
      Integer actualT2,
      Long predictedWinnerId,
      Long advancedTeamId,
      int multiplier) {
    if (actualT1 == null || actualT2 == null) {
      return new ScoreBreakdown(0, 0, 0, 0, multiplier, 0);
    }

    boolean exact = betT1 == actualT1 && betT2 == actualT2;
    int betWinner = Integer.compare(betT1, betT2);
    int actualWinner = Integer.compare(actualT1, actualT2);

    int outcome = 0;
    if (betWinner == actualWinner) {
      if (knockout
          && betWinner == 0
          && actualWinner == 0
          && predictedWinnerId != null
          && advancedTeamId != null) {
        outcome = predictedWinnerId.equals(advancedTeamId) ? 3 : 0;
      } else {
        outcome = 3;
      }
    }

    int team1Exact = betT1 == actualT1 ? 2 : 0;
    int team2Exact = betT2 == actualT2 ? 2 : 0;
    int goalDiff = (!exact && (betT1 - betT2) == (actualT1 - actualT2)) ? 1 : 0;

    int total = (outcome + team1Exact + team2Exact + goalDiff) * multiplier;
    return new ScoreBreakdown(outcome, team1Exact, team2Exact, goalDiff, multiplier, total);
  }
}
```

- [ ] **Step 4: Run it, expect PASS (6 tests)**

Run: `./mvnw -q -Dtest=ScoreBreakdownTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/scoring/ScoreBreakdown.java backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownTest.java
git commit -m "feat(scoring): ScoreBreakdown — additive point components"
```

---

## Task 2: Pin `ScoreBreakdown` to the DB function

**Files:**
- Create: `backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownDivergenceIT.java`

Mirrors `compare/ScoringDivergenceTest`: feed identical inputs to `ScoreBreakdown.of(...).total()` and the live DB `score_match_for_bet(...)` and assert agreement.

- [ ] **Step 1: Write the IT**

Create `backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownDivergenceIT.java`:

```java
package io.quiniela.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins {@link ScoreBreakdown#of}'s total against the live Postgres
 * {@code score_match_for_bet} across every 0–5 scoreline and multipliers 1–3
 * (group + knockout), plus explicit knockout-regulation-draw cases. Guarantees a
 * player's per-component breakdown always sums to their real, stored points.
 */
class ScoreBreakdownDivergenceIT extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void breakdownTotalMatchesDbAcrossScorelinesAndMultipliers() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<String> mismatches = new ArrayList<>();
    jdbc.query(
        """
        SELECT ko, b1, b2, a1, a2, mult,
               score_match_for_bet(ko, b1, b2, a1, a2, NULL, NULL, mult) AS pts
        FROM generate_series(0, 5) AS b1,
             generate_series(0, 5) AS b2,
             generate_series(0, 5) AS a1,
             generate_series(0, 5) AS a2,
             generate_series(1, 3) AS mult,
             (VALUES (false), (true)) AS k(ko)
        """,
        rs -> {
          boolean ko = rs.getBoolean("ko");
          int b1 = rs.getInt("b1");
          int b2 = rs.getInt("b2");
          int a1 = rs.getInt("a1");
          int a2 = rs.getInt("a2");
          int mult = rs.getInt("mult");
          int db = rs.getInt("pts");
          int java = ScoreBreakdown.of(ko, b1, b2, a1, a2, null, null, mult).total();
          if (db != java) {
            mismatches.add(
                String.format(
                    "ko=%s bet=%d-%d actual=%d-%d mult=%d -> java=%d db=%d",
                    ko, b1, b2, a1, a2, mult, java, db));
          }
        });
    assertThat(mismatches)
        .as("ScoreBreakdown total must match the DB function; divergences:\n%s",
            String.join("\n", mismatches))
        .isEmpty();
  }

  @Test
  void knockoutRegulationDrawMatchesDb() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // Predicted advancer == actual advancer (5).
    Integer dbMatch =
        jdbc.queryForObject(
            "SELECT score_match_for_bet(true, 1, 1, 0, 0, 5, 5, 2)", Integer.class);
    assertThat(dbMatch).isEqualTo(ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 5L, 2).total());
    // Predicted advancer (5) != actual advancer (7).
    Integer dbWrong =
        jdbc.queryForObject(
            "SELECT score_match_for_bet(true, 1, 1, 0, 0, 5, 7, 2)", Integer.class);
    assertThat(dbWrong).isEqualTo(ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 7L, 2).total());
  }
}
```

- [ ] **Step 2: Run it, expect PASS**

Run: `./mvnw -q -Dtest=ScoreBreakdownDivergenceIT test`
Expected: PASS — if it FAILS, `ScoreBreakdown.of` diverges from the DB; fix `ScoreBreakdown` (not the test) until they agree.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/io/quiniela/api/scoring/ScoreBreakdownDivergenceIT.java
git commit -m "test(scoring): pin ScoreBreakdown total to the DB scoring function"
```

---

## Task 3: `ScorecardView` records + `ScorecardService`

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/ranking/ScorecardView.java`
- Create: `backend/src/main/java/io/quiniela/api/ranking/ScorecardService.java`

- [ ] **Step 1: Create the response records**

Create `backend/src/main/java/io/quiniela/api/ranking/ScorecardView.java`:

```java
package io.quiniela.api.ranking;

import io.quiniela.api.scoring.ScoreBreakdown;
import java.util.List;

/** A player's points broken down by stage and, within a stage, by match. */
public record ScorecardView(
    long userId, String displayName, int totalPoints, List<StageScore> stages) {

  public record TeamRef(String code, String name, String flag) {}

  public record MatchScore(
      long matchId,
      TeamRef team1,
      TeamRef team2,
      String kickoffAt,
      Integer betScoreT1,
      Integer betScoreT2,
      Integer actualScoreT1,
      Integer actualScoreT2,
      ScoreBreakdown breakdown) {}

  public record StageScore(
      String roundCode, String roundName, int points, List<MatchScore> matches) {}
}
```

- [ ] **Step 2: Create the service**

Create `backend/src/main/java/io/quiniela/api/ranking/ScorecardService.java`:

```java
package io.quiniela.api.ranking;

import io.quiniela.api.ranking.ScorecardView.MatchScore;
import io.quiniela.api.ranking.ScorecardView.StageScore;
import io.quiniela.api.ranking.ScorecardView.TeamRef;
import io.quiniela.api.scoring.ScoreBreakdown;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds a player's scorecard: their played, bet-on matches, each with a
 * {@link ScoreBreakdown}, grouped by stage (round sequence order) with per-stage
 * totals. Any player is viewable — only played matches (already revealed) appear,
 * so nothing private is exposed.
 */
@Service
public class ScorecardService {

  private static final Long DEFAULT_POOL_ID = 1L;

  private final JdbcTemplate jdbc;

  public ScorecardService(DataSource ds) {
    this.jdbc = new JdbcTemplate(ds);
  }

  // Accumulator for one round while we fold the result rows.
  private static final class StageAcc {
    final String roundName;
    final List<MatchScore> matches = new ArrayList<>();
    int points = 0;

    StageAcc(String roundName) {
      this.roundName = roundName;
    }
  }

  @Transactional(readOnly = true)
  public ScorecardView getScorecard(Long userId) {
    var head =
        jdbc.query(
            "SELECT u.display_name, q.points FROM quiniela q JOIN users u ON u.id = q.user_id "
                + "WHERE q.pool_id = ? AND q.user_id = ?",
            rs -> rs.next() ? new String[] {rs.getString(1), String.valueOf(rs.getInt(2))} : null,
            DEFAULT_POOL_ID,
            userId);
    if (head == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No scorecard for this user");
    }
    String displayName = head[0];
    int totalPoints = Integer.parseInt(head[1]);

    // Played, bet-on matches in round-sequence then kickoff order.
    Map<String, StageAcc> byRound = new LinkedHashMap<>();
    jdbc.query(
        """
        SELECT r.code AS round_code, r.name AS round_name, r.points_multiplier AS mult,
               m.id AS match_id, m.kickoff_at AS kickoff_at,
               t1.code AS t1_code, t1.name AS t1_name, t1.flag_emoji AS t1_flag,
               t2.code AS t2_code, t2.name AS t2_name, t2.flag_emoji AS t2_flag,
               b.score_t1 AS bet_t1, b.score_t2 AS bet_t2, b.predicted_winner_id AS pred_winner,
               m.score_t1 AS act_t1, m.score_t2 AS act_t2, m.advanced_team_id AS adv_team
        FROM bet b
        JOIN quiniela q ON q.id = b.quiniela_id
        JOIN match m ON m.id = b.match_id
        JOIN round r ON r.id = m.round_id
        LEFT JOIN team t1 ON t1.id = m.team_1_id
        LEFT JOIN team t2 ON t2.id = m.team_2_id
        WHERE q.pool_id = ? AND q.user_id = ? AND m.played = TRUE
        ORDER BY r.sequence ASC, m.kickoff_at ASC, m.id ASC
        """,
        rs -> {
          String roundCode = rs.getString("round_code");
          boolean knockout = !"GROUP".equals(roundCode);
          int mult = rs.getInt("mult");
          int betT1 = rs.getInt("bet_t1");
          int betT2 = rs.getInt("bet_t2");
          Integer actT1 = (Integer) rs.getObject("act_t1");
          Integer actT2 = (Integer) rs.getObject("act_t2");
          Long predWinner = (Long) rs.getObject("pred_winner");
          Long advTeam = (Long) rs.getObject("adv_team");

          ScoreBreakdown breakdown =
              ScoreBreakdown.of(knockout, betT1, betT2, actT1, actT2, predWinner, advTeam, mult);

          MatchScore ms =
              new MatchScore(
                  rs.getLong("match_id"),
                  new TeamRef(rs.getString("t1_code"), rs.getString("t1_name"), rs.getString("t1_flag")),
                  new TeamRef(rs.getString("t2_code"), rs.getString("t2_name"), rs.getString("t2_flag")),
                  rs.getTimestamp("kickoff_at").toInstant().toString(),
                  betT1,
                  betT2,
                  actT1,
                  actT2,
                  breakdown);

          StageAcc acc =
              byRound.computeIfAbsent(roundCode, k -> new StageAcc(rs.getString("round_name")));
          acc.matches.add(ms);
          acc.points += breakdown.total();
        },
        DEFAULT_POOL_ID,
        userId);

    List<StageScore> stages = new ArrayList<>();
    for (var e : byRound.entrySet()) {
      stages.add(new StageScore(e.getKey(), e.getValue().roundName, e.getValue().points, e.getValue().matches));
    }
    return new ScorecardView(userId, displayName, totalPoints, stages);
  }
}
```

- [ ] **Step 3: Compile**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/ranking/ScorecardView.java backend/src/main/java/io/quiniela/api/ranking/ScorecardService.java
git commit -m "feat(ranking): ScorecardService — points by stage + per-match breakdown"
```

---

## Task 4: `ScorecardController` endpoint + IT

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/ranking/ScorecardController.java`
- Create: `backend/src/test/java/io/quiniela/api/ranking/ScorecardControllerIT.java`

- [ ] **Step 1: Write the failing IT**

Create `backend/src/test/java/io/quiniela/api/ranking/ScorecardControllerIT.java`:

```java
package io.quiniela.api.ranking;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class ScorecardControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;
  User viewer;
  User target;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);
    viewer = save("sc-viewer");
    target = save("sc-target");
    // target's quiniela with a bet on match 1 (group) — set match 1 to 2-1, target bet 2-1 = 7.
    jdbc.update("INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?)", target.getId());
    Long qid = jdbc.queryForObject("SELECT id FROM quiniela WHERE user_id = ?", Long.class, target.getId());
    jdbc.update("INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2) VALUES (?,1,2,1)", qid);
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = 1");
  }

  @AfterEach
  void reset() {
    jdbc.update("UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE WHERE id = 1");
  }

  private User save(String slug) {
    var u = new User("g-" + slug, slug + "@example.com", slug.toUpperCase(), null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    return users.save(u);
  }

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/ranking/" + target.getId() + "/scorecard")).andExpect(status().isUnauthorized());
  }

  @Test
  void returnsAnyPlayersStageBreakdown() throws Exception {
    String token = jwt.issue(viewer);
    mockMvc
        .perform(get("/api/ranking/" + target.getId() + "/scorecard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(target.getId()))
        .andExpect(jsonPath("$.stages[?(@.roundCode == 'GROUP')].points").value(7))
        .andExpect(jsonPath("$.stages[?(@.roundCode == 'GROUP')].matches[0].breakdown.outcome").value(3))
        .andExpect(jsonPath("$.stages[?(@.roundCode == 'GROUP')].matches[0].breakdown.total").value(7));
  }

  @Test
  void notFoundForAUserWithoutAQuiniela() throws Exception {
    String token = jwt.issue(viewer);
    // viewer has no quiniela in this test.
    mockMvc
        .perform(get("/api/ranking/" + viewer.getId() + "/scorecard").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }
}
```

- [ ] **Step 2: Run it, expect FAIL** (no endpoint → 404 on the auth'd call too)

Run: `./mvnw -q -Dtest=ScorecardControllerIT test`
Expected: FAIL — `returnsAnyPlayersStageBreakdown` gets 404 (no controller).

- [ ] **Step 3: Create the controller**

Create `backend/src/main/java/io/quiniela/api/ranking/ScorecardController.java`:

```java
package io.quiniela.api.ranking;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A player's points by stage + per-match breakdown. Any pool member is viewable. */
@RestController
@RequestMapping("/api/ranking")
public class ScorecardController {

  private final ScorecardService service;

  public ScorecardController(ScorecardService service) {
    this.service = service;
  }

  @GetMapping("/{userId}/scorecard")
  public ResponseEntity<ScorecardView> scorecard(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getScorecard(userId));
  }
}
```

- [ ] **Step 4: Run it, expect PASS (3 tests)**

Run: `./mvnw -q -Dtest=ScorecardControllerIT test`
Expected: PASS. (The `ResponseStatusException(NOT_FOUND)` from the service maps to 404 for the no-quiniela case.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/ranking/ScorecardController.java backend/src/test/java/io/quiniela/api/ranking/ScorecardControllerIT.java
git commit -m "feat(ranking): GET /api/ranking/{userId}/scorecard"
```

---

## Task 5: Full backend verify

**Files:** none (gate only).

- [ ] **Step 1:** `./mvnw -q verify` → BUILD SUCCESS (runs the full suite incl. the new unit + ITs). If Spotless fails: `./mvnw -q spotless:apply` then re-run.
- [ ] **Step 2:** `git add -A && git commit -m "chore(scoring): backend verify fixups" || echo clean`

---

## Self-Review (completed during planning)

- **Spec coverage (Plan 1 portion):** `ScoreBreakdown.of` replicating V016/V019 incl. knockout-draw refinement (Task 1) ✓; pinned to the DB across scorelines/multipliers + explicit draw cases (Task 2) ✓; scorecard service — played, bet-on matches, breakdown per match, grouped by round sequence with per-stage totals (Task 3) ✓; `GET /api/ranking/{userId}/scorecard`, any player, 404 for non-member, 401 unauth (Task 4) ✓; played-only (the query's `m.played = TRUE`) ✓. Frontend is Plan 2.
- **Placeholder scan:** none — full code in every step.
- **Type consistency:** `ScoreBreakdown.of(boolean,int,int,Integer,Integer,Long,Long,int)` defined in Task 1, called identically in Tasks 2 + 3. `ScorecardView`/`StageScore`/`MatchScore`/`TeamRef` defined in Task 3, produced by the service + asserted by the IT (Task 4) with matching JSON field names (`stages`, `roundCode`, `points`, `matches`, `breakdown.outcome`, `breakdown.total`). The DB scoring call in Task 2 uses the V019 8-arg signature `score_match_for_bet(ko,b1,b2,a1,a2,NULL,NULL,mult)`.

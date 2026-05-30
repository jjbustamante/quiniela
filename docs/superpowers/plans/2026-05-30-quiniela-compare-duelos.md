# Compare ("Duelos") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `/compare` placeholder with a real "Duelos" screen that compares a player's quiniela against the pool in two modes — 1 vs 1 (head-to-head diff) and Grupo (consensus) — with picks of other players hidden until their round locks.

**Architecture:** Two read-only backend endpoints (`/api/compare/group`, `/api/compare/h2h`) compute everything server-side so unlocked picks never reach the browser. Visibility reuses the tournament's existing two-deadline lock model (group-stage deadline + knockout deadline) via a new shared helper on `LockClock`. The frontend is a server-rendered Next.js page whose mode + rival live in the URL, with the rival list reusing the existing `/api/ranking` endpoint.

**Tech Stack:** Spring Boot 4 + Java 25, `JdbcTemplate` (mirrors `RankingService`), Testcontainers Postgres + MockMvc for ITs; Next.js 16 server components + TypeScript, next-intl, Vitest + RTL.

**Spec:** `docs/superpowers/specs/2026-05-30-quiniela-compare-duelos-design.md`

---

## Reference facts (verified against the codebase)

- **Lock model is two deadlines**, not per-round: group-stage matches lock at `tournament.group_stage_deadline`; knockout matches lock at `tournament.knockout_deadline`. This is exactly what `BracketService.saveBet` already enforces. "Per-round after lock" in the spec maps onto this two-deadline model.
- **Round code** `'GROUP'` identifies group-stage; any other `round.code` is knockout (see `V005` trigger).
- **Single pool / tournament**: `pool_id = 1`, `tournament_id = 1` (constants used across services).
- **Tables/columns:**
  - `quiniela(id, pool_id, user_id, points)` — UNIQUE `(pool_id, user_id)`.
  - `bet(quiniela_id, match_id, score_t1, score_t2)` — PK `(quiniela_id, match_id)`.
  - `match(id, tournament_id, round_id, group_code, team_1_id, team_2_id, score_t1, score_t2, played, kickoff_at)`.
  - `team(id, code, name, group_code, flag_emoji)`; `round(id, code, name, sequence)`; `users(id, display_name)`.
- **Auth in controllers**: `@AuthenticationPrincipal Jwt jwt`, `Long.parseLong(jwt.getSubject())` is the userId; return `401` when `jwt == null`.
- **Test harness**: extend `io.quiniela.api.support.AbstractIntegrationTest` (Testcontainers singleton Postgres, Flyway-migrated, wipes `bet`/`quiniela`/`users` before each test). Manipulate lock state with `jdbc.update("UPDATE tournament SET group_stage_deadline=…, knockout_deadline=… WHERE id=1")` and restore in `@AfterEach` (pattern from `BracketLockUiIT`). Seeded data: 104 matches, 12 groups.
- **Run backend unit test:** from `backend/`: `./mvnw -q test -Dtest=<ClassName>`
- **Run backend IT:** from `backend/`: `./mvnw -q verify -Dit.test=<ClassName> -DfailIfNoTests=false`
- **Run frontend tests:** from `frontend/`: `pnpm test` (or `pnpm vitest run <path>`)

## File structure

**Backend (new unless noted):**
- `backend/src/main/java/io/quiniela/api/bracket/LockClock.java` — **modify**: add reveal-helper methods.
- `backend/src/main/java/io/quiniela/api/compare/CompareService.java` — consensus + h2h logic, DTOs as records.
- `backend/src/main/java/io/quiniela/api/compare/CompareController.java` — two GET endpoints.
- `backend/src/test/java/io/quiniela/api/bracket/LockClockRevealTest.java` — unit test (no DB).
- `backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java`
- `backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java`

**Frontend (new unless noted):**
- `frontend/lib/api/compare.ts` — types + fetchers.
- `frontend/app/compare/page.tsx` — **modify**: replace placeholder.
- `frontend/components/compare/CompareModeToggle.tsx` (client)
- `frontend/components/compare/RivalPicker.tsx` (client)
- `frontend/components/compare/GroupConsensus.tsx`
- `frontend/components/compare/H2HCompare.tsx`
- `frontend/messages/es-CO.json` + `frontend/messages/en.json` — **modify**: add `compare.*`.
- `frontend/components/compare/CompareModeToggle.test.tsx`, `frontend/components/compare/GroupConsensus.test.tsx`

---

## Task 1: Reveal helpers on LockClock

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/bracket/LockClock.java`
- Test: `backend/src/test/java/io/quiniela/api/bracket/LockClockRevealTest.java`

- [ ] **Step 1: Write the failing unit test**

```java
package io.quiniela.api.bracket;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bracket.LockClock.TournamentDeadlines;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LockClockRevealTest {

  private final LockClock clock = new LockClock(null); // helpers don't touch the DataSource

  private static final Instant PAST = Instant.parse("2020-01-01T00:00:00Z");
  private static final Instant FUTURE = Instant.parse("2999-01-01T00:00:00Z");
  private final Instant now = Instant.parse("2026-06-15T00:00:00Z");

  @Test
  void groupRevealableOnlyAfterGroupDeadline() {
    assertThat(clock.isMatchRevealable(now, new TournamentDeadlines(PAST, FUTURE), "GROUP")).isTrue();
    assertThat(clock.isMatchRevealable(now, new TournamentDeadlines(FUTURE, FUTURE), "GROUP")).isFalse();
  }

  @Test
  void knockoutRevealableOnlyAfterKnockoutDeadline() {
    assertThat(clock.isMatchRevealable(now, new TournamentDeadlines(PAST, PAST), "R32")).isTrue();
    assertThat(clock.isMatchRevealable(now, new TournamentDeadlines(PAST, FUTURE), "R32")).isFalse();
  }

  @Test
  void nullDeadlineIsNeverRevealable() {
    assertThat(clock.isMatchRevealable(now, new TournamentDeadlines(null, null), "GROUP")).isFalse();
    assertThat(clock.isMatchRevealable(now, new TournamentDeadlines(null, null), "FINAL")).isFalse();
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=LockClockRevealTest` (from `backend/`)
Expected: FAIL — `isMatchRevealable` does not exist (compile error).

- [ ] **Step 3: Add the helper methods to LockClock**

Add these methods inside the `LockClock` class (after `fetchTournamentDeadlines`):

```java
  /** True once the group-stage deadline has passed. */
  public boolean isGroupRevealable(java.time.Instant now, TournamentDeadlines d) {
    return d.groupStageDeadline() != null && now.isAfter(d.groupStageDeadline());
  }

  /** True once the knockout deadline has passed. */
  public boolean isKnockoutRevealable(java.time.Instant now, TournamentDeadlines d) {
    return d.knockoutDeadline() != null && now.isAfter(d.knockoutDeadline());
  }

  /**
   * Whether a match's picks may be revealed to OTHER players: group-stage matches
   * (round code "GROUP") gate on the group deadline; everything else on the knockout
   * deadline. A player's own picks are always visible to themselves regardless.
   */
  public boolean isMatchRevealable(java.time.Instant now, TournamentDeadlines d, String roundCode) {
    return "GROUP".equals(roundCode) ? isGroupRevealable(now, d) : isKnockoutRevealable(now, d);
  }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=LockClockRevealTest` (from `backend/`)
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/bracket/LockClock.java \
        backend/src/test/java/io/quiniela/api/bracket/LockClockRevealTest.java
git commit -m "feat(compare): add reveal-visibility helpers to LockClock"
```

---

## Task 2: Group consensus endpoint

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`
- Create: `backend/src/main/java/io/quiniela/api/compare/CompareController.java`
- Test: `backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
package io.quiniela.api.compare;

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

class CompareGroupConsensusIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void restoreDeadlines() {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC',"
            + " knockout_deadline = TIMESTAMPTZ '2026-06-28 17:00 UTC' WHERE id = 1");
  }

  /** Create a user + their quiniela, and place a bet on match 1. Returns the JWT. */
  private String userWithBetOnMatch1(String slug, int t1, int t2) {
    var u = new User("g-" + slug, slug + "@example.com", slug.toUpperCase(), null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    u = users.save(u);
    jdbc.update("INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?)", u.getId());
    Long qid = jdbc.queryForObject("SELECT id FROM quiniela WHERE user_id = ?", Long.class, u.getId());
    jdbc.update("INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2) VALUES (?,1,?,?)", qid, t1, t2);
    return jwt.issue(u);
  }

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/compare/group")).andExpect(status().isUnauthorized());
  }

  @Test
  void hidesDistributionBeforeGroupLock() throws Exception {
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() + INTERVAL '7 days' WHERE id = 1");
    String me = userWithBetOnMatch1("grp-pre-me", 2, 1);
    userWithBetOnMatch1("grp-pre-rival", 0, 0);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        // match 1 is group-stage; before lock it is not revealed and exposes no distribution
        .andExpect(jsonPath("$.matches[0].matchId").value(1))
        .andExpect(jsonPath("$.matches[0].revealed").value(false))
        .andExpect(jsonPath("$.matches[0].distribution.length()").value(0))
        // my own pick is still echoed back to me
        .andExpect(jsonPath("$.matches[0].myScoreT1").value(2))
        .andExpect(jsonPath("$.matches[0].myScoreT2").value(1));
  }

  @Test
  void revealsConsensusAfterGroupLock() throws Exception {
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    String me = userWithBetOnMatch1("grp-post-me", 2, 1);
    userWithBetOnMatch1("grp-post-r1", 2, 1); // agrees with me -> 2-1 has count 2 (majority)
    userWithBetOnMatch1("grp-post-r2", 0, 0);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matches[0].revealed").value(true))
        .andExpect(jsonPath("$.matches[0].totalPicks").value(3))
        // 2-1 picked twice is the modal score, and that's my pick -> majority true, rebel false
        .andExpect(jsonPath("$.matches[0].majority").value(true))
        .andExpect(jsonPath("$.matches[0].rebel").value(false));
  }

  @Test
  void flagsRebelWhenOnlyPickerOfAScore() throws Exception {
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    String me = userWithBetOnMatch1("grp-rebel-me", 4, 4); // unique score
    userWithBetOnMatch1("grp-rebel-r1", 1, 0);
    userWithBetOnMatch1("grp-rebel-r2", 1, 0);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matches[0].majority").value(false))
        .andExpect(jsonPath("$.matches[0].rebel").value(true));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q verify -Dit.test=CompareGroupConsensusIT -DfailIfNoTests=false` (from `backend/`)
Expected: FAIL — no `/api/compare/group` mapping (404), and the classes don't compile yet.

- [ ] **Step 3: Create CompareService with the group-consensus logic**

```java
package io.quiniela.api.compare;

import io.quiniela.api.bracket.LockClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompareService {

  private static final Long POOL_ID = 1L;
  private static final Long TOURNAMENT_ID = 1L;

  private final JdbcTemplate jdbc;
  private final LockClock lockClock;

  public CompareService(DataSource ds, LockClock lockClock) {
    this.jdbc = new JdbcTemplate(ds);
    this.lockClock = lockClock;
  }

  // ── DTOs ────────────────────────────────────────────────────────────────
  public record ScoreCount(int scoreT1, int scoreT2, int count) {}

  public record MatchConsensus(
      Long matchId,
      String roundCode,
      String team1Code,
      String team1Flag,
      String team2Code,
      String team2Flag,
      String kickoffAt,
      Integer actualScoreT1,
      Integer actualScoreT2,
      boolean played,
      boolean revealed,
      Integer myScoreT1,
      Integer myScoreT2,
      List<ScoreCount> distribution,
      int totalPicks,
      boolean majority,
      boolean rebel) {}

  public record GroupConsensusView(List<MatchConsensus> matches) {}

  /** Lightweight row of match metadata, ordered by kickoff. */
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
      boolean played) {}

  private List<MatchMeta> fetchMatchMeta() {
    return jdbc.query(
        """
        SELECT m.id, r.code AS round_code, m.kickoff_at, m.score_t1, m.score_t2, m.played,
               t1.code AS t1_code, t1.flag_emoji AS t1_flag,
               t2.code AS t2_code, t2.flag_emoji AS t2_flag
        FROM match m
        JOIN round r ON r.id = m.round_id
        LEFT JOIN team t1 ON t1.id = m.team_1_id
        LEFT JOIN team t2 ON t2.id = m.team_2_id
        WHERE m.tournament_id = ?
        ORDER BY m.kickoff_at ASC, m.id ASC
        """,
        (rs, n) ->
            new MatchMeta(
                rs.getLong("id"),
                rs.getString("round_code"),
                rs.getString("t1_code"),
                rs.getString("t1_flag"),
                rs.getString("t2_code"),
                rs.getString("t2_flag"),
                rs.getTimestamp("kickoff_at").toInstant().toString(),
                (Integer) rs.getObject("score_t1"),
                (Integer) rs.getObject("score_t2"),
                rs.getBoolean("played")),
        TOURNAMENT_ID);
  }

  /** matchId -> [scoreT1, scoreT2] for the given user's bets (empty if no quiniela). */
  private Map<Long, int[]> fetchBetsForUser(Long userId) {
    Map<Long, int[]> out = new HashMap<>();
    jdbc.query(
        """
        SELECT b.match_id, b.score_t1, b.score_t2
        FROM bet b JOIN quiniela q ON q.id = b.quiniela_id
        WHERE q.pool_id = ? AND q.user_id = ?
        """,
        rs -> {
          out.put(rs.getLong("match_id"), new int[] {rs.getInt("score_t1"), rs.getInt("score_t2")});
        },
        POOL_ID,
        userId);
    return out;
  }

  @Transactional(readOnly = true)
  public GroupConsensusView getGroupConsensus(Long userId) {
    var deadlines = lockClock.fetchTournamentDeadlines(TOURNAMENT_ID);
    Instant now = Instant.now();

    Map<Long, int[]> myBets = fetchBetsForUser(userId);

    // Whole-pool distribution: matchId -> ("t1:t2" -> count)
    Map<Long, Map<String, Integer>> dist = new HashMap<>();
    jdbc.query(
        """
        SELECT b.match_id, b.score_t1, b.score_t2, COUNT(*) AS cnt
        FROM bet b JOIN quiniela q ON q.id = b.quiniela_id
        WHERE q.pool_id = ?
        GROUP BY b.match_id, b.score_t1, b.score_t2
        """,
        rs -> {
          long mid = rs.getLong("match_id");
          String key = rs.getInt("score_t1") + ":" + rs.getInt("score_t2");
          dist.computeIfAbsent(mid, k -> new HashMap<>()).put(key, rs.getInt("cnt"));
        },
        POOL_ID);

    List<MatchConsensus> out = new ArrayList<>();
    for (MatchMeta m : fetchMatchMeta()) {
      boolean revealed = lockClock.isMatchRevealable(now, deadlines, m.roundCode());
      int[] mine = myBets.get(m.id());
      Integer myT1 = mine == null ? null : mine[0];
      Integer myT2 = mine == null ? null : mine[1];

      List<ScoreCount> distribution = new ArrayList<>();
      int total = 0;
      boolean majority = false;
      boolean rebel = false;

      if (revealed) {
        Map<String, Integer> counts = dist.getOrDefault(m.id(), Map.of());
        int max = 0;
        for (var e : counts.entrySet()) {
          String[] parts = e.getKey().split(":");
          int c = e.getValue();
          total += c;
          max = Math.max(max, c);
          distribution.add(new ScoreCount(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), c));
        }
        distribution.sort((a, b) -> b.count() - a.count());
        if (mine != null) {
          int myCount = counts.getOrDefault(myT1 + ":" + myT2, 0);
          majority = myCount > 0 && myCount == max;
          rebel = myCount == 1 && total > 1;
        }
      }

      out.add(
          new MatchConsensus(
              m.id(),
              m.roundCode(),
              m.t1Code(),
              m.t1Flag(),
              m.t2Code(),
              m.t2Flag(),
              m.kickoffAt(),
              m.actualT1(),
              m.actualT2(),
              m.played(),
              revealed,
              myT1,
              myT2,
              distribution,
              total,
              majority,
              rebel));
    }
    return new GroupConsensusView(out);
  }
}
```

- [ ] **Step 4: Create CompareController**

```java
package io.quiniela.api.compare;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compare")
public class CompareController {

  private final CompareService service;

  public CompareController(CompareService service) {
    this.service = service;
  }

  @GetMapping("/group")
  public ResponseEntity<CompareService.GroupConsensusView> group(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getGroupConsensus(userId));
  }
}
```

- [ ] **Step 5: Run the IT to verify it passes**

Run: `./mvnw -q verify -Dit.test=CompareGroupConsensusIT -DfailIfNoTests=false` (from `backend/`)
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/compare/ \
        backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java
git commit -m "feat(compare): add group consensus endpoint with lock-gated visibility"
```

---

## Task 3: Head-to-head endpoint

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareController.java`
- Test: `backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
package io.quiniela.api.compare;

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

class CompareH2HIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void restoreDeadlines() {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC',"
            + " knockout_deadline = TIMESTAMPTZ '2026-06-28 17:00 UTC' WHERE id = 1");
  }

  private long createUserWithBetOnMatch1(String slug, int t1, int t2) {
    var u = new User("g-" + slug, slug + "@example.com", slug.toUpperCase(), null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    u = users.save(u);
    jdbc.update("INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?)", u.getId());
    Long qid = jdbc.queryForObject("SELECT id FROM quiniela WHERE user_id = ?", Long.class, u.getId());
    jdbc.update("INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2) VALUES (?,1,?,?)", qid, t1, t2);
    return u.getId();
  }

  private String token(long userId) {
    return jwt.issue(users.findById(userId).orElseThrow());
  }

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/compare/h2h?vs=1")).andExpect(status().isUnauthorized());
  }

  @Test
  void hidesRivalPickBeforeLock() throws Exception {
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() + INTERVAL '7 days' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-pre-me", 2, 1);
    long rival = createUserWithBetOnMatch1("h2h-pre-rival", 0, 0);

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rivalUserId").value((int) rival))
        .andExpect(jsonPath("$.matches[0].matchId").value(1))
        .andExpect(jsonPath("$.matches[0].revealed").value(false))
        .andExpect(jsonPath("$.matches[0].state").value("hidden"))
        .andExpect(jsonPath("$.matches[0].myScoreT1").value(2))
        .andExpect(jsonPath("$.matches[0].rivalScoreT1").doesNotExist());
  }

  @Test
  void revealsRivalPickAndClassifiesDifferAfterLock() throws Exception {
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-diff-me", 2, 1);
    long rival = createUserWithBetOnMatch1("h2h-diff-rival", 0, 0);

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.differCount").value(1))
        .andExpect(jsonPath("$.agreeCount").value(0))
        .andExpect(jsonPath("$.matches[0].revealed").value(true))
        .andExpect(jsonPath("$.matches[0].state").value("differ"))
        .andExpect(jsonPath("$.matches[0].rivalScoreT1").value(0))
        .andExpect(jsonPath("$.matches[0].rivalScoreT2").value(0));
  }

  @Test
  void classifiesAgreeWhenPicksMatch() throws Exception {
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-agree-me", 1, 1);
    long rival = createUserWithBetOnMatch1("h2h-agree-rival", 1, 1);

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.agreeCount").value(1))
        .andExpect(jsonPath("$.differCount").value(0))
        .andExpect(jsonPath("$.matches[0].state").value("agree"));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q verify -Dit.test=CompareH2HIT -DfailIfNoTests=false` (from `backend/`)
Expected: FAIL — no `/api/compare/h2h` mapping; `getH2H` undefined.

- [ ] **Step 3: Add the h2h DTOs and method to CompareService**

Add inside `CompareService` (below the group DTOs / `getGroupConsensus`):

```java
  public record H2HMatch(
      Long matchId,
      String roundCode,
      String team1Code,
      String team1Flag,
      String team2Code,
      String team2Flag,
      String kickoffAt,
      Integer actualScoreT1,
      Integer actualScoreT2,
      boolean played,
      boolean revealed,
      Integer myScoreT1,
      Integer myScoreT2,
      Integer rivalScoreT1,
      Integer rivalScoreT2,
      String state) {} // "agree" | "differ" | "hidden"

  public record H2HView(
      Long rivalUserId,
      String rivalDisplayName,
      int agreeCount,
      int differCount,
      Integer myPoints,
      Integer rivalPoints,
      List<H2HMatch> matches) {}

  @Transactional(readOnly = true)
  public H2HView getH2H(Long userId, Long rivalUserId) {
    if (rivalUserId == null) throw new IllegalArgumentException("vs (rival user id) required");

    String rivalName =
        jdbc.query(
            "SELECT u.display_name FROM users u"
                + " JOIN quiniela q ON q.user_id = u.id AND q.pool_id = ?"
                + " WHERE u.id = ?",
            rs -> rs.next() ? rs.getString(1) : null,
            POOL_ID,
            rivalUserId);
    if (rivalName == null && rivalUserId.equals(userId) == false) {
      // Rival not in this pool — treat as no comparison rather than leaking anything.
      throw new IllegalArgumentException("Unknown rival");
    }

    var deadlines = lockClock.fetchTournamentDeadlines(TOURNAMENT_ID);
    Instant now = Instant.now();
    Map<Long, int[]> myBets = fetchBetsForUser(userId);
    Map<Long, int[]> rivalBets = fetchBetsForUser(rivalUserId);

    int agree = 0;
    int differ = 0;
    List<H2HMatch> matches = new ArrayList<>();
    for (MatchMeta m : fetchMatchMeta()) {
      boolean revealed = lockClock.isMatchRevealable(now, deadlines, m.roundCode());
      int[] mine = myBets.get(m.id());
      int[] theirs = rivalBets.get(m.id());
      Integer myT1 = mine == null ? null : mine[0];
      Integer myT2 = mine == null ? null : mine[1];
      Integer rvT1 = (revealed && theirs != null) ? theirs[0] : null;
      Integer rvT2 = (revealed && theirs != null) ? theirs[1] : null;

      String state;
      if (!revealed) {
        state = "hidden";
      } else if (mine != null && theirs != null && mine[0] == theirs[0] && mine[1] == theirs[1]) {
        state = "agree";
        agree++;
      } else if (mine != null && theirs != null) {
        state = "differ";
        differ++;
      } else {
        state = "differ"; // revealed but one side has no pick
      }

      matches.add(
          new H2HMatch(
              m.id(),
              m.roundCode(),
              m.t1Code(),
              m.t1Flag(),
              m.t2Code(),
              m.t2Flag(),
              m.kickoffAt(),
              m.actualT1(),
              m.actualT2(),
              m.played(),
              revealed,
              myT1,
              myT2,
              rvT1,
              rvT2,
              state));
    }
    // Points tally is added in Task 4 (optional). Null for now.
    return new H2HView(rivalUserId, rivalName, agree, differ, null, null, matches);
  }
```

- [ ] **Step 4: Add the h2h endpoint + error handler to CompareController**

Add to `CompareController`:

```java
  @GetMapping("/h2h")
  public ResponseEntity<CompareService.H2HView> h2h(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("vs") Long vs) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getH2H(userId, vs));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
```

- [ ] **Step 5: Run the IT to verify it passes**

Run: `./mvnw -q verify -Dit.test=CompareH2HIT -DfailIfNoTests=false` (from `backend/`)
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/compare/ \
        backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java
git commit -m "feat(compare): add head-to-head endpoint with lock-gated rival picks"
```

---

## Task 4 (OPTIONAL): Head-to-head points tally

> Optional per the spec. Skip if time-constrained — the frontend already shows `Coinciden N · Difieren M` without it. Build only if it stays simple.

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`
- Modify: `backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java`

- [ ] **Step 1: Add a failing test for the tally**

Append to `CompareH2HIT`:

```java
  @Test
  void tallySumsPointsOnPlayedMatches() throws Exception {
    jdbc.update("UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-tally-me", 2, 1); // exact -> 5 pts
    long rival = createUserWithBetOnMatch1("h2h-tally-rival", 1, 0); // correct winner -> 2 pts
    // Record the real result for match 1 as 2-1 and mark played (fires scoring trigger).
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = 1");

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myPoints").value(5))
        .andExpect(jsonPath("$.rivalPoints").value(2));
  }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q verify -Dit.test=CompareH2HIT -DfailIfNoTests=false` (from `backend/`)
Expected: FAIL — `myPoints`/`rivalPoints` are null.

- [ ] **Step 3: Add a Java scoring helper mirroring `score_match_for_bet` (V005)**

Add to `CompareService`:

```java
  /** Mirror of V005 score_match_for_bet for the h2h tally. */
  static int scoreMatchForBet(boolean knockout, int betT1, int betT2, Integer actualT1, Integer actualT2) {
    if (actualT1 == null || actualT2 == null) return 0;
    int base;
    if (betT1 == actualT1 && betT2 == actualT2) {
      base = 5;
    } else {
      int betWinner = Integer.compare(betT1, betT2); // -1,0,1
      int actWinner = Integer.compare(actualT1, actualT2);
      if (betWinner == actWinner) {
        if (betWinner == 0) base = 2; // correct draw
        else if ((betT1 - betT2) == (actualT1 - actualT2)) base = 3; // winner + goal diff
        else base = 2; // winner only
      } else {
        base = 0;
      }
    }
    return knockout ? base * 2 : base;
  }
```

- [ ] **Step 4: Compute the tally inside `getH2H`**

In `getH2H`, replace the trailing `return new H2HView(rivalUserId, rivalName, agree, differ, null, null, matches);` with a version that sums points over **played** matches. Add accumulators in the loop (where `m` and the bets are in scope), then return them:

Add before the `for` loop:

```java
    int myPoints = 0;
    int rivalPoints = 0;
```

Add inside the loop, after computing `mine`/`theirs` (only played matches contribute; both group and knockout, knockout doubled):

```java
      if (Boolean.TRUE.equals(m.played()) && m.actualT1() != null && m.actualT2() != null) {
        boolean knockout = !"GROUP".equals(m.roundCode());
        if (mine != null) myPoints += scoreMatchForBet(knockout, mine[0], mine[1], m.actualT1(), m.actualT2());
        if (theirs != null) rivalPoints += scoreMatchForBet(knockout, theirs[0], theirs[1], m.actualT1(), m.actualT2());
      }
```

Change the return to:

```java
    return new H2HView(rivalUserId, rivalName, agree, differ, myPoints, rivalPoints, matches);
```

- [ ] **Step 5: Run the IT to verify it passes**

Run: `./mvnw -q verify -Dit.test=CompareH2HIT -DfailIfNoTests=false` (from `backend/`)
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/compare/CompareService.java \
        backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java
git commit -m "feat(compare): add head-to-head points tally"
```

---

## Task 5: Frontend API lib

**Files:**
- Create: `frontend/lib/api/compare.ts`

- [ ] **Step 1: Create the API module (types mirror the backend DTOs)**

```ts
import { api } from "./client";

export type ScoreCount = { scoreT1: number; scoreT2: number; count: number };

export type MatchConsensus = {
  matchId: number;
  roundCode: string;
  team1Code: string | null;
  team1Flag: string | null;
  team2Code: string | null;
  team2Flag: string | null;
  kickoffAt: string;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  played: boolean;
  revealed: boolean;
  myScoreT1: number | null;
  myScoreT2: number | null;
  distribution: ScoreCount[];
  totalPicks: number;
  majority: boolean;
  rebel: boolean;
};

export type GroupConsensusView = { matches: MatchConsensus[] };

export type H2HMatchState = "agree" | "differ" | "hidden";

export type H2HMatch = {
  matchId: number;
  roundCode: string;
  team1Code: string | null;
  team1Flag: string | null;
  team2Code: string | null;
  team2Flag: string | null;
  kickoffAt: string;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  played: boolean;
  revealed: boolean;
  myScoreT1: number | null;
  myScoreT2: number | null;
  rivalScoreT1: number | null;
  rivalScoreT2: number | null;
  state: H2HMatchState;
};

export type H2HView = {
  rivalUserId: number;
  rivalDisplayName: string | null;
  agreeCount: number;
  differCount: number;
  myPoints: number | null;
  rivalPoints: number | null;
  matches: H2HMatch[];
};

export async function getGroupConsensus(): Promise<GroupConsensusView> {
  return api<GroupConsensusView>("/api/compare/group");
}

export async function getH2H(vs: number): Promise<H2HView> {
  return api<H2HView>(`/api/compare/h2h?vs=${vs}`);
}
```

- [ ] **Step 2: Typecheck**

Run: `pnpm typecheck` (from `frontend/`)
Expected: PASS (no type errors).

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/api/compare.ts
git commit -m "feat(compare): add frontend API client for compare endpoints"
```

---

## Task 6: i18n keys

**Files:**
- Modify: `frontend/messages/es-CO.json`
- Modify: `frontend/messages/en.json`

- [ ] **Step 1: Add a `compare` namespace to `es-CO.json`**

Add this top-level key (Spanish is the source of truth). Place it alphabetically near other namespaces; ensure valid JSON (comma handling):

```json
  "compare": {
    "title": "Duelos",
    "modeH2H": "1 vs 1",
    "modeGroup": "Grupo",
    "pickRival": "Elige un rival",
    "rivalLabel": "vs.",
    "summary": "Coinciden {agree} · Difieren {differ}",
    "summaryWinning": "Coinciden {agree} · Difieren {differ} · {points}",
    "colMatch": "Partido",
    "colYou": "Tú",
    "colReal": "Real",
    "agreeSection": "Coinciden ({count})",
    "majority": "Con la mayoría",
    "rebel": "Rebelde 🔥",
    "youTag": "tú",
    "lockedTitle": "AÚN NO,\nPACIENCIA.",
    "lockedHelp": "Los picks de los demás se revelan cuando se cierre la quiniela.",
    "emptyGroup": "Todavía no hay suficientes pronósticos para comparar.",
    "noRival": "Elige un rival para ver el duelo."
  }
```

- [ ] **Step 2: Add the matching `compare` namespace to `en.json`**

```json
  "compare": {
    "title": "Duels",
    "modeH2H": "1 vs 1",
    "modeGroup": "Group",
    "pickRival": "Pick a rival",
    "rivalLabel": "vs.",
    "summary": "Agree {agree} · Differ {differ}",
    "summaryWinning": "Agree {agree} · Differ {differ} · {points}",
    "colMatch": "Match",
    "colYou": "You",
    "colReal": "Actual",
    "agreeSection": "Agree ({count})",
    "majority": "With the majority",
    "rebel": "Rebel 🔥",
    "youTag": "you",
    "lockedTitle": "NOT YET,\nHANG TIGHT.",
    "lockedHelp": "Everyone's picks are revealed once the bracket locks.",
    "emptyGroup": "Not enough predictions to compare yet.",
    "noRival": "Pick a rival to see the duel."
  }
```

- [ ] **Step 3: Verify JSON parses**

Run: `node -e "JSON.parse(require('fs').readFileSync('messages/es-CO.json','utf8')); JSON.parse(require('fs').readFileSync('messages/en.json','utf8')); console.log('ok')"` (from `frontend/`)
Expected: prints `ok`.

- [ ] **Step 4: Commit**

```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(compare): add i18n keys for Duelos screen"
```

---

## Task 7: Compare page shell + mode toggle + rival picker

**Files:**
- Modify: `frontend/app/compare/page.tsx`
- Create: `frontend/components/compare/CompareModeToggle.tsx`
- Create: `frontend/components/compare/RivalPicker.tsx`

- [ ] **Step 1: Create the mode toggle (client component)**

```tsx
"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";

export function CompareModeToggle({ mode }: { mode: "group" | "h2h" }) {
  const t = useTranslations("compare");
  const router = useRouter();
  const params = useSearchParams();

  function switchTo(next: "group" | "h2h") {
    const sp = new URLSearchParams(params.toString());
    sp.set("mode", next);
    router.push(`/compare?${sp.toString()}`);
  }

  return (
    <div className="mx-3 mt-3 flex overflow-hidden rounded-full border-[1.5px] border-[var(--color-line-ink)]">
      <button
        type="button"
        onClick={() => switchTo("h2h")}
        className={`flex-1 py-2 text-center text-xs font-extrabold uppercase tracking-tight ${
          mode === "h2h" ? "bg-[var(--color-line-ink)] text-[var(--color-bg-paper)]" : ""
        }`}
      >
        {t("modeH2H")}
      </button>
      <button
        type="button"
        onClick={() => switchTo("group")}
        className={`flex-1 py-2 text-center text-xs font-extrabold uppercase tracking-tight ${
          mode === "group" ? "bg-[var(--color-line-ink)] text-[var(--color-bg-paper)]" : ""
        }`}
      >
        {t("modeGroup")}
      </button>
    </div>
  );
}
```

- [ ] **Step 2: Create the rival picker (client component)**

```tsx
"use client";

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";

export type RivalOption = { userId: number; displayName: string | null };

export function RivalPicker({
  rivals,
  selected,
}: {
  rivals: RivalOption[];
  selected: number | null;
}) {
  const t = useTranslations("compare");
  const router = useRouter();

  return (
    <div className="mx-3 mt-3 flex items-center gap-2">
      <span className="chrome-label chrome-label-muted">{t("rivalLabel")}</span>
      <select
        className="rounded-full border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-1.5 text-sm font-bold"
        value={selected ?? ""}
        onChange={(e) => router.push(`/compare?mode=h2h&vs=${e.target.value}`)}
      >
        <option value="" disabled>
          {t("pickRival")}
        </option>
        {rivals.map((r) => (
          <option key={r.userId} value={r.userId}>
            {r.displayName ?? `#${r.userId}`}
          </option>
        ))}
      </select>
    </div>
  );
}
```

- [ ] **Step 3: Replace the placeholder page with the real shell**

Replace the entire contents of `frontend/app/compare/page.tsx`:

```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getRanking } from "@/lib/api/ranking";
import { getGroupConsensus, getH2H } from "@/lib/api/compare";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CompareModeToggle } from "@/components/compare/CompareModeToggle";
import { RivalPicker } from "@/components/compare/RivalPicker";
import { GroupConsensus } from "@/components/compare/GroupConsensus";
import { H2HCompare } from "@/components/compare/H2HCompare";

export default async function ComparePage({
  searchParams,
}: {
  searchParams: Promise<{ mode?: string; vs?: string }>;
}) {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const sp = await searchParams;
  const mode: "group" | "h2h" = sp.mode === "h2h" ? "h2h" : "group";
  const vs = sp.vs ? Number(sp.vs) : null;

  const tNav = await getTranslations("nav");
  const ranking = await getRanking();
  // Rivals = everyone in the pool except me.
  const rivals = ranking.entries
    .filter((e) => !e.isYou)
    .map((e) => ({ userId: e.userId, displayName: e.displayName }));

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={tNav("compare").toUpperCase()} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <CompareModeToggle mode={mode} />
        {mode === "h2h" ? (
          <>
            <RivalPicker rivals={rivals} selected={vs} />
            {vs ? <H2HCompare data={await getH2H(vs)} /> : <H2HCompare data={null} />}
          </>
        ) : (
          <GroupConsensus data={await getGroupConsensus()} />
        )}
      </div>
      <BottomNav activeKey="compare" />
    </main>
  );
}
```

- [ ] **Step 4: Typecheck (will fail until Task 8 adds the view components)**

Run: `pnpm typecheck` (from `frontend/`)
Expected: FAIL — `GroupConsensus` / `H2HCompare` modules not found yet. This is expected; Task 8 resolves it. Do NOT commit yet.

- [ ] **Step 5: (defer commit to Task 8)**

Proceed directly to Task 8; commit page + toggle + picker + views together once typecheck passes.

---

## Task 8: GroupConsensus + H2HCompare components

**Files:**
- Create: `frontend/components/compare/GroupConsensus.tsx`
- Create: `frontend/components/compare/H2HCompare.tsx`

- [ ] **Step 1: Create GroupConsensus**

```tsx
import { getTranslations } from "next-intl/server";
import type { GroupConsensusView, MatchConsensus } from "@/lib/api/compare";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

export async function GroupConsensus({ data }: { data: GroupConsensusView }) {
  const t = await getTranslations("compare");
  const revealed = data.matches.filter((m) => m.revealed);

  if (revealed.length === 0) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-start justify-center gap-4 px-6 py-16">
        <h1 className="headline-display whitespace-pre-line text-[44px] sm:text-6xl">{t("lockedTitle")}</h1>
        <p className="font-sans text-base text-[var(--color-text-muted)]">{t("lockedHelp")}</p>
      </section>
    );
  }

  return (
    <section className="mx-3 mt-3 flex flex-col gap-3">
      {revealed.map((m) => (
        <ConsensusCard key={m.matchId} m={m} majorityLabel={t("majority")} rebelLabel={t("rebel")} youLabel={t("youTag")} />
      ))}
    </section>
  );
}

function ConsensusCard({
  m,
  majorityLabel,
  rebelLabel,
  youLabel,
}: {
  m: MatchConsensus;
  majorityLabel: string;
  rebelLabel: string;
  youLabel: string;
}) {
  const top = m.distribution.slice(0, 4);
  const max = top.reduce((acc, s) => Math.max(acc, s.count), 1);
  const mineKey = m.myScoreT1 !== null ? `${m.myScoreT1}:${m.myScoreT2}` : null;

  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-extrabold">
          {teamLabel(m.team1Flag, m.team1Code)}–{teamLabel(m.team2Flag, m.team2Code)}
        </span>
        {m.rebel ? (
          <span className="rounded-full bg-[var(--color-accent-gold)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-line-ink)]">
            {rebelLabel}
          </span>
        ) : m.majority ? (
          <span className="rounded-full bg-[var(--color-line-ink)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-bg-paper)]">
            {majorityLabel}
          </span>
        ) : null}
      </div>
      {top.map((s) => {
        const key = `${s.scoreT1}:${s.scoreT2}`;
        const isMine = key === mineKey;
        return (
          <div key={key} className="mb-1 flex items-center gap-2 text-xs">
            <span className={`w-9 font-extrabold ${isMine ? "text-[var(--color-accent-red)]" : ""}`}>
              {s.scoreT1}–{s.scoreT2}
            </span>
            <span className="h-3.5 flex-1 overflow-hidden rounded bg-[var(--color-line-soft,#e7ddcc)]">
              <span
                className={`block h-full ${isMine ? "bg-[var(--color-accent-red)]" : "bg-[#cbb8a0]"}`}
                style={{ width: `${Math.round((s.count / max) * 100)}%` }}
              />
            </span>
            <span className="w-10 text-right font-bold text-[var(--color-text-muted)]">
              {isMine ? youLabel : s.count}
            </span>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 2: Create H2HCompare (differences first, agreements collapsed)**

```tsx
import { getTranslations } from "next-intl/server";
import type { H2HView, H2HMatch } from "@/lib/api/compare";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

function score(t1: number | null, t2: number | null): string {
  return t1 === null || t2 === null ? "—" : `${t1}–${t2}`;
}

export async function H2HCompare({ data }: { data: H2HView | null }) {
  const t = await getTranslations("compare");

  if (!data) {
    return (
      <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] p-6 text-center">
        <p className="font-display text-base font-extrabold uppercase text-[var(--color-text-muted)]">
          {t("noRival")}
        </p>
      </section>
    );
  }

  const visible = data.matches.filter((m) => m.revealed);
  const differ = visible.filter((m) => m.state === "differ");
  const agree = visible.filter((m) => m.state === "agree");

  const points =
    data.myPoints !== null && data.rivalPoints !== null
      ? `${data.myPoints}–${data.rivalPoints}`
      : null;

  const rival = data.rivalDisplayName ?? `#${data.rivalUserId}`;

  return (
    <div className="mx-3 mt-2">
      <p className="px-1 py-2 text-xs font-semibold text-[var(--color-text-muted)]">
        {points
          ? t("summaryWinning", { agree: data.agreeCount, differ: data.differCount, points })
          : t("summary", { agree: data.agreeCount, differ: data.differCount })}
      </p>
      <table className="w-full border-collapse">
        <thead>
          <tr className="border-b-[1.5px] border-[var(--color-line-ink)] text-[9px] uppercase text-[var(--color-text-muted)]">
            <th className="py-1.5 text-left">{t("colMatch")}</th>
            <th className="py-1.5">{t("colYou")}</th>
            <th className="py-1.5">{rival}</th>
            <th className="py-1.5">{t("colReal")}</th>
          </tr>
        </thead>
        <tbody>
          {differ.map((m) => (
            <Row key={m.matchId} m={m} highlight />
          ))}
          {agree.length > 0 && (
            <tr>
              <td colSpan={4} className="pt-3 text-[9px] font-extrabold uppercase text-[var(--color-text-muted)]">
                {t("agreeSection", { count: agree.length })}
              </td>
            </tr>
          )}
          {agree.map((m) => (
            <Row key={m.matchId} m={m} highlight={false} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Row({ m, highlight }: { m: H2HMatch; highlight: boolean }) {
  return (
    <tr className={`border-b border-[var(--color-line-soft,#e7ddcc)] text-xs ${highlight ? "bg-[#fff4d6]" : "opacity-60"}`}>
      <td className="py-2 text-left font-extrabold">
        {teamLabel(m.team1Flag, m.team1Code)}–{teamLabel(m.team2Flag, m.team2Code)}
      </td>
      <td className="py-2 text-center font-extrabold">{score(m.myScoreT1, m.myScoreT2)}</td>
      <td className="py-2 text-center font-extrabold text-[var(--color-accent-red)]">
        {score(m.rivalScoreT1, m.rivalScoreT2)}
      </td>
      <td className="py-2 text-center text-[var(--color-text-muted)]">{score(m.actualScoreT1, m.actualScoreT2)}</td>
    </tr>
  );
}
```

- [ ] **Step 3: Typecheck the whole frontend**

Run: `pnpm typecheck` (from `frontend/`)
Expected: PASS.

- [ ] **Step 4: Build to confirm the page compiles (server components + data fetch wiring)**

Run: `pnpm build` (from `frontend/`)
Expected: build succeeds; `/compare` shows as a route.

- [ ] **Step 5: Commit page + toggle + picker + views together**

```bash
git add frontend/app/compare/page.tsx frontend/components/compare/
git commit -m "feat(compare): build Duelos screen with 1v1 and group consensus views"
```

---

## Task 9: Frontend component tests

**Files:**
- Create: `frontend/components/compare/CompareModeToggle.test.tsx`
- Create: `frontend/components/compare/GroupConsensus.test.tsx`

- [ ] **Step 1: Write the mode-toggle test**

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { CompareModeToggle } from "./CompareModeToggle";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("mode=group"),
}));

const messages = { compare: { modeH2H: "1 vs 1", modeGroup: "Grupo" } };

function renderToggle(mode: "group" | "h2h") {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <CompareModeToggle mode={mode} />
    </NextIntlClientProvider>,
  );
}

describe("CompareModeToggle", () => {
  it("navigates to h2h mode when the 1 vs 1 tab is clicked", () => {
    renderToggle("group");
    fireEvent.click(screen.getByText("1 vs 1"));
    expect(push).toHaveBeenCalledWith("/compare?mode=h2h");
  });
});
```

- [ ] **Step 2: Run it to verify it passes**

Run: `pnpm vitest run components/compare/CompareModeToggle.test.tsx` (from `frontend/`)
Expected: PASS.

- [ ] **Step 3: Write a render test for GroupConsensus (locked + revealed)**

> `GroupConsensus` is an async server component. Await it to get the element tree, then render the result.

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { GroupConsensusView } from "@/lib/api/compare";
import { GroupConsensus } from "./GroupConsensus";

vi.mock("next-intl/server", () => ({
  getTranslations: async () => (key: string, vars?: Record<string, unknown>) => {
    const map: Record<string, string> = {
      lockedTitle: "AÚN NO",
      lockedHelp: "se revela al cerrar",
      majority: "Con la mayoría",
      rebel: "Rebelde",
      youTag: "tú",
    };
    return map[key] ?? key;
  },
}));

const messages = {};

async function renderGC(data: GroupConsensusView) {
  const ui = await GroupConsensus({ data });
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

describe("GroupConsensus", () => {
  it("shows the locked state when nothing is revealed", async () => {
    await renderGC({ matches: [{
      matchId: 1, roundCode: "GROUP", team1Code: "BRA", team1Flag: "🇧🇷", team2Code: "SRB", team2Flag: "🇷🇸",
      kickoffAt: "2026-06-11T17:00:00Z", actualScoreT1: null, actualScoreT2: null, played: false,
      revealed: false, myScoreT1: 2, myScoreT2: 1, distribution: [], totalPicks: 0, majority: false, rebel: false,
    }] });
    expect(screen.getByText("AÚN NO")).toBeTruthy();
  });

  it("renders a consensus bar and the rebel tag when revealed", async () => {
    await renderGC({ matches: [{
      matchId: 1, roundCode: "GROUP", team1Code: "BRA", team1Flag: "🇧🇷", team2Code: "SRB", team2Flag: "🇷🇸",
      kickoffAt: "2026-06-11T17:00:00Z", actualScoreT1: null, actualScoreT2: null, played: false,
      revealed: true, myScoreT1: 4, myScoreT2: 4,
      distribution: [{ scoreT1: 1, scoreT2: 0, count: 5 }, { scoreT1: 4, scoreT2: 4, count: 1 }],
      totalPicks: 6, majority: false, rebel: true,
    }] });
    expect(screen.getByText("Rebelde")).toBeTruthy();
    expect(screen.getByText("1–0")).toBeTruthy();
  });
});
```

- [ ] **Step 4: Run it to verify it passes**

Run: `pnpm vitest run components/compare/GroupConsensus.test.tsx` (from `frontend/`)
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/components/compare/CompareModeToggle.test.tsx \
        frontend/components/compare/GroupConsensus.test.tsx
git commit -m "test(compare): cover mode toggle and group consensus rendering"
```

---

## Task 10: Retire placeholder keys + full verification

**Files:**
- Modify: `frontend/messages/es-CO.json`, `frontend/messages/en.json`

- [ ] **Step 1: Remove the now-unused placeholder compare keys**

In both `es-CO.json` and `en.json`, delete the `placeholder.compareHeadline` and `placeholder.compareHelp` entries (the `/compare` page no longer uses the `placeholder` namespace). Leave other `placeholder.*` keys (e.g. `comingSoon`) untouched if used elsewhere.

- [ ] **Step 2: Confirm nothing else references the removed keys**

Run: `grep -rn "compareHeadline\|compareHelp" frontend/app frontend/components` (from repo root)
Expected: no matches.

- [ ] **Step 3: Run the full frontend test + typecheck**

Run: `pnpm typecheck && pnpm test` (from `frontend/`)
Expected: PASS.

- [ ] **Step 4: Run the full backend compare ITs together**

Run: `./mvnw -q verify -Dit.test='Compare*IT,LockClockRevealTest' -DfailIfNoTests=false` (from `backend/`)
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "chore(compare): retire unused placeholder copy"
```

---

## Self-review notes (for the implementer)

- **Privacy is the highest-risk requirement.** The ITs in Tasks 2 & 3 prove picks stay hidden before lock and appear after, for both group and knockout deadlines. Do not weaken these.
- **Distribution is only ever populated for revealed matches** — the gate is in `getGroupConsensus` (the `if (revealed)` block) and `getH2H` (`rvT1/rvT2` are null unless `revealed`). If you refactor, keep the gate server-side.
- **`vs` pointing at a non-pool user** returns `400` (handled in `CompareController`), never a partial leak.
- **Optional Task 4** can be skipped; the frontend already renders the summary without `points` (the `summaryWinning` vs `summary` branch in `H2HCompare`).

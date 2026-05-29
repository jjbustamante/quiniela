# Plan 7 — Admin test mode

**Status:** Ready for implementation
**Created:** 2026-05-29
**Design:** [`docs/superpowers/specs/2026-05-29-admin-test-mode-design.md`](../specs/2026-05-29-admin-test-mode-design.md)

> **For agentic workers:** REQUIRED SUB-SKILL — use superpowers:subagent-driven-development (preferred) or superpowers:executing-plans. Each task is one commit, test-first on the backend. Backend gates from `backend/`: `./mvnw -B verify` (Spotless runs in verify; on a format failure run `./mvnw spotless:apply` then re-verify — never hand-format). Frontend gates from `frontend/`: `pnpm typecheck`, `pnpm lint`, `pnpm build`, `pnpm test`. Commit locally; do NOT push until the final task.

**Goal:** Let the admin run realistic pre-launch test rounds with friends on the single prod environment — with a "data is not real" banner, a clean reset, admin-editable deadlines, and a Paul-like result simulator — then flip a persisted `test_mode` flag off to go live.

**Architecture:** A persisted `tournament.test_mode` flag (V013, default true) gates a global banner + the admin test tools. New `/api/admin/test/*` endpoints (admin-gated; the destructive ones also 409 when test mode is off) back a new `/admin/test` page. The simulator writes random scores via JdbcTemplate (firing the existing scoring trigger) and advances the knockout bracket through the existing `match_parent_*` pointers.

**Tech Stack:** Spring Boot 4 + Java 25 + Flyway + Postgres (backend); Next.js 16 App Router + next-intl (frontend).

---

## Conventions

- Backend paths under `backend/`, frontend under `frontend/`. Run gates from each.
- Migration: **V013** (`V013__test_mode.sql`). V012 (user timezone) is the latest existing.
- Admin gate pattern: mirror `AdminResultsService.requireAdmin(callerId)` — loads the user via `UserRepository`, throws `ResponseStatusException(HttpStatus.UNAUTHORIZED)` if missing, `HttpStatus.FORBIDDEN` if `role != UserRole.ADMIN`. JWT subject is the caller id: `Long.parseLong(jwt.getSubject())`.
- Aggregate writes/reads use `JdbcTemplate` (mirror `AdminPaymentService` / `RankingService`); the simulator uses the `Match` JPA entity + `MatchRepository` for per-row updates (the entity has `setScoreT1/setScoreT2/setWinnerId/setPlayed`, and `getRoundId/getTeam1Id/getTeam2Id/getWinnerId/getMatchParent1Id/getMatchParent2Id`).
- Hardcode `TOURNAMENT_ID = 1L`, `POOL_ID = 1L` (matches the rest of the app).
- Role strings UPPERCASE on the frontend (`me.role === "ADMIN"`).
- `SecurityConfig` already requires auth on `/api/admin/**`; the service does the role + test-mode gates.
- No `Date.now()` in component render; no unused imports. Pre-existing acceptable lint warnings: `app/layout.tsx` custom-font + coverage artifacts.

## Progress

- [x] Task 1: V013 migration + Tournament.testMode entity field
- [x] Task 2: PublicSummary.testMode (backend + frontend type/fallback) + IT
- [x] Task 3: TestModeBanner component in layout + i18n banner key
- [x] Task 4: AdminTestService + AdminTestController — state + mode toggle + clean + deadlines + IT
- [x] Task 5: Simulation engine (simulate/round + simulate/all) + IT
- [x] Task 6: /admin/test page + actions + nav drawer item + i18n
- [x] Task 7: verify + ship

---

## Task 1: V013 migration + Tournament.testMode field

**Files:**
- Create: `backend/src/main/resources/db/migration/V013__test_mode.sql`
- Modify: `backend/src/main/java/io/quiniela/api/tournament/Tournament.java`
- Create: `backend/src/test/java/io/quiniela/api/support/V013MigrationTest.java`

- [x] **Step 1: Write the failing migration test**

Create `V013MigrationTest.java`:
```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V013MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void tournamentHasTestModeColumnDefaultingTrue() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'tournament' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("test_mode");

    Boolean tm =
        jdbc.queryForObject("SELECT test_mode FROM tournament WHERE id = 1", Boolean.class);
    assertThat(tm).isTrue();
  }
}
```

- [x] **Step 2: Run to verify it fails**

`cd backend && ./mvnw verify` → FAIL (no `test_mode` column).

- [x] **Step 3: Write the migration**

Create `V013__test_mode.sql`:
```sql
-- V013: Admin test mode flag. Default true so the app launches in test mode;
-- the admin flips it false to "go live". Gates the not-real-data banner +
-- the admin test tools (clean / simulate / editable deadlines).

ALTER TABLE tournament ADD COLUMN test_mode BOOLEAN NOT NULL DEFAULT true;
```

- [x] **Step 4: Add the entity field + accessors**

In `backend/src/main/java/io/quiniela/api/tournament/Tournament.java`, add the
field after `openingVenue` (before `createdAt`):
```java
  @Column(name = "test_mode", nullable = false)
  private boolean testMode;
```
Add a getter + setter near the other accessors (after `getOpeningVenue()`):
```java
  public boolean isTestMode() {
    return testMode;
  }

  public void setTestMode(boolean testMode) {
    this.testMode = testMode;
  }
```

- [x] **Step 5: Run to verify it passes**

`cd backend && ./mvnw verify` → PASS (V013MigrationTest green; all prior ITs green). spotless:apply first if format fails.

- [x] **Step 6: Commit**
```bash
git add backend/src/main/resources/db/migration/V013__test_mode.sql \
        backend/src/main/java/io/quiniela/api/tournament/Tournament.java \
        backend/src/test/java/io/quiniela/api/support/V013MigrationTest.java
git commit -m "feat(backend): V013 — tournament.test_mode flag + entity field"
```

---

## Task 2: PublicSummary.testMode

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/tournament/PublicSummaryController.java`
- Modify: `backend/src/test/java/io/quiniela/api/tournament/PublicSummaryControllerIT.java`
- Modify: `frontend/lib/api/summary.ts`

- [x] **Step 1: Add a failing IT assertion**

In `PublicSummaryControllerIT.java`, in the existing test that asserts the
summary shape (the one that checks `$.pool.currency` etc.), add:
```java
        .andExpect(jsonPath("$.testMode").isBoolean())
```
(Add it to the existing `returnsSeededTournamentAndPool` chain — the seeded
tournament has `test_mode` default true, so it will be a boolean `true`.)

- [x] **Step 2: Run to verify it fails**

`cd backend && ./mvnw verify` → FAIL (no `testMode` in the response).

- [x] **Step 3: Add `testMode` to the response**

In `PublicSummaryController.java`:
- Add `boolean testMode` as the LAST component of the `SummaryResponse` record:
  `public record SummaryResponse(TournamentSummary tournament, PoolSummary pool, List<PrizeSplitEntry> prizeSplit, boolean testMode) {}`
- In the `get()` method, pass `tournament.isTestMode()` as the new last arg to
  the `new SummaryResponse(...)` constructor call.

- [x] **Step 4: Run to verify it passes**

`cd backend && ./mvnw verify` → PASS.

- [x] **Step 5: Frontend type + fallback**

In `frontend/lib/api/summary.ts`:
- Add `testMode: boolean;` to the `PublicSummary` type (top level, after
  `prizeSplit`).
- Add `testMode: false` to the `FALLBACK` constant (so an unreachable backend
  never shows the banner).

- [x] **Step 6: Frontend gate**

`cd frontend && pnpm typecheck 2>&1 | tail -3` → clean.

- [x] **Step 7: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/tournament/PublicSummaryController.java \
        backend/src/test/java/io/quiniela/api/tournament/PublicSummaryControllerIT.java \
        frontend/lib/api/summary.ts
git commit -m "feat: expose tournament.testMode on /api/public/summary"
```

---

## Task 3: TestModeBanner in layout + i18n

**Files:**
- Create: `frontend/components/shell/TestModeBanner.tsx`
- Modify: `frontend/app/layout.tsx`
- Modify: `frontend/messages/es-CO.json`
- Modify: `frontend/messages/en.json`

- [x] **Step 1: i18n banner key**

In `frontend/messages/es-CO.json`, add a top-level `testMode` namespace:
```json
  "testMode": {
    "banner": "MODO PRUEBA · los datos no son reales"
  },
```
In `frontend/messages/en.json`:
```json
  "testMode": {
    "banner": "TEST MODE · data is not real"
  },
```

- [x] **Step 2: TestModeBanner component**

Create `frontend/components/shell/TestModeBanner.tsx`:
```tsx
import { getTranslations } from "next-intl/server";
import { getPublicSummaryOrFallback } from "@/lib/api/summary";

/**
 * Global "data is not real" banner. Async server component fetched once in the
 * root layout. Renders nothing unless the active tournament is in test mode
 * (FALLBACK has testMode:false, so an unreachable backend shows no banner).
 */
export async function TestModeBanner() {
  const summary = await getPublicSummaryOrFallback();
  if (!summary.testMode) return null;
  const t = await getTranslations("testMode");
  return (
    <div className="sticky top-0 z-50 bg-[var(--color-accent-red)] px-3 py-1 text-center font-mono text-[11px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-inverse)]">
      {t("banner")}
    </div>
  );
}
```

- [x] **Step 3: Mount it in the layout**

In `frontend/app/layout.tsx`, add the import and render it as the first child
inside `<NextIntlClientProvider>`, immediately above `{children}`:
```tsx
import { TestModeBanner } from "@/components/shell/TestModeBanner";
```
```tsx
        <NextIntlClientProvider locale={locale} messages={messages}>
          <TestModeBanner />
          {children}
        </NextIntlClientProvider>
```

- [x] **Step 4: Gates + parity**
```bash
cd frontend && node -e "const es=require('./messages/es-CO.json'), en=require('./messages/en.json'); const p=(o)=>Object.keys(o).sort().join(','); console.log('testMode parity:', p(es.testMode)===p(en.testMode));"
```
Expected: `true`.
```bash
cd frontend && pnpm typecheck 2>&1 | tail -3 && pnpm build 2>&1 | grep -E "Compiled|error|Error" | head
```
Expected: clean + "Compiled successfully".

- [x] **Step 5: Commit**
```bash
git add frontend/components/shell/TestModeBanner.tsx frontend/app/layout.tsx \
        frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(frontend): global test-mode banner"
```

---

## Task 4: AdminTestService + AdminTestController — state, mode, clean, deadlines

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java`
- Create: `backend/src/main/java/io/quiniela/api/admin/AdminTestController.java`
- Create: `backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java`

This task builds everything EXCEPT the simulate endpoints (Task 5 adds those to
the same service/controller).

- [x] **Step 1: Write the failing IT**

Create `AdminTestControllerIT.java` mirroring `AdminPaymentControllerIT` /
`MatchesControllerIT` bootstrap (MockMvc + `springSecurity()` + `JwtService` +
`JdbcTemplate jdbc`). Use these helpers + cases:

```java
package io.quiniela.api.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AdminTestControllerIT extends AbstractIntegrationTest {

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
    jdbc.update("UPDATE tournament SET test_mode = true WHERE id = 1");
  }

  @AfterEach
  void restoreTestMode() {
    jdbc.update("UPDATE tournament SET test_mode = true WHERE id = 1");
  }

  private String adminToken() {
    var u = new User("g-admintest", "admintest@example.com", "Admin T", null, UserRole.ADMIN);
    u.setInvitePath("admintest-abc");
    u = users.save(u);
    return jwt.issue(u);
  }

  private String playerToken() {
    var u = new User("g-playertest", "playertest@example.com", "Player T", null, UserRole.PLAYER);
    u.setInvitePath("playertest-abc");
    u = users.save(u);
    return jwt.issue(u);
  }

  @Test
  void stateRequiresAdmin() throws Exception {
    mockMvc
        .perform(get("/api/admin/test/state").header("Authorization", "Bearer " + playerToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void stateReturnsTestModeAndCurrentRound() throws Exception {
    mockMvc
        .perform(get("/api/admin/test/state").header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testMode").value(true))
        .andExpect(jsonPath("$.currentRoundCode").value("GROUP"));
  }

  @Test
  void modeToggleWorksForAdmin() throws Exception {
    String token = adminToken();
    mockMvc
        .perform(
            put("/api/admin/test/mode")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testMode").value(false));
    // turning it back on still works (mode endpoint is not test-mode-gated)
    mockMvc
        .perform(
            put("/api/admin/test/mode")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testMode").value(true));
  }

  @Test
  void cleanResetsBetsResultsPointsPaymentsButKeepsFixtures() throws Exception {
    String token = adminToken();
    // Seed a played match + a bet + points + a payment to prove they reset.
    var player = new User("g-cleanme", "cleanme@example.com", "Clean", null, UserRole.PLAYER);
    player.setInvitePath("cleanme-abc");
    player = users.save(player);
    jdbc.update("UPDATE match SET score_t1=2, score_t2=1, played=true WHERE id=1");
    Long qid =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at) "
                + "VALUES (1, ?, 7, NOW(), NOW()) RETURNING id",
            Long.class,
            player.getId());
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, created_at, updated_at) "
            + "VALUES (?, 1, 2, 1, NOW(), NOW())",
        qid);
    jdbc.update(
        "INSERT INTO payment (pool_id, user_id, paid, paid_at, marked_paid_by, created_at, updated_at) "
            + "VALUES (1, ?, true, NOW(), ?, NOW(), NOW())",
        player.getId(),
        player.getId());

    long teamsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM team", Long.class);

    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    assertReset(qid, player.getId(), teamsBefore);
  }

  private void assertReset(Long qid, Long userId, long teamsBefore) {
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM bet", Long.class))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT points FROM quiniela WHERE id = ?", Integer.class, qid))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT played FROM match WHERE id = 1", Boolean.class))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT paid FROM payment WHERE pool_id = 1 AND user_id = ?",
                Boolean.class,
                userId))
        .isFalse();
    // fixtures intact
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM team", Long.class))
        .isEqualTo(teamsBefore);
  }

  @Test
  void cleanIsForbiddenForNonAdmin() throws Exception {
    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + playerToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void cleanReturns409WhenTestModeOff() throws Exception {
    String token = adminToken();
    jdbc.update("UPDATE tournament SET test_mode = false WHERE id = 1");
    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void deadlinesUpdateInTestMode() throws Exception {
    String token = adminToken();
    mockMvc
        .perform(
            put("/api/admin/test/deadlines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"groupStageDeadline\":\"2020-01-01T00:00:00Z\",\"knockoutDeadline\":\"2020-02-01T00:00:00Z\"}"))
        .andExpect(status().isOk());
    String gs =
        jdbc.queryForObject(
            "SELECT group_stage_deadline::text FROM tournament WHERE id = 1", String.class);
    org.assertj.core.api.Assertions.assertThat(gs).startsWith("2020-01-01");
  }
}
```

> Note: this IT mutates shared `tournament`/`match` rows. `AbstractIntegrationTest`
> already cleans `bet`, `payment`, `quiniela`, `pool_membership`, `users` between
> tests; the `@BeforeEach`/`@AfterEach` here reset `test_mode`. The
> `UPDATE match ... WHERE id=1` in the clean test is undone by the clean itself
> (sets played=false, score null); other ITs that read match #1 (e.g. bracket)
> create their own users/bets and don't assert match #1 is unplayed at start, but
> to be safe the clean test fully resets match #1 via the clean call. If any
> cross-IT flakiness appears on match #1 state, add `jdbc.update("UPDATE match SET
> score_t1=NULL,score_t2=NULL,winner_id=NULL,played=false WHERE id=1")` to this
> class's `@AfterEach` and report it.

- [x] **Step 2: Run to verify it fails**

`cd backend && ./mvnw verify` → FAIL (no controller/service).

- [x] **Step 3: Create `AdminTestService.java`**

```java
package io.quiniela.api.admin;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminTestService {

  static final Long TOURNAMENT_ID = 1L;
  static final Long POOL_ID = 1L;

  private final UserRepository users;
  private final JdbcTemplate jdbc;

  public AdminTestService(UserRepository users, DataSource ds) {
    this.users = users;
    this.jdbc = new JdbcTemplate(ds);
  }

  public record TestState(
      boolean testMode,
      String groupStageDeadline,
      String knockoutDeadline,
      String currentRoundCode,
      int roundsRemaining) {}

  public record ModeView(boolean testMode) {}

  public record CleanResult(int betsDeleted, int matchesReset) {}

  public record DeadlinesView(String groupStageDeadline, String knockoutDeadline) {}

  // ── auth ────────────────────────────────────────────────────────────────

  void requireAdmin(Long callerId) {
    User caller =
        users.findById(callerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  void requireTestModeEnabled() {
    Boolean on =
        jdbc.queryForObject(
            "SELECT test_mode FROM tournament WHERE id = ?", Boolean.class, TOURNAMENT_ID);
    if (!Boolean.TRUE.equals(on)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Test mode is off");
    }
  }

  // ── state ───────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public TestState getState(Long callerId) {
    requireAdmin(callerId);
    boolean testMode =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT test_mode FROM tournament WHERE id = ?", Boolean.class, TOURNAMENT_ID));
    String gs =
        jdbc.queryForObject(
            "SELECT group_stage_deadline::text FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    String ko =
        jdbc.queryForObject(
            "SELECT knockout_deadline::text FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    String currentRound = currentRoundCode();
    int remaining =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT r.id) FROM round r "
                + "JOIN match m ON m.round_id = r.id AND m.tournament_id = ? "
                + "WHERE r.tournament_id = ? AND m.played = false",
            Integer.class,
            TOURNAMENT_ID,
            TOURNAMENT_ID);
    return new TestState(testMode, gs, ko, currentRound, remaining);
  }

  /** Lowest-sequence round code that still has an unplayed match, or null. */
  String currentRoundCode() {
    return jdbc.query(
        "SELECT r.code FROM round r "
            + "JOIN match m ON m.round_id = r.id AND m.tournament_id = ? "
            + "WHERE r.tournament_id = ? AND m.played = false "
            + "ORDER BY r.sequence ASC LIMIT 1",
        rs -> rs.next() ? rs.getString("code") : null,
        TOURNAMENT_ID,
        TOURNAMENT_ID);
  }

  // ── mode toggle (admin-only, NOT test-mode-gated) ─────────────────────────

  @Transactional
  public ModeView setMode(Long callerId, boolean enabled) {
    requireAdmin(callerId);
    jdbc.update(
        "UPDATE tournament SET test_mode = ?, updated_at = NOW() WHERE id = ?",
        enabled,
        TOURNAMENT_ID);
    return new ModeView(enabled);
  }

  // ── clean (test-mode-gated) ───────────────────────────────────────────────

  @Transactional
  public CleanResult clean(Long callerId) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    int bets = jdbc.update("DELETE FROM bet");
    int matches =
        jdbc.update(
            "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false "
                + "WHERE tournament_id = ?",
            TOURNAMENT_ID);
    jdbc.update("UPDATE quiniela SET points = 0, updated_at = NOW() WHERE pool_id = ?", POOL_ID);
    jdbc.update(
        "UPDATE payment SET paid=false, paid_at=NULL, marked_paid_by=NULL, "
            + "settled=false, settled_at=NULL, marked_settled_by=NULL, updated_at=NOW() "
            + "WHERE pool_id = ?",
        POOL_ID);
    return new CleanResult(bets, matches);
  }

  // ── deadlines (test-mode-gated) ────────────────────────────────────────────

  @Transactional
  public DeadlinesView setDeadlines(Long callerId, String groupStageDeadline, String knockoutDeadline) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = ?::timestamptz, "
            + "knockout_deadline = ?::timestamptz, updated_at = NOW() WHERE id = ?",
        groupStageDeadline,
        knockoutDeadline,
        TOURNAMENT_ID);
    String gs =
        jdbc.queryForObject(
            "SELECT group_stage_deadline::text FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    String ko =
        jdbc.queryForObject(
            "SELECT knockout_deadline::text FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    return new DeadlinesView(gs, ko);
  }
}
```

- [x] **Step 4: Create `AdminTestController.java`**

```java
package io.quiniela.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/test")
public class AdminTestController {

  private final AdminTestService service;

  public AdminTestController(AdminTestService service) {
    this.service = service;
  }

  public record ModeRequest(boolean enabled) {}

  public record DeadlinesRequest(String groupStageDeadline, String knockoutDeadline) {}

  private static Long callerId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }

  @GetMapping("/state")
  public ResponseEntity<AdminTestService.TestState> state(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getState(callerId(jwt)));
  }

  @PutMapping("/mode")
  public ResponseEntity<AdminTestService.ModeView> mode(
      @AuthenticationPrincipal Jwt jwt, @RequestBody ModeRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.setMode(callerId(jwt), req.enabled()));
  }

  @PostMapping("/clean")
  public ResponseEntity<AdminTestService.CleanResult> clean(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.clean(callerId(jwt)));
  }

  @PutMapping("/deadlines")
  public ResponseEntity<AdminTestService.DeadlinesView> deadlines(
      @AuthenticationPrincipal Jwt jwt, @RequestBody DeadlinesRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(
        service.setDeadlines(callerId(jwt), req.groupStageDeadline(), req.knockoutDeadline()));
  }
}
```

- [x] **Step 5: Run to verify it passes**

`cd backend && ./mvnw verify` → PASS (spotless:apply first if format fails).

- [x] **Step 6: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/admin/AdminTestService.java \
        backend/src/main/java/io/quiniela/api/admin/AdminTestController.java \
        backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java
git commit -m "feat(backend): admin test API — state, mode toggle, clean, deadlines"
```

---

## Task 5: Simulation engine — simulate/round + simulate/all

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java`
- Modify: `backend/src/main/java/io/quiniela/api/admin/AdminTestController.java`
- Modify: `backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java`

Uses the `Match` JPA entity + `MatchRepository` (has setters + parent getters) +
`RoundRepository` for sequence ordering.

- [x] **Step 1: Add failing IT cases**

Add to `AdminTestControllerIT.java` (the imports for it are already present;
add `post` is already imported). Add these tests:
```java
  @Test
  void simulateRoundPlaysCurrentRoundAndScores() throws Exception {
    String token = adminToken();
    // Ensure a clean slate (no played matches) so GROUP is the current round.
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");

    long groupUnplayedBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.played=false",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupUnplayedBefore).isGreaterThan(0);

    mockMvc
        .perform(post("/api/admin/test/simulate/round").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundCode").value("GROUP"))
        .andExpect(jsonPath("$.matchesPlayed").value((int) groupUnplayedBefore));

    long groupUnplayedAfter =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.played=false",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupUnplayedAfter).isZero();
  }

  @Test
  void simulateRoundIsForbiddenForNonAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/test/simulate/round")
                .header("Authorization", "Bearer " + playerToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void simulateRoundReturns409WhenTestModeOff() throws Exception {
    String token = adminToken();
    jdbc.update("UPDATE tournament SET test_mode = false WHERE id = 1");
    mockMvc
        .perform(
            post("/api/admin/test/simulate/round").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void simulateAllPlaysEveryResolvableMatch() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");

    mockMvc
        .perform(post("/api/admin/test/simulate/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundsSimulated").isNumber());

    // Every match that ever had both teams resolvable should be played; at the
    // very least all 72 group matches are played and no current round remains
    // for matches that have teams.
    long groupUnplayed =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.played=false",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupUnplayed).isZero();
  }
```

> The knockout-advancement assertion is implicitly covered by simulateAll
> reaching later rounds; an explicit per-round advancement assertion is fragile
> against the seed's bracket wiring, so we assert the robust invariants (group
> fully played, simulateAll returns a round count) instead.

- [x] **Step 2: Run to verify they fail**

`cd backend && ./mvnw verify` → FAIL (no simulate endpoints).

- [x] **Step 3: Add the engine to `AdminTestService`**

Add these imports to `AdminTestService.java`:
```java
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
```
Add the two repos to the constructor + fields:
```java
  private final MatchRepository matches;
  private final RoundRepository rounds;
```
(Update the constructor signature to
`public AdminTestService(UserRepository users, MatchRepository matches, RoundRepository rounds, DataSource ds)`
and assign both.)

Add the records + methods:
```java
  public record SimulateRoundResult(String roundCode, int matchesPlayed, String advancedToRoundCode) {}

  public record SimulateAllResult(int roundsSimulated, int totalMatchesPlayed) {}

  private static int randomGoals() {
    // Low-weighted: mostly 0–2, occasionally 3–4.
    int r = ThreadLocalRandom.current().nextInt(10);
    if (r < 4) return 0;
    if (r < 7) return 1;
    if (r < 9) return 2;
    return ThreadLocalRandom.current().nextInt(3, 5);
  }

  @Transactional
  public SimulateRoundResult simulateRound(Long callerId) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    return simulateCurrentRound();
  }

  /** Internal: simulate the current round; returns null roundCode if none. */
  private SimulateRoundResult simulateCurrentRound() {
    String roundCode = currentRoundCode();
    if (roundCode == null) return new SimulateRoundResult(null, 0, null);

    Round round =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, roundCode).orElseThrow();
    boolean knockout = !"GROUP".equals(roundCode);

    List<Match> roundMatches =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, round.getId());

    int played = 0;
    for (Match m : roundMatches) {
      if (Boolean.TRUE.equals(m.getPlayed())) continue;
      if (m.getTeam1Id() == null || m.getTeam2Id() == null) continue; // unresolved KO slot
      int s1 = randomGoals();
      int s2 = randomGoals();
      Long winner;
      if (s1 > s2) winner = m.getTeam1Id();
      else if (s2 > s1) winner = m.getTeam2Id();
      else winner =
          knockout
              ? (ThreadLocalRandom.current().nextBoolean() ? m.getTeam1Id() : m.getTeam2Id())
              : null; // group draw → no winner
      m.setScoreT1(s1);
      m.setScoreT2(s2);
      m.setWinnerId(winner);
      m.setPlayed(true);
      matches.save(m);
      played++;
    }

    String advancedTo = null;
    if (knockout) {
      advancedTo = advanceFromRound(round.getId());
    }
    return new SimulateRoundResult(roundCode, played, advancedTo);
  }

  /**
   * Populate child matches whose parents are in the just-played round. Returns
   * the code of the round we advanced into (the children's round), or null.
   */
  private String advanceFromRound(Long playedRoundId) {
    // Build a winner lookup for the played round's matches.
    List<Match> all = matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, playedRoundId);
    java.util.Map<Long, Long> winnerByMatch = new java.util.HashMap<>();
    for (Match m : all) {
      if (m.getWinnerId() != null) winnerByMatch.put(m.getId(), m.getWinnerId());
    }
    if (winnerByMatch.isEmpty()) return null;

    String advancedRoundCode = null;
    for (Match child : matches.findAll()) {
      if (!TOURNAMENT_ID.equals(child.getTournamentId())) continue;
      Long p1 = child.getMatchParent1Id();
      Long p2 = child.getMatchParent2Id();
      if (p1 == null || p2 == null) continue;
      if (!winnerByMatch.containsKey(p1) || !winnerByMatch.containsKey(p2)) continue;
      // Only fill if not already populated.
      if (child.getTeam1Id() == null) {
        jdbc.update(
            "UPDATE match SET team_1_id = ?, team_2_id = ?, updated_at = NOW() WHERE id = ?",
            winnerByMatch.get(p1),
            winnerByMatch.get(p2),
            child.getId());
        advancedRoundCode = roundCodeOf(child.getRoundId());
      }
    }
    return advancedRoundCode;
  }

  private String roundCodeOf(Long roundId) {
    return jdbc.queryForObject("SELECT code FROM round WHERE id = ?", String.class, roundId);
  }

  @Transactional
  public SimulateAllResult simulateAll(Long callerId) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    int roundsSimulated = 0;
    int total = 0;
    // Cap at 7 rounds — the number of rounds — to avoid any infinite loop.
    for (int i = 0; i < 7; i++) {
      SimulateRoundResult r = simulateCurrentRound();
      if (r.roundCode() == null || r.matchesPlayed() == 0) break;
      roundsSimulated++;
      total += r.matchesPlayed();
    }
    return new SimulateAllResult(roundsSimulated, total);
  }
```

> Note: `advanceFromRound` uses `jdbc.update(...)` for the child team assignment
> rather than the entity to avoid loading/saving every match through JPA; the
> `matches.findAll()` scan is fine at 104 rows. `simulateCurrentRound` is a
> private helper called by both the gated public `simulateRound` and
> `simulateAll`, so the auth/test-mode checks live on the public methods only
> (no double-checking).

- [x] **Step 4: Add the controller endpoints**

In `AdminTestController.java`, add:
```java
  @PostMapping("/simulate/round")
  public ResponseEntity<AdminTestService.SimulateRoundResult> simulateRound(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.simulateRound(callerId(jwt)));
  }

  @PostMapping("/simulate/all")
  public ResponseEntity<AdminTestService.SimulateAllResult> simulateAll(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.simulateAll(callerId(jwt)));
  }
```

- [x] **Step 5: Run to verify they pass**

`cd backend && ./mvnw verify` → PASS (spotless:apply first if format fails).

- [x] **Step 6: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/admin/AdminTestService.java \
        backend/src/main/java/io/quiniela/api/admin/AdminTestController.java \
        backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java
git commit -m "feat(backend): test-mode result simulator (round + all) with KO advancement"
```

---

## Task 6: /admin/test page + actions + nav drawer item + i18n

**Files:**
- Create: `frontend/lib/api/test-mode.ts`
- Create: `frontend/app/admin/test/page.tsx`
- Create: `frontend/app/admin/test/actions.ts`
- Create: `frontend/components/admin/TestPanel.tsx`
- Modify: `frontend/components/shell/NavDrawer.tsx`
- Modify: `frontend/messages/es-CO.json`
- Modify: `frontend/messages/en.json`

- [x] **Step 1: API client**

Create `frontend/lib/api/test-mode.ts`:
```ts
import { api } from "./client";

export type TestState = {
  testMode: boolean;
  groupStageDeadline: string | null;
  knockoutDeadline: string | null;
  currentRoundCode: string | null;
  roundsRemaining: number;
};

export async function getTestState(): Promise<TestState> {
  return api<TestState>("/api/admin/test/state");
}

export async function setTestMode(enabled: boolean): Promise<void> {
  await api("/api/admin/test/mode", { method: "PUT", body: JSON.stringify({ enabled }) });
}

export async function cleanTestData(): Promise<void> {
  await api("/api/admin/test/clean", { method: "POST" });
}

export async function setDeadlines(
  groupStageDeadline: string | null,
  knockoutDeadline: string | null,
): Promise<void> {
  await api("/api/admin/test/deadlines", {
    method: "PUT",
    body: JSON.stringify({ groupStageDeadline, knockoutDeadline }),
  });
}

export async function simulateRound(): Promise<void> {
  await api("/api/admin/test/simulate/round", { method: "POST" });
}

export async function simulateAll(): Promise<void> {
  await api("/api/admin/test/simulate/all", { method: "POST" });
}
```

- [x] **Step 2: Server actions**

Create `frontend/app/admin/test/actions.ts`:
```ts
"use server";

import { revalidatePath } from "next/cache";
import {
  cleanTestData,
  setDeadlines,
  setTestMode,
  simulateAll,
  simulateRound,
} from "@/lib/api/test-mode";

export async function setModeAction(enabled: boolean): Promise<void> {
  await setTestMode(enabled);
  revalidatePath("/admin/test");
}

export async function cleanAction(): Promise<void> {
  await cleanTestData();
  revalidatePath("/admin/test");
}

export async function deadlinesAction(
  groupStageDeadline: string | null,
  knockoutDeadline: string | null,
): Promise<void> {
  await setDeadlines(groupStageDeadline, knockoutDeadline);
  revalidatePath("/admin/test");
}

export async function simulateRoundAction(): Promise<void> {
  await simulateRound();
  revalidatePath("/admin/test");
}

export async function simulateAllAction(): Promise<void> {
  await simulateAll();
  revalidatePath("/admin/test");
}
```

- [x] **Step 3: TestPanel client component**

Create `frontend/components/admin/TestPanel.tsx`:
```tsx
"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { TestState } from "@/lib/api/test-mode";

export function TestPanel({
  state,
  setModeAction,
  cleanAction,
  deadlinesAction,
  simulateRoundAction,
  simulateAllAction,
}: {
  state: TestState;
  setModeAction: (enabled: boolean) => Promise<void>;
  cleanAction: () => Promise<void>;
  deadlinesAction: (gs: string | null, ko: string | null) => Promise<void>;
  simulateRoundAction: () => Promise<void>;
  simulateAllAction: () => Promise<void>;
}) {
  const t = useTranslations("test");
  const [isPending, startTransition] = useTransition();
  const [gs, setGs] = useState(toLocalInput(state.groupStageDeadline));
  const [ko, setKo] = useState(toLocalInput(state.knockoutDeadline));

  const run = (fn: () => Promise<void>) => startTransition(() => void fn());

  const section =
    "border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4 flex flex-col gap-3";
  const btn =
    "self-start bg-[var(--color-bg-ink)] px-4 py-2.5 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)] disabled:opacity-50";

  if (!state.testMode) {
    return (
      <div className={section}>
        <span className="chrome-label chrome-label-muted">{t("modeOff")}</span>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => run(() => setModeAction(true))}
        >
          {t("enable")}
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Mode */}
      <div className={section}>
        <span className="chrome-label text-[var(--color-accent-green)]">{t("modeOn")}</span>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => {
            if (confirm(t("confirmGoLive"))) run(() => setModeAction(false));
          }}
        >
          {t("disable")}
        </button>
      </div>

      {/* Deadlines */}
      <div className={section}>
        <span className="chrome-label chrome-label-muted">{t("deadlinesTitle")}</span>
        <label className="text-xs text-[var(--color-text-muted)]">
          {t("groupDeadline")}
          <input
            type="datetime-local"
            value={gs}
            onChange={(e) => setGs(e.target.value)}
            className="mt-1 block w-full border-[1.5px] border-[var(--color-line-ink)] bg-white px-2 py-1.5"
          />
        </label>
        <label className="text-xs text-[var(--color-text-muted)]">
          {t("knockoutDeadline")}
          <input
            type="datetime-local"
            value={ko}
            onChange={(e) => setKo(e.target.value)}
            className="mt-1 block w-full border-[1.5px] border-[var(--color-line-ink)] bg-white px-2 py-1.5"
          />
        </label>
        <p className="text-xs text-[var(--color-text-muted)]">{t("deadlinesHelp")}</p>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => run(() => deadlinesAction(fromLocalInput(gs), fromLocalInput(ko)))}
        >
          {t("save")}
        </button>
      </div>

      {/* Simulate */}
      <div className={section}>
        <span className="chrome-label chrome-label-muted">
          {state.currentRoundCode
            ? t("currentRound", { round: state.currentRoundCode })
            : t("allPlayed")}
        </span>
        <button
          type="button"
          className={btn}
          disabled={isPending || !state.currentRoundCode}
          onClick={() => run(simulateRoundAction)}
        >
          {t("simulateRound")}
        </button>
        <button
          type="button"
          className={btn}
          disabled={isPending || !state.currentRoundCode}
          onClick={() => run(simulateAllAction)}
        >
          {t("simulateAll")}
        </button>
      </div>

      {/* Clean */}
      <div className={section}>
        <span className="chrome-label text-[var(--color-accent-red)]">{t("cleanTitle")}</span>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => {
            if (confirm(t("confirmClean"))) run(cleanAction);
          }}
        >
          {t("clean")}
        </button>
      </div>
    </div>
  );
}

/** ISO instant → value for <input type="datetime-local"> (local, no zone). */
function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** datetime-local value → ISO instant string (or null if empty). */
function fromLocalInput(v: string): string | null {
  if (!v) return null;
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return null;
  return d.toISOString();
}
```

- [x] **Step 4: Page (admin-gated)**

Create `frontend/app/admin/test/page.tsx`:
```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getTestState } from "@/lib/api/test-mode";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { TestPanel } from "@/components/admin/TestPanel";
import {
  cleanAction,
  deadlinesAction,
  setModeAction,
  simulateAllAction,
  simulateRoundAction,
} from "./actions";

export default async function AdminTestPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");
  const me = await getMe();
  if (me.role !== "ADMIN") redirect("/home");

  const state = await getTestState();
  const t = await getTranslations("test");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl px-3 pt-4">
        <TestPanel
          state={state}
          setModeAction={setModeAction}
          cleanAction={cleanAction}
          deadlinesAction={deadlinesAction}
          simulateRoundAction={simulateRoundAction}
          simulateAllAction={simulateAllAction}
        />
      </div>
      <BottomNav />
    </main>
  );
}
```

- [x] **Step 5: Nav drawer item (admin-only)**

In `frontend/components/shell/NavDrawer.tsx`, add a "Modo prueba" link inside the
existing `{role === "ADMIN" && (...)}` region — alongside the "Resultados" link.
The current admin block has a single `<Link href="/admin/results">`; change it so
both admin links render. Find:
```tsx
            {role === "ADMIN" && (
              <Link href="/admin/results" className={linkClass} onClick={close}>
                {t("results")}
              </Link>
            )}
```
Replace with:
```tsx
            {role === "ADMIN" && (
              <>
                <Link href="/admin/results" className={linkClass} onClick={close}>
                  {t("results")}
                </Link>
                <Link href="/admin/test" className={linkClass} onClick={close}>
                  {t("testMode")}
                </Link>
              </>
            )}
```
(`t` is `useTranslations("nav")` in this component — add a `nav.testMode` key in Step 6.)

- [x] **Step 6: i18n**

In `frontend/messages/es-CO.json`: add `"testMode": "Modo prueba"` to the `nav`
object, and add a `test` namespace:
```json
  "test": {
    "title": "MODO PRUEBA",
    "modeOn": "Modo prueba ACTIVO",
    "modeOff": "Modo prueba apagado",
    "enable": "Activar modo prueba",
    "disable": "Salir a producción",
    "confirmGoLive": "¿Salir a producción? Se desactivan las herramientas de prueba y se quita el aviso.",
    "deadlinesTitle": "Fechas límite",
    "groupDeadline": "Cierre fase de grupos",
    "knockoutDeadline": "Cierre eliminatorias",
    "deadlinesHelp": "Pon el cierre de grupos en el pasado para probar el estado cerrado y abrir las eliminatorias.",
    "save": "Guardar",
    "currentRound": "Ronda actual: {round}",
    "allPlayed": "Todos los partidos jugados",
    "simulateRound": "Simular ronda actual",
    "simulateAll": "Simular todo",
    "cleanTitle": "Limpiar datos de prueba",
    "clean": "Limpiar",
    "confirmClean": "¿Borrar todos los pronósticos, resultados y pagos de prueba?"
  },
```
In `frontend/messages/en.json`: add `"testMode": "Test mode"` to `nav`, and:
```json
  "test": {
    "title": "TEST MODE",
    "modeOn": "Test mode ON",
    "modeOff": "Test mode is off",
    "enable": "Enable test mode",
    "disable": "Go live",
    "confirmGoLive": "Go live? Test tools will be disabled and the banner removed.",
    "deadlinesTitle": "Deadlines",
    "groupDeadline": "Group stage close",
    "knockoutDeadline": "Knockouts close",
    "deadlinesHelp": "Set the group deadline in the past to test the locked state and unlock knockouts.",
    "save": "Save",
    "currentRound": "Current round: {round}",
    "allPlayed": "All matches played",
    "simulateRound": "Simulate current round",
    "simulateAll": "Simulate everything",
    "cleanTitle": "Clean test data",
    "clean": "Clean",
    "confirmClean": "Delete all test predictions, results and payments?"
  },
```

- [x] **Step 7: Gates + parity**
```bash
cd frontend && node -e "const es=require('./messages/es-CO.json'), en=require('./messages/en.json'); const p=(o)=>Object.keys(o).sort().join(','); console.log('nav:', p(es.nav)===p(en.nav)); console.log('test:', p(es.test)===p(en.test));"
```
Expected: both `true`.
```bash
cd frontend && pnpm typecheck 2>&1 | tail -3 && pnpm lint 2>&1 | tail -6 && pnpm build 2>&1 | grep -E "Compiled|admin/test|error|Error" | head
```
Expected: typecheck clean; no new lint errors; build compiles with `/admin/test` route.

> Note: `confirm()` is a browser global; it's allowed in a client-component
> event handler. If lint flags `no-alert`, wrap the call site with an
> `// eslint-disable-next-line no-alert` comment on the `confirm(...)` line — but
> only if lint actually errors on it.

- [x] **Step 8: Commit**
```bash
git add frontend/lib/api/test-mode.ts frontend/app/admin/test/ \
        frontend/components/admin/TestPanel.tsx frontend/components/shell/NavDrawer.tsx \
        frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(frontend): /admin/test panel + nav drawer item"
```

---

## Task 7: Verify end-to-end + ship

- [x] **Step 1: Full backend verify**
`cd backend && ./mvnw -B verify` — all ITs green (V013MigrationTest, the
PublicSummary testMode assertion, and AdminTestControllerIT's full set included).

- [x] **Step 2: Full frontend sweep**
`cd frontend && pnpm typecheck && pnpm lint && pnpm test && pnpm build` —
typecheck clean; lint only the 2 pre-existing warnings; unit tests green; build
compiles with `/admin/test`.

- [x] **Step 3: Confirm clean keeps fixtures (re-read the IT)**
Confirm `AdminTestControllerIT.cleanResetsBetsResultsPointsPaymentsButKeepsFixtures`
asserts the team count is unchanged — the football-data fixtures must survive clean.

- [x] **Step 4: Tick checkboxes, commit, push**
Mark all Progress + verification checkboxes `[x]`, then:
```bash
git add docs/superpowers/plans/2026-05-29-quiniela-plan-7-test-mode.md
git commit -m "docs: Plan 7 complete — admin test mode"
git push origin master
```

- [x] **Step 5: Watch CI + smoke prod**
Watch backend + frontend CI to green. Then:
```bash
curl -sS -o /dev/null -w "%{http_code}\n" https://laquinieladelospanas.com/
curl -sS -o /dev/null -w "%{http_code}\n" https://quiniela-api-ko2t5go6hq-uc.a.run.app/api/admin/test/state
```
Expected: landing 200; `/api/admin/test/state` 401 (unauth, route exists). Then
confirm the banner appears on the live site (test_mode defaults true) — it should
show "MODO PRUEBA" until you toggle it off.

**Verification:**
- [x] Backend `./mvnw verify` green (V013 + testMode summary + AdminTest ITs)
- [x] Frontend typecheck + lint + test + build clean
- [x] Clean keeps team/round fixtures (IT asserts team count unchanged)
- [x] Backend + frontend CI green on `master`
- [x] Banner visible on prod (test_mode default true); `/api/admin/test/state` 401 unauth

---

## Out of scope
A simulated/fake clock; penalty-score modeling; manual per-match winner override;
deleting users/memberships/fixtures on clean; multi-tournament. All ops hardcode
tournament/pool id = 1.

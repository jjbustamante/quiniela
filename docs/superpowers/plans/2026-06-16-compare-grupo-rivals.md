# Compare GRUPO — date organization + rival-aware drill-down — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the `/matches` Date↔Stage organization to both `/compare` tabs (defaulting to Today), and add a rivals-above highlight plus names-on-demand drill-down to the GRUPO consensus view.

**Architecture:** Backend `CompareService` (JdbcTemplate) gains (a) past/today/upcoming partitioning copied from `MatchesService`, (b) per-scoreline rivals-above counts via a `COUNT(*) FILTER` on the distribution query, and (c) a new lazy per-match picks endpoint. Frontend converts the two server-rendered compare components (`GroupConsensus`, `H2HCompare`) to client components with the same toggle `MatchTabs` uses; the drill-down fetches named picks through a Next.js Server Action wrapping the existing `api()` helper.

**Tech Stack:** Spring Boot 4 + Java 25 + JdbcTemplate + Postgres; Next.js 16 + TypeScript + Tailwind + next-intl; Vitest + Testing Library (frontend), JUnit + MockMvc + Testcontainers `AbstractIntegrationTest` (backend).

## Global Constraints

- **Spring Boot 4 idioms only**; no schema migration in this work (all reads).
- **UI copy stays Spanish** (`es-CO.json` is the canonical locale; `en.json` mirrors).
- **Pool/tournament are hardcoded `1L`** in compare services, matching existing code.
- **"Revealed" gate is law:** a match's picks are exposed only when `played == true` OR `LockClock.isMatchRevealable(now, deadlines, roundCode)` — server-side, never UI-only.
- **"Rival above me" = strictly greater `quiniela.points`, admins excluded (`role <> 'admin'`), bots included** — same population as `/api/ranking`.
- **TDD, frequent commits**, one logical change per commit.
- Backend tests run: `cd backend && ./mvnw test` (unit, Surefire) / `./mvnw verify -Pintegration` for ITs — **verify the exact integration profile/command in `backend/pom.xml` before first run** and use whatever the existing ITs use.
- Frontend tests run: `cd frontend && pnpm test` (Vitest). Type check: `pnpm typecheck`.

---

## File Structure

**Backend (modify):**
- `backend/src/main/java/io/quiniela/api/compare/CompareService.java` — DTO shape changes, partition, rivals-above, new picks method.
- `backend/src/main/java/io/quiniela/api/compare/CompareController.java` — new `GET /api/compare/match/{matchId}/picks`.
- `backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java` — adapt to partitioned shape; add rivals-above + picks ITs.
- `backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java` — adapt to partitioned shape.

**Frontend (modify/create):**
- `frontend/lib/api/compare.ts` — type changes + `getMatchPicks`.
- `frontend/lib/actions/compare-picks.ts` — **create**: server action wrapping `getMatchPicks`.
- `frontend/components/compare/GroupConsensus.tsx` — server→client, toggle, rivals UI, drill-down.
- `frontend/components/compare/H2HCompare.tsx` — server→client, toggle.
- `frontend/components/compare/GroupConsensus.test.tsx` — adapt + new cases.
- `frontend/components/compare/H2HCompare.test.tsx` — adapt + new cases.
- `frontend/app/compare/page.tsx` — no logic change; confirm it still passes `data` (client components accept serialized props).
- `frontend/messages/es-CO.json`, `frontend/messages/en.json` — new `compare` keys.

---

## Task 1: Backend — partition GroupConsensusView into past/today/upcoming

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`
- Test: `backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java`

**Interfaces:**
- Produces: `record GroupConsensusView(Instant serverTime, List<MatchConsensus> past, List<MatchConsensus> today, List<MatchConsensus> upcoming)` — replaces the flat `matches` list. Task 5 (frontend types) and Task 6 consume this.

- [ ] **Step 1: Adapt existing ITs to the new shape (make them fail).**

In `CompareGroupConsensusIT.java`, replace every `$.matches[...]` jsonPath with a bucket-agnostic deep scan so the assertions don't depend on which day-bucket a fixed-date fixture lands in. Examples:

```java
// hidesDistributionBeforeGroupLock — was $.matches[0]
.andExpect(jsonPath("$..[?(@.matchId == 1)].revealed").value(org.hamcrest.Matchers.hasItem(false)))
.andExpect(jsonPath("$..[?(@.matchId == 1)].distribution.length()").value(org.hamcrest.Matchers.hasItem(0)))
.andExpect(jsonPath("$..[?(@.matchId == 1)].myScoreT1").value(org.hamcrest.Matchers.hasItem(2)))

// revealsConsensusAfterGroupLock — was $.matches[0]
.andExpect(jsonPath("$..[?(@.matchId == 1)].totalPicks").value(org.hamcrest.Matchers.hasItem(3)))
.andExpect(jsonPath("$..[?(@.matchId == 1)].majority").value(org.hamcrest.Matchers.hasItem(true)))
.andExpect(jsonPath("$..[?(@.matchId == 1)].rebel").value(org.hamcrest.Matchers.hasItem(false)))

// flagsRebelWhenOnlyPickerOfAScore — was $.matches[0]
.andExpect(jsonPath("$..[?(@.matchId == 1)].majority").value(org.hamcrest.Matchers.hasItem(false)))
.andExpect(jsonPath("$..[?(@.matchId == 1)].rebel").value(org.hamcrest.Matchers.hasItem(true)))

// revealsAPlayedKnockoutMatchEvenBeforeTheKnockoutDeadline — was $.matches[?(@.matchId == 73)]
.andExpect(jsonPath("$..[?(@.matchId == 73)].revealed").value(org.hamcrest.Matchers.hasItem(true)))
.andExpect(jsonPath("$..[?(@.matchId == 73)].totalPicks").value(org.hamcrest.Matchers.hasItem(2)))
```

Add an explicit partition-shape assertion to one test (e.g. `revealsConsensusAfterGroupLock`):

```java
.andExpect(jsonPath("$.serverTime").exists())
.andExpect(jsonPath("$.past").isArray())
.andExpect(jsonPath("$.today").isArray())
.andExpect(jsonPath("$.upcoming").isArray());
```

- [ ] **Step 2: Run the ITs to confirm they fail.**

Run the GroupConsensus IT (use the project's integration command). Expected: FAIL — current response has `matches`, not `past/today/upcoming`/`serverTime`; deep-scan assertions also fail against the flat shape.

- [ ] **Step 3: Change the DTO and add the partition helper.**

In `CompareService.java`, replace the record:

```java
public record GroupConsensusView(
    Instant serverTime,
    List<MatchConsensus> past,
    List<MatchConsensus> today,
    List<MatchConsensus> upcoming) {}
```

Add a private day-bounds helper (SQL copied verbatim from `MatchesService.getMatches`, which buckets by the caller's local calendar day, COALESCE to `America/Bogota`):

```java
private record DayBounds(Instant startOfToday, Instant startOfTomorrow) {}

private DayBounds dayBounds(Long userId) {
  Instant[] b =
      jdbc.queryForObject(
          "SELECT DATE_TRUNC('day', NOW() AT TIME ZONE u.tz) AT TIME ZONE u.tz AS today_start, "
              + "(DATE_TRUNC('day', NOW() AT TIME ZONE u.tz) + INTERVAL '1 day') AT TIME ZONE u.tz "
              + "  AS tomorrow_start "
              + "FROM (SELECT COALESCE((SELECT timezone FROM users WHERE id = ?), "
              + "                      'America/Bogota') AS tz) u",
          (rs, n) ->
              new Instant[] {
                rs.getTimestamp("today_start").toInstant(),
                rs.getTimestamp("tomorrow_start").toInstant()
              },
          userId);
  return new DayBounds(b[0], b[1]);
}
```

- [ ] **Step 4: Partition the result in `getGroupConsensus`.**

At the end of `getGroupConsensus`, replace `return new GroupConsensusView(out);` with bucketing by `Instant.parse(mc.kickoffAt())` (the field is the ISO string from `MatchMeta`):

```java
DayBounds bounds = dayBounds(userId);
List<MatchConsensus> past = new ArrayList<>();
List<MatchConsensus> today = new ArrayList<>();
List<MatchConsensus> upcoming = new ArrayList<>();
for (MatchConsensus mc : out) {
  Instant k = Instant.parse(mc.kickoffAt());
  if (k.isBefore(bounds.startOfToday())) past.add(mc);
  else if (k.isBefore(bounds.startOfTomorrow())) today.add(mc);
  else upcoming.add(mc);
}
past.sort((a, b) -> Instant.parse(b.kickoffAt()).compareTo(Instant.parse(a.kickoffAt())));
return new GroupConsensusView(
    Instant.now(), List.copyOf(past), List.copyOf(today), List.copyOf(upcoming));
```

(`out` is already kickoff-ASC from `fetchMatchMeta`, so `today`/`upcoming` stay chronological; only `past` is reversed — matching `MatchesService`.)

- [ ] **Step 5: Run the ITs to confirm they pass.**

Run the GroupConsensus IT. Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add backend/src/main/java/io/quiniela/api/compare/CompareService.java \
        backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java
git commit -m "feat(compare): partition group consensus into past/today/upcoming"
```

---

## Task 2: Backend — rivals-above counts on the GRUPO distribution

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`
- Test: `backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java`

**Interfaces:**
- Produces:
  - `record ScoreCount(int scoreT1, int scoreT2, int count, int rivalsAboveCount)`
  - `MatchConsensus` gains trailing fields `int rivalsAboveTotal, int rivalsAbovePicked`.
  Task 5 + Task 7 consume these.

- [ ] **Step 1: Write a failing IT for rivals-above.**

Add to `CompareGroupConsensusIT.java`. The helper inserts quinielas with default `points = 0`; bump points directly so "above me" is deterministic.

```java
@Test
void countsRivalsRankedAboveMePerScoreline() throws Exception {
  jdbc.update(
      "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
  // Me: pick 2-1, 0 points (bottom).
  String me = userWithBetOnMatch1("rab-me", 2, 1);
  // Two rivals ABOVE me (more points) who also picked 2-1.
  String r1 = userWithBetOnMatch1("rab-above1", 2, 1);
  String r2 = userWithBetOnMatch1("rab-above2", 2, 1);
  jdbc.update("UPDATE quiniela SET points = 50 WHERE user_id = (SELECT id FROM users WHERE email = ?)", "rab-above1@example.com");
  jdbc.update("UPDATE quiniela SET points = 40 WHERE user_id = (SELECT id FROM users WHERE email = ?)", "rab-above2@example.com");
  // One rival BELOW me (0 points) who picked something else.
  userWithBetOnMatch1("rab-below", 0, 0);

  mockMvc
      .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$..[?(@.matchId == 1)].rivalsAboveTotal").value(org.hamcrest.Matchers.hasItem(2)))
      .andExpect(jsonPath("$..[?(@.matchId == 1)].rivalsAbovePicked").value(org.hamcrest.Matchers.hasItem(2)))
      // the 2-1 scoreline carries both rivals-above
      .andExpect(jsonPath("$..[?(@.matchId == 1)].distribution[?(@.scoreT1 == 2 && @.scoreT2 == 1)].rivalsAboveCount")
          .value(org.hamcrest.Matchers.hasItem(2)));
}
```

- [ ] **Step 2: Run it to confirm it fails.**

Expected: FAIL — `rivalsAboveTotal`/`rivalsAbovePicked`/`rivalsAboveCount` don't exist yet.

- [ ] **Step 3: Extend the records.**

```java
public record ScoreCount(int scoreT1, int scoreT2, int count, int rivalsAboveCount) {}

public record MatchConsensus(
    Long matchId, String roundCode,
    String team1Code, String team1Flag, String team2Code, String team2Flag,
    String kickoffAt,
    Integer actualScoreT1, Integer actualScoreT2,
    boolean played, boolean revealed,
    Integer myScoreT1, Integer myScoreT2,
    List<ScoreCount> distribution,
    int totalPicks, boolean majority, boolean rebel,
    int rivalsAboveTotal, int rivalsAbovePicked) {}
```

- [ ] **Step 4: Compute caller points + rivals-above total, and an above-filtered distribution.**

In `getGroupConsensus`, after `Map<Long, int[]> myBets = fetchBetsForUser(userId);` add:

```java
Integer myPointsObj =
    jdbc.query(
        "SELECT points FROM quiniela WHERE pool_id = ? AND user_id = ?",
        rs -> rs.next() ? rs.getInt("points") : null,
        POOL_ID, userId);
int myPoints = myPointsObj == null ? 0 : myPointsObj;

Integer rivalsAboveTotalObj =
    jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM quiniela q JOIN users u ON u.id = q.user_id
        WHERE q.pool_id = ? AND u.role <> 'admin' AND q.points > ?
        """,
        Integer.class, POOL_ID, myPoints);
int rivalsAboveTotal = rivalsAboveTotalObj == null ? 0 : rivalsAboveTotalObj;
```

Change the distribution aggregation query to also count above-me picks per scoreline (join `users` so the admin filter and points threshold apply to the FILTER only — the unfiltered `cnt` keeps existing `totalPicks` semantics):

```java
Map<Long, Map<String, int[]>> dist = new HashMap<>(); // key -> [count, aboveCount]
jdbc.query(
    """
    SELECT b.match_id, b.score_t1, b.score_t2,
           COUNT(*) AS cnt,
           COUNT(*) FILTER (WHERE u.role <> 'admin' AND q.points > ?) AS above_cnt
    FROM bet b
    JOIN quiniela q ON q.id = b.quiniela_id
    JOIN users u ON u.id = q.user_id
    WHERE q.pool_id = ?
    GROUP BY b.match_id, b.score_t1, b.score_t2
    """,
    rs -> {
      long mid = rs.getLong("match_id");
      String key = rs.getInt("score_t1") + ":" + rs.getInt("score_t2");
      dist.computeIfAbsent(mid, k -> new HashMap<>())
          .put(key, new int[] {rs.getInt("cnt"), rs.getInt("above_cnt")});
    },
    myPoints, POOL_ID);
```

- [ ] **Step 5: Build the per-scoreline counts and per-match `rivalsAbovePicked`.**

Replace the `if (revealed) { ... }` distribution-building block so it reads the `int[]{count, above}` values, and accumulate `rivalsAbovePicked`:

```java
List<ScoreCount> distribution = new ArrayList<>();
int total = 0;
int rivalsAbovePicked = 0;
boolean majority = false;
boolean rebel = false;

if (revealed) {
  Map<String, int[]> counts = dist.getOrDefault(m.id(), Map.of());
  int max = 0;
  for (var e : counts.entrySet()) {
    String[] parts = e.getKey().split(":");
    int c = e.getValue()[0];
    int above = e.getValue()[1];
    total += c;
    rivalsAbovePicked += above;
    max = Math.max(max, c);
    distribution.add(
        new ScoreCount(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), c, above));
  }
  distribution.sort((a, b) -> b.count() - a.count());
  if (mine != null) {
    int myCount = counts.containsKey(myT1 + ":" + myT2) ? counts.get(myT1 + ":" + myT2)[0] : 0;
    int peak = max;
    long peakCount = counts.values().stream().filter(v -> v[0] == peak).count();
    majority = myCount > 0 && myCount == max && peakCount == 1;
    rebel = myCount == 1 && total > 1;
  }
}
```

Then pass the two new trailing args into the `new MatchConsensus(...)` constructor: `..., rebel, rivalsAboveTotal, rivalsAbovePicked)`.

- [ ] **Step 6: Run the IT (and the Task 1 ITs) to confirm all pass.**

Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add backend/src/main/java/io/quiniela/api/compare/CompareService.java \
        backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java
git commit -m "feat(compare): add rivals-above counts to group consensus distribution"
```

---

## Task 3: Backend — `GET /api/compare/match/{matchId}/picks` (named drill-down)

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareController.java`
- Test: `backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java`

**Interfaces:**
- Produces:
  - `record MatchPick(String displayName, int rank, int points, boolean isYou, boolean isBot, boolean isAboveMe, int scoreT1, int scoreT2, Integer pointsEarned)`
  - `record MatchPicksView(Long matchId, Integer actualScoreT1, Integer actualScoreT2, boolean played, List<MatchPick> picks)`
  - `CompareService.getMatchPicks(Long userId, Long matchId)` returning `MatchPicksView`.
  - Endpoint `GET /api/compare/match/{matchId}/picks` → 200 with the view, 403 if the match is not revealed, 401 if unauthenticated.
  Task 5 + Task 8 consume the JSON shape.

- [ ] **Step 1: Write failing ITs for the picks endpoint.**

```java
@Test
void returnsNamedPicksRankedForARevealedMatch() throws Exception {
  jdbc.update(
      "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
  String me = userWithBetOnMatch1("pk-me", 2, 1);
  String top = userWithBetOnMatch1("pk-top", 1, 1);
  jdbc.update("UPDATE quiniela SET points = 99 WHERE user_id = (SELECT id FROM users WHERE email = ?)", "pk-top@example.com");

  mockMvc
      .perform(get("/api/compare/match/1/picks").header("Authorization", "Bearer " + me))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.matchId").value(1))
      .andExpect(jsonPath("$.picks.length()").value(2))
      // ranked by points DESC: the 99-point rival is first and is flagged above me
      .andExpect(jsonPath("$.picks[0].isAboveMe").value(true))
      .andExpect(jsonPath("$.picks[0].scoreT1").value(1))
      .andExpect(jsonPath("$.picks[?(@.isYou == true)].scoreT1").value(org.hamcrest.Matchers.hasItem(2)));
}

@Test
void forbidsPicksForAnUnrevealedMatch() throws Exception {
  jdbc.update(
      "UPDATE tournament SET group_stage_deadline = NOW() + INTERVAL '7 days' WHERE id = 1");
  String me = userWithBetOnMatch1("pk-hidden-me", 2, 1);

  mockMvc
      .perform(get("/api/compare/match/1/picks").header("Authorization", "Bearer " + me))
      .andExpect(status().isForbidden());
}

@Test
void picksRequiresAuth() throws Exception {
  mockMvc.perform(get("/api/compare/match/1/picks")).andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: Run them to confirm they fail.**

Expected: FAIL — endpoint returns 404 (no mapping).

- [ ] **Step 3: Add the records and service method.**

In `CompareService.java`:

```java
public record MatchPick(
    String displayName, int rank, int points,
    boolean isYou, boolean isBot, boolean isAboveMe,
    int scoreT1, int scoreT2, Integer pointsEarned) {}

public record MatchPicksView(
    Long matchId, Integer actualScoreT1, Integer actualScoreT2,
    boolean played, List<MatchPick> picks) {}

@Transactional(readOnly = true)
public MatchPicksView getMatchPicks(Long userId, Long matchId) {
  MatchMeta meta =
      jdbc.query(
          """
          SELECT m.id, r.code AS round_code, m.kickoff_at, m.score_t1, m.score_t2, m.played,
                 r.points_multiplier AS points_multiplier,
                 t1.code AS t1_code, t1.flag_emoji AS t1_flag,
                 t2.code AS t2_code, t2.flag_emoji AS t2_flag
          FROM match m
          JOIN round r ON r.id = m.round_id
          LEFT JOIN team t1 ON t1.id = m.team_1_id
          LEFT JOIN team t2 ON t2.id = m.team_2_id
          WHERE m.id = ? AND m.tournament_id = ?
          """,
          rs ->
              rs.next()
                  ? new MatchMeta(
                      rs.getLong("id"), rs.getString("round_code"),
                      rs.getString("t1_code"), rs.getString("t1_flag"),
                      rs.getString("t2_code"), rs.getString("t2_flag"),
                      rs.getTimestamp("kickoff_at").toInstant().toString(),
                      (Integer) rs.getObject("score_t1"), (Integer) rs.getObject("score_t2"),
                      rs.getBoolean("played"), rs.getInt("points_multiplier"))
                  : null,
          matchId, TOURNAMENT_ID);
  if (meta == null) {
    throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.NOT_FOUND, "Unknown match");
  }

  var deadlines = lockClock.fetchTournamentDeadlines(TOURNAMENT_ID);
  boolean revealed =
      meta.played() || LockClock.isMatchRevealable(Instant.now(), deadlines, meta.roundCode());
  if (!revealed) {
    throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.FORBIDDEN, "Match not revealed");
  }

  Integer myPointsObj =
      jdbc.query(
          "SELECT points FROM quiniela WHERE pool_id = ? AND user_id = ?",
          rs -> rs.next() ? rs.getInt("points") : null,
          POOL_ID, userId);
  int myPoints = myPointsObj == null ? 0 : myPointsObj;

  List<MatchPick> picks =
      jdbc.query(
          """
          WITH ranked AS (
            SELECT q.id AS quiniela_id, q.user_id, u.display_name, q.points, u.is_bot,
                   RANK() OVER (ORDER BY q.points DESC) AS rk
            FROM quiniela q JOIN users u ON u.id = q.user_id
            WHERE q.pool_id = ? AND u.role <> 'admin'
          )
          SELECT r.display_name, r.points, r.user_id, r.is_bot, r.rk,
                 b.score_t1, b.score_t2
          FROM ranked r
          JOIN bet b ON b.quiniela_id = r.quiniela_id AND b.match_id = ?
          ORDER BY r.rk ASC, r.display_name ASC
          """,
          (rs, n) -> {
            int s1 = rs.getInt("score_t1");
            int s2 = rs.getInt("score_t2");
            int pts = rs.getInt("points");
            long uid = rs.getLong("user_id");
            Integer earned =
                meta.played() && meta.actualT1() != null && meta.actualT2() != null
                    ? scoreMatchForBet(
                        meta.pointsMultiplier(), s1, s2, meta.actualT1(), meta.actualT2())
                    : null;
            return new MatchPick(
                rs.getString("display_name"),
                rs.getInt("rk"),
                pts,
                userId != null && userId.equals(uid),
                rs.getBoolean("is_bot"),
                pts > myPoints,
                s1, s2, earned);
          },
          POOL_ID, matchId);

  return new MatchPicksView(
      meta.id(), meta.actualT1(), meta.actualT2(), meta.played(), List.copyOf(picks));
}
```

- [ ] **Step 4: Add the controller mapping.**

In `CompareController.java` add:

```java
@GetMapping("/match/{matchId}/picks")
public ResponseEntity<CompareService.MatchPicksView> matchPicks(
    @AuthenticationPrincipal Jwt jwt,
    @org.springframework.web.bind.annotation.PathVariable Long matchId) {
  if (jwt == null) return ResponseEntity.status(401).build();
  Long userId = Long.parseLong(jwt.getSubject());
  return ResponseEntity.ok(service.getMatchPicks(userId, matchId));
}
```

(`ResponseStatusException` is handled by Spring's default resolver → correct 403/404 status; the existing `@ExceptionHandler(IllegalArgumentException.class)` is unaffected.)

- [ ] **Step 5: Run the ITs to confirm they pass.**

Expected: PASS (all three new tests + the existing suite).

- [ ] **Step 6: Commit.**

```bash
git add backend/src/main/java/io/quiniela/api/compare/CompareController.java \
        backend/src/main/java/io/quiniela/api/compare/CompareService.java \
        backend/src/test/java/io/quiniela/api/compare/CompareGroupConsensusIT.java
git commit -m "feat(compare): add per-match named picks endpoint with revealed guard"
```

---

## Task 4: Backend — partition H2HView into past/today/upcoming

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/compare/CompareService.java`
- Test: `backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java`

**Interfaces:**
- Produces: `record H2HView(Long rivalUserId, String rivalDisplayName, int agreeCount, int differCount, Integer myPoints, Integer rivalPoints, Instant serverTime, List<H2HMatch> past, List<H2HMatch> today, List<H2HMatch> upcoming)` — `matches` field removed; `agreeCount`/`differCount` remain global totals. Task 5 + Task 9 consume this.

- [ ] **Step 1: Adapt the existing H2H ITs to the new shape (make them fail).**

Open `CompareH2HIT.java`. Replace `$.matches[...]` assertions with deep-scan equivalents (same pattern as Task 1, e.g. `$..[?(@.matchId == 1)].state`), and add a shape assertion to one test:

```java
.andExpect(jsonPath("$.serverTime").exists())
.andExpect(jsonPath("$.past").isArray())
.andExpect(jsonPath("$.today").isArray())
.andExpect(jsonPath("$.upcoming").isArray());
```

Keep all `agreeCount` / `differCount` assertions unchanged (they're top-level and stay global).

- [ ] **Step 2: Run the H2H ITs to confirm they fail.**

Expected: FAIL — response still has flat `matches`.

- [ ] **Step 3: Change the DTO.**

```java
public record H2HView(
    Long rivalUserId, String rivalDisplayName,
    int agreeCount, int differCount,
    Integer myPoints, Integer rivalPoints,
    Instant serverTime,
    List<H2HMatch> past, List<H2HMatch> today, List<H2HMatch> upcoming) {}
```

- [ ] **Step 4: Partition at the end of `getH2H`.**

Replace `return new H2HView(rivalUserId, rivalName, agree, differ, myPoints, rivalPoints, matches);` with:

```java
DayBounds bounds = dayBounds(userId);
List<H2HMatch> past = new ArrayList<>();
List<H2HMatch> today = new ArrayList<>();
List<H2HMatch> upcoming = new ArrayList<>();
for (H2HMatch hm : matches) {
  Instant k = Instant.parse(hm.kickoffAt());
  if (k.isBefore(bounds.startOfToday())) past.add(hm);
  else if (k.isBefore(bounds.startOfTomorrow())) today.add(hm);
  else upcoming.add(hm);
}
past.sort((a, b) -> Instant.parse(b.kickoffAt()).compareTo(Instant.parse(a.kickoffAt())));
return new H2HView(
    rivalUserId, rivalName, agree, differ, myPoints, rivalPoints,
    Instant.now(), List.copyOf(past), List.copyOf(today), List.copyOf(upcoming));
```

(`dayBounds` from Task 1 is reused.)

- [ ] **Step 5: Run the H2H ITs to confirm they pass.**

Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add backend/src/main/java/io/quiniela/api/compare/CompareService.java \
        backend/src/test/java/io/quiniela/api/compare/CompareH2HIT.java
git commit -m "feat(compare): partition h2h view into past/today/upcoming"
```

---

## Task 5: Frontend — API types, server action, and translations

**Files:**
- Modify: `frontend/lib/api/compare.ts`
- Create: `frontend/lib/actions/compare-picks.ts`
- Modify: `frontend/messages/es-CO.json`, `frontend/messages/en.json`

**Interfaces:**
- Produces (consumed by Tasks 6–9):
  - `ScoreCount` gains `rivalsAboveCount: number`.
  - `MatchConsensus` gains `rivalsAboveTotal: number; rivalsAbovePicked: number`.
  - `GroupConsensusView = { serverTime: string; past: MatchConsensus[]; today: MatchConsensus[]; upcoming: MatchConsensus[] }`.
  - `H2HView` gains `serverTime` and `past/today/upcoming: H2HMatch[]` (replacing `matches`).
  - `MatchPick`, `MatchPicksView` types + `getMatchPicks(matchId)`.
  - Server action `fetchMatchPicks(matchId): Promise<MatchPicksView | null>`.
  - New `compare` i18n keys: `viewByDate`, `viewByStage`, `tabPast`, `tabToday`, `tabUpcoming`, `emptyPast`, `emptyToday`, `emptyUpcoming`, `rivalsAbove`, `rivalsAboveWith`, `picksTitle`, `picksAboveOnly`, `picksClose`, `colPoints`.

- [ ] **Step 1: Update `frontend/lib/api/compare.ts` types.**

Replace `ScoreCount`, `GroupConsensusView`, `H2HView` and add the picks types + fetchers:

```typescript
export type ScoreCount = {
  scoreT1: number;
  scoreT2: number;
  count: number;
  rivalsAboveCount: number;
};

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
  rivalsAboveTotal: number;
  rivalsAbovePicked: number;
};

export type GroupConsensusView = {
  serverTime: string;
  past: MatchConsensus[];
  today: MatchConsensus[];
  upcoming: MatchConsensus[];
};

// ...keep H2HMatch / H2HMatchState unchanged...

export type H2HView = {
  rivalUserId: number;
  rivalDisplayName: string | null;
  agreeCount: number;
  differCount: number;
  myPoints: number | null;
  rivalPoints: number | null;
  serverTime: string;
  past: H2HMatch[];
  today: H2HMatch[];
  upcoming: H2HMatch[];
};

export type MatchPick = {
  displayName: string | null;
  rank: number;
  points: number;
  isYou: boolean;
  isBot: boolean;
  isAboveMe: boolean;
  scoreT1: number;
  scoreT2: number;
  pointsEarned: number | null;
};

export type MatchPicksView = {
  matchId: number;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  played: boolean;
  picks: MatchPick[];
};

export async function getMatchPicks(matchId: number): Promise<MatchPicksView> {
  return api<MatchPicksView>(`/api/compare/match/${matchId}/picks`);
}
```

(`getGroupConsensus` and `getH2H` keep their signatures.)

- [ ] **Step 2: Create the server action `frontend/lib/actions/compare-picks.ts`.**

The `api()` helper is server-only (uses `auth()` + `redirect`). A client component cannot call it directly, so wrap it in a Server Action. Return `null` on the 403 (unrevealed) so the client can render nothing gracefully.

```typescript
"use server";

import { getMatchPicks } from "@/lib/api/compare";
import type { MatchPicksView } from "@/lib/api/compare";
import { ApiError } from "@/lib/api/client";

export async function fetchMatchPicks(matchId: number): Promise<MatchPicksView | null> {
  try {
    return await getMatchPicks(matchId);
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) return null;
    throw e;
  }
}
```

- [ ] **Step 3: Add translation keys to `frontend/messages/es-CO.json` (`compare` object).**

```json
"viewByDate": "Por fecha",
"viewByStage": "Por fase",
"tabPast": "PASADOS",
"tabToday": "HOY",
"tabUpcoming": "PRÓXIMOS",
"emptyPast": "Aún no hay partidos jugados",
"emptyToday": "Sin partidos hoy",
"emptyUpcoming": "No hay más partidos",
"rivalsAbove": "{n} por encima de ti",
"rivalsAboveWith": "{n} contigo",
"picksTitle": "Quién eligió qué",
"picksAboveOnly": "Por encima de ti",
"picksClose": "Cerrar",
"colPoints": "Pts"
```

- [ ] **Step 4: Add the same keys to `frontend/messages/en.json` (`compare` object).**

```json
"viewByDate": "By date",
"viewByStage": "By stage",
"tabPast": "PAST",
"tabToday": "TODAY",
"tabUpcoming": "UPCOMING",
"emptyPast": "No matches played yet",
"emptyToday": "No matches today",
"emptyUpcoming": "No more matches",
"rivalsAbove": "{n} ahead of you",
"rivalsAboveWith": "{n} with you",
"picksTitle": "Who picked what",
"picksAboveOnly": "Ahead of you",
"picksClose": "Close",
"colPoints": "Pts"
```

- [ ] **Step 5: Type-check.**

Run: `cd frontend && pnpm typecheck`
Expected: `compare.ts` and the action compile. (`GroupConsensus.tsx` / `H2HCompare.tsx` will now have type errors against the new shape — those are fixed in Tasks 6 & 9. If `typecheck` fails ONLY in those two files, that's expected; proceed.)

- [ ] **Step 6: Commit.**

```bash
git add frontend/lib/api/compare.ts frontend/lib/actions/compare-picks.ts \
        frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(compare): api types, picks server action, and i18n keys"
```

---

## Task 6: Frontend — GroupConsensus to a client component with Date↔Stage toggle

**Files:**
- Modify: `frontend/components/compare/GroupConsensus.tsx`
- Test: `frontend/components/compare/GroupConsensus.test.tsx`

**Interfaces:**
- Consumes: `GroupConsensusView` (partitioned), `MatchConsensus` from Task 5.
- Produces: a `"use client"` `GroupConsensus({ data }: { data: GroupConsensusView })` with `mode` (`date`/`stage`) + `tab` (`past`/`today`/`upcoming`) state, defaulting to Date→Today. `ConsensusCard` stays a child (extended in Tasks 7–8).

- [ ] **Step 1: Rewrite the test for the new shape + toggle (make it fail).**

Replace `GroupConsensus.test.tsx`. Note the component is now a client component using `useTranslations`, so wrap with `NextIntlClientProvider` carrying a `compare` + `home` message map (no more `vi.mock("next-intl/server")`), and render synchronously (no `await`).

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import type { GroupConsensusView, MatchConsensus } from "@/lib/api/compare";
import { GroupConsensus } from "./GroupConsensus";

const messages = {
  compare: {
    lockedTitle: "AÚN NO", lockedHelp: "se revela al cerrar",
    majority: "Con la mayoría", rebel: "Rebelde", youTag: "tú",
    viewByDate: "Por fecha", viewByStage: "Por fase",
    tabPast: "Pasados", tabToday: "Hoy", tabUpcoming: "Próximos",
    emptyPast: "—", emptyToday: "Sin partidos hoy", emptyUpcoming: "—",
    rivalsAbove: "{n} por encima de ti", rivalsAboveWith: "{n} contigo",
    picksTitle: "Quién eligió qué", picksAboveOnly: "Por encima de ti",
    picksClose: "Cerrar", colPoints: "Pts",
  },
  home: { chipGROUP: "Grupos", chipR32: "16vos" },
};

function card(over: Partial<MatchConsensus> & { matchId: number; roundCode: string; kickoffAt: string }): MatchConsensus {
  return {
    team1Code: "BRA", team1Flag: "🇧🇷", team2Code: "SRB", team2Flag: "🇷🇸",
    actualScoreT1: null, actualScoreT2: null, played: true, revealed: true,
    myScoreT1: 1, myScoreT2: 0,
    distribution: [{ scoreT1: 1, scoreT2: 0, count: 2, rivalsAboveCount: 0 }],
    totalPicks: 2, majority: true, rebel: false,
    rivalsAboveTotal: 0, rivalsAbovePicked: 0,
    ...over,
  };
}

function renderGC(data: GroupConsensusView) {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <GroupConsensus data={data} />
    </NextIntlClientProvider>,
  );
}

const base: GroupConsensusView = { serverTime: "2026-06-16T12:00:00Z", past: [], today: [], upcoming: [] };

describe("GroupConsensus", () => {
  it("defaults to the Today tab in date mode", () => {
    renderGC({ ...base, today: [card({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-16T15:00:00Z" })] });
    expect(screen.getByRole("button", { name: /hoy/i })).toBeInTheDocument();
    expect(screen.getByText("1–0")).toBeInTheDocument();
    expect(screen.queryByTestId("stage-header")).not.toBeInTheDocument();
  });

  it("shows an empty-today message when today is empty", () => {
    renderGC({ ...base, upcoming: [card({ matchId: 2, roundCode: "GROUP", kickoffAt: "2026-06-20T15:00:00Z" })] });
    // initial tab falls back to upcoming when today is empty (mirrors MatchTabs)
    expect(screen.getByText("1–0")).toBeInTheDocument();
  });

  it("switches to stage sections", async () => {
    renderGC({
      ...base,
      past: [card({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-01T15:00:00Z" })],
      today: [card({ matchId: 2, roundCode: "R32", kickoffAt: "2026-06-16T15:00:00Z" })],
    });
    await userEvent.click(screen.getByRole("button", { name: /por fase/i }));
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
  });

  it("shows the locked state when everything is empty", () => {
    renderGC(base);
    expect(screen.getByText("AÚN NO")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test to confirm it fails.**

Run: `cd frontend && pnpm test GroupConsensus`
Expected: FAIL — component is still a server component with the old flat `matches` prop.

- [ ] **Step 3: Rewrite `GroupConsensus.tsx` as a client component.**

```tsx
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { GroupConsensusView, MatchConsensus } from "@/lib/api/compare";
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { StageSection } from "@/components/shared/StageSection";
import { ConsensusCard } from "./ConsensusCard";

type Tab = "past" | "today" | "upcoming";
type Mode = "date" | "stage";

export function GroupConsensus({ data }: { data: GroupConsensusView }) {
  const t = useTranslations("compare");
  const tRound = useTranslations("home");

  const all = [...data.past, ...data.today, ...data.upcoming];
  const [mode, setMode] = useState<Mode>("date");
  const initial: Tab = data.today.length > 0 ? "today" : data.upcoming.length > 0 ? "upcoming" : "past";
  const [tab, setTab] = useState<Tab>(initial);

  if (all.length === 0) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-start justify-center gap-4 px-6 py-16">
        <h1 className="headline-display whitespace-pre-line text-[44px] sm:text-6xl">{t("lockedTitle")}</h1>
        <p className="font-sans text-base text-[var(--color-text-muted)]">{t("lockedHelp")}</p>
      </section>
    );
  }

  const now = new Date(data.serverTime).getTime();
  const list = tab === "past" ? data.past : tab === "today" ? data.today : data.upcoming;
  const emptyLabel = tab === "past" ? t("emptyPast") : tab === "today" ? t("emptyToday") : t("emptyUpcoming");

  return (
    <div className="mx-3 mt-3 flex flex-col">
      <div className="mb-3 flex gap-1.5">
        <ModeButton active={mode === "date"} onClick={() => setMode("date")}>{t("viewByDate")}</ModeButton>
        <ModeButton active={mode === "stage"} onClick={() => setMode("stage")}>{t("viewByStage")}</ModeButton>
      </div>

      {mode === "date" ? (
        <>
          <div className="flex gap-1 border-b-[1.5px] border-[var(--color-line-ink)] px-1">
            <TabButton active={tab === "past"} onClick={() => setTab("past")}>{t("tabPast")} · {data.past.length}</TabButton>
            <TabButton active={tab === "today"} onClick={() => setTab("today")}>{t("tabToday")} · {data.today.length}</TabButton>
            <TabButton active={tab === "upcoming"} onClick={() => setTab("upcoming")}>{t("tabUpcoming")} · {data.upcoming.length}</TabButton>
          </div>
          {list.length === 0 ? (
            <div className="mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
              <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">{emptyLabel}</p>
            </div>
          ) : (
            <div className="mt-3 flex flex-col gap-2">
              {list.map((m) => <ConsensusCard key={m.matchId} m={m} />)}
            </div>
          )}
        </>
      ) : (
        <div className="flex flex-col gap-2">
          {groupMatchesByStage(all, now).map((g, i) => (
            <StageSection key={g.roundCode} header={tRound(`chip${g.roundCode}` as never)} count={g.matches.length} defaultOpen={i === 0}>
              {g.matches.map((m) => <ConsensusCard key={m.matchId} m={m} />)}
            </StageSection>
          ))}
        </div>
      )}
    </div>
  );
}

function ModeButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className={`border-[1.5px] px-3 py-1.5 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${active ? "border-[var(--color-line-ink)] bg-[var(--color-accent-gold)] text-[var(--color-text-primary)]" : "border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"}`}>
      {children}
    </button>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className={`-mb-[1.5px] border-b-[3px] px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${active ? "border-[var(--color-accent-red)] text-[var(--color-text-primary)]" : "border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"}`}>
      {children}
    </button>
  );
}
```

- [ ] **Step 4: Extract `ConsensusCard` into its own client file (no behavior change yet).**

Create `frontend/components/compare/ConsensusCard.tsx` with the existing card markup, now a client component reading its own labels via `useTranslations("compare")`. (Tasks 7–8 extend it.)

```tsx
"use client";

import { useTranslations } from "next-intl";
import type { MatchConsensus } from "@/lib/api/compare";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

export function ConsensusCard({ m }: { m: MatchConsensus }) {
  const t = useTranslations("compare");
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
          <span className="rounded-full bg-[var(--color-accent-gold)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-line-ink)]">{t("rebel")}</span>
        ) : m.majority ? (
          <span className="rounded-full bg-[var(--color-line-ink)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-bg-paper)]">{t("majority")}</span>
        ) : null}
      </div>
      {top.map((s) => {
        const key = `${s.scoreT1}:${s.scoreT2}`;
        const isMine = key === mineKey;
        return (
          <div key={key} className="mb-1 flex items-center gap-2 text-xs">
            <span className={`w-9 font-extrabold ${isMine ? "text-[var(--color-accent-red)]" : ""}`}>{s.scoreT1}–{s.scoreT2}</span>
            <span className="h-3.5 flex-1 overflow-hidden rounded bg-[var(--color-line-soft,#e7ddcc)]">
              <span className={`block h-full ${isMine ? "bg-[var(--color-accent-red)]" : "bg-[#cbb8a0]"}`} style={{ width: `${Math.round((s.count / max) * 100)}%` }} />
            </span>
            <span className="w-10 text-right font-bold text-[var(--color-text-muted)]">{isMine ? t("youTag") : s.count}</span>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 5: Run the test to confirm it passes.**

Run: `cd frontend && pnpm test GroupConsensus`
Expected: PASS.

- [ ] **Step 6: Confirm the page still compiles.**

`frontend/app/compare/page.tsx` already does `<GroupConsensus data={await getGroupConsensus()} />` — passing serialized data to a client component is valid; no edit needed. Run `cd frontend && pnpm typecheck`. Expected: no errors in `GroupConsensus.tsx`/`ConsensusCard.tsx`/`page.tsx` (H2HCompare may still error until Task 9).

- [ ] **Step 7: Commit.**

```bash
git add frontend/components/compare/GroupConsensus.tsx \
        frontend/components/compare/ConsensusCard.tsx \
        frontend/components/compare/GroupConsensus.test.tsx
git commit -m "feat(compare): date/stage toggle on group consensus (default today)"
```

---

## Task 7: Frontend — rivals-above highlight in ConsensusCard

**Files:**
- Modify: `frontend/components/compare/ConsensusCard.tsx`
- Test: `frontend/components/compare/ConsensusCard.test.tsx` (create)

**Interfaces:**
- Consumes: `MatchConsensus.rivalsAboveTotal`, `rivalsAbovePicked`, and `ScoreCount.rivalsAboveCount` from Task 5.

- [ ] **Step 1: Write a failing test for the rivals-above markers.**

Create `frontend/components/compare/ConsensusCard.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import type { MatchConsensus } from "@/lib/api/compare";
import { ConsensusCard } from "./ConsensusCard";

const messages = {
  compare: {
    rebel: "Rebelde", majority: "Con la mayoría", youTag: "tú",
    rivalsAbove: "{n} por encima de ti", rivalsAboveWith: "{n} contigo",
    picksTitle: "Quién eligió qué", picksAboveOnly: "Por encima de ti",
    picksClose: "Cerrar", colPoints: "Pts",
  },
};

function wrap(m: MatchConsensus) {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <ConsensusCard m={m} />
    </NextIntlClientProvider>,
  );
}

const m: MatchConsensus = {
  matchId: 1, roundCode: "GROUP", team1Code: "MEX", team1Flag: "🇲🇽", team2Code: "RSA", team2Flag: "🇿🇦",
  kickoffAt: "2026-06-16T15:00:00Z", actualScoreT1: null, actualScoreT2: null, played: true, revealed: true,
  myScoreT1: 2, myScoreT2: 1,
  distribution: [
    { scoreT1: 2, scoreT2: 1, count: 5, rivalsAboveCount: 2 },
    { scoreT1: 2, scoreT2: 0, count: 13, rivalsAboveCount: 3 },
  ],
  totalPicks: 18, majority: false, rebel: false,
  rivalsAboveTotal: 5, rivalsAbovePicked: 5,
};

describe("ConsensusCard rivals-above", () => {
  it("shows the match-level rivals-above summary", () => {
    wrap(m);
    expect(screen.getByText("5 por encima de ti")).toBeInTheDocument();
  });

  it("marks bars where rivals-above picked that score", () => {
    wrap(m);
    expect(screen.getAllByTestId("rivals-above-mark").length).toBe(2);
  });

  it("renders no rivals-above UI when the caller is on top", () => {
    wrap({ ...m, rivalsAboveTotal: 0, rivalsAbovePicked: 0,
      distribution: m.distribution.map((s) => ({ ...s, rivalsAboveCount: 0 })) });
    expect(screen.queryByTestId("rivals-above-mark")).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run it to confirm it fails.**

Run: `cd frontend && pnpm test ConsensusCard`
Expected: FAIL — no summary text, no `rivals-above-mark` testids.

- [ ] **Step 3: Add the summary line and per-bar marks.**

In `ConsensusCard.tsx`, add the match-level summary just below the team header `</div>` (before the `{top.map(...)}`):

```tsx
{m.rivalsAboveTotal > 0 && (
  <p className="mb-2 text-[10px] font-bold uppercase tracking-wide text-[var(--color-text-muted)]">
    {t("rivalsAbove", { n: m.rivalsAboveTotal })}
  </p>
)}
```

Inside the per-bar row, append a mark after the count `<span>` when `s.rivalsAboveCount > 0`:

```tsx
{s.rivalsAboveCount > 0 && (
  <span data-testid="rivals-above-mark"
    className="ml-1 shrink-0 rounded bg-[var(--color-accent-gold)] px-1 text-[9px] font-extrabold text-[var(--color-line-ink)]">
    ↑{s.rivalsAboveCount}
  </span>
)}
```

(Place the mark inside the existing row flex container so it sits next to the count.)

- [ ] **Step 4: Run the test to confirm it passes.**

Run: `cd frontend && pnpm test ConsensusCard`
Expected: PASS. Also re-run `pnpm test GroupConsensus` — still green.

- [ ] **Step 5: Commit.**

```bash
git add frontend/components/compare/ConsensusCard.tsx \
        frontend/components/compare/ConsensusCard.test.tsx
git commit -m "feat(compare): rivals-above highlight on consensus bars"
```

---

## Task 8: Frontend — names-on-demand drill-down

**Files:**
- Modify: `frontend/components/compare/ConsensusCard.tsx`
- Test: `frontend/components/compare/ConsensusCard.test.tsx`

**Interfaces:**
- Consumes: `fetchMatchPicks(matchId)` server action + `MatchPicksView` from Task 5.

- [ ] **Step 1: Add a failing test for the drill-down (mock the server action).**

Append to `ConsensusCard.test.tsx`. Mock the action module so no real fetch happens:

```tsx
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import type { MatchPicksView } from "@/lib/api/compare";

vi.mock("@/lib/actions/compare-picks", () => ({
  fetchMatchPicks: vi.fn(async (): Promise<MatchPicksView> => ({
    matchId: 1, actualScoreT1: null, actualScoreT2: null, played: false,
    picks: [
      { displayName: "Carlos", rank: 1, points: 99, isYou: false, isBot: false, isAboveMe: true, scoreT1: 2, scoreT2: 0, pointsEarned: null },
      { displayName: "Tú", rank: 9, points: 0, isYou: true, isBot: false, isAboveMe: false, scoreT1: 2, scoreT2: 1, pointsEarned: null },
    ],
  })),
}));

describe("ConsensusCard drill-down", () => {
  it("loads and shows named picks when a bar is tapped", async () => {
    wrap(m);
    await userEvent.click(screen.getByRole("button", { name: /2–0/ }));
    expect(await screen.findByText("Carlos")).toBeInTheDocument();
  });

  it("filters to rivals-above when the ↑ mark is tapped", async () => {
    wrap(m);
    const marks = screen.getAllByTestId("rivals-above-mark");
    await userEvent.click(marks[0]);
    expect(await screen.findByText("Carlos")).toBeInTheDocument();
    // 'Tú' is not above me, so filtered out of the above-only view
    expect(screen.queryByText("Tú")).not.toBeInTheDocument();
  });
});
```

(For these, the score `<span>`/mark must be tappable — Step 3 makes the bar row a `<button>` and the mark a `<button>`.)

- [ ] **Step 2: Run it to confirm it fails.**

Run: `cd frontend && pnpm test ConsensusCard`
Expected: FAIL — bars/marks aren't interactive; no picks list renders.

- [ ] **Step 3: Make bars and marks open a per-match picks panel.**

In `ConsensusCard.tsx`:

- Add state + lazy cache:

```tsx
import { useState } from "react";
import { fetchMatchPicks } from "@/lib/actions/compare-picks";
import type { MatchPick, MatchPicksView } from "@/lib/api/compare";

// inside ConsensusCard:
const [picks, setPicks] = useState<MatchPicksView | null>(null);
const [filter, setFilter] = useState<{ score?: string; aboveOnly?: boolean } | null>(null);

async function open(score?: string, aboveOnly?: boolean) {
  setFilter({ score, aboveOnly });
  if (!picks) {
    const data = await fetchMatchPicks(m.matchId);
    setPicks(data);
  }
}
```

- Wrap the score label in a `<button onClick={() => open(`${s.scoreT1}:${s.scoreT2}`)}>` (keep the same classes; add `type="button"` and a left-aligned button reset). The accessible name includes the `s.scoreT1–s.scoreT2` text so the test's `name: /2–0/` matches.
- Change the `rivals-above-mark` span to a `<button type="button" data-testid="rivals-above-mark" onClick={() => open(`${s.scoreT1}:${s.scoreT2}`, true)}>` with the same classes.
- Render the panel at the bottom of the card when `filter` is set:

```tsx
{filter && (
  <div className="mt-3 border-t border-[var(--color-line-soft,#e7ddcc)] pt-2">
    <div className="mb-1 flex items-center justify-between">
      <span className="text-[10px] font-extrabold uppercase text-[var(--color-text-muted)]">
        {filter.aboveOnly ? t("picksAboveOnly") : t("picksTitle")}
      </span>
      <button type="button" className="text-[10px] font-bold uppercase text-[var(--color-accent-red)]" onClick={() => setFilter(null)}>
        {t("picksClose")}
      </button>
    </div>
    {picks === null ? null : (
      <ul className="flex flex-col gap-0.5">
        {filterPicks(picks.picks, filter).map((p, i) => (
          <li key={`${p.rank}-${i}`} className={`flex items-center justify-between text-xs ${p.isYou ? "font-extrabold text-[var(--color-accent-red)]" : ""}`}>
            <span>{p.isAboveMe ? "↑ " : ""}{p.displayName ?? "—"}{p.isBot ? " 🤖" : ""}</span>
            <span className="font-bold">
              {p.scoreT1}–{p.scoreT2}
              {p.pointsEarned !== null ? ` · +${p.pointsEarned}` : ""}
            </span>
          </li>
        ))}
      </ul>
    )}
  </div>
)}
```

- Add the filter helper (module scope):

```tsx
function filterPicks(picks: MatchPick[], f: { score?: string; aboveOnly?: boolean }): MatchPick[] {
  return picks.filter((p) => {
    if (f.aboveOnly && !p.isAboveMe) return false;
    if (f.score && `${p.scoreT1}:${p.scoreT2}` !== f.score) return false;
    return true;
  });
}
```

(When the ↑ mark is tapped we pass both `score` and `aboveOnly: true`; the test for the mark expects only rivals-above on that score — "Tú" is on `2:1`, not `2:0`, and isn't above, so it's excluded either way. When a bar is tapped we pass only `score`, so everyone on that scoreline shows.)

- [ ] **Step 4: Run the test to confirm it passes.**

Run: `cd frontend && pnpm test ConsensusCard`
Expected: PASS. Re-run `pnpm test GroupConsensus` — still green.

- [ ] **Step 5: Commit.**

```bash
git add frontend/components/compare/ConsensusCard.tsx \
        frontend/components/compare/ConsensusCard.test.tsx
git commit -m "feat(compare): names-on-demand drill-down on consensus bars"
```

---

## Task 9: Frontend — H2HCompare to a client component with Date↔Stage toggle

**Files:**
- Modify: `frontend/components/compare/H2HCompare.tsx`
- Test: `frontend/components/compare/H2HCompare.test.tsx`

**Interfaces:**
- Consumes: partitioned `H2HView` from Task 5.
- Produces: `"use client"` `H2HCompare({ data }: { data: H2HView | null })` with the same Date↔Stage toggle, default Today, preserving differ-first ordering within each bucket.

- [ ] **Step 1: Rewrite the H2H test for the new shape + toggle (make it fail).**

Read the current `H2HCompare.test.tsx` first to preserve its existing assertions, then adapt: convert from `vi.mock("next-intl/server")` + awaited render to client render under `NextIntlClientProvider` (with `compare` + `home` messages), and change `data.matches` fixtures to `past/today/upcoming` with a `serverTime`. Add:

```tsx
it("defaults to today and lists the rival's differing picks first", () => {
  // build a today bucket with one 'differ' + one 'agree' match; assert order/red styling
});
it("switches to stage mode", async () => {
  // click "Por fase", assert stage-header text
});
```

(Mirror the data factory + provider setup from the Task 6 GroupConsensus test.)

- [ ] **Step 2: Run it to confirm it fails.**

Run: `cd frontend && pnpm test H2HCompare`
Expected: FAIL — still a server component with flat `matches`.

- [ ] **Step 3: Rewrite `H2HCompare.tsx` as a client component.**

Convert to `"use client"`, `useTranslations` (drop `getTranslations`/`async`). Replace the single `groupMatchesByStage(visible, …)` render with the Date↔Stage structure from Task 6 (reuse the same `ModeButton`/`TabButton`; consider importing shared ones — if not extracted, copy them as in Task 6). Key points:

- `all = [...data.past, ...data.today, ...data.upcoming]`; `revealed = all.filter(m => m.revealed)` for the empty/locked check (unchanged logic).
- Default tab: `data.today.length > 0 ? "today" : data.upcoming.length > 0 ? "upcoming" : "past"`.
- The summary `<p>` (`summary` / `summaryWinning`) stays at the top, using `data.agreeCount` / `data.differCount` (global totals) — unchanged.
- **Date mode:** for the active bucket, render the existing You/Rival/Real `<table>`; order rows differ-first within the bucket: `const differ = list.filter(m => m.state === "differ"); const agree = list.filter(m => m.state === "agree"); const rows = [...differ, ...agree];` (same as the current per-stage logic). Keep `Row` and `highlight={m.state === "differ"}` exactly as today.
- **Stage mode:** keep the current `groupMatchesByStage(revealed, now)` + `StageSection` + per-stage differ-first table (the existing body), with `now = new Date(data.serverTime).getTime()`.
- Keep the `noRival` (null data) and `lockedTitle` (no revealed) early returns.

- [ ] **Step 4: Run the test to confirm it passes.**

Run: `cd frontend && pnpm test H2HCompare`
Expected: PASS.

- [ ] **Step 5: Full typecheck + full frontend test run.**

Run: `cd frontend && pnpm typecheck && pnpm test`
Expected: no type errors; all suites green.

- [ ] **Step 6: Commit.**

```bash
git add frontend/components/compare/H2HCompare.tsx \
        frontend/components/compare/H2HCompare.test.tsx
git commit -m "feat(compare): date/stage toggle on h2h (default today)"
```

---

## Task 10: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Backend — full test suite.**

Run the unit + integration suites (the commands the project uses; see Global Constraints). Expected: all green, including the adapted `CompareGroupConsensusIT` and `CompareH2HIT`.

- [ ] **Step 2: Frontend — full suite + typecheck + lint.**

Run: `cd frontend && pnpm typecheck && pnpm lint && pnpm test`
Expected: all green.

- [ ] **Step 3: Manual smoke (optional but recommended).**

Use the `/run` or `/verify` skill to launch the app, open `/compare`, confirm: GRUPO defaults to Hoy; the Por fecha/Por fase toggle works; `↑ N` marks appear on bars where rivals-above picked; tapping a bar lists names; tapping a `↑` mark lists only rivals-above; the 1 vs 1 tab shows the same toggle and defaults to Hoy.

- [ ] **Step 4: Final commit / branch ready.**

Branch `feature/compare-rivals-date-org` now holds the spec + all 9 implementation commits. Use the `superpowers:finishing-a-development-branch` skill to decide merge/PR.

---

## Self-Review Notes

- **Spec coverage:** Part 1 (date org, both tabs) → Tasks 1, 4, 6, 9. Part 2 (rivals-above) → Tasks 2, 7. Part 3 (drill-down) → Tasks 3, 5, 8. i18n + types → Task 5. Verification → Task 10.
- **Type consistency:** `rivalsAboveCount` (ScoreCount), `rivalsAboveTotal`/`rivalsAbovePicked` (MatchConsensus), `MatchPick`/`MatchPicksView`, `fetchMatchPicks`, `getMatchPicks` are defined in Task 5 and consumed with identical names in Tasks 6–8. Backend records in Tasks 1–4 match the frontend types in Task 5 field-for-field.
- **Privacy gate:** enforced in Task 3 (`ResponseStatusException FORBIDDEN`) and verified by `forbidsPicksForAnUnrevealedMatch`.
- **Flakiness guard:** date-bucket-dependent ITs use deep-scan jsonPath (`$..[?(@.matchId == N)]`) so fixed-date fixtures don't break as real `NOW()` advances.
- **Open verification item (not a placeholder):** confirm the backend integration test command/profile in `backend/pom.xml` before Task 1 Step 2, and confirm `frontend/app/compare/page.tsx` needs no edit once the components are client (it passes only serializable `data`).

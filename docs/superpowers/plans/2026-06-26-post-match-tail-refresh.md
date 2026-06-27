# Post-Match Tail Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After any match finalizes, schedule a short deduped series of full-competition refreshes so the next round's pairings/advancements surface within ~30 min instead of waiting for the 06:00 daily cron.

**Architecture:** When `FootballDataSyncService.syncMatch` detects a match flipped to FINAL, it enqueues fixtures-only refresh tasks (`syncFull()`) at interval-aligned wall-clock slots over a bounded tail window. Tasks hit a new `/internal/sync/fixtures` endpoint, dedup by slot via Cloud Tasks names, and never re-score frozen matches (the existing `WHERE NOT match.played` guard).

**Tech Stack:** Java 21 records, Spring Boot, Spring JDBC, Google Cloud Tasks, JUnit 5 + AssertJ, Testcontainers (via `AbstractIntegrationTest`), Maven (`./mvnw`).

## Global Constraints

- Backend module root: `backend/`. Run all commands from there (`cd backend`).
- Package: `io.quiniela.api.footballdata` (config filter in `io.quiniela.api.config`).
- All `/internal/**` endpoints are guarded by `SyncTokenFilter` (`X-Sync-Token` shared secret) — do **not** add app-level auth.
- Internal sync endpoints always return `200` for handled outcomes (so Cloud Tasks/Scheduler never retry-storm).
- `syncFull()` already exists and is the only structural-refresh path; reuse it, do not duplicate.
- Defaults ship in `application.yml`; no IaC / Cloud Scheduler change in this plan.
- Test commands: `./mvnw -q -Dtest=<Class>[#<method>] test`. Unit tests are fast; IT classes extend `AbstractIntegrationTest` (spins a Postgres container).

---

### Task 1: Add tail-refresh config properties

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/SyncProperties.java`
- Modify: `backend/src/main/resources/application.yml:90-99` (the `sync:` block)
- Test: `backend/src/test/java/io/quiniela/api/footballdata/SyncPropertiesTest.java`

**Interfaces:**
- Produces: `SyncProperties` canonical constructor gains two params (order matters): `Integer tailRefreshIntervalMinutes, Integer tailWindowHours` appended **before** `Tasks tasks`. Accessors `tailRefreshIntervalMinutes()` (default 30) and `tailWindowHours()` (default 3).

- [ ] **Step 1: Update the failing test**

In `SyncPropertiesTest.java`, replace the existing `appliesDefaultsWhenNullsGiven` body to match the new arity and assert the new defaults:

```java
  @Test
  void appliesDefaultsWhenNullsGiven() {
    SyncProperties p = new SyncProperties(null, null, null, null, null, null, null);
    assertThat(p.firstPollOffsetMinutes()).isEqualTo(0);
    assertThat(p.pollWindowHours()).isEqualTo(5);
    assertThat(p.retryIntervalMinutes()).isEqualTo(5);
    assertThat(p.tailRefreshIntervalMinutes()).isEqualTo(30);
    assertThat(p.tailWindowHours()).isEqualTo(3);
    assertThat(p.tasks().enabled()).isFalse();
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=SyncPropertiesTest test`
Expected: COMPILE FAILURE — constructor `SyncProperties(...)` does not take 7 args / `tailRefreshIntervalMinutes()` not found.

- [ ] **Step 3: Add the fields and defaults**

In `SyncProperties.java`, change the record components and compact constructor:

```java
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
    String token,
    Integer firstPollOffsetMinutes,
    Integer pollWindowHours,
    Integer retryIntervalMinutes,
    Integer tailRefreshIntervalMinutes,
    Integer tailWindowHours,
    Tasks tasks) {

  public SyncProperties {
    if (firstPollOffsetMinutes == null) firstPollOffsetMinutes = 0;
    if (pollWindowHours == null) pollWindowHours = 5;
    if (retryIntervalMinutes == null) retryIntervalMinutes = 5;
    if (tailRefreshIntervalMinutes == null) tailRefreshIntervalMinutes = 30;
    if (tailWindowHours == null) tailWindowHours = 3;
    if (tasks == null) tasks = new Tasks(null, null, null);
  }
```

(Leave the nested `Tasks` record unchanged.)

- [ ] **Step 4: Bind the new properties in application.yml**

In `application.yml`, inside the `sync:` block (after `retry-interval-minutes`), add:

```yaml
    tail-refresh-interval-minutes: ${APP_SYNC_TAIL_REFRESH_INTERVAL_MINUTES:30}
    tail-window-hours: ${APP_SYNC_TAIL_WINDOW_HOURS:3}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=SyncPropertiesTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/SyncProperties.java \
        backend/src/main/resources/application.yml \
        backend/src/test/java/io/quiniela/api/footballdata/SyncPropertiesTest.java
git commit -m "feat(sync): add tail-refresh interval and window config"
```

---

### Task 2: Add the pure `tailSlots` slot calculator

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java` (add `import java.util.ArrayList;` and a new static method)
- Test: `backend/src/test/java/io/quiniela/api/footballdata/FootballDataLoaderTest.java` (add `import java.time.Instant;`)

**Interfaces:**
- Produces: `static List<Instant> tailSlots(Instant now, int intervalMinutes, int windowHours)` — interval-aligned `Instant`s strictly after `now`, up to and including `now + windowHours`. Empty when interval or window ≤ 0.

- [ ] **Step 1: Write the failing test**

Add to `FootballDataLoaderTest.java` (add `import java.time.Instant;` near the top, and `import static org.assertj.core.api.Assertions.assertThat;` already present):

```java
  @Test
  void tailSlotsAlignsToIntervalBoundariesWithinWindow() {
    // 14:05Z, 30-min interval, 3h window → 14:30,15:00,...,17:00 (6 slots)
    Instant now = Instant.parse("2026-06-28T14:05:00Z");
    assertThat(FootballDataSyncService.tailSlots(now, 30, 3))
        .containsExactly(
            Instant.parse("2026-06-28T14:30:00Z"),
            Instant.parse("2026-06-28T15:00:00Z"),
            Instant.parse("2026-06-28T15:30:00Z"),
            Instant.parse("2026-06-28T16:00:00Z"),
            Instant.parse("2026-06-28T16:30:00Z"),
            Instant.parse("2026-06-28T17:00:00Z"));
  }

  @Test
  void tailSlotsSkipsCurrentBoundaryAndHandlesZeroWindow() {
    Instant onBoundary = Instant.parse("2026-06-28T14:30:00Z");
    assertThat(FootballDataSyncService.tailSlots(onBoundary, 30, 1))
        .first()
        .isEqualTo(Instant.parse("2026-06-28T15:00:00Z")); // never schedules "now"
    assertThat(FootballDataSyncService.tailSlots(onBoundary, 30, 0)).isEmpty();
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=FootballDataLoaderTest test`
Expected: COMPILE FAILURE — `tailSlots` not defined.

- [ ] **Step 3: Implement `tailSlots`**

In `FootballDataSyncService.java`, add `import java.util.ArrayList;` to the imports, then add this static method (place it near the other static helpers like `mapStageToRoundCode`):

```java
  /**
   * Interval-aligned full-refresh slots over the tail window, all strictly after {@code now}.
   * Slots align to epoch-based interval boundaries (for 30 min in UTC that is :00/:30), so
   * concurrent finals enqueue the SAME slot names and Cloud Tasks dedups them to one refresh
   * per slot. Empty when interval or window is non-positive.
   */
  static List<Instant> tailSlots(Instant now, int intervalMinutes, int windowHours) {
    List<Instant> slots = new ArrayList<>();
    if (intervalMinutes <= 0 || windowHours <= 0) return slots;
    long intervalSec = intervalMinutes * 60L;
    long deadline = now.getEpochSecond() + windowHours * 3600L;
    long slot = ((now.getEpochSecond() / intervalSec) + 1) * intervalSec;
    while (slot <= deadline) {
      slots.add(Instant.ofEpochSecond(slot));
      slot += intervalSec;
    }
    return slots;
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=FootballDataLoaderTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java \
        backend/src/test/java/io/quiniela/api/footballdata/FootballDataLoaderTest.java
git commit -m "feat(sync): add interval-aligned tailSlots calculator"
```

---

### Task 3: Add `enqueueFixturesRefresh` to the task-queue abstraction

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/ResultsTaskQueue.java`
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/CloudTasksResultsQueue.java`
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/NoopResultsTaskQueue.java` (add `import java.time.Instant;`)
- Modify: `backend/src/test/java/io/quiniela/api/footballdata/FakeResultsTaskQueue.java`

**Interfaces:**
- Produces: `ResultsTaskQueue.enqueueFixturesRefresh(Instant when, String dedupName)` — schedules a POST to `/internal/sync/fixtures`.
- Produces (test double): `FakeResultsTaskQueue.fixturesCalls` (`List<FixturesRefresh>`) and nested `record FixturesRefresh(Instant when, String dedupName)`.

> All four implementors are updated in this single task so the build (main + test sources) stays green. `NoopResultsTaskQueue` returns a lambda today (single-method interface) — it must become an anonymous class once the interface has two methods.

- [ ] **Step 1: Add the interface method**

In `ResultsTaskQueue.java`, add inside the interface (after the existing `enqueue` Javadoc/method):

```java
  /**
   * Schedule a POST to /internal/sync/fixtures at {@code when} — a full structural refresh that
   * runs regardless of any match's played state. Cloud Tasks dedups by {@code dedupName}.
   */
  void enqueueFixturesRefresh(Instant when, String dedupName);
```

- [ ] **Step 2: Implement it on the Cloud Tasks queue**

In `CloudTasksResultsQueue.java`, add this method after `enqueue`:

```java
  @Override
  public void enqueueFixturesRefresh(Instant when, String dedupName) {
    String queue = props.tasks().queue();
    String url = props.tasks().targetBase() + "/internal/sync/fixtures";
    HttpRequest req =
        HttpRequest.newBuilder()
            .setUrl(url)
            .setHttpMethod(HttpMethod.POST)
            .putHeaders("X-Sync-Token", props.token() == null ? "" : props.token())
            .setBody(ByteString.EMPTY)
            .build();
    Task task =
        Task.newBuilder()
            .setName(queue + "/tasks/" + dedupName)
            .setHttpRequest(req)
            .setScheduleTime(Timestamp.newBuilder().setSeconds(when.getEpochSecond()).build())
            .build();
    try (CloudTasksClient client = CloudTasksClient.create()) {
      client.createTask(queue, task);
      log.info("enqueued fixtures refresh at {} (name={})", when, dedupName);
    } catch (com.google.api.gax.rpc.AlreadyExistsException e) {
      log.debug("fixtures task {} already enqueued; skipping (dedup)", dedupName);
    } catch (Exception e) {
      log.warn("failed to enqueue fixtures refresh (name={})", dedupName, e);
    }
  }
```

- [ ] **Step 3: Convert the noop queue to an anonymous class implementing both methods**

In `NoopResultsTaskQueue.java`, add `import java.time.Instant;`, then replace the lambda bean body:

```java
  @Bean
  @ConditionalOnMissingBean(ResultsTaskQueue.class)
  ResultsTaskQueue resultsTaskQueueNoop() {
    return new ResultsTaskQueue() {
      @Override
      public void enqueue(long matchId, Instant when, String dedupName) {
        log.info("[noop queue] would enqueue match {} at {} (name={})", matchId, when, dedupName);
      }

      @Override
      public void enqueueFixturesRefresh(Instant when, String dedupName) {
        log.info("[noop queue] would enqueue fixtures refresh at {} (name={})", when, dedupName);
      }
    };
  }
```

- [ ] **Step 4: Extend the test double**

In `FakeResultsTaskQueue.java`, add the new record, list, and override:

```java
  public record FixturesRefresh(Instant when, String dedupName) {}

  public final List<FixturesRefresh> fixturesCalls = new ArrayList<>();

  @Override
  public void enqueueFixturesRefresh(Instant when, String dedupName) {
    fixturesCalls.add(new FixturesRefresh(when, dedupName));
  }
```

- [ ] **Step 5: Compile to verify all implementors satisfy the interface**

Run: `cd backend && ./mvnw -q test-compile`
Expected: BUILD SUCCESS (no "does not override abstract method" errors).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/ResultsTaskQueue.java \
        backend/src/main/java/io/quiniela/api/footballdata/CloudTasksResultsQueue.java \
        backend/src/main/java/io/quiniela/api/footballdata/NoopResultsTaskQueue.java \
        backend/src/test/java/io/quiniela/api/footballdata/FakeResultsTaskQueue.java
git commit -m "feat(sync): add enqueueFixturesRefresh to results task queue"
```

---

### Task 4: Schedule the tail refresh when a match finalizes

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java` (the `syncMatch` FINAL branch + a private helper)
- Test: `backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncServiceIT.java`

**Interfaces:**
- Consumes: `tailSlots(Instant, int, int)` (Task 2), `SyncProperties.tailRefreshIntervalMinutes()`/`tailWindowHours()` (Task 1), `ResultsTaskQueue.enqueueFixturesRefresh(...)` + `FakeResultsTaskQueue.fixturesCalls` (Task 3).
- Produces: side effect only — fixtures-refresh tasks named `fixtures-<slotEpochSeconds>`.

- [ ] **Step 1: Write the failing tests**

Add two tests to `FootballDataSyncServiceIT.java` (reusing the existing `match(...)`, `groupRound()`, `stubbed`, `queue` helpers). Use match ids in the class's reserved 6100–6299 range:

```java
  @Test
  void syncMatchSchedulesTailRefreshOnFinal() {
    queue.calls.clear();
    queue.fixturesCalls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6210, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6210, "FINISHED", 1, 0)));

    sync.syncMatch(6210L);

    assertThat(queue.calls).isEmpty(); // no per-match result re-enqueue
    assertThat(queue.fixturesCalls)
        .isNotEmpty()
        .allSatisfy(c -> assertThat(c.dedupName()).startsWith("fixtures-"));
  }

  @Test
  void syncMatchDoesNotScheduleTailWhenStillUnplayed() {
    queue.calls.clear();
    queue.fixturesCalls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6211, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6211, "IN_PLAY", null, null)));

    sync.syncMatch(6211L);

    assertThat(queue.fixturesCalls).isEmpty(); // still unplayed → no tail
  }
```

Add to the `@AfterEach cleanTestMatches()` nothing new is needed (6210/6211 are inside the existing `id >= 6100 AND id <= 6299` cleanup).

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q -Dtest=FootballDataSyncServiceIT#syncMatchSchedulesTailRefreshOnFinal+syncMatchDoesNotScheduleTailWhenStillUnplayed test`
Expected: FAIL — `syncMatchSchedulesTailRefreshOnFinal` fails because `fixturesCalls` is empty (no tail scheduled yet); the unplayed test passes already.

- [ ] **Step 3: Wire the FINAL branch to schedule the tail**

In `FootballDataSyncService.syncMatch`, change the `nowPlayed` branch from:

```java
    if (Boolean.TRUE.equals(nowPlayed)) {
      return new SyncResult(true, n, 0, null); // got it; no re-enqueue
    }
```

to:

```java
    if (Boolean.TRUE.equals(nowPlayed)) {
      scheduleTailRefresh();
      return new SyncResult(true, n, 0, null); // got it; no result re-enqueue
    }
```

Then add the private helper (place it just after `maybeReEnqueue`):

```java
  /**
   * After a match finalizes, enqueue a short series of full-competition refreshes so the next
   * round's pairings/advancements (which football-data.org publishes shortly after) surface
   * within one tail interval instead of waiting for the daily cron. Deduped by slot so multiple
   * matches finishing together collapse to one refresh per slot.
   */
  private void scheduleTailRefresh() {
    List<Instant> slots =
        tailSlots(Instant.now(), props.tailRefreshIntervalMinutes(), props.tailWindowHours());
    for (Instant slot : slots) {
      queue.enqueueFixturesRefresh(slot, "fixtures-" + slot.getEpochSecond());
    }
    log.info("scheduleTailRefresh: enqueued {} fixtures refreshes", slots.size());
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=FootballDataSyncServiceIT test`
Expected: PASS (all existing IT tests plus the two new ones). The existing `syncMatchAppliesFinishedResultAndDoesNotReEnqueue` still passes — it only asserts `queue.calls` (per-match), which the tail does not touch.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java \
        backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncServiceIT.java
git commit -m "feat(sync): schedule tail fixtures refresh when a match finalizes"
```

---

### Task 5: Expose `POST /internal/sync/fixtures`

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/InternalSyncController.java`
- Test: `backend/src/test/java/io/quiniela/api/footballdata/InternalSyncControllerIT.java`

**Interfaces:**
- Consumes: `FootballDataSyncService.syncFull()` (already exists).
- Produces: `POST /internal/sync/fixtures` → `200` with `SyncResult`; `401` without the token.

- [ ] **Step 1: Write the failing tests**

Add to `InternalSyncControllerIT.java`:

```java
  @Test
  void fixturesWithoutTokenRejected() throws Exception {
    mockMvc.perform(post("/internal/sync/fixtures")).andExpect(status().isUnauthorized());
  }

  @Test
  void fixturesWithTokenReturns200() throws Exception {
    mockMvc
        .perform(post("/internal/sync/fixtures").header("X-Sync-Token", "test-token"))
        .andExpect(status().isOk());
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q -Dtest=InternalSyncControllerIT test`
Expected: FAIL — `fixturesWithTokenReturns200` returns 404 (no mapping); the no-token case may already 401 via the filter but the 200 case fails.

- [ ] **Step 3: Add the endpoint**

In `InternalSyncController.java`, add after the `results` mapping:

```java
  @PostMapping("/fixtures")
  public ResponseEntity<SyncResult> fixtures() {
    return ResponseEntity.ok(service.syncFull());
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=InternalSyncControllerIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/InternalSyncController.java \
        backend/src/test/java/io/quiniela/api/footballdata/InternalSyncControllerIT.java
git commit -m "feat(sync): add POST /internal/sync/fixtures structural refresh endpoint"
```

---

### Task 6: Full backend verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full backend test suite**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS — all tests green, including the new unit + IT tests and the unchanged sync/freeze/bracket suites.

- [ ] **Step 2: Sanity-check the config keys resolve**

Run: `cd backend && ./mvnw -q -Dtest=SyncPropertiesTest test` and confirm PASS (defaults 30/3 applied). The env overrides `APP_SYNC_TAIL_REFRESH_INTERVAL_MINUTES` / `APP_SYNC_TAIL_WINDOW_HOURS` are available but unset in prod (defaults apply).

- [ ] **Step 3: Final commit (only if any incidental fixups were needed)**

```bash
git status   # expect clean working tree if Tasks 1-5 committed cleanly
```

---

## Notes for the implementer

- **Prod behavior after deploy:** the existing daily cron and per-match polling are unchanged. The only new runtime behavior is that finalizing a match now enqueues `fixtures-*` Cloud Tasks; those POST `/internal/sync/fixtures`, which runs `syncFull()` (fixtures + results upsert, `WHERE NOT match.played` freeze guard intact — no re-scoring).
- **Why the tail fires after *every* final** (not just stage-ending ones): detecting "last match of a stage" is unnecessary complexity; idempotent + slot-deduped refreshes make extra firings cheap, and this uniformly covers group→R32 plus every knockout transition.
- **No IaC change:** the new endpoint is just another `/internal/**` route already covered by the deployed `SyncTokenFilter` + scheduler/queue infra.

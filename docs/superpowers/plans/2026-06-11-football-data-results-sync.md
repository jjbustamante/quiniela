# Football-data Results Auto-Sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically pull match results from football-data.org after each game and apply them, without re-scoring already-played games, while keeping the Cloud Run instance scaled-to-zero between match windows.

**Architecture:** A once-daily Cloud Scheduler job hits `POST /internal/sync/daily`, which refreshes fixtures and enqueues one Cloud Task per upcoming match (timed to `kickoff + 105 min`). Each task hits `POST /internal/sync/results?matchId=N`, fetches results, UPSERTs them, and re-enqueues every 15 min until the match is FINISHED or a 5h cap. The shared `upsertMatches` carries a `WHERE NOT match.played` guard so already-scored games are frozen (their points-recompute trigger never fires). Admin-authenticated `/api/admin/sync/*` endpoints allow manual triggering for testing.

**Tech Stack:** Java 21 / Spring Boot 4, Spring Security 7 (OAuth2 resource server, HMAC JWT), Postgres (Flyway), `google-cloud-tasks`, Cloud Scheduler + Cloud Tasks (Terraform), JUnit 5 + Testcontainers + WireMock + spring-security-test.

**Spec:** `docs/superpowers/specs/2026-06-11-football-data-results-sync-design.md`

---

## File Structure

**Create (main):**
- `backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java` — owns `upsertMatches` (with freeze guard) + `runDaily`/`planToday`/`syncMatch`/`syncFull` + `SyncResult`.
- `backend/src/main/java/io/quiniela/api/footballdata/SyncProperties.java` — `@ConfigurationProperties(prefix="app.sync")`.
- `backend/src/main/java/io/quiniela/api/footballdata/ResultsTaskQueue.java` — interface.
- `backend/src/main/java/io/quiniela/api/footballdata/CloudTasksResultsQueue.java` — prod impl (`@ConditionalOnProperty app.sync.tasks.enabled=true`).
- `backend/src/main/java/io/quiniela/api/footballdata/NoopResultsTaskQueue.java` — fallback impl (`@ConditionalOnMissingBean`), logs instead of enqueuing.
- `backend/src/main/java/io/quiniela/api/config/SyncTokenFilter.java` — shared-secret guard for `/internal/**`.
- `backend/src/main/java/io/quiniela/api/footballdata/InternalSyncController.java` — `/internal/sync/{daily,results}`.
- `backend/src/main/java/io/quiniela/api/footballdata/AdminSyncController.java` — `/api/admin/sync/{daily,match/{id}}`.
- `iac/tasks.tf`, `iac/scheduler.tf` — Cloud Tasks queue + daily Scheduler job.

**Modify (main):**
- `backend/src/main/java/io/quiniela/api/footballdata/FootballDataLoader.java` — delegate match upsert to the service.
- `backend/src/main/java/io/quiniela/api/config/SecurityConfig.java` — register `SyncTokenFilter`, permit `/internal/**`.
- `backend/src/main/resources/application.yml` — `app.sync` block.
- `backend/pom.xml` — add `google-cloud-tasks` (version via property).
- `iac/secrets.tf`, `iac/service_accounts.tf`, `iac/apis.tf`, `iac/cloud_run.tf` — secret, IAM, API enablement, env vars.

**Create (test):**
- `backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncFreezeIT.java`
- `backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncServiceIT.java`
- `backend/src/test/java/io/quiniela/api/footballdata/FakeResultsTaskQueue.java`
- `backend/src/test/java/io/quiniela/api/config/SyncTokenFilterTest.java`
- `backend/src/test/java/io/quiniela/api/footballdata/InternalSyncControllerIT.java`
- `backend/src/test/java/io/quiniela/api/footballdata/AdminSyncControllerIT.java`

**Conventions (follow exactly):**
- Run one test class: from `backend/`, `./mvnw -q test -Dtest=ClassName` (Testcontainers needs Docker running).
- Integration tests extend `io.quiniela.api.support.AbstractIntegrationTest` (shared Postgres singleton, Flyway on, `cleanWritableTables` wipes bets/quiniela/users before each).
- Externalize all new Maven versions to `<properties>` (project rule — never inline).
- Match table columns: `id, tournament_id, round_id, group_code, team_1_id, team_2_id, score_t1, score_t2, winner_id, advanced_team_id, played, kickoff_at, updated_at`. `TOURNAMENT_ID = 1L`.

---

## Task 1: Extract `upsertMatches` into `FootballDataSyncService` with the freeze guard

This is the integrity-critical task. We move the match UPSERT out of `FootballDataLoader` into a reusable service and add `WHERE NOT match.played` so a re-sync can never re-score a finished game.

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java`
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataLoader.java`
- Test: `backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncFreezeIT.java`

- [ ] **Step 1: Write the failing freeze test**

Create `FootballDataSyncFreezeIT.java`. It seeds a finished match with a known score, calls `upsertMatches` with a *different* score for the same match id, and asserts the row is untouched (the UPDATE was suppressed, so the points trigger never fired).

```java
package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.footballdata.FootballDataClient.CompetitionMatchesResponse;
import io.quiniela.api.footballdata.FootballDataClient.MatchApi;
import io.quiniela.api.footballdata.FootballDataClient.MatchScore;
import io.quiniela.api.footballdata.FootballDataClient.MatchScoreFull;
import io.quiniela.api.footballdata.FootballDataClient.MatchTeam;
import io.quiniela.api.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FootballDataSyncFreezeIT extends AbstractIntegrationTest {

  @Autowired FootballDataSyncService sync;
  @Autowired JdbcTemplate jdbc;

  @Test
  void doesNotRewriteAnAlreadyPlayedMatch() {
    // A GROUP match (round_id resolved from seeded rounds) already played 2-1.
    Long roundId = jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP'", Long.class);
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name) VALUES "
            + "(7001,1,'AAA','Team A'),(7002,1,'BBB','Team B') ON CONFLICT (id) DO NOTHING");
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, "
            + " score_t1, score_t2, played, kickoff_at) "
            + "VALUES (8001, 1, ?, 'A', 7001, 7002, 2, 1, TRUE, now() - interval '3 hours')",
        roundId);

    // API now reports a DIFFERENT score for the same match.
    MatchApi changed =
        new MatchApi(
            8001L,
            "2026-06-11T17:00:00Z",
            "FINISHED",
            "GROUP_STAGE",
            "Group A",
            new MatchTeam(7001L, "Team A"),
            new MatchTeam(7002L, "Team B"),
            new MatchScore("HOME_TEAM", "REGULAR", new MatchScoreFull(5, 0), null));

    sync.upsertMatches(new CompetitionMatchesResponse(List.of(changed)));

    // Frozen: the already-played row is unchanged.
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 8001", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT score_t2 FROM match WHERE id = 8001", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 8001", Boolean.class))
        .isTrue();
  }

  @Test
  void appliesResultToANotYetPlayedMatch() {
    Long roundId = jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP'", Long.class);
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name) VALUES "
            + "(7003,1,'CCC','Team C'),(7004,1,'DDD','Team D') ON CONFLICT (id) DO NOTHING");
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, "
            + " played, kickoff_at) "
            + "VALUES (8002, 1, ?, 'A', 7003, 7004, FALSE, now() - interval '3 hours')",
        roundId);

    MatchApi finished =
        new MatchApi(
            8002L,
            "2026-06-11T17:00:00Z",
            "FINISHED",
            "GROUP_STAGE",
            "Group A",
            new MatchTeam(7003L, "Team C"),
            new MatchTeam(7004L, "Team D"),
            new MatchScore("HOME_TEAM", "REGULAR", new MatchScoreFull(3, 0), null));

    sync.upsertMatches(new CompetitionMatchesResponse(List.of(finished)));

    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 8002", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 8002", Integer.class))
        .isEqualTo(3);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=FootballDataSyncFreezeIT`
Expected: FAIL — compilation error, `FootballDataSyncService` does not exist yet.

- [ ] **Step 3: Create `FootballDataSyncService` with the guarded UPSERT**

Create `FootballDataSyncService.java`. For now include only what these tests need: `upsertMatches` + the three static mappers moved verbatim from the loader, plus the freeze `WHERE NOT match.played`. (The `runDaily`/`planToday`/`syncMatch`/`syncFull` methods come in Task 4 — leave them out now to keep this task small.)

```java
package io.quiniela.api.footballdata;

import io.quiniela.api.footballdata.FootballDataClient.CompetitionMatchesResponse;
import io.quiniela.api.footballdata.FootballDataClient.MatchApi;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared football-data result-sync logic. The UPSERT freezes already-played matches
 * (WHERE NOT match.played) so a re-sync can never re-score a finalized game — critical
 * once betting is reopened and bets may be edited after a game was already scored.
 */
@Service
public class FootballDataSyncService {

  static final Long TOURNAMENT_ID = 1L;
  private static final Logger log = LoggerFactory.getLogger(FootballDataSyncService.class);

  private final RoundRepository rounds;
  private final JdbcTemplate jdbc;

  public FootballDataSyncService(RoundRepository rounds, DataSource dataSource) {
    this.rounds = rounds;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  /** UPSERT every match in the payload. Already-played rows are frozen (no UPDATE, no trigger). */
  @Transactional
  public int upsertMatches(CompetitionMatchesResponse resp) {
    if (resp == null || resp.matches() == null) return 0;

    Map<String, Long> roundByCode = new HashMap<>();
    for (Round r : rounds.findAll()) roundByCode.put(r.getCode(), r.getId());

    int n = 0;
    for (MatchApi m : resp.matches()) {
      String roundCode = mapStageToRoundCode(m.stage());
      Long roundId = roundByCode.get(roundCode);
      if (roundId == null) {
        log.debug("Skipping match {}: unmapped stage {}", m.id(), m.stage());
        continue;
      }
      String groupCode = "GROUP".equals(roundCode) ? mapGroupName(m.group()) : null;
      Long team1Id = m.homeTeam() != null ? m.homeTeam().id() : null;
      Long team2Id = m.awayTeam() != null ? m.awayTeam().id() : null;
      Instant kickoff = m.utcDate() != null ? Instant.parse(m.utcDate()) : Instant.now();
      Integer scoreT1 =
          m.score() != null && m.score().fullTime() != null ? m.score().fullTime().home() : null;
      Integer scoreT2 =
          m.score() != null && m.score().fullTime() != null ? m.score().fullTime().away() : null;
      boolean played = "FINISHED".equals(m.status());
      Long advancedTeamId = advancingTeamId(m);

      // WHERE NOT match.played freezes finalized games: the UPDATE is suppressed entirely,
      // so the BEFORE UPDATE points trigger never fires for an already-scored match.
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
              + "  kickoff_at = EXCLUDED.kickoff_at "
              + "WHERE NOT match.played",
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
      n++;
    }
    jdbc.execute("SELECT setval('match_id_seq', GREATEST(1, (SELECT MAX(id) FROM match)))");
    return n;
  }

  /** Map football-data.org's "Group A" -> "A". Returns null for unrecognized values. */
  static String mapGroupName(String apiGroup) {
    if (apiGroup == null) return null;
    if (apiGroup.startsWith("Group ") && apiGroup.length() == 7) return apiGroup.substring(6, 7);
    if (apiGroup.startsWith("GROUP_") && apiGroup.length() == 7) return apiGroup.substring(6, 7);
    return null;
  }

  /** Progressing team from a finished knockout (winner names the side; null on draw/missing). */
  static Long advancingTeamId(MatchApi m) {
    if (m.score() == null || m.score().winner() == null) return null;
    return switch (m.score().winner()) {
      case "HOME_TEAM" -> m.homeTeam() != null ? m.homeTeam().id() : null;
      case "AWAY_TEAM" -> m.awayTeam() != null ? m.awayTeam().id() : null;
      default -> null;
    };
  }

  /** Map football-data.org match.stage codes to our round.code values. */
  static String mapStageToRoundCode(String apiStage) {
    if (apiStage == null) return null;
    return switch (apiStage) {
      case "GROUP_STAGE" -> "GROUP";
      case "LAST_32", "ROUND_OF_32" -> "R32";
      case "LAST_16", "ROUND_OF_16" -> "R16";
      case "QUARTER_FINALS", "QUARTERFINALS" -> "QF";
      case "SEMI_FINALS", "SEMIFINALS" -> "SF";
      case "THIRD_PLACE", "PLAY_OFF_FOR_THIRD_PLACE" -> "THIRD_PLACE";
      case "FINAL" -> "FINAL";
      default -> null;
    };
  }
}
```

- [ ] **Step 4: Run the freeze test to verify it passes**

Run: `./mvnw -q test -Dtest=FootballDataSyncFreezeIT`
Expected: PASS (both tests).

- [ ] **Step 5: Refactor `FootballDataLoader` to delegate, then run its tests**

In `FootballDataLoader.java`: inject `FootballDataSyncService sync` into the constructor; in `load()` replace the match section (current lines 155–212, the `// Insert matches.` block through the `setval('match_id_seq'...)` + log) with a single delegation, and delete the now-moved static helpers `mapStageToRoundCode`, `advancingTeamId` (keep nothing duplicated). Replace the loader's own `mapGroupName` calls (standings + team grouping) with `FootballDataSyncService.mapGroupName(...)` and delete the loader's `mapGroupName`.

Replace the match block with:

```java
    // Insert/refresh matches via the shared sync service (freeze guard inside).
    var matchesResp = client.getMatches(competitionCode);
    int matchesInserted = sync.upsertMatches(matchesResp);
    log.info("football-data loader: upserted {} matches", matchesInserted);
```

Constructor change (add the parameter and field):

```java
  private final FootballDataSyncService sync;

  public FootballDataLoader(
      @org.springframework.beans.factory.annotation.Value("${app.football-data.enabled:false}")
          boolean enabled,
      @org.springframework.beans.factory.annotation.Value("${app.football-data.competition-code:WC}")
          String competitionCode,
      FootballDataClient client,
      TeamRepository teams,
      RoundRepository rounds,
      FootballDataSyncService sync,
      DataSource dataSource) {
    this.enabled = enabled;
    this.competitionCode = competitionCode;
    this.client = client;
    this.teams = teams;
    this.rounds = rounds;
    this.sync = sync;
    this.jdbc = new JdbcTemplate(dataSource);
  }
```

In `load()`, the two `mapGroupName(...)` usages become `FootballDataSyncService.mapGroupName(...)`.

Run: `./mvnw -q test -Dtest=FootballDataLoaderIT,FootballDataLoaderTest,FootballDataSyncFreezeIT`
Expected: PASS. (`FootballDataLoaderIT.resyncUpdatesResultAndStoresPenaltyAdvancingTeam` still passes because that match starts `played=false`, so the guard allows the update.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java \
        backend/src/main/java/io/quiniela/api/footballdata/FootballDataLoader.java \
        backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncFreezeIT.java
git commit -m "feat(sync): extract upsertMatches with WHERE NOT played freeze guard"
```

---

## Task 2: `SyncProperties` config + `application.yml` block

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/footballdata/SyncProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/io/quiniela/api/QuinielaApiApplication.java` (enable config props scanning if not already)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/footballdata/SyncPropertiesTest.java`:

```java
package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SyncPropertiesTest {

  @Test
  void appliesDefaultsWhenNullsGiven() {
    SyncProperties p = new SyncProperties(null, null, null, null, null);
    assertThat(p.matchMinDurationMinutes()).isEqualTo(105);
    assertThat(p.pollWindowHours()).isEqualTo(5);
    assertThat(p.retryIntervalMinutes()).isEqualTo(15);
    assertThat(p.tasks().enabled()).isFalse();
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=SyncPropertiesTest`
Expected: FAIL — `SyncProperties` does not exist.

- [ ] **Step 3: Create `SyncProperties`**

```java
package io.quiniela.api.footballdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Config for the results auto-sync. Bound from {@code app.sync.*}. */
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
    String token,
    Integer matchMinDurationMinutes,
    Integer pollWindowHours,
    Integer retryIntervalMinutes,
    Tasks tasks) {

  public SyncProperties {
    if (matchMinDurationMinutes == null) matchMinDurationMinutes = 105;
    if (pollWindowHours == null) pollWindowHours = 5;
    if (retryIntervalMinutes == null) retryIntervalMinutes = 15;
    if (tasks == null) tasks = new Tasks(null, null, null);
  }

  /** Cloud Tasks settings. {@code enabled=false} ⇒ no-op queue (local/test). */
  public record Tasks(Boolean enabled, String queue, String targetBase) {
    public Tasks {
      if (enabled == null) enabled = false;
    }
  }
}
```

- [ ] **Step 4: Register the properties**

In `QuinielaApiApplication.java`, ensure the class is picked up. If the app already uses `@ConfigurationPropertiesScan`, nothing to do. Otherwise add `@org.springframework.boot.context.properties.EnableConfigurationProperties(io.quiniela.api.footballdata.SyncProperties.class)` to the application class. Verify which one exists:

Run: `grep -nE "ConfigurationPropertiesScan|EnableConfigurationProperties" backend/src/main/java/io/quiniela/api/QuinielaApiApplication.java`
If neither prints, add `@ConfigurationPropertiesScan` to the class (it auto-detects `@ConfigurationProperties` records in the package).

- [ ] **Step 5: Add the `app.sync` block to `application.yml`**

Under the existing `app:` map (sibling of `football-data:`), add:

```yaml
  sync:
    token: ${APP_SYNC_TOKEN:}                       # shared secret; empty => /internal/** returns 401
    match-min-duration-minutes: ${APP_SYNC_MATCH_MIN_DURATION_MINUTES:105}
    poll-window-hours: ${APP_SYNC_POLL_WINDOW_HOURS:5}
    retry-interval-minutes: ${APP_SYNC_RETRY_INTERVAL_MINUTES:15}
    tasks:
      enabled: ${APP_SYNC_TASKS_ENABLED:false}
      queue: ${APP_SYNC_TASKS_QUEUE:}               # projects/<p>/locations/<l>/queues/results-sync
      target-base: ${APP_SYNC_TASKS_TARGET_BASE:}   # the api's own Cloud Run base URL
```

- [ ] **Step 6: Run the test + a context smoke test**

Run: `./mvnw -q test -Dtest=SyncPropertiesTest,QuinielaApiApplicationTests`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/SyncProperties.java \
        backend/src/test/java/io/quiniela/api/footballdata/SyncPropertiesTest.java \
        backend/src/main/resources/application.yml \
        backend/src/main/java/io/quiniela/api/QuinielaApiApplication.java
git commit -m "feat(sync): app.sync configuration properties"
```

---

## Task 3: `ResultsTaskQueue` interface + fake + no-op + Cloud Tasks impl

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/footballdata/ResultsTaskQueue.java`
- Create: `backend/src/main/java/io/quiniela/api/footballdata/NoopResultsTaskQueue.java`
- Create: `backend/src/main/java/io/quiniela/api/footballdata/CloudTasksResultsQueue.java`
- Create: `backend/src/test/java/io/quiniela/api/footballdata/FakeResultsTaskQueue.java`
- Modify: `backend/pom.xml`

- [ ] **Step 1: Add the `google-cloud-tasks` dependency (version via property)**

In `pom.xml`, add a property inside `<properties>`:

```xml
        <google-cloud-tasks.version>2.51.0</google-cloud-tasks.version>
```

And a dependency inside `<dependencies>`:

```xml
        <dependency>
            <groupId>com.google.cloud</groupId>
            <artifactId>google-cloud-tasks</artifactId>
            <version>${google-cloud-tasks.version}</version>
        </dependency>
```

Run: `./mvnw -q dependency:resolve -Dsilent=true` (or `./mvnw -q compile`)
Expected: resolves without error.

- [ ] **Step 2: Define the interface**

```java
package io.quiniela.api.footballdata;

import java.time.Instant;

/** Enqueues a deferred per-match result check. Implementations: Cloud Tasks (prod), no-op (local). */
public interface ResultsTaskQueue {

  /**
   * Schedule a POST to /internal/sync/results?matchId={matchId} at {@code when}.
   *
   * @param dedupName stable task name; Cloud Tasks dedups by name so re-planning is idempotent.
   */
  void enqueue(long matchId, Instant when, String dedupName);
}
```

- [ ] **Step 3: No-op impl (fallback when tasks disabled)**

```java
package io.quiniela.api.footballdata;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Logs instead of enqueuing. Active whenever no real {@link ResultsTaskQueue} bean exists. */
@Configuration
public class NoopResultsTaskQueue {

  private static final Logger log = LoggerFactory.getLogger(NoopResultsTaskQueue.class);

  @Bean
  @ConditionalOnMissingBean(ResultsTaskQueue.class)
  ResultsTaskQueue noopResultsTaskQueue() {
    return (matchId, when, dedupName) ->
        log.info("[noop queue] would enqueue match {} at {} (name={})", matchId, when, dedupName);
  }
}
```

- [ ] **Step 4: Cloud Tasks impl (prod)**

```java
package io.quiniela.api.footballdata;

import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Enqueues an HTTP-target Cloud Task that calls back into this service's
 * /internal/sync/results endpoint, carrying the shared-secret header. Authenticates to
 * Cloud Tasks via the runtime SA's ADC. Active only when app.sync.tasks.enabled=true.
 */
@Component
@ConditionalOnProperty(prefix = "app.sync.tasks", name = "enabled", havingValue = "true")
public class CloudTasksResultsQueue implements ResultsTaskQueue {

  private static final Logger log = LoggerFactory.getLogger(CloudTasksResultsQueue.class);

  private final SyncProperties props;

  public CloudTasksResultsQueue(SyncProperties props) {
    this.props = props;
  }

  @Override
  public void enqueue(long matchId, Instant when, String dedupName) {
    String queue = props.tasks().queue();
    String url = props.tasks().targetBase() + "/internal/sync/results?matchId=" + matchId;
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
      log.info("enqueued result check for match {} at {} (name={})", matchId, when, dedupName);
    } catch (com.google.api.gax.rpc.AlreadyExistsException e) {
      log.debug("task {} already enqueued; skipping (dedup)", dedupName);
    } catch (Exception e) {
      log.warn("failed to enqueue result check for match {}", matchId, e);
    }
  }
}
```

> Note: `CloudTasksClient.create()` per call is acceptable at this volume (a handful/day). If it ever gets hot, promote it to a singleton `@Bean` with `@PreDestroy` close.

- [ ] **Step 5: Test fake (records calls)**

```java
package io.quiniela.api.footballdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Test double: records enqueue calls instead of hitting Cloud Tasks. */
public class FakeResultsTaskQueue implements ResultsTaskQueue {

  public record Enqueued(long matchId, Instant when, String dedupName) {}

  public final List<Enqueued> calls = new ArrayList<>();

  @Override
  public void enqueue(long matchId, Instant when, String dedupName) {
    calls.add(new Enqueued(matchId, when, dedupName));
  }
}
```

- [ ] **Step 6: Compile to verify wiring**

Run: `./mvnw -q test-compile`
Expected: compiles clean.

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/java/io/quiniela/api/footballdata/ResultsTaskQueue.java \
        backend/src/main/java/io/quiniela/api/footballdata/NoopResultsTaskQueue.java \
        backend/src/main/java/io/quiniela/api/footballdata/CloudTasksResultsQueue.java \
        backend/src/test/java/io/quiniela/api/footballdata/FakeResultsTaskQueue.java
git commit -m "feat(sync): ResultsTaskQueue (Cloud Tasks + noop + fake)"
```

---

## Task 4: Sync service methods — `runDaily`, `planToday`, `syncMatch`, `syncFull`, `SyncResult`

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java`
- Test: `backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncServiceIT.java`

- [ ] **Step 1: Write the failing test**

Create `FootballDataSyncServiceIT.java`. It overrides the `FootballDataClient` with a stub and the `ResultsTaskQueue` with the fake via `@TestConfiguration`, then exercises planning + per-match behavior.

```java
package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.footballdata.FootballDataClient.CompetitionMatchesResponse;
import io.quiniela.api.footballdata.FootballDataClient.MatchApi;
import io.quiniela.api.footballdata.FootballDataClient.MatchScore;
import io.quiniela.api.footballdata.FootballDataClient.MatchScoreFull;
import io.quiniela.api.footballdata.FootballDataClient.MatchTeam;
import io.quiniela.api.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Import(FootballDataSyncServiceIT.Stubs.class)
@TestPropertySource(properties = {"app.football-data.enabled=true"})
class FootballDataSyncServiceIT extends AbstractIntegrationTest {

  // Mutable holder so each test sets the matches the stubbed client returns.
  static volatile CompetitionMatchesResponse stubbed = new CompetitionMatchesResponse(List.of());

  @TestConfiguration
  static class Stubs {
    @Bean @Primary
    FootballDataClient stubClient() {
      return new FootballDataClient("http://unused", "x") {
        @Override
        public CompetitionMatchesResponse getMatches(String code) {
          return stubbed;
        }
      };
    }

    @Bean @Primary
    FakeResultsTaskQueue fakeQueue() {
      return new FakeResultsTaskQueue();
    }
  }

  @Autowired FootballDataSyncService sync;
  @Autowired FakeResultsTaskQueue queue;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seedTeams() {
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name) VALUES "
            + "(6001,1,'HHH','Home'),(6002,1,'AAA','Away') ON CONFLICT (id) DO NOTHING");
  }

  private Long groupRound() {
    return jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP'", Long.class);
  }

  private static MatchApi match(long id, String status, Integer h, Integer a) {
    return new MatchApi(
        id, "2026-06-11T17:00:00Z", status, "GROUP_STAGE", "Group A",
        new MatchTeam(6001L, "H"), new MatchTeam(6002L, "A"),
        new MatchScore(h == null ? null : (h > a ? "HOME_TEAM" : "AWAY_TEAM"), "REGULAR",
            new MatchScoreFull(h, a), null));
  }

  @Test
  void planTodayEnqueuesOneTaskPerUpcomingUnplayedMatch() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6101, 1, ?, 'A', 6001, 6002, FALSE, now() + interval '2 hours'), "
            + "       (6102, 1, ?, 'A', 6001, 6002, TRUE,  now() + interval '3 hours'), "  // played → skip
            + "       (6103, 1, ?, 'A', 6001, 6002, FALSE, now() + interval '40 hours')",   // >24h → skip
        groupRound(), groupRound(), groupRound());

    int enqueued = sync.planToday();

    assertThat(enqueued).isEqualTo(1);
    assertThat(queue.calls).singleElement()
        .satisfies(c -> assertThat(c.matchId()).isEqualTo(6101L));
  }

  @Test
  void syncMatchAppliesFinishedResultAndDoesNotReEnqueue() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6201, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6201, "FINISHED", 2, 0)));

    sync.syncMatch(6201L);

    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 6201", Boolean.class)).isTrue();
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 6201", Integer.class)).isEqualTo(2);
    assertThat(queue.calls).isEmpty(); // got the result → no re-enqueue
  }

  @Test
  void syncMatchReEnqueuesWhenNotYetFinishedWithinWindow() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6202, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6202, "IN_PLAY", null, null)));

    sync.syncMatch(6202L);

    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 6202", Boolean.class)).isFalse();
    assertThat(queue.calls).singleElement()
        .satisfies(c -> assertThat(c.matchId()).isEqualTo(6202L));
  }

  @Test
  void syncMatchIsNoOpForAlreadyPlayedMatch() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, score_t1, score_t2, played, kickoff_at) "
            + "VALUES (6203, 1, ?, 'A', 6001, 6002, 1, 0, TRUE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6203, "FINISHED", 4, 4)));

    sync.syncMatch(6203L);

    // Frozen, and no re-enqueue.
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 6203", Integer.class)).isEqualTo(1);
    assertThat(queue.calls).isEmpty();
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=FootballDataSyncServiceIT`
Expected: FAIL — `planToday`, `syncMatch`, `runDaily`, `syncFull` don't exist; constructor lacks `FootballDataClient`, `ResultsTaskQueue`, `SyncProperties`.

- [ ] **Step 3: Add the methods + widen the constructor**

Edit `FootballDataSyncService`: add fields/params `FootballDataClient client`, `ResultsTaskQueue queue`, `SyncProperties props`, `@Value("${app.football-data.enabled:false}") boolean footballDataEnabled`, `@Value("${app.football-data.competition-code:WC}") String competitionCode`; add the `SyncResult` record and the four methods.

```java
  // add imports:
  // import java.time.Duration;
  // import java.util.List;
  // import java.util.Map;
  // import org.springframework.beans.factory.annotation.Value;

  public record SyncResult(boolean apiCalled, int matchesUpserted, int enqueued, String skippedReason) {
    static SyncResult skipped(String reason) { return new SyncResult(false, 0, 0, reason); }
  }

  // new fields (added alongside rounds/jdbc):
  private final FootballDataClient client;
  private final ResultsTaskQueue queue;
  private final SyncProperties props;
  private final boolean footballDataEnabled;
  private final String competitionCode;

  // replace the constructor with:
  public FootballDataSyncService(
      RoundRepository rounds,
      DataSource dataSource,
      FootballDataClient client,
      ResultsTaskQueue queue,
      SyncProperties props,
      @Value("${app.football-data.enabled:false}") boolean footballDataEnabled,
      @Value("${app.football-data.competition-code:WC}") String competitionCode) {
    this.rounds = rounds;
    this.jdbc = new JdbcTemplate(dataSource);
    this.client = client;
    this.queue = queue;
    this.props = props;
    this.footballDataEnabled = footballDataEnabled;
    this.competitionCode = competitionCode;
  }

  /** Daily entry point: refresh fixtures, then enqueue today's per-match checks. */
  public SyncResult runDaily() {
    if (!footballDataEnabled) return SyncResult.skipped("integration disabled");
    SyncResult full = syncFull();
    int enqueued = planToday();
    return new SyncResult(full.apiCalled(), full.matchesUpserted(), enqueued, null);
  }

  /** Ungated full upsert (fixtures + any finished results for unplayed rows). */
  public SyncResult syncFull() {
    if (!footballDataEnabled) return SyncResult.skipped("integration disabled");
    try {
      int n = upsertMatches(client.getMatches(competitionCode));
      return new SyncResult(true, n, 0, null);
    } catch (Exception e) {
      log.warn("syncFull failed", e);
      return new SyncResult(true, 0, 0, "api error: " + e.getMessage());
    }
  }

  /** Enqueue one task per not-yet-played match kicking off within the next 24h. */
  public int planToday() {
    List<Map<String, Object>> due =
        jdbc.queryForList(
            "SELECT id, kickoff_at FROM match "
                + "WHERE tournament_id = ? AND played = false "
                + "  AND kickoff_at BETWEEN now() AND now() + interval '24 hours' "
                + "ORDER BY kickoff_at",
            TOURNAMENT_ID);
    int count = 0;
    for (Map<String, Object> row : due) {
      long id = ((Number) row.get("id")).longValue();
      Instant kickoff = ((java.sql.Timestamp) row.get("kickoff_at")).toInstant();
      Instant when = kickoff.plus(Duration.ofMinutes(props.matchMinDurationMinutes()));
      queue.enqueue(id, when, "match-" + id + "-" + when.getEpochSecond());
      count++;
    }
    log.info("planToday: enqueued {} result checks", count);
    return count;
  }

  /** Per-task check: apply result if final, else re-enqueue within the poll window. */
  public SyncResult syncMatch(long matchId) {
    if (!footballDataEnabled) return SyncResult.skipped("integration disabled");

    Map<String, Object> row;
    try {
      row =
          jdbc.queryForMap(
              "SELECT played, kickoff_at FROM match WHERE id = ?", matchId);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      return SyncResult.skipped("unknown match");
    }
    if (Boolean.TRUE.equals(row.get("played"))) return SyncResult.skipped("already final");

    int n;
    try {
      n = upsertMatches(client.getMatches(competitionCode));
    } catch (Exception e) {
      log.warn("syncMatch {} api error", matchId, e);
      maybeReEnqueue(matchId, ((java.sql.Timestamp) row.get("kickoff_at")).toInstant());
      return new SyncResult(true, 0, 0, "api error: " + e.getMessage());
    }

    Boolean nowPlayed =
        jdbc.queryForObject("SELECT played FROM match WHERE id = ?", Boolean.class, matchId);
    if (Boolean.TRUE.equals(nowPlayed)) {
      return new SyncResult(true, n, 0, null); // got it; no re-enqueue
    }
    int enq = maybeReEnqueue(matchId, ((java.sql.Timestamp) row.get("kickoff_at")).toInstant());
    return new SyncResult(true, n, enq, null);
  }

  private int maybeReEnqueue(long matchId, Instant kickoff) {
    Instant deadline = kickoff.plus(Duration.ofHours(props.pollWindowHours()));
    Instant next = Instant.now().plus(Duration.ofMinutes(props.retryIntervalMinutes()));
    if (next.isAfter(deadline)) {
      log.warn("giving up on match {} — past poll window ({})", matchId, deadline);
      return 0;
    }
    queue.enqueue(matchId, next, "match-" + matchId + "-" + next.getEpochSecond());
    return 1;
  }
```

> **JPA-cache caveat:** read `played` back via `JdbcTemplate` (as above), not via a JPA repository, so the post-UPSERT value isn't masked by a stale first-level cache entity.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=FootballDataSyncServiceIT`
Expected: PASS (all four tests).

- [ ] **Step 5: Re-run the earlier suites to confirm no regression**

Run: `./mvnw -q test -Dtest=FootballDataSyncFreezeIT,FootballDataLoaderIT,FootballDataLoaderTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/FootballDataSyncService.java \
        backend/src/test/java/io/quiniela/api/footballdata/FootballDataSyncServiceIT.java
git commit -m "feat(sync): runDaily/planToday/syncMatch/syncFull with re-enqueue"
```

---

## Task 5: `SyncTokenFilter` + SecurityConfig

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/config/SyncTokenFilter.java`
- Modify: `backend/src/main/java/io/quiniela/api/config/SecurityConfig.java`
- Test: `backend/src/test/java/io/quiniela/api/config/SyncTokenFilterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.quiniela.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SyncTokenFilterTest {

  private MockHttpServletResponse run(String configured, String header) throws Exception {
    SyncTokenFilter filter = new SyncTokenFilter(configured);
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/sync/daily");
    if (header != null) req.addHeader("X-Sync-Token", header);
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req, res, new MockFilterChain());
    return res;
  }

  @Test
  void rejectsMissingHeader() throws Exception {
    assertThat(run("secret", null).getStatus()).isEqualTo(401);
  }

  @Test
  void rejectsWrongToken() throws Exception {
    assertThat(run("secret", "nope").getStatus()).isEqualTo(401);
  }

  @Test
  void failsClosedWhenNoTokenConfigured() throws Exception {
    assertThat(run("", "anything").getStatus()).isEqualTo(401);
  }

  @Test
  void passesOnMatch() throws Exception {
    MockHttpServletResponse res = run("secret", "secret");
    assertThat(res.getStatus()).isEqualTo(200);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=SyncTokenFilterTest`
Expected: FAIL — `SyncTokenFilter` does not exist.

- [ ] **Step 3: Implement the filter**

```java
package io.quiniela.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards /internal/** with a shared-secret header. The Cloud Run service is publicly invokable
 * (allUsers), so IAM can't gate this route — the secret is checked in-app. Fails closed when no
 * token is configured. Constant-time comparison to avoid timing leaks.
 */
@Component
public class SyncTokenFilter extends OncePerRequestFilter {

  private final String configuredToken;

  public SyncTokenFilter(@Value("${app.sync.token:}") String configuredToken) {
    this.configuredToken = configuredToken == null ? "" : configuredToken;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String provided = request.getHeader("X-Sync-Token");
    if (configuredToken.isEmpty() || provided == null || !constantTimeEquals(configuredToken, provided)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
```

> The unit test calls `doFilter` directly with a non-`/internal/...`? No — it posts to `/internal/sync/daily`, so `shouldNotFilter` is false and the body runs. On a pass, `MockFilterChain` leaves status 200.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=SyncTokenFilterTest`
Expected: PASS.

- [ ] **Step 5: Wire the filter + permit `/internal/**` in SecurityConfig**

Modify `SecurityConfig.filterChain`: add `/internal/**` to `permitAll` and register the filter before the authorization filter. Inject `SyncTokenFilter` into the bean method.

```java
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, JwtDecoder jwtDecoder, SyncTokenFilter syncTokenFilter) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authz ->
                authz
                    .requestMatchers(
                        "/actuator/**", "/auth/**", "/api/invite/**", "/api/public/**", "/internal/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
        .addFilterBefore(
            syncTokenFilter,
            org.springframework.security.web.access.intercept.AuthorizationFilter.class);
    return http.build();
  }
```

- [ ] **Step 6: Run security + smoke tests**

Run: `./mvnw -q test -Dtest=SyncTokenFilterTest,QuinielaApiApplicationTests`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/config/SyncTokenFilter.java \
        backend/src/main/java/io/quiniela/api/config/SecurityConfig.java \
        backend/src/test/java/io/quiniela/api/config/SyncTokenFilterTest.java
git commit -m "feat(sync): SyncTokenFilter guarding /internal/**"
```

---

## Task 6: `InternalSyncController`

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/footballdata/InternalSyncController.java`
- Test: `backend/src/test/java/io/quiniela/api/footballdata/InternalSyncControllerIT.java`

- [ ] **Step 1: Write the failing test**

```java
package io.quiniela.api.footballdata;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@TestPropertySource(properties = {"app.sync.token=test-token"})
class InternalSyncControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void rejectsWithoutToken() throws Exception {
    mockMvc.perform(post("/internal/sync/daily")).andExpect(status().isUnauthorized());
  }

  @Test
  void dailyWithTokenReturns200() throws Exception {
    mockMvc
        .perform(post("/internal/sync/daily").header("X-Sync-Token", "test-token"))
        .andExpect(status().isOk());
  }

  @Test
  void resultsWithTokenReturns200() throws Exception {
    mockMvc
        .perform(
            post("/internal/sync/results").param("matchId", "999999").header("X-Sync-Token", "test-token"))
        .andExpect(status().isOk());
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=InternalSyncControllerIT`
Expected: FAIL — controller missing (404/no mapping) and/or 401 on the token path.

- [ ] **Step 3: Implement the controller**

```java
package io.quiniela.api.footballdata;

import io.quiniela.api.footballdata.FootballDataSyncService.SyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-facing sync endpoints. Auth is the shared-secret header enforced by SyncTokenFilter
 * (not JWT). Always 200 for handled outcomes so Cloud Scheduler / Cloud Tasks don't retry-storm.
 */
@RestController
@RequestMapping("/internal/sync")
public class InternalSyncController {

  private final FootballDataSyncService service;

  public InternalSyncController(FootballDataSyncService service) {
    this.service = service;
  }

  @PostMapping("/daily")
  public ResponseEntity<SyncResult> daily() {
    return ResponseEntity.ok(service.runDaily());
  }

  @PostMapping("/results")
  public ResponseEntity<SyncResult> results(@RequestParam long matchId) {
    return ResponseEntity.ok(service.syncMatch(matchId));
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=InternalSyncControllerIT`
Expected: PASS. (`integration disabled` makes the bodies no-op but still 200; the unknown matchId returns `skipped("unknown match")`.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/InternalSyncController.java \
        backend/src/test/java/io/quiniela/api/footballdata/InternalSyncControllerIT.java
git commit -m "feat(sync): InternalSyncController (/internal/sync/daily,results)"
```

---

## Task 7: `AdminSyncController` (manual triggers)

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/footballdata/AdminSyncController.java`
- Test: `backend/src/test/java/io/quiniela/api/footballdata/AdminSyncControllerIT.java`

- [ ] **Step 1: Write the failing test**

```java
package io.quiniela.api.footballdata;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

class AdminSyncControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  private String tokenFor(UserRole role, String sub) {
    var u = users.save(new User(sub, sub + "@example.com", sub, null, role));
    return jwt.issue(u);
  }

  @Test
  void nonAdminIsForbidden() throws Exception {
    String token = tokenFor(UserRole.PLAYER, "g-pl");
    mockMvc
        .perform(post("/api/admin/sync/daily").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanTriggerDaily() throws Exception {
    String token = tokenFor(UserRole.ADMIN, "g-admin");
    mockMvc
        .perform(post("/api/admin/sync/daily").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void adminCanTriggerSingleMatch() throws Exception {
    String token = tokenFor(UserRole.ADMIN, "g-admin2");
    mockMvc
        .perform(post("/api/admin/sync/match/424242").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=AdminSyncControllerIT`
Expected: FAIL — controller missing.

- [ ] **Step 3: Implement the controller**

```java
package io.quiniela.api.footballdata;

import io.quiniela.api.footballdata.FootballDataSyncService.SyncResult;
import io.quiniela.api.user.AdminGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only manual triggers for the results sync (testing / catch-up). Reuses AdminGuard +
 * Google-JWT auth — no shared secret. Mirrors PaulAdminController.
 */
@RestController
@RequestMapping("/api/admin/sync")
public class AdminSyncController {

  private final FootballDataSyncService service;
  private final AdminGuard adminGuard;

  public AdminSyncController(FootballDataSyncService service, AdminGuard adminGuard) {
    this.service = service;
    this.adminGuard = adminGuard;
  }

  private static Long callerId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }

  /** Refresh fixtures and enqueue today's per-match result checks now. */
  @PostMapping("/daily")
  public ResponseEntity<SyncResult> daily(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    adminGuard.requireAdmin(callerId(jwt));
    return ResponseEntity.ok(service.runDaily());
  }

  /** Force an immediate result check for one match (bypasses the task schedule). */
  @PostMapping("/match/{matchId}")
  public ResponseEntity<SyncResult> match(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long matchId) {
    if (jwt == null) return ResponseEntity.status(401).build();
    adminGuard.requireAdmin(callerId(jwt));
    return ResponseEntity.ok(service.syncMatch(matchId));
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=AdminSyncControllerIT`
Expected: PASS.

- [ ] **Step 5: Run the whole footballdata + config test set**

Run: `./mvnw -q test -Dtest=FootballDataSyncFreezeIT,FootballDataSyncServiceIT,FootballDataLoaderIT,FootballDataLoaderTest,InternalSyncControllerIT,AdminSyncControllerIT,SyncTokenFilterTest,SyncPropertiesTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/footballdata/AdminSyncController.java \
        backend/src/test/java/io/quiniela/api/footballdata/AdminSyncControllerIT.java
git commit -m "feat(sync): AdminSyncController manual triggers"
```

---

## Task 8: Infrastructure (Terraform)

> IaC validation only — no live `apply` here. From `iac/`, use `terraform` (or `tofu`, whichever the repo uses — check `iac/README.md`). Replace `<...>` with the existing variable references.

**Files:**
- Create: `iac/tasks.tf`, `iac/scheduler.tf`
- Modify: `iac/secrets.tf`, `iac/service_accounts.tf`, `iac/apis.tf`, `iac/cloud_run.tf`

- [ ] **Step 1: Enable the new APIs (`iac/apis.tf`)**

Add to the enabled-services list (mirror existing entries):

```hcl
    "cloudscheduler.googleapis.com",  # daily sync planner
    "cloudtasks.googleapis.com",      # per-match result-check tasks
```

- [ ] **Step 2: Secret for the shared token (`iac/secrets.tf`)**

```hcl
# ─── sync shared secret (X-Sync-Token for /internal/** ) ─────────────────────
resource "random_password" "sync_token" {
  length  = 48
  special = false
}

resource "google_secret_manager_secret" "sync_token" {
  project   = var.project_id
  secret_id = "sync-token"
  replication { auto {} }
  depends_on = [google_project_service.enabled]
}

resource "google_secret_manager_secret_version" "sync_token" {
  secret      = google_secret_manager_secret.sync_token.id
  secret_data = random_password.sync_token.result
}
```

(Add `random` to `iac/providers.tf` `required_providers` if not present:
`random = { source = "hashicorp/random", version = "~> 3.6" }`.)

- [ ] **Step 3: Cloud Tasks queue (`iac/tasks.tf`)**

```hcl
resource "google_cloud_tasks_queue" "results_sync" {
  name     = "results-sync"
  location = var.region
  project  = var.project_id

  rate_limits {
    max_dispatches_per_second = 1
    max_concurrent_dispatches = 2
  }
  retry_config {
    max_attempts       = 5
    min_backoff        = "30s"
    max_backoff        = "300s"
    max_doublings      = 2
  }
  depends_on = [google_project_service.enabled]
}
```

- [ ] **Step 4: IAM — enqueuer + secret accessor (`iac/service_accounts.tf`)**

Mirror the existing `api_runtime_football_data` secret binding and add the Cloud Tasks role:

```hcl
resource "google_secret_manager_secret_iam_member" "api_runtime_sync_token" {
  secret_id = google_secret_manager_secret.sync_token.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api_runtime.email}"
}

resource "google_project_iam_member" "api_runtime_cloudtasks_enqueuer" {
  project = var.project_id
  role    = "roles/cloudtasks.enqueuer"
  member  = "serviceAccount:${google_service_account.api_runtime.email}"
}
```

- [ ] **Step 5: Daily Cloud Scheduler job (`iac/scheduler.tf`)**

```hcl
resource "google_cloud_scheduler_job" "daily_plan" {
  name      = "quiniela-daily-plan"
  project   = var.project_id
  region    = var.region
  schedule  = "0 11 * * *"   # 06:00 America/Bogota
  time_zone = "Etc/UTC"

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.api.uri}/internal/sync/daily"
    headers = {
      "X-Sync-Token" = random_password.sync_token.result
    }
  }
  retry_config {
    retry_count = 1
  }
  depends_on = [google_project_service.enabled]
}
```

- [ ] **Step 6: Wire env vars on the api service (`iac/cloud_run.tf`)**

Add these `env` blocks alongside the existing `APP_FOOTBALL_DATA_*` blocks, and add the secret/IAM deps to the service's `depends_on`:

```hcl
      env {
        name = "APP_SYNC_TOKEN"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.sync_token.secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "APP_SYNC_TASKS_ENABLED"
        value = "true"
      }
      env {
        name  = "APP_SYNC_TASKS_QUEUE"
        value = google_cloud_tasks_queue.results_sync.id
      }
      env {
        name  = "APP_SYNC_TASKS_TARGET_BASE"
        value = google_cloud_run_v2_service.api.uri
      }
```

Add to the api service resource's `depends_on` list:

```hcl
    google_secret_manager_secret_iam_member.api_runtime_sync_token,
    google_project_iam_member.api_runtime_cloudtasks_enqueuer,
```

> `google_cloud_tasks_queue.results_sync.id` is the full `projects/<p>/locations/<l>/queues/results-sync` path the app needs for `APP_SYNC_TASKS_QUEUE`. `APP_SYNC_TASKS_TARGET_BASE` self-references the service URI — Cloud Run computes `.uri` from a deterministic name, so this isn't a cycle.

- [ ] **Step 7: Validate**

Run (from `iac/`): `terraform fmt && terraform validate`
Expected: `Success! The configuration is valid.` (Run `terraform init` first if providers changed.)

- [ ] **Step 8: Commit**

```bash
git add iac/tasks.tf iac/scheduler.tf iac/secrets.tf iac/service_accounts.tf iac/apis.tf iac/cloud_run.tf iac/providers.tf
git commit -m "feat(sync): Cloud Tasks queue + daily Cloud Scheduler + secret/IAM/env"
```

---

## Task 9: Full suite + final commit

- [ ] **Step 1: Run the complete backend test suite**

Run (from `backend/`): `./mvnw -q test`
Expected: BUILD SUCCESS. If unrelated flaky ITs surface, re-run the affected class; the sync feature's classes must all pass.

- [ ] **Step 2: Manual-test checklist (record outcomes; do not block the commit)**

These are for the live "game in 2 hours" validation once deployed — capture in the PR description, not automated here:
1. `terraform apply` in `iac/`.
2. Deploy the api image.
3. From an admin session: `POST /api/admin/sync/daily` → confirm a Cloud Task appears at `kickoff + 105 min` (`gcloud tasks list --queue=results-sync --location=<region>`).
4. After full-time: `POST /api/admin/sync/match/{id}` → confirm the result + points landed, and that an already-played group game's score is untouched.

- [ ] **Step 3: Final integration commit (if any formatting/cleanup remains)**

```bash
git add -A
git commit -m "chore(sync): tidy + full suite green" || echo "nothing to commit"
```

---

## Self-Review Notes (addressed)

- **Spec coverage:** freeze guard (Task 1), post-match per-match polling + re-enqueue (Task 4), Cloud Tasks + daily Scheduler + schedule-from-DB (Tasks 3,4,8), shared-secret auth (Task 5), internal + admin endpoints (Tasks 6,7), config (Task 2), IaC incl. APIs/secret/IAM/env (Task 8). Daily full-sync fallback = `runDaily()→syncFull()` (Task 4).
- **Type consistency:** `SyncResult(boolean apiCalled, int matchesUpserted, int enqueued, String skippedReason)`, `ResultsTaskQueue.enqueue(long, Instant, String)`, `upsertMatches(CompetitionMatchesResponse)→int`, `syncMatch(long)→SyncResult` — used identically across service, controllers, and tests.
- **Known follow-ups (out of scope):** single-match `getMatch(id)` API optimization; per-match manual-result lock; admin-UI button calling `/api/admin/sync/*`.

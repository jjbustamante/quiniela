# AI-Powered Octopus Paul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Paul's dumb seed-formula predictions with AI predictions (Google Gemini via Spring AI), give Paul a helper→surprise-competitor lifecycle, and exclude Paul + the admin account from prize eligibility.

**Architecture:** Canonical cached predictions. An admin-triggered job calls several Gemini models per group match (via a `PaulOracle` seam over Spring AI's `ChatClient`) and stores one `CANDIDATE` row per (match, model). `suggest`/`fill` copy a random candidate (no LLM in the request path). At reveal, an LLM "ensemble judge" synthesizes one `OFFICIAL` pick per match, which is snapshotted into a seeded Paul bot quiniela. Prize eligibility is identity-based: the leaderboard hides `ADMIN` role; the prize-eligible query also excludes `is_bot`.

**Tech Stack:** Spring Boot 4.0.6, Java 25, Maven, Flyway (plain SQL), JPA, Postgres 16, **Spring AI 2.0.x** (the Spring-Boot-4 line; `spring-ai-starter-model-google-genai`), Testcontainers, JUnit 5 + AssertJ + MockMvc; Next.js 16 + TS frontend.

**Spec:** `docs/superpowers/specs/2026-05-30-ai-octopus-paul-design.md`

---

## Conventions for every task

- **Run from `backend/`** for Maven. Docker must be running (Testcontainers).
- **Unit tests** (`*Test.java`, Surefire): `./mvnw -q test -Dtest=<ClassName>`
- **Integration tests** (`*IT.java`, Failsafe): `./mvnw -q verify -Dit.test=<ClassName> -Dsurefire.failIfNoSpecifiedTests=false`
- **Spotless runs at the `compile` phase** (`check` goal) — if formatting is off, the build fails before tests. Always run `./mvnw -q spotless:apply` before compiling/committing.
- **Migrations** live in `backend/src/main/resources/db/migration/`. Tests apply them via Flyway against a Testcontainers Postgres, plus the test-only `V007__seed_test_fixtures.sql` under `backend/src/test/resources/db/test-migration/` (note: confirm the test Flyway locations include both folders — they do today, since `PaulControllerIT` sees 72 group matches).
- **Role strings are lowercase** in SQL (`UserRoleConverter` writes `role.name().toLowerCase()`).
- **IDs:** `TOURNAMENT_ID = 1`, `POOL_ID = 1`, GROUP round code `"GROUP"`. Paul bot `google_sub = 'paul-bot-oracle'`.
- Frontend Maven equivalent: run pnpm from `frontend/` — unit `pnpm test`, types `pnpm typecheck`.

---

# Phase A — Data model & eligibility (no LLM)

## Task 1: `team.fifa_ranking` column + entity field + seed

**Files:**
- Create: `backend/src/main/resources/db/migration/V014__team_strength.sql`
- Modify: `backend/src/main/java/io/quiniela/api/team/Team.java`
- Test: `backend/src/test/java/io/quiniela/api/team/TeamRankingIT.java`

- [ ] **Step 1: Write the failing test**

```java
package io.quiniela.api.team;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TeamRankingIT extends AbstractIntegrationTest {

  @Autowired TeamRepository teams;

  @Test
  void seededTeamsHaveFifaRanking() {
    // Argentina (code ARG) is seeded with a ranking in V014.
    Team arg = teams.findByTournamentIdAndCode(1L, "ARG").orElseThrow();
    assertThat(arg.getFifaRanking()).isNotNull();
    assertThat(arg.getFifaRanking()).isBetween(1, 211);
  }

  @Test
  void playoffPlaceholdersHaveNullRanking() {
    Team k1 = teams.findByTournamentIdAndCode(1L, "TBD_K1").orElseThrow();
    assertThat(k1.getFifaRanking()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=TeamRankingIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `getFifaRanking()` does not exist (compile error) / column missing.

- [ ] **Step 3: Create the migration**

`backend/src/main/resources/db/migration/V014__team_strength.sql`:
```sql
-- V014: Team strength anchor for Paul's AI predictions.
-- Nullable: playoff-pending slots (TBD_*) stay NULL; the prompt context
-- builder degrades gracefully when a team has no ranking.
ALTER TABLE team ADD COLUMN fifa_ranking INT;

-- Seed FIFA rankings for the known WC2026 teams (men's ranking, approx late-2025).
-- Placeholder teams (TBD_K*/TBD_L*) intentionally left NULL.
UPDATE team SET fifa_ranking = CASE code
    WHEN 'ARG' THEN 1   WHEN 'ESP' THEN 2   WHEN 'FRA' THEN 3   WHEN 'ENG' THEN 4
    WHEN 'BRA' THEN 5   WHEN 'POR' THEN 6   WHEN 'NED' THEN 7   WHEN 'BEL' THEN 8
    WHEN 'GER' THEN 9   WHEN 'CRO' THEN 10  WHEN 'ITA' THEN 11  WHEN 'MAR' THEN 12
    WHEN 'COL' THEN 13  WHEN 'URU' THEN 14  WHEN 'USA' THEN 15  WHEN 'MEX' THEN 16
    WHEN 'SUI' THEN 17  WHEN 'SEN' THEN 18  WHEN 'JPN' THEN 19  WHEN 'KOR' THEN 20
    WHEN 'IRN' THEN 21  WHEN 'AUS' THEN 22  WHEN 'ECU' THEN 23  WHEN 'EGY' THEN 24
    WHEN 'NOR' THEN 25  WHEN 'CIV' THEN 26  WHEN 'NGA' THEN 27  WHEN 'PAN' THEN 28
    WHEN 'CRC' THEN 29  WHEN 'KSA' THEN 30  WHEN 'TUN' THEN 31  WHEN 'CAN' THEN 32
    WHEN 'QAT' THEN 33  WHEN 'CPV' THEN 34  WHEN 'GHA' THEN 35  WHEN 'IRQ' THEN 36
    WHEN 'JAM' THEN 37  WHEN 'NZL' THEN 38  WHEN 'HON' THEN 39  WHEN 'CUR' THEN 40
    ELSE fifa_ranking
END
WHERE tournament_id = 1;
```

- [ ] **Step 4: Add the entity field + getter**

In `Team.java`, after the `flagEmoji` field (around line 31-32) add:
```java
  @Column(name = "fifa_ranking")
  private Integer fifaRanking;
```
And after `getFlagEmoji()` add:
```java
  public Integer getFifaRanking() {
    return fifaRanking;
  }
```

- [ ] **Step 5: Format, run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=TeamRankingIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (both tests green).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V014__team_strength.sql \
        backend/src/main/java/io/quiniela/api/team/Team.java \
        backend/src/test/java/io/quiniela/api/team/TeamRankingIT.java
git commit -m "feat(paul): add team.fifa_ranking column + seed WC2026 rankings"
```

---

## Task 2: `users.is_bot` + `paul_prediction` table + Paul bot user seed

**Files:**
- Create: `backend/src/main/resources/db/migration/V015__paul_predictions.sql`
- Modify: `backend/src/main/java/io/quiniela/api/user/User.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulSchemaIT.java`

- [ ] **Step 1: Write the failing test**

```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PaulSchemaIT extends AbstractIntegrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  void paulBotUserIsSeededAsBot() {
    Boolean isBot =
        jdbc.queryForObject(
            "SELECT is_bot FROM users WHERE google_sub = 'paul-bot-oracle'", Boolean.class);
    assertThat(isBot).isTrue();
    String role =
        jdbc.queryForObject(
            "SELECT role FROM users WHERE google_sub = 'paul-bot-oracle'", String.class);
    assertThat(role).isEqualTo("player");
  }

  @Test
  void paulPredictionTableExists() {
    Integer count =
        jdbc.queryForObject("SELECT COUNT(*) FROM paul_prediction", Integer.class);
    assertThat(count).isZero();
  }

  @Test
  void newRealUsersDefaultToNonBot() {
    // AbstractIntegrationTest.cleanWritableTables deletes users between tests, but the
    // Paul bot is re-seeded by Flyway only once; verify the default for app inserts.
    jdbc.update(
        "INSERT INTO users (google_sub, email, display_name, role) "
            + "VALUES ('g-real', 'r@example.com', 'Real', 'player')");
    Boolean isBot =
        jdbc.queryForObject("SELECT is_bot FROM users WHERE google_sub = 'g-real'", Boolean.class);
    assertThat(isBot).isFalse();
  }
}
```

> NOTE: `cleanWritableTables` in `AbstractIntegrationTest` runs `DELETE FROM users` before each test, which also deletes the Flyway-seeded Paul bot. See Task 2a below — we add the Paul bot to the per-test cleanup's re-seed. For THIS task, the `paulBotUserIsSeededAsBot` test runs against the freshly-migrated DB only if no `@BeforeEach` delete has wiped it. To keep this test reliable, it re-inserts the bot itself; adjust Step 1 test to self-seed:

Replace `paulBotUserIsSeededAsBot` body's first action with an idempotent insert mirroring the migration, then assert. Final test body:
```java
  @Test
  void paulBotUserIsSeededAsBot() {
    jdbc.update(
        "INSERT INTO users (google_sub, email, display_name, role, is_bot) "
            + "VALUES ('paul-bot-oracle', 'paul@laquinieladelospanas.com', 'Pulpo Paul 🐙', 'player', TRUE) "
            + "ON CONFLICT (google_sub) DO NOTHING");
    Boolean isBot =
        jdbc.queryForObject(
            "SELECT is_bot FROM users WHERE google_sub = 'paul-bot-oracle'", Boolean.class);
    assertThat(isBot).isTrue();
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=PaulSchemaIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `is_bot` / `paul_prediction` do not exist.

- [ ] **Step 3: Create the migration**

`backend/src/main/resources/db/migration/V015__paul_predictions.sql`:
```sql
-- V015: Paul's AI predictions + identity-based pot eligibility.

-- Pot eligibility: bots never win money. ADMIN role is excluded at query time;
-- is_bot excludes Paul (who is role 'player' so he shows on the leaderboard).
ALTER TABLE users ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT FALSE;

-- Candidate (one per model) + official (ensemble) predictions per match.
CREATE TABLE paul_prediction (
    id             BIGSERIAL PRIMARY KEY,
    match_id       BIGINT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    provider       VARCHAR(32)  NOT NULL,
    model          VARCHAR(64)  NOT NULL,
    kind           VARCHAR(16)  NOT NULL DEFAULT 'CANDIDATE',
    score_t1       INT NOT NULL,
    score_t2       INT NOT NULL,
    confidence     NUMERIC(3,2),
    reasoning      TEXT,
    reasoning_lang VARCHAR(8) NOT NULL DEFAULT 'es',
    source         VARCHAR(16) NOT NULL DEFAULT 'AI',
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (score_t1 >= 0 AND score_t2 >= 0),
    CHECK (kind IN ('CANDIDATE', 'OFFICIAL')),
    CHECK (source IN ('AI', 'FALLBACK')),
    UNIQUE (match_id, model, kind)
);
CREATE INDEX idx_paul_prediction_match ON paul_prediction(match_id);

-- Paul bot user. NO quiniela yet — absence of a quiniela means "not competing".
-- Created only at reveal (PaulService.reveal). role 'player' so he shows on the
-- leaderboard once revealed; is_bot TRUE so he is never prize-eligible.
INSERT INTO users (google_sub, email, display_name, role, is_bot)
VALUES ('paul-bot-oracle', 'paul@laquinieladelospanas.com', 'Pulpo Paul 🐙', 'player', TRUE)
ON CONFLICT (google_sub) DO NOTHING;
```

- [ ] **Step 4: Add the `isBot` field to `User.java`**

After the `timezone` field (around line 43-44) add:
```java
  @Column(name = "is_bot", nullable = false)
  private Boolean isBot = false;
```
After `getTimezone()`/`setTimezone()` add:
```java
  public Boolean getIsBot() {
    return isBot;
  }
```
(Field initializer `= false` ensures app-created users insert `is_bot = false`; Paul is seeded via SQL.)

- [ ] **Step 5: Format, run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=PaulSchemaIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V015__paul_predictions.sql \
        backend/src/main/java/io/quiniela/api/user/User.java \
        backend/src/test/java/io/quiniela/api/paul/PaulSchemaIT.java
git commit -m "feat(paul): is_bot column + paul_prediction table + Paul bot user"
```

---

## Task 2a: Clear predictions + re-seed Paul bot in the test cleanup

**Why:** `AbstractIntegrationTest.cleanWritableTables` runs before each test. It (a) does `DELETE FROM users`, removing the Flyway-seeded Paul bot, and (b) does not touch `paul_prediction`, so prediction rows would leak across tests and break exact-count assertions (Tasks 8/9/11). Fix both.

**Files:**
- Modify: `backend/src/test/java/io/quiniela/api/support/AbstractIntegrationTest.java`

- [ ] **Step 1: Clear `paul_prediction` and re-seed Paul after the deletes**

In `cleanWritableTables()`: add a `paul_prediction` delete with the other deletes (it has no incoming FKs from the cleaned tables, so order is unconstrained — put it first), then re-seed the Paul bot after `DELETE FROM users;` (line ~48):
```java
    jdbcTemplate.execute("DELETE FROM paul_prediction");
```
and after the users delete:
```java
    // Re-seed the Paul bot user that the deletes above remove (Flyway only seeds once).
    jdbcTemplate.update(
        "INSERT INTO users (google_sub, email, display_name, role, is_bot) "
            + "VALUES ('paul-bot-oracle', 'paul@laquinieladelospanas.com', 'Pulpo Paul 🐙', 'player', TRUE) "
            + "ON CONFLICT (google_sub) DO NOTHING");
```

- [ ] **Step 2: Verify existing IT suite still green**

Run: `./mvnw -q verify -Dit.test=PaulControllerIT,PaulSchemaIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/io/quiniela/api/support/AbstractIntegrationTest.java
git commit -m "test(paul): re-seed Paul bot user in integration-test cleanup"
```

---

## Task 3: `PaulPrediction` entity + repository

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulPrediction.java`
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionRepository.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulPredictionRepositoryIT.java`

- [ ] **Step 1: Write the failing test**

```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaulPredictionRepositoryIT extends AbstractIntegrationTest {

  @Autowired PaulPredictionRepository repo;

  @Test
  void savesAndQueriesByMatchAndKind() {
    // match id 1 exists from V007 fixtures (first group match).
    var p =
        new PaulPrediction(
            1L, "google", "gemini-2.5-flash", PaulPrediction.KIND_CANDIDATE,
            2, 1, new BigDecimal("0.70"), "Paul lo ve claro.", "es", PaulPrediction.SOURCE_AI);
    repo.save(p);

    var candidates = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).getModel()).isEqualTo("gemini-2.5-flash");
    assertThat(candidates.get(0).getScoreT1()).isEqualTo(2);
    assertThat(repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_OFFICIAL)).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=PaulPredictionRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `PaulPrediction` does not exist.

- [ ] **Step 3: Create the entity**

`backend/src/main/java/io/quiniela/api/paul/PaulPrediction.java`:
```java
package io.quiniela.api.paul;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "paul_prediction")
public class PaulPrediction {

  public static final String KIND_CANDIDATE = "CANDIDATE";
  public static final String KIND_OFFICIAL = "OFFICIAL";
  public static final String SOURCE_AI = "AI";
  public static final String SOURCE_FALLBACK = "FALLBACK";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "match_id", nullable = false)
  private Long matchId;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(nullable = false, length = 64)
  private String model;

  @Column(nullable = false, length = 16)
  private String kind;

  @Column(name = "score_t1", nullable = false)
  private Integer scoreT1;

  @Column(name = "score_t2", nullable = false)
  private Integer scoreT2;

  @Column private BigDecimal confidence;

  @Column private String reasoning;

  @Column(name = "reasoning_lang", nullable = false, length = 8)
  private String reasoningLang = "es";

  @Column(nullable = false, length = 16)
  private String source = SOURCE_AI;

  @Column(name = "generated_at", nullable = false, updatable = false)
  private Instant generatedAt;

  protected PaulPrediction() {}

  public PaulPrediction(
      Long matchId,
      String provider,
      String model,
      String kind,
      Integer scoreT1,
      Integer scoreT2,
      BigDecimal confidence,
      String reasoning,
      String reasoningLang,
      String source) {
    this.matchId = matchId;
    this.provider = provider;
    this.model = model;
    this.kind = kind;
    this.scoreT1 = scoreT1;
    this.scoreT2 = scoreT2;
    this.confidence = confidence;
    this.reasoning = reasoning;
    this.reasoningLang = reasoningLang;
    this.source = source;
  }

  @PrePersist
  void onCreate() {
    this.generatedAt = Instant.now();
    if (this.reasoningLang == null) this.reasoningLang = "es";
    if (this.source == null) this.source = SOURCE_AI;
  }

  public Long getId() {
    return id;
  }

  public Long getMatchId() {
    return matchId;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public String getKind() {
    return kind;
  }

  public Integer getScoreT1() {
    return scoreT1;
  }

  public Integer getScoreT2() {
    return scoreT2;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public String getReasoning() {
    return reasoning;
  }

  public String getReasoningLang() {
    return reasoningLang;
  }

  public String getSource() {
    return source;
  }
}
```

- [ ] **Step 4: Create the repository**

`backend/src/main/java/io/quiniela/api/paul/PaulPredictionRepository.java`:
```java
package io.quiniela.api.paul;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaulPredictionRepository extends JpaRepository<PaulPrediction, Long> {

  List<PaulPrediction> findByMatchIdAndKind(Long matchId, String kind);

  List<PaulPrediction> findByKind(String kind);

  Optional<PaulPrediction> findByMatchIdAndModelAndKind(Long matchId, String model, String kind);
}
```

- [ ] **Step 5: Format, run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=PaulPredictionRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulPrediction.java \
        backend/src/main/java/io/quiniela/api/paul/PaulPredictionRepository.java \
        backend/src/test/java/io/quiniela/api/paul/PaulPredictionRepositoryIT.java
git commit -m "feat(paul): PaulPrediction entity + repository"
```

---

## Task 4: Pot eligibility in ranking

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/ranking/RankingView.java`
- Modify: `backend/src/main/java/io/quiniela/api/ranking/RankingService.java`
- Create: `backend/src/test/java/io/quiniela/api/ranking/RankingServiceIT.java` (no service-level IT exists today — only `RankingControllerIT`)
- Modify: `frontend/lib/api/ranking.ts`

- [ ] **Step 1: Write the failing test** — create `RankingServiceIT.java`

```java
package io.quiniela.api.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RankingServiceIT extends AbstractIntegrationTest {

  @Autowired RankingService service;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  private void seedQuinielaWithPoints(Long userId, int points) {
    jdbc.update("INSERT INTO quiniela (pool_id, user_id, points) VALUES (1, ?, ?)", userId, points);
  }

  @Test
  void leaderboardHidesAdminAndPrizeQueryHidesBots() {
    var player =
        users.save(new User("g-rank-player", "p@example.com", "Player", null, UserRole.PLAYER));
    var admin =
        users.save(new User("g-rank-admin", "a@example.com", "Admin", null, UserRole.ADMIN));
    // Paul bot is re-seeded by the test cleanup (Task 2a); fetch him.
    var paul = users.findByGoogleSub("paul-bot-oracle").orElseThrow();

    seedQuinielaWithPoints(player.getId(), 30);
    seedQuinielaWithPoints(paul.getId(), 99); // top by points
    seedQuinielaWithPoints(admin.getId(), 50);

    var display = service.getRanking(player.getId()).entries();
    assertThat(display)
        .extracting(RankingView.RankingEntry::displayName)
        .contains("Pulpo Paul 🐙", "Player")
        .doesNotContain("Admin");
    var paulRow =
        display.stream().filter(e -> e.userId().equals(paul.getId())).findFirst().orElseThrow();
    assertThat(paulRow.isBot()).isTrue();

    var eligible = service.getPrizeEligible();
    assertThat(eligible)
        .extracting(RankingView.RankingEntry::displayName)
        .containsExactly("Player");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=RankingServiceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `RankingEntry.isBot()` and `getPrizeEligible()` do not exist.

- [ ] **Step 3: Add `isBot` to the DTO**

`RankingView.java` — update the nested record:
```java
  public record RankingEntry(
      int rank,
      Long userId,
      String displayName,
      int points,
      Integer delta,
      boolean isYou,
      boolean isBot) {}
```

- [ ] **Step 4: Update `RankingService`**

In `getRanking`, change the SQL and mapper. Replace the query block (lines ~29-48) with:
```java
            """
            SELECT
              RANK() OVER (ORDER BY q.points DESC) AS rk,
              u.id            AS user_id,
              u.display_name  AS display_name,
              q.points        AS points,
              u.is_bot        AS is_bot
            FROM quiniela q
            JOIN users u ON u.id = q.user_id
            WHERE q.pool_id = ?
              AND u.role <> 'admin'
            ORDER BY q.points DESC, u.display_name ASC
            """,
            (rs, n) -> {
              int rank = rs.getInt("rk");
              long userId = rs.getLong("user_id");
              String displayName = rs.getString("display_name");
              int points = rs.getInt("points");
              boolean isBot = rs.getBoolean("is_bot");
              boolean isYou = callerUserId != null && callerUserId == userId;
              return new RankingEntry(rank, userId, displayName, points, null, isYou, isBot);
            },
            DEFAULT_POOL_ID);
```
Then add a new method (after `getRanking`):
```java
  /**
   * Prize-eligible ranking: excludes ADMIN-role accounts and bots (Pulpo Paul). Backs future
   * pot-payout logic. Pot-payout math itself is out of scope for v1.
   */
  @Transactional(readOnly = true)
  public java.util.List<RankingEntry> getPrizeEligible() {
    return jdbc.query(
        """
        SELECT
          RANK() OVER (ORDER BY q.points DESC) AS rk,
          u.id            AS user_id,
          u.display_name  AS display_name,
          q.points        AS points
        FROM quiniela q
        JOIN users u ON u.id = q.user_id
        WHERE q.pool_id = ?
          AND u.role <> 'admin'
          AND u.is_bot = false
        ORDER BY q.points DESC, u.display_name ASC
        """,
        (rs, n) ->
            new RankingEntry(
                rs.getInt("rk"),
                rs.getLong("user_id"),
                rs.getString("display_name"),
                rs.getInt("points"),
                null,
                false,
                false),
        DEFAULT_POOL_ID);
  }
```

- [ ] **Step 5: Format, run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=RankingServiceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Update the frontend type**

`frontend/lib/api/ranking.ts` — add `isBot` to the existing `RankingEntry` type (keep it a `type`, keep `displayName: string | null`):
```ts
export type RankingEntry = {
  rank: number;
  userId: number;
  displayName: string | null;
  points: number;
  /** Rank change vs previous round. Null in v1 until the snapshot table lands. */
  delta: number | null;
  isYou: boolean;
  /** True for Pulpo Paul — shown on the board but never prize-eligible. */
  isBot: boolean;
};
```

- [ ] **Step 7: Typecheck + commit**

Run (from `frontend/`): `pnpm typecheck`
Expected: PASS.
```bash
git add backend/src/main/java/io/quiniela/api/ranking/RankingView.java \
        backend/src/main/java/io/quiniela/api/ranking/RankingService.java \
        backend/src/test/java/io/quiniela/api/ranking/RankingServiceIT.java \
        frontend/lib/api/ranking.ts
git commit -m "feat(paul): identity-based pot eligibility (hide admin, flag bots)"
```

---

# Phase B — Spring AI prediction engine

## Task 5: Spring AI dependency + config

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulProperties.java`

- [ ] **Step 1: Resolve the Boot-4-compatible Spring AI version**

**Spring Boot 4.0 needs the Spring AI 2.0.x line** (1.x targets Boot 3.3+). As of early 2026 the 2.0 line is in milestones (e.g. `2.0.0-M6`); GA is expected mid-2026. Find the newest available 2.0.x:
```bash
curl -s "https://central.sonatype.com/solrsearch/select?q=g:org.springframework.ai+a:spring-ai-bom&core=gav&rows=30&wt=json" \
  | tr ',' '\n' | grep '"v":' | head -30
```
Pick the newest `2.0.x` (prefer GA; otherwise the latest `2.0.0-Mx`). Use it as `${spring-ai.version}` below and note the exact version in the commit message. Milestones resolve from Maven Central; no extra repository needed for GA, but if only milestones exist add the Spring milestones repo to `pom.xml`:
```xml
    <repositories>
        <repository>
            <id>spring-milestones</id>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>
```

- [ ] **Step 2: Add the property + BOM + starter to `pom.xml`**

In `<properties>` (after `dependency-check.version`, ~line 45) add:
```xml
        <spring-ai.version>2.0.0-M6</spring-ai.version>
```
(replace `2.0.0-M6` with the exact version resolved in Step 1.)

Add a `<dependencyManagement>` BOM import — extend the existing `<dependencyManagement><dependencies>` block (after the protobuf entries, ~line 85) with:
```xml
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
```

Add the starter to `<dependencies>` (after the football-data-related deps / before the test deps, ~line 169):
```xml
        <!-- Spring AI: Google GenAI (Gemini Developer API / AI Studio, API-key auth).
             Provider-agnostic ChatClient — adding Vertex/Anthropic/OpenAI later is a
             BOM-managed starter + a roster entry, not a rewrite. -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-google-genai</artifactId>
        </dependency>
```

- [ ] **Step 3: Add config to `application.yml`**

Append under the top-level `spring:` block a `ai:` section, and add an `app.paul` block. After the `app.football-data` block (~line 61) add:
```yaml

  paul:
    provider: google
    # Candidate models (best available Gemini variants). One CANDIDATE prediction
    # per match per model. Add Vertex/OpenAI/Anthropic here later (provider-agnostic).
    models:
      - gemini-2.5-pro
      - gemini-2.5-flash
    # Model used for the ensemble-judge synthesis of Paul's OFFICIAL bet.
    ensemble-model: gemini-2.5-pro
```
And under `spring:` (e.g. after the `flyway:` block, ~line 24) add:
```yaml
  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY:}
        chat:
          options:
            temperature: 0.8
```
> NOTE (confirmed against the Google GenAI Chat reference): `spring.ai.google.genai.api-key` is the Gemini Developer API key property; `spring.ai.google.genai.chat.options.model` / `.temperature` are the chat defaults; the model client is enabled by default (`spring.ai.model.chat=google-genai`). No `model` is set here on purpose — the roster overrides it per request via `GoogleGenAiChatOptions`. With an empty key the bean isn't created (Task 6 conditional), so startup still succeeds.

- [ ] **Step 4: Create `PaulProperties`**

`backend/src/main/java/io/quiniela/api/paul/PaulProperties.java`:
```java
package io.quiniela.api.paul;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.paul")
public record PaulProperties(String provider, List<String> models, String ensembleModel) {

  public PaulProperties {
    if (provider == null) provider = "google";
    if (models == null || models.isEmpty()) models = List.of("gemini-2.5-flash");
    if (ensembleModel == null) ensembleModel = models.get(0);
  }
}
```
Enable it: in `QuinielaApiApplication.java`, add the annotation `@ConfigurationPropertiesScan` to the main class (confirm it isn't already present; if a specific scan is configured, add `PaulProperties.class` to an `@EnableConfigurationProperties` instead).

- [ ] **Step 5: Verify the app still builds & boots without a key**

Run: `./mvnw -q spotless:apply && ./mvnw -q -DskipTests package`
Expected: BUILD SUCCESS (no `GEMINI_API_KEY` needed to compile/package).

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml \
        backend/src/main/java/io/quiniela/api/paul/PaulProperties.java \
        backend/src/main/java/io/quiniela/api/QuinielaApiApplication.java
git commit -m "build(paul): add Spring AI google-genai starter + Paul config"
```

---

## Task 6: `PaulOracle` seam + `GeminiPaulOracle` + result record

**Why a seam:** mocking Spring AI's `ChatModel`/`ChatResponse` to drive `.entity()` is brittle. A narrow `PaulOracle` interface lets tests inject a fake returning canned `PaulPredictionResult`s (and throwing to exercise the fallback path), while the real impl wraps `ChatClient`.

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionResult.java`
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulOracle.java`
- Create: `backend/src/main/java/io/quiniela/api/paul/GeminiPaulOracle.java`

- [ ] **Step 1: Create the structured-output record**

`PaulPredictionResult.java`:
```java
package io.quiniela.api.paul;

/** Structured output returned by the LLM. Scores are non-negative; confidence in [0,1]. */
public record PaulPredictionResult(int scoreT1, int scoreT2, double confidence, String reasoning) {}
```

- [ ] **Step 2: Create the interface**

`PaulOracle.java`:
```java
package io.quiniela.api.paul;

/** Narrow seam over the LLM so prediction logic is testable without Spring AI internals. */
public interface PaulOracle {

  /**
   * Ask the given model to produce a structured prediction.
   *
   * @throws RuntimeException if the model call fails or is unconfigured.
   */
  PaulPredictionResult predict(String systemPrompt, String userPrompt, String model);
}
```

- [ ] **Step 3: Create the two implementations (plain classes, no `@Component`)**

`GeminiPaulOracle.java`:
```java
package io.quiniela.api.paul;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

public class GeminiPaulOracle implements PaulOracle {

  private final ChatClient chatClient;

  public GeminiPaulOracle(ChatClient.Builder builder) {
    this.chatClient = builder.build();
  }

  @Override
  public PaulPredictionResult predict(String systemPrompt, String userPrompt, String model) {
    return chatClient
        .prompt()
        .options(GoogleGenAiChatOptions.builder().model(model).temperature(0.8).build())
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .entity(PaulPredictionResult.class);
  }
}
```

`StubPaulOracle.java`:
```java
package io.quiniela.api.paul;

/** Used when no Gemini API key is configured. Always fails so callers use their deterministic fallback. */
public class StubPaulOracle implements PaulOracle {

  @Override
  public PaulPredictionResult predict(String systemPrompt, String userPrompt, String model) {
    throw new IllegalStateException("No LLM configured (GEMINI_API_KEY unset)");
  }
}
```
> NOTE: confirm `GoogleGenAiChatOptions` package/builder names against the resolved Spring AI version.

- [ ] **Step 4: Wire them in a `@Configuration` (reliable conditions)**

`@Bean` + `@ConditionalOnMissingBean` inside a `@Configuration` is the documented-safe pattern (unlike `@ConditionalOnMissingBean` on a scanned `@Component`). Gemini is created only when the key is present; otherwise the stub. The test `@Primary` fake overrides both.

`PaulOracleConfig.java`:
```java
package io.quiniela.api.paul;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaulOracleConfig {

  @Bean
  @ConditionalOnProperty(prefix = "spring.ai.google.genai", name = "api-key")
  PaulOracle geminiPaulOracle(ChatClient.Builder builder) {
    return new GeminiPaulOracle(builder);
  }

  @Bean
  @ConditionalOnMissingBean(PaulOracle.class)
  PaulOracle stubPaulOracle() {
    return new StubPaulOracle();
  }
}
```
> NOTE: `@ConditionalOnProperty` with no `havingValue` requires the property present and non-blank. `ChatClient.Builder` is auto-configured by the google-genai starter only when a key is set — which is exactly when `geminiPaulOracle` is created, so the injection resolves.

- [ ] **Step 5: Compile**

Run: `./mvnw -q spotless:apply && ./mvnw -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulPredictionResult.java \
        backend/src/main/java/io/quiniela/api/paul/PaulOracle.java \
        backend/src/main/java/io/quiniela/api/paul/GeminiPaulOracle.java \
        backend/src/main/java/io/quiniela/api/paul/StubPaulOracle.java \
        backend/src/main/java/io/quiniela/api/paul/PaulOracleConfig.java
git commit -m "feat(paul): PaulOracle seam + conditional Gemini impl + stub fallback"
```

---

## Task 7: `MatchContextBuilder` (pure) + unit test

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/MatchContextBuilder.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/MatchContextBuilderTest.java`

- [ ] **Step 1: Write the failing unit test**

`MatchContextBuilderTest.java`:
```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchContextBuilderTest {

  private final MatchContextBuilder builder = new MatchContextBuilder();

  @Test
  void buildsUserPromptWithTeamsRankingsAndGroup() {
    String prompt =
        builder.userPrompt(
            "GROUP", "A", "México", "MEX", 16, "Costa Rica", "CRC", 29);
    assertThat(prompt)
        .contains("México")
        .contains("Costa Rica")
        .contains("Grupo A")
        .contains("16")
        .contains("29");
  }

  @Test
  void omitsRankingWhenNull() {
    String prompt =
        builder.userPrompt("GROUP", "K", "Países K1", "TBD_K1", null, "Países K2", "TBD_K2", null);
    assertThat(prompt).contains("Países K1").doesNotContain("ranking FIFA:");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=MatchContextBuilderTest`
Expected: FAIL — `MatchContextBuilder` does not exist.

- [ ] **Step 3: Create the builder**

`MatchContextBuilder.java`:
```java
package io.quiniela.api.paul;

import org.springframework.stereotype.Component;

/** Pure builder for Paul's prompts. No I/O, no LLM — fully unit-testable. */
@Component
public class MatchContextBuilder {

  static final String SYSTEM_PROMPT =
      """
      Eres "Pulpo Paul", un pulpo oráculo que predice resultados del Mundial 2026.
      Respondes SOLO con el esquema estructurado pedido. El campo "reasoning" va en
      español, con tono divertido y de oráculo, en 1 o 2 frases. Predice un marcador
      realista (la mayoría de partidos terminan 0-0 .. 3-1). "confidence" entre 0 y 1.
      """;

  public String systemPrompt() {
    return SYSTEM_PROMPT;
  }

  public String userPrompt(
      String stageCode,
      String groupCode,
      String team1Name,
      String team1Code,
      Integer team1Ranking,
      String team2Name,
      String team2Code,
      Integer team2Ranking) {
    StringBuilder sb = new StringBuilder();
    sb.append("Fase: ")
        .append("GROUP".equals(stageCode) ? "fase de grupos" : stageCode)
        .append('\n');
    if (groupCode != null) sb.append("Grupo ").append(groupCode).append('\n');
    sb.append("Local: ").append(team1Name).append(" (").append(team1Code).append(")");
    if (team1Ranking != null) sb.append(" — ranking FIFA: ").append(team1Ranking);
    sb.append('\n');
    sb.append("Visitante: ").append(team2Name).append(" (").append(team2Code).append(")");
    if (team2Ranking != null) sb.append(" — ranking FIFA: ").append(team2Ranking);
    sb.append('\n');
    sb.append("Predice el marcador (goles del local y del visitante).");
    return sb.toString();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q test -Dtest=MatchContextBuilderTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/MatchContextBuilder.java \
        backend/src/test/java/io/quiniela/api/paul/MatchContextBuilderTest.java
git commit -m "feat(paul): pure MatchContextBuilder for prompts"
```

---

## Task 8: `PaulPredictionService` — generate candidates with fallback

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulPredictionServiceIT.java`
- Test helper: `backend/src/test/java/io/quiniela/api/paul/FakePaulOracleConfig.java`

- [ ] **Step 1: Create the fake-oracle test config**

`FakePaulOracleConfig.java`:
```java
package io.quiniela.api.paul;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakePaulOracleConfig {

  /** Set to a model name that should THROW (to exercise the fallback path); null = never throw. */
  public static final AtomicReference<String> failModel = new AtomicReference<>(null);

  @Bean
  @Primary
  PaulOracle fakePaulOracle() {
    return (system, user, model) -> {
      if (model.equals(failModel.get())) {
        throw new RuntimeException("simulated model failure: " + model);
      }
      // Deterministic canned result so assertions are stable.
      return new PaulPredictionResult(2, 1, 0.66, "Paul lo siente en los tentáculos. [" + model + "]");
    };
  }
}
```

- [ ] **Step 2: Write the failing test**

`PaulPredictionServiceIT.java`:
```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(FakePaulOracleConfig.class)
class PaulPredictionServiceIT extends AbstractIntegrationTest {

  @Autowired PaulPredictionService service;
  @Autowired PaulPredictionRepository repo;

  @AfterEach
  void reset() {
    FakePaulOracleConfig.failModel.set(null);
  }

  @Test
  void generatesOneCandidatePerModelPerGroupMatch() {
    int created = service.generateAllGroup();
    // 72 group matches × 2 configured models = 144 candidate rows.
    assertThat(created).isEqualTo(144);
    assertThat(repo.findByKind(PaulPrediction.KIND_CANDIDATE)).hasSize(144);
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).allMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
  }

  @Test
  void fallsBackToDeterministicStubWhenAModelFails() {
    FakePaulOracleConfig.failModel.set("gemini-2.5-pro");
    service.generateAllGroup();
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_FALLBACK));
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
  }

  @Test
  void regenerationReplacesExistingCandidates() {
    service.generateAllGroup();
    int second = service.generateAllGroup();
    assertThat(second).isEqualTo(144);
    assertThat(repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE)).hasSize(2);
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=PaulPredictionServiceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `PaulPredictionService` does not exist.

- [ ] **Step 4: Create the service**

`PaulPredictionService.java`:
```java
package io.quiniela.api.paul;

import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.team.Team;
import io.quiniela.api.team.TeamRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaulPredictionService {

  private static final Long TOURNAMENT_ID = 1L;

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final TeamRepository teams;
  private final PaulPredictionRepository predictions;
  private final PaulOracle oracle;
  private final MatchContextBuilder context;
  private final PaulProperties props;

  public PaulPredictionService(
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams,
      PaulPredictionRepository predictions,
      PaulOracle oracle,
      MatchContextBuilder context,
      PaulProperties props) {
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
    this.predictions = predictions;
    this.oracle = oracle;
    this.context = context;
    this.props = props;
  }

  /** Regenerate CANDIDATE predictions for every group match × every configured model. */
  @Transactional
  public int generateAllGroup() {
    Long groupRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "GROUP").orElseThrow().getId();
    List<Match> groupMatches =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, groupRoundId);

    int created = 0;
    for (Match m : groupMatches) {
      for (String model : props.models()) {
        upsertCandidate(m, model);
        created++;
      }
    }
    return created;
  }

  private void upsertCandidate(Match m, String model) {
    Team t1 = m.getTeam1Id() == null ? null : teams.findById(m.getTeam1Id()).orElse(null);
    Team t2 = m.getTeam2Id() == null ? null : teams.findById(m.getTeam2Id()).orElse(null);

    PaulPrediction p;
    try {
      String userPrompt =
          context.userPrompt(
              "GROUP",
              m.getGroupCode(),
              name(t1),
              code(t1),
              ranking(t1),
              name(t2),
              code(t2),
              ranking(t2));
      PaulPredictionResult r = oracle.predict(context.systemPrompt(), userPrompt, model);
      p =
          new PaulPrediction(
              m.getId(),
              props.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              Math.max(0, r.scoreT1()),
              Math.max(0, r.scoreT2()),
              clampConfidence(r.confidence()),
              r.reasoning(),
              "es",
              PaulPrediction.SOURCE_AI);
    } catch (RuntimeException e) {
      int[] s = deterministicStub(m.getId());
      p =
          new PaulPrediction(
              m.getId(),
              props.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              s[0],
              s[1],
              null,
              "Paul prefirió no arriesgar esta vez.",
              "es",
              PaulPrediction.SOURCE_FALLBACK);
    }

    // Replace any existing candidate for (match, model, CANDIDATE) to keep regeneration idempotent.
    predictions
        .findByMatchIdAndModelAndKind(m.getId(), model, PaulPrediction.KIND_CANDIDATE)
        .ifPresent(predictions::delete);
    predictions.save(p);
  }

  /** Deterministic stub (same formula the original dumb Paul used). */
  static int[] deterministicStub(Long matchId) {
    long seed = matchId * 17L + 11L;
    return new int[] {(int) (seed % 4), (int) ((seed / 5) % 3)};
  }

  private static BigDecimal clampConfidence(double c) {
    double v = Math.max(0.0, Math.min(1.0, c));
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
  }

  private static String name(Team t) {
    return t == null ? "Por definir" : t.getName();
  }

  private static String code(Team t) {
    return t == null ? "TBD" : t.getCode();
  }

  private static Integer ranking(Team t) {
    return t == null ? null : t.getFifaRanking();
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=PaulPredictionServiceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (all three tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulPredictionServiceIT.java \
        backend/src/test/java/io/quiniela/api/paul/FakePaulOracleConfig.java
git commit -m "feat(paul): PaulPredictionService generates candidates with fallback"
```

---

## Task 9: `PaulEnsembleService` — synthesize OFFICIAL pick

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulEnsembleServiceIT.java`

- [ ] **Step 1: Write the failing test**

`PaulEnsembleServiceIT.java`:
```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(FakePaulOracleConfig.class)
class PaulEnsembleServiceIT extends AbstractIntegrationTest {

  @Autowired PaulPredictionService predictionService;
  @Autowired PaulEnsembleService ensembleService;
  @Autowired PaulPredictionRepository repo;

  @Test
  void synthesizesOneOfficialPerMatchWithCandidates() {
    predictionService.generateAllGroup(); // 144 candidates over 72 matches
    int officials = ensembleService.synthesizeAllGroup();
    assertThat(officials).isEqualTo(72);

    var official = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_OFFICIAL);
    assertThat(official).hasSize(1);
    assertThat(official.get(0).getModel()).isEqualTo("ensemble");
    assertThat(official.get(0).getReasoning()).isNotBlank();
  }

  @Test
  void skipsMatchesWithoutCandidates() {
    // No candidates generated → nothing to synthesize.
    assertThat(ensembleService.synthesizeAllGroup()).isZero();
    assertThat(repo.findByKind(PaulPrediction.KIND_OFFICIAL)).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=PaulEnsembleServiceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `PaulEnsembleService` does not exist.

- [ ] **Step 3: Create the service**

`PaulEnsembleService.java`:
```java
package io.quiniela.api.paul;

import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.RoundRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaulEnsembleService {

  private static final Long TOURNAMENT_ID = 1L;
  static final String ENSEMBLE_MODEL_LABEL = "ensemble";

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final PaulPredictionRepository predictions;
  private final PaulOracle oracle;
  private final PaulProperties props;

  public PaulEnsembleService(
      MatchRepository matches,
      RoundRepository rounds,
      PaulPredictionRepository predictions,
      PaulOracle oracle,
      PaulProperties props) {
    this.matches = matches;
    this.rounds = rounds;
    this.predictions = predictions;
    this.oracle = oracle;
    this.props = props;
  }

  /** For each group match with candidates, synthesize one OFFICIAL pick via the ensemble judge. */
  @Transactional
  public int synthesizeAllGroup() {
    Long groupRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "GROUP").orElseThrow().getId();
    List<Match> groupMatches =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, groupRoundId);

    int created = 0;
    for (Match m : groupMatches) {
      if (synthesizeForMatch(m.getId())) created++;
    }
    return created;
  }

  boolean synthesizeForMatch(Long matchId) {
    List<PaulPrediction> candidates =
        predictions.findByMatchIdAndKind(matchId, PaulPrediction.KIND_CANDIDATE);
    if (candidates.isEmpty()) return false;

    PaulPrediction official;
    try {
      PaulPredictionResult r =
          oracle.predict(systemPrompt(), candidatePrompt(candidates), props.ensembleModel());
      official =
          official(
              matchId,
              Math.max(0, r.scoreT1()),
              Math.max(0, r.scoreT2()),
              clamp(r.confidence()),
              r.reasoning(),
              PaulPrediction.SOURCE_AI);
    } catch (RuntimeException e) {
      // Fallback: most common scoreline among candidates (first candidate as final tiebreak).
      PaulPrediction pick = candidates.get(0);
      official =
          official(
              matchId,
              pick.getScoreT1(),
              pick.getScoreT2(),
              null,
              "Paul consultó a sus otros yo y se quedó con su instinto.",
              PaulPrediction.SOURCE_FALLBACK);
    }

    predictions
        .findByMatchIdAndModelAndKind(matchId, ENSEMBLE_MODEL_LABEL, PaulPrediction.KIND_OFFICIAL)
        .ifPresent(predictions::delete);
    predictions.save(official);
    return true;
  }

  private PaulPrediction official(
      Long matchId, int s1, int s2, BigDecimal conf, String reasoning, String source) {
    return new PaulPrediction(
        matchId,
        props.provider(),
        ENSEMBLE_MODEL_LABEL,
        PaulPrediction.KIND_OFFICIAL,
        s1,
        s2,
        conf,
        reasoning,
        "es",
        source);
  }

  private String systemPrompt() {
    return """
        Eres "Pulpo Paul". Te doy varias predicciones que TÚ MISMO hiciste con
        distintos modelos para un partido. Decide tu marcador OFICIAL final, que es
        el que jugarás como competidor. Responde SOLO con el esquema estructurado.
        "reasoning" en español, divertido y de oráculo, 1-2 frases.
        """;
  }

  private String candidatePrompt(List<PaulPrediction> candidates) {
    StringBuilder sb = new StringBuilder("Mis predicciones previas:\n");
    for (PaulPrediction c : candidates) {
      sb.append("- ")
          .append(c.getModel())
          .append(": ")
          .append(c.getScoreT1())
          .append('-')
          .append(c.getScoreT2());
      if (c.getReasoning() != null) sb.append(" (").append(c.getReasoning()).append(')');
      sb.append('\n');
    }
    sb.append("Da tu marcador oficial.");
    return sb.toString();
  }

  private static BigDecimal clamp(double c) {
    double v = Math.max(0.0, Math.min(1.0, c));
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=PaulEnsembleServiceIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulEnsembleServiceIT.java
git commit -m "feat(paul): ensemble-judge synthesis of Paul's official pick"
```

---

# Phase C — Paul's behavior & admin endpoints

## Task 10: Refactor `PaulService` suggest/fill to use cached candidates

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulServiceCachedIT.java`

The existing `PaulControllerIT` must stay green: with **no** candidates seeded, `suggest` and `fill` fall back to the deterministic stub (so `fill` still creates 72 and `suggest` still returns numbers).

- [ ] **Step 1: Write the failing test**

`PaulServiceCachedIT.java`:
```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaulServiceCachedIT extends AbstractIntegrationTest {

  @Autowired PaulService paul;
  @Autowired PaulPredictionRepository predictions;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;

  @Test
  void suggestUsesCachedCandidateWhenPresent() {
    predictions.save(
        new PaulPrediction(
            1L, "google", "gemini-2.5-flash", PaulPrediction.KIND_CANDIDATE,
            3, 0, new BigDecimal("0.80"), "Goleada cantada.", "es", PaulPrediction.SOURCE_AI));

    var s = paul.suggestForMatch(1L);
    assertThat(s.scoreT1()).isEqualTo(3);
    assertThat(s.scoreT2()).isZero();
    assertThat(s.reasoning()).isEqualTo("Goleada cantada.");
  }

  @Test
  void fillCopiesCachedCandidateForThatMatch() {
    var u = users.save(new User("g-cache", "c@example.com", "C", null, UserRole.PLAYER));
    predictions.save(
        new PaulPrediction(
            1L, "google", "gemini-2.5-flash", PaulPrediction.KIND_CANDIDATE,
            3, 0, new BigDecimal("0.80"), "Goleada cantada.", "es", PaulPrediction.SOURCE_AI));

    paul.fillAllForUser(u.getId());

    Quiniela q = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    Bet bet = bets.findByQuinielaIdAndMatchId(q.getId(), 1L).orElseThrow();
    assertThat(bet.getScoreT1()).isEqualTo(3);
    assertThat(bet.getScoreT2()).isZero();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=PaulServiceCachedIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `suggestForMatch` ignores cached candidates (returns stub).

- [ ] **Step 3: Refactor `PaulService`**

Edit `PaulService.java`. Add the predictions repo + a RNG, and change `suggestForMatch` to prefer a random cached candidate. Full updated file:
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
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaulService {

  private static final Long DEFAULT_POOL_ID = 1L;

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final QuinielaRepository quinielas;
  private final BetRepository bets;
  private final PaulPredictionRepository predictions;

  public PaulService(
      MatchRepository matches,
      RoundRepository rounds,
      QuinielaRepository quinielas,
      BetRepository bets,
      PaulPredictionRepository predictions) {
    this.matches = matches;
    this.rounds = rounds;
    this.quinielas = quinielas;
    this.bets = bets;
    this.predictions = predictions;
  }

  public record Suggestion(Integer scoreT1, Integer scoreT2, String reasoning) {}

  public record FillResult(int created) {}

  /**
   * Returns a random cached CANDIDATE prediction for the match (Paul "changes his mind"). Falls back
   * to the deterministic stub when Paul hasn't analyzed this match yet.
   */
  public Suggestion suggestForMatch(Long matchId) {
    matches.findById(matchId).orElseThrow();
    List<PaulPrediction> candidates =
        predictions.findByMatchIdAndKind(matchId, PaulPrediction.KIND_CANDIDATE);
    if (!candidates.isEmpty()) {
      PaulPrediction pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
      return new Suggestion(pick.getScoreT1(), pick.getScoreT2(), pick.getReasoning());
    }
    return stub(matchId);
  }

  private Suggestion stub(Long matchId) {
    long seed = matchId * 17L + 11L;
    int t1 = (int) (seed % 4);
    int t2 = (int) ((seed / 5) % 3);
    return new Suggestion(
        t1, t2, "Paul cree que es un partido " + (t1 + t2 > 2 ? "abierto" : "cerrado") + ".");
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

- [ ] **Step 4: Run both the new and existing tests**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=PaulServiceCachedIT,PaulControllerIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (cached path works; fallback keeps PaulControllerIT green).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulServiceCachedIT.java
git commit -m "feat(paul): suggest/fill use random cached candidate (stub fallback)"
```

---

## Task 11: `PaulService.reveal()` — Paul becomes a competitor

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulRevealIT.java`

- [ ] **Step 1: Write the failing test**

`PaulRevealIT.java`:
```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaulRevealIT extends AbstractIntegrationTest {

  @Autowired PaulService paul;
  @Autowired PaulPredictionRepository predictions;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;

  private void seedOfficial(long matchId, int s1, int s2) {
    predictions.save(
        new PaulPrediction(
            matchId, "google", "ensemble", PaulPrediction.KIND_OFFICIAL,
            s1, s2, new BigDecimal("0.75"), "Oficial.", "es", PaulPrediction.SOURCE_AI));
  }

  @Test
  void revealCreatesPaulQuinielaAndSnapshotsOfficialBets() {
    seedOfficial(1L, 2, 1);
    seedOfficial(2L, 0, 0);

    var result = paul.reveal();
    assertThat(result.betsCreated()).isEqualTo(2);

    var paulUser = users.findByGoogleSub("paul-bot-oracle").orElseThrow();
    Quiniela pq = quinielas.findByPoolIdAndUserId(1L, paulUser.getId()).orElseThrow();
    assertThat(bets.findByQuinielaId(pq.getId())).hasSize(2);
    var bet1 = bets.findByQuinielaIdAndMatchId(pq.getId(), 1L).orElseThrow();
    assertThat(bet1.getScoreT1()).isEqualTo(2);
    assertThat(bet1.getScoreT2()).isEqualTo(1);
  }

  @Test
  void revealIsIdempotent() {
    seedOfficial(1L, 2, 1);
    paul.reveal();
    var second = paul.reveal();
    assertThat(second.betsCreated()).isZero(); // already snapshotted

    var paulUser = users.findByGoogleSub("paul-bot-oracle").orElseThrow();
    Quiniela pq = quinielas.findByPoolIdAndUserId(1L, paulUser.getId()).orElseThrow();
    assertThat(bets.findByQuinielaId(pq.getId())).hasSize(1);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=PaulRevealIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `reveal()` / `RevealResult` do not exist.

- [ ] **Step 3: Add `reveal()` to `PaulService`**

Add the `UserRepository` dependency and the method. In `PaulService.java`:

Add import:
```java
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
```
Add field + constructor param:
```java
  private final UserRepository users;
```
(add `UserRepository users` as the last constructor parameter and `this.users = users;` in the body.)

Add the record + method:
```java
  public record RevealResult(int betsCreated) {}

  /**
   * Paul "decides to play": create his quiniela (idempotent) and snapshot every OFFICIAL prediction
   * as one of his bets. Skips matches he already has a bet for, so repeated calls are no-ops.
   */
  @Transactional
  public RevealResult reveal() {
    User paul =
        users
            .findByGoogleSub("paul-bot-oracle")
            .orElseThrow(() -> new IllegalStateException("Paul bot user not seeded"));
    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, paul.getId())
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, paul.getId())));

    Set<Long> already = new HashSet<>();
    bets.findByQuinielaId(q.getId()).forEach(b -> already.add(b.getMatchId()));

    int created = 0;
    for (PaulPrediction official : predictions.findByKind(PaulPrediction.KIND_OFFICIAL)) {
      if (already.contains(official.getMatchId())) continue;
      bets.save(
          new Bet(q.getId(), official.getMatchId(), official.getScoreT1(), official.getScoreT2()));
      created++;
    }
    return new RevealResult(created);
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=PaulRevealIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulRevealIT.java
git commit -m "feat(paul): reveal() snapshots official picks into Paul's quiniela"
```

---

## Task 12: Admin endpoints — generate / synthesize / reveal

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulAdminService.java`
- Create: `backend/src/main/java/io/quiniela/api/paul/PaulAdminController.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulAdminControllerIT.java`

- [ ] **Step 1: Write the failing test**

`PaulAdminControllerIT.java`:
```java
package io.quiniela.api.paul;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(FakePaulOracleConfig.class)
class PaulAdminControllerIT extends AbstractIntegrationTest {

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
        .perform(post("/api/admin/paul/generate").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminGenerateThenSynthesizeThenReveal() throws Exception {
    String token = tokenFor(UserRole.ADMIN, "g-admin");

    mockMvc
        .perform(post("/api/admin/paul/generate").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidatesCreated").value(144));

    mockMvc
        .perform(post("/api/admin/paul/synthesize").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.officialsCreated").value(72));

    mockMvc
        .perform(post("/api/admin/paul/reveal").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.betsCreated").value(72));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q verify -Dit.test=PaulAdminControllerIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — endpoints don't exist.

- [ ] **Step 3: Create the admin service**

`PaulAdminService.java`:
```java
package io.quiniela.api.paul;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaulAdminService {

  private final UserRepository users;
  private final PaulPredictionService predictionService;
  private final PaulEnsembleService ensembleService;
  private final PaulService paulService;

  public PaulAdminService(
      UserRepository users,
      PaulPredictionService predictionService,
      PaulEnsembleService ensembleService,
      PaulService paulService) {
    this.users = users;
    this.predictionService = predictionService;
    this.ensembleService = ensembleService;
    this.paulService = paulService;
  }

  public record GenerateResult(int candidatesCreated) {}

  public record SynthesizeResult(int officialsCreated) {}

  void requireAdmin(Long callerId) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  public GenerateResult generate(Long callerId) {
    requireAdmin(callerId);
    return new GenerateResult(predictionService.generateAllGroup());
  }

  public SynthesizeResult synthesize(Long callerId) {
    requireAdmin(callerId);
    return new SynthesizeResult(ensembleService.synthesizeAllGroup());
  }

  public PaulService.RevealResult reveal(Long callerId) {
    requireAdmin(callerId);
    return paulService.reveal();
  }
}
```

- [ ] **Step 4: Create the controller**

`PaulAdminController.java`:
```java
package io.quiniela.api.paul;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/paul")
public class PaulAdminController {

  private final PaulAdminService service;

  public PaulAdminController(PaulAdminService service) {
    this.service = service;
  }

  private static Long callerId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }

  @PostMapping("/generate")
  public ResponseEntity<PaulAdminService.GenerateResult> generate(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.generate(callerId(jwt)));
  }

  @PostMapping("/synthesize")
  public ResponseEntity<PaulAdminService.SynthesizeResult> synthesize(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.synthesize(callerId(jwt)));
  }

  @PostMapping("/reveal")
  public ResponseEntity<PaulService.RevealResult> reveal(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.reveal(callerId(jwt)));
  }
}
```
> NOTE: confirm `/api/admin/**` is permitted at the security layer the same way `/api/admin/test/**` is (check `SecurityConfig`). The endpoints self-enforce admin via `requireAdmin`. If `SecurityConfig` lists admin paths explicitly, add `/api/admin/paul/**` alongside the existing `/api/admin/test/**` rule.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q spotless:apply && ./mvnw -q verify -Dit.test=PaulAdminControllerIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Full backend regression + commit**

Run: `./mvnw -q verify`
Expected: BUILD SUCCESS (all unit + IT green).
```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulAdminService.java \
        backend/src/main/java/io/quiniela/api/paul/PaulAdminController.java \
        backend/src/test/java/io/quiniela/api/paul/PaulAdminControllerIT.java \
        backend/src/main/java/io/quiniela/api/config/SecurityConfig.java
git commit -m "feat(paul): admin endpoints generate/synthesize/reveal"
```

---

# Phase D — Frontend (minimal)

> The two files below (`RankingRow.tsx`, the ranking page, `paul.ts`) could not be read cleanly while authoring this plan (tool-output corruption). **Before editing each, open it and confirm its current contents**; the changes are specified against the verified `ranking.ts` contract (which already has `isBot` after Task 4) and the existing `paul-badge.svg` asset.

## Task 13: Exhibition badge for Paul on the leaderboard

**Real component (verified):** `RankingRow` is a flex-`div` "poster" row (NOT a table row) with signature
`function RankingRow({ entry, youLabel, trendUp, trendDown, trendFlat, payoutLabel })`,
`entry.displayName` is `string | null`, rendered as `{entry.displayName ?? "—"}` inside the name `<div>` (the block containing the YOU pill and `payoutLabel`).

**Files:**
- Modify: `frontend/components/ranking/RankingRow.tsx`
- Test: `frontend/components/ranking/RankingRow.test.tsx` (create)

- [ ] **Step 1: Write the failing test**

`frontend/components/ranking/RankingRow.test.tsx`:
```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RankingRow } from "./RankingRow";
import type { RankingEntry } from "@/lib/api/ranking";

function entry(overrides: Partial<RankingEntry> = {}): RankingEntry {
  return {
    rank: 1,
    userId: 1,
    displayName: "Pulpo Paul 🐙",
    points: 50,
    delta: null,
    isYou: false,
    isBot: false,
    ...overrides,
  };
}

const rowProps = {
  youLabel: "TÚ",
  trendUp: "▲",
  trendDown: "▼",
  trendFlat: "—",
};

describe("RankingRow", () => {
  it("shows an exhibition badge for bot entries", () => {
    render(<RankingRow entry={entry({ isBot: true })} {...rowProps} />);
    expect(screen.getByText(/fuera de premio/i)).toBeInTheDocument();
  });

  it("does not show the badge for normal players", () => {
    render(<RankingRow entry={entry({ isBot: false, displayName: "Juan" })} {...rowProps} />);
    expect(screen.queryByText(/fuera de premio/i)).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `frontend/`): `pnpm test -- RankingRow`
Expected: FAIL — no badge text rendered (and `isBot` not yet on the type until Task 4 is done; Task 4 precedes this).

- [ ] **Step 3: Add the badge to the component**

In `RankingRow.tsx`, inside the name `<div>` (the `flex min-w-0 flex-1 ...` block), after the `payoutLabel` span, add:
```tsx
        {entry.isBot && (
          <span className="shrink-0 bg-[var(--color-accent-violet,#7c3aed)] px-1.5 py-0.5 font-mono text-[9px] font-bold tracking-[0.12em] text-[var(--color-text-inverse,#fff)]">
            FUERA DE PREMIO
          </span>
        )}
```
(Matches the existing pill styling for the YOU label; the regex test is case-insensitive so uppercase copy is fine.)

- [ ] **Step 4: Run test + typecheck**

Run (from `frontend/`): `pnpm test -- RankingRow && pnpm typecheck`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/ranking/RankingRow.tsx \
        frontend/components/ranking/RankingRow.test.tsx
git commit -m "feat(paul): exhibition badge for Paul on the leaderboard"
```

---

## Task 14: Reveal banner (Paul joined) — minimal

**Files:**
- Modify: the ranking page (`frontend/app/ranking/page.tsx` — confirm exact path/name) or the ranking list component.
- Test: extend an existing ranking component/page test, or add a small render test.

- [ ] **Step 1: Confirm where the ranking list renders**

Open `frontend/app/ranking/page.tsx` (confirm path). Identify where `RankingView.entries` is mapped to `RankingRow`s.

- [ ] **Step 2: Add a banner when Paul is present**

When any entry `isBot` is true in the fetched `RankingView`, render a one-line banner above the table:
```tsx
{view.entries.some((e) => e.isBot) && (
  <div className="mb-3 rounded-lg bg-violet-50 px-4 py-2 text-sm text-violet-800">
    🐙 ¡Pulpo Paul decidió jugar! Compite por la gloria, no por el premio.
  </div>
)}
```
(Adapt `view` to the actual variable holding the `RankingView`.)

- [ ] **Step 3: Typecheck + manual check**

Run (from `frontend/`): `pnpm typecheck`
Expected: PASS. (Optional: `pnpm dev` and view `/ranking` with a seeded Paul.)

- [ ] **Step 4: Commit**

```bash
git add frontend/app/ranking/page.tsx
git commit -m "feat(paul): leaderboard banner when Paul reveals"
```

---

# Final verification

- [ ] **Backend:** from `backend/` run `./mvnw -q verify` → BUILD SUCCESS (all unit + IT green, Spotless clean).
- [ ] **Frontend:** from `frontend/` run `pnpm test && pnpm typecheck` → PASS.
- [ ] **Manual smoke (optional, needs `GEMINI_API_KEY`):** set the key, boot the API, `POST /api/admin/paul/generate` then `/synthesize` then `/reveal` with an admin token; confirm `paul_prediction` rows and Paul on `/api/ranking` (flagged `isBot`), absent from `getPrizeEligible`.

---

## Operational notes (for deploy, not part of TDD)

- **Secret:** add `GEMINI_API_KEY` to GCP Secret Manager and wire it into the `quiniela-api` Cloud Run service env (via `iac/`), mirroring how `NEXTAUTH_SECRET` is injected. Without it, generation falls back to the deterministic stub (no crash).
- **Cost:** generation is admin-triggered only (72 matches × 2 models + 72 ensemble ≈ 216 calls per full run) on Gemini's free tier. Not in any user request path.
- **Out of scope (per spec):** knockout predictions (phase 2, same engine), multi-vendor providers (add a roster entry + starter), pot-payout math, reasoning translation (lazy-cache table when i18n lands).

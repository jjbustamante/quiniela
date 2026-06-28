# Paul Knockout Predictions (v1.1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Paul generate, suggest, fill, and reveal predictions for knockout matches (R32 now, later rounds as they resolve), including the advancing team on a predicted regulation draw.

**Architecture:** Generalize Paul's group-only pipeline to an *open-match* rule — predict every match whose both teams are assigned and whose kickoff is still in the future. One code path serves the group stage (historically) and each knockout round progressively. Add an `advancing` field to the LLM result, a `predicted_winner_id` column on `paul_prediction`, and thread the advancing team through generation → ensemble → suggest/fill/reveal. The knockout *storage and scoring* side (bet.predicted_winner_id, match.advanced_team_id, the scoring trigger) already exists and is not touched.

**Tech Stack:** Java 25 records, Spring Boot 4, Spring Data JPA, Flyway (plain SQL), JUnit 5 + AssertJ, Testcontainers (via `AbstractIntegrationTest`), Maven (`./mvnw`). Frontend: Next.js 16 + TypeScript + Vitest.

## Global Constraints

- Backend module root: `backend/`. Run all backend commands from there (`cd backend`).
- Package: `io.quiniela.api.paul` (domain types under `io.quiniela.api.{match,team,bet,bracket}`).
- Tournament id is `1L`; default pool id is `1L`.
- **Flyway, plain SQL** migrations under `backend/src/main/resources/db/migration/`. Next free version is **V023** (V022 is the highest applied; V021 is test-only).
- **Maven version externalization:** never hardcode plugin/dependency versions inline — already satisfied; this plan adds no dependencies.
- **UI copy stays Spanish.**
- **Spring Boot 4 idioms only.**
- Format with Spotless / Google Java Format (compile-phase enforced) — 2-space indent. If a build fails on formatting, run `./mvnw spotless:apply`.
- Test commands: `./mvnw -q -Dtest=<Class>[#<method>] test`. Unit tests are fast; IT classes extend `AbstractIntegrationTest` (spins a Postgres 16 container; group matches and both deadlines are reanchored ~10 days into the future, so all group matches are "open"; knockout seed matches have NULL teams).
- **Advancing convention:** the LLM returns `advancing` ∈ `"LOCAL"` | `"VISITANTE"` | `null`. `LOCAL → team1Id`, `VISITANTE → team2Id`. A `predicted_winner_id` is stored **only** when the match is knockout **and** the predicted score is a draw (`scoreT1 == scoreT2`); otherwise it is `null` (mirrors `BracketService.saveBet`).
- **Open-match rule:** a match is predictable iff `team1Id != null && team2Id != null && now < kickoffAt`.

---

### Task 1: Add `predicted_winner_id` to `paul_prediction` (migration + entity)

**Files:**
- Create: `backend/src/main/resources/db/migration/V023__paul_prediction_predicted_winner.sql`
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulPrediction.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulPredictionRepositoryIT.java`

**Interfaces:**
- Produces: `PaulPrediction` constructor gains a trailing `Long predictedWinnerId` param (now 11 args); accessor `getPredictedWinnerId()`.

- [ ] **Step 1: Write the migration**

Create `V023__paul_prediction_predicted_winner.sql`:

```sql
-- V023: Paul's predicted advancing team for knockout draws.
--
-- Mirrors bet.predicted_winner_id (V016). NULL for group predictions and for
-- decisive knockout scores; set only when Paul predicts a regulation draw and
-- names which team advances on penalties.
ALTER TABLE paul_prediction ADD COLUMN predicted_winner_id BIGINT REFERENCES team(id);
```

- [ ] **Step 2: Add the failing repository test**

In `PaulPredictionRepositoryIT.java`, add a test that saves a prediction with a winner and reads it back. Use a real seeded team id (`1` = MEX) and a seeded match id (`1`):

```java
  @Test
  void persistsAndReadsPredictedWinnerId() {
    PaulPrediction p =
        new PaulPrediction(
            1L, "vertex", "gemini-2.5-pro", PaulPrediction.KIND_OFFICIAL,
            1, 1, null, "empate, avanza local", "es", PaulPrediction.SOURCE_AI, 1L);
    repo.saveAndFlush(p);
    PaulPrediction read = repo.findById(p.getId()).orElseThrow();
    assertThat(read.getPredictedWinnerId()).isEqualTo(1L);
  }
```

(If `PaulPredictionRepositoryIT` does not already autowire the repo as `repo`, match its existing field name; check the top of the file.)

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionRepositoryIT#persistsAndReadsPredictedWinnerId test`
Expected: COMPILE FAILURE — `PaulPrediction(...)` does not take 11 args / `getPredictedWinnerId()` not found.

- [ ] **Step 4: Add the field, constructor param, and getter**

In `PaulPrediction.java`, add the column field after `source`/`generatedAt` region (place the field declaration near the other `@Column`s, e.g. after the `source` field):

```java
  @Column(name = "predicted_winner_id")
  private Long predictedWinnerId;
```

Change the constructor signature to append the param and assign it:

```java
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
      String source,
      Long predictedWinnerId) {
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
    this.predictedWinnerId = predictedWinnerId;
  }
```

Add the getter near the others:

```java
  public Long getPredictedWinnerId() {
    return predictedWinnerId;
  }
```

> NOTE: this breaks the three existing `new PaulPrediction(...)` call sites (two in `PaulPredictionService`, one in `PaulEnsembleService`). They are updated in Tasks 6 and 7. To keep the build green *now*, do Step 5's targeted test only after Tasks 6/7, OR temporarily pass `null` as the 11th arg at those three call sites in this task. **Recommended:** pass `null` at all three call sites now (search `new PaulPrediction(`), so the module compiles; Tasks 6/7 replace `null` with the real value.

- [ ] **Step 5: Patch the three existing call sites to pass `null` (temporary)**

In `PaulPredictionService.upsertCandidate` (both the AI branch and the fallback branch) and in `PaulEnsembleService.official(...)`, append `, null` as the final constructor argument. (Tasks 6/7 replace these.)

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionRepositoryIT test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V023__paul_prediction_predicted_winner.sql \
        backend/src/main/java/io/quiniela/api/paul/PaulPrediction.java \
        backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java \
        backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulPredictionRepositoryIT.java
git commit -m "feat(paul): add predicted_winner_id to paul_prediction"
```

---

### Task 2: Add `advancing` to `PaulPredictionResult` + extend the fake oracle

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionResult.java`
- Modify: `backend/src/test/java/io/quiniela/api/paul/FakePaulOracleConfig.java`

**Interfaces:**
- Produces: `PaulPredictionResult(int scoreT1, int scoreT2, double confidence, String reasoning, String advancing)` — `advancing` is `"LOCAL"`/`"VISITANTE"`/`null`.
- Produces (test double): `FakePaulOracleConfig.forcedResult` (`AtomicReference<PaulPredictionResult>`) — when non-null, returned for every call; lets tests drive draws + advancing.

- [ ] **Step 1: Add the record component**

In `PaulPredictionResult.java`:

```java
package io.quiniela.api.paul;

/**
 * Structured output returned by the LLM. Scores are non-negative; confidence in [0,1].
 * {@code advancing} is the team that progresses on a knockout regulation draw:
 * {@code "LOCAL"} (team 1), {@code "VISITANTE"} (team 2), or {@code null} (group / decisive).
 */
public record PaulPredictionResult(
    int scoreT1, int scoreT2, double confidence, String reasoning, String advancing) {}
```

- [ ] **Step 2: Update the fake oracle to the new arity + add the forcedResult hook**

In `FakePaulOracleConfig.java`, replace the class body:

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

  /** When non-null, returned for every prediction (lets a test force a draw + advancing). */
  public static final AtomicReference<PaulPredictionResult> forcedResult =
      new AtomicReference<>(null);

  @Bean
  @Primary
  PaulOracle fakePaulOracle() {
    return (system, user, provider, model) -> {
      if (model.equals(failModel.get())) {
        throw new RuntimeException("simulated model failure: " + model);
      }
      PaulPredictionResult forced = forcedResult.get();
      if (forced != null) return forced;
      return new PaulPredictionResult(
          2, 1, 0.66, "Paul lo siente en los tentáculos. [" + model + "]", null);
    };
  }
}
```

- [ ] **Step 3: Run the existing prediction IT to confirm nothing regressed**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionServiceIT test`
Expected: PASS (the default fake result is now a 5-arg record with `advancing=null`; group predictions are unaffected).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulPredictionResult.java \
        backend/src/test/java/io/quiniela/api/paul/FakePaulOracleConfig.java
git commit -m "feat(paul): add advancing field to PaulPredictionResult + fake oracle hook"
```

---

### Task 3: Make `MatchContextBuilder.userPrompt` round-aware

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/MatchContextBuilder.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/MatchContextBuilderTest.java`

**Interfaces:**
- Consumes: callers pass the round **code** (`"GROUP"`, `"R32"`, `"R16"`, `"QF"`, `"SF"`, `"THIRD_PLACE"`, `"FINAL"`) as the first arg.
- Produces: `userPrompt(String roundCode, String groupCode, String t1Name, String t1Code, Integer t1Ranking, String t2Name, String t2Code, Integer t2Ranking)` — knockout rounds render the stage label and append the advancing instruction; group rounds are unchanged in spirit.

- [ ] **Step 1: Write the failing tests**

Replace the two existing tests' expectations and add knockout cases in `MatchContextBuilderTest.java`:

```java
  @Test
  void buildsUserPromptWithTeamsRankingsAndGroup() {
    String prompt = builder.userPrompt("GROUP", "A", "México", "MEX", 16, "Costa Rica", "CRC", 29);
    assertThat(prompt)
        .contains("México")
        .contains("Costa Rica")
        .contains("Grupo A")
        .contains("16")
        .contains("29")
        .doesNotContain("avanza");
  }

  @Test
  void omitsRankingWhenNull() {
    String prompt =
        builder.userPrompt("GROUP", "K", "Países K1", "TBD_K1", null, "Países K2", "TBD_K2", null);
    assertThat(prompt).contains("Países K1").doesNotContain("ranking FIFA:");
  }

  @Test
  void knockoutPromptNamesStageAndAsksForAdvancingTeam() {
    String prompt =
        builder.userPrompt("R32", null, "Brasil", "BRA", 5, "Corea", "KOR", 23);
    assertThat(prompt)
        .contains("dieciseisavos de final")
        .doesNotContain("Grupo")
        .contains("LOCAL")
        .contains("VISITANTE")
        .contains("advancing");
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q -Dtest=MatchContextBuilderTest test`
Expected: FAIL — `knockoutPromptNamesStageAndAsksForAdvancingTeam` fails (no advancing instruction; stage renders as raw "R32").

- [ ] **Step 3: Implement the round-aware prompt**

In `MatchContextBuilder.java`, replace `userPrompt` and add a label map:

```java
  public String userPrompt(
      String roundCode,
      String groupCode,
      String team1Name,
      String team1Code,
      Integer team1Ranking,
      String team2Name,
      String team2Code,
      Integer team2Ranking) {
    boolean knockout = !"GROUP".equals(roundCode);
    StringBuilder sb = new StringBuilder();
    sb.append("Fase: ").append(stageLabel(roundCode)).append('\n');
    if (!knockout && groupCode != null) sb.append("Grupo ").append(groupCode).append('\n');
    sb.append("Local: ").append(team1Name).append(" (").append(team1Code).append(")");
    if (team1Ranking != null) sb.append(" — ranking FIFA: ").append(team1Ranking);
    sb.append('\n');
    sb.append("Visitante: ").append(team2Name).append(" (").append(team2Code).append(")");
    if (team2Ranking != null) sb.append(" — ranking FIFA: ").append(team2Ranking);
    sb.append('\n');
    sb.append("Predice el marcador (goles del local y del visitante).");
    if (knockout) {
      sb.append(
          "\nEs eliminación directa: alguien debe avanzar. Si predices empate en tiempo"
              + " reglamentario, indica en \"advancing\" qué equipo avanza por penales:"
              + " \"LOCAL\" o \"VISITANTE\".");
    }
    return sb.toString();
  }

  private static String stageLabel(String roundCode) {
    return switch (roundCode) {
      case "GROUP" -> "fase de grupos";
      case "R32" -> "dieciseisavos de final";
      case "R16" -> "octavos de final";
      case "QF" -> "cuartos de final";
      case "SF" -> "semifinal";
      case "THIRD_PLACE" -> "partido por el tercer puesto";
      case "FINAL" -> "final";
      default -> roundCode;
    };
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=MatchContextBuilderTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/MatchContextBuilder.java \
        backend/src/test/java/io/quiniela/api/paul/MatchContextBuilderTest.java
git commit -m "feat(paul): round-aware prompt with knockout advancing instruction"
```

---

### Task 4: Permit the `advancing` key in the raw-JSON oracles

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/VertexPaulOracle.java`
- Modify: `backend/src/main/java/io/quiniela/api/paul/OpenAiCompatVertexOracle.java`

**Interfaces:** none (behavior change to the prompt sent to the model). These two oracles parse raw JSON into `PaulPredictionResult`; the new `advancing` key must be allowed so the model can return it. The Gemini Spring-AI oracle derives its schema from the record automatically (non-primary path; left as-is). There are no unit tests for these oracles in the codebase — they need a live LLM — so coverage is via the FakeOracle-driven service ITs (Tasks 6–10).

- [ ] **Step 1: Update the Vertex oracle JSON key list**

In `VertexPaulOracle.predict`, change the `prompt` suffix to include `advancing`:

```java
    String prompt =
        systemPrompt
            + "\n\n"
            + userPrompt
            + "\n\nResponde SOLO con un objeto JSON con exactamente estas claves: "
            + "{\"scoreT1\": entero >= 0, \"scoreT2\": entero >= 0, "
            + "\"confidence\": número entre 0 y 1, \"reasoning\": texto en español, "
            + "\"advancing\": \"LOCAL\" | \"VISITANTE\" | null (solo eliminación directa)}.";
```

- [ ] **Step 2: Update the OpenAI-compat oracle JSON key list**

In `OpenAiCompatVertexOracle.predict`, change `jsonRules`:

```java
    String jsonRules =
        "\n\nResponde SOLO con un objeto JSON (sin texto extra, sin markdown) con exactamente estas "
            + "claves: {\"scoreT1\": entero >= 0, \"scoreT2\": entero >= 0, "
            + "\"confidence\": número entre 0 y 1, \"reasoning\": texto en español, "
            + "\"advancing\": \"LOCAL\" | \"VISITANTE\" | null (solo eliminación directa)}.";
```

- [ ] **Step 3: Compile to verify both oracles still build**

Run: `cd backend && ./mvnw -q test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/VertexPaulOracle.java \
        backend/src/main/java/io/quiniela/api/paul/OpenAiCompatVertexOracle.java
git commit -m "feat(paul): allow advancing key in raw-JSON oracle prompts"
```

---

### Task 5: Add the open-match query to `MatchRepository`

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/match/MatchRepository.java`
- Test: `backend/src/test/java/io/quiniela/api/match/MatchRepositoryIT.java` (create if absent)

**Interfaces:**
- Produces: `List<Match> findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(Long tournamentId, java.time.Instant cutoff)` — open matches (both teams set, kickoff in the future), kickoff-ascending.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/match/MatchRepositoryIT.java`:

```java
package io.quiniela.api.match;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MatchRepositoryIT extends AbstractIntegrationTest {

  @Autowired MatchRepository matches;

  @Test
  void openMatchesIncludeFutureGroupAndExcludeTeamlessKnockout() {
    var open =
        matches.findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
            1L, Instant.now());
    // 72 group matches are reanchored into the future (V021) and have teams.
    // Knockout seed matches have NULL teams, so they are excluded.
    assertThat(open).hasSize(72);
    assertThat(open).allMatch(m -> m.getTeam1Id() != null && m.getTeam2Id() != null);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=MatchRepositoryIT test`
Expected: COMPILE FAILURE — method not defined.

- [ ] **Step 3: Add the derived query**

In `MatchRepository.java`, add (and ensure `import java.time.Instant;`):

```java
  List<Match>
      findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
          Long tournamentId, java.time.Instant cutoff);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=MatchRepositoryIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/match/MatchRepository.java \
        backend/src/test/java/io/quiniela/api/match/MatchRepositoryIT.java
git commit -m "feat(match): add open-match query (teams set, future kickoff)"
```

---

### Task 6: Generalize candidate generation to open matches (`generateOpen`)

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java`
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulJobService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulPredictionServiceIT.java`

**Interfaces:**
- Consumes: `MatchRepository.findBy…KickoffAtAfter…` (Task 5); `MatchContextBuilder.userPrompt(roundCode, …)` (Task 3); `PaulPredictionResult.advancing()` (Task 2); `PaulPrediction(…, Long predictedWinnerId)` (Task 1).
- Produces: `PaulPredictionService.generateOpen()` and `generateOpen(PaulProgress)`; static helper `advancingTeamId(String advancing, Match m, Team t1, Team t2)`. Replaces `generateAllGroup`.

- [ ] **Step 1: Update/extend the IT tests**

In `PaulPredictionServiceIT.java`: (a) rename existing `generateAllGroup()` calls to `generateOpen()`; (b) add knockout cases. Add imports `import io.quiniela.api.match.MatchRepository;` is not needed — use `jdbc`/SQL via the autowired `JdbcTemplate` exposed by the base class? The base class keeps `jdbcTemplate` private. Instead insert the knockout match through the autowired `MatchRepository` is not possible (no setter ctor). Use a new autowired `org.springframework.jdbc.core.JdbcTemplate jdbc` field in the test and an `@AfterEach` cleanup. Full updated test class:

```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(FakePaulOracleConfig.class)
class PaulPredictionServiceIT extends AbstractIntegrationTest {

  private static final long KO_MATCH = 9001L;

  @Autowired PaulPredictionService service;
  @Autowired PaulPredictionRepository repo;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void reset() {
    FakePaulOracleConfig.failModel.set(null);
    FakePaulOracleConfig.forcedResult.set(null);
    jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", KO_MATCH);
    jdbc.update("DELETE FROM match WHERE id = ?", KO_MATCH);
  }

  private void insertR32Match() {
    // round 2 = R32; teams 1 (MEX) and 2 (CRC); future kickoff so it is "open".
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        KO_MATCH);
  }

  @Test
  void generatesOneCandidatePerModelPerGroupMatch() {
    int created = service.generateOpen();
    // 72 open group matches × 2 configured models = 144 candidate rows.
    assertThat(created).isEqualTo(144);
    assertThat(repo.findByKind(PaulPrediction.KIND_CANDIDATE)).hasSize(144);
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).allMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
    assertThat(forMatch1).allMatch(p -> p.getPredictedWinnerId() == null); // group → null
  }

  @Test
  void fallsBackToDeterministicStubWhenAModelFails() {
    FakePaulOracleConfig.failModel.set("gemini-2.5-pro");
    service.generateOpen();
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_FALLBACK));
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
  }

  @Test
  void regenerationReplacesExistingCandidates() {
    service.generateOpen();
    int second = service.generateOpen();
    assertThat(second).isEqualTo(144);
    assertThat(repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE)).hasSize(2);
  }

  @Test
  void knockoutDrawSetsPredictedWinnerFromAdvancing() {
    insertR32Match();
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(1, 1, 0.5, "empate, avanza local", "LOCAL"));
    service.generateOpen();
    var ko = repo.findByMatchIdAndKind(KO_MATCH, PaulPrediction.KIND_CANDIDATE);
    assertThat(ko).hasSize(2);
    assertThat(ko).allMatch(p -> p.getScoreT1() == 1 && p.getScoreT2() == 1);
    assertThat(ko).allMatch(p -> p.getPredictedWinnerId() != null && p.getPredictedWinnerId() == 1L);
  }

  @Test
  void knockoutDecisiveLeavesPredictedWinnerNull() {
    insertR32Match();
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(2, 1, 0.7, "gana el local", "LOCAL"));
    service.generateOpen();
    var ko = repo.findByMatchIdAndKind(KO_MATCH, PaulPrediction.KIND_CANDIDATE);
    assertThat(ko).allMatch(p -> p.getPredictedWinnerId() == null); // decisive → null
  }

  @Test
  void knockoutDrawWithoutAdvancingFallsBackToTeam1() {
    insertR32Match();
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(0, 0, 0.5, "empate sin pick", null));
    service.generateOpen();
    var ko = repo.findByMatchIdAndKind(KO_MATCH, PaulPrediction.KIND_CANDIDATE);
    // Seeded test teams have NULL fifa_ranking → deterministic fallback = team1 (id 1).
    assertThat(ko).allMatch(p -> p.getPredictedWinnerId() != null && p.getPredictedWinnerId() == 1L);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionServiceIT test`
Expected: COMPILE FAILURE — `generateOpen()` not defined.

- [ ] **Step 3: Implement `generateOpen` + helpers; remove `generateAllGroup`**

In `PaulPredictionService.java`: add imports `import io.quiniela.api.match.Round;`, `import java.time.Instant;`, `import java.util.HashMap;`, `import java.util.Map;`. Replace `generateAllGroup`/`generateAllGroup(progress)` with:

```java
  /** Regenerate CANDIDATE predictions for every OPEN match (teams set, future kickoff) × model. */
  public int generateOpen() {
    return generateOpen(PaulProgress.NOOP);
  }

  /**
   * Per-item-transactional variant: each (match, model) candidate is written in its own short
   * transaction so a multi-minute batch never holds one connection open (and partial progress
   * survives a crash). Reports against {@code progress} for live job tracking. Covers the group
   * stage (pre-kickoff) and each knockout round as its matches resolve.
   */
  public int generateOpen(PaulProgress progress) {
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, Instant.now());
    Map<Long, Round> roundById = new HashMap<>();
    rounds.findByTournamentIdOrderBySequenceAsc(TOURNAMENT_ID).forEach(r -> roundById.put(r.getId(), r));

    List<PaulProperties.ModelSpec> roster = props.roster();
    progress.start(open.size() * roster.size());
    int created = 0;
    for (Match m : open) {
      Round r = roundById.get(m.getRoundId());
      for (PaulProperties.ModelSpec spec : roster) {
        tx.executeWithoutResult(s -> upsertCandidate(m, r, spec));
        created++;
        progress.tick();
      }
    }
    return created;
  }
```

Change `upsertCandidate` to take the `Round` and compute the winner:

```java
  private void upsertCandidate(Match m, Round r, PaulProperties.ModelSpec spec) {
    String model = spec.model();
    Team t1 = m.getTeam1Id() == null ? null : teams.findById(m.getTeam1Id()).orElse(null);
    Team t2 = m.getTeam2Id() == null ? null : teams.findById(m.getTeam2Id()).orElse(null);
    boolean knockout = !"GROUP".equals(r.getCode());

    PaulPrediction p;
    try {
      String userPrompt =
          context.userPrompt(
              r.getCode(),
              m.getGroupCode(),
              name(t1),
              code(t1),
              ranking(t1),
              name(t2),
              code(t2),
              ranking(t2));
      PaulPredictionResult res =
          oracle.predict(context.systemPrompt(), userPrompt, spec.provider(), model);
      int s1 = Math.max(0, res.scoreT1());
      int s2 = Math.max(0, res.scoreT2());
      Long pwid = (knockout && s1 == s2) ? advancingTeamId(res.advancing(), m, t1, t2) : null;
      p =
          new PaulPrediction(
              m.getId(),
              spec.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              s1,
              s2,
              clampConfidence(res.confidence()),
              res.reasoning(),
              "es",
              PaulPrediction.SOURCE_AI,
              pwid);
    } catch (RuntimeException e) {
      int[] s = deterministicStub(m.getId());
      Long pwid = (knockout && s[0] == s[1]) ? advancingTeamId(null, m, t1, t2) : null;
      p =
          new PaulPrediction(
              m.getId(),
              spec.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              s[0],
              s[1],
              null,
              "Paul prefirió no arriesgar esta vez.",
              "es",
              PaulPrediction.SOURCE_FALLBACK,
              pwid);
    }

    predictions
        .findByMatchIdAndModelAndKind(m.getId(), model, PaulPrediction.KIND_CANDIDATE)
        .ifPresent(
            existing -> {
              predictions.delete(existing);
              predictions.flush();
            });
    predictions.save(p);
  }
```

Add the shared advancing helpers (place near `deterministicStub`):

```java
  /**
   * Map the model's advancing pick to a team id, falling back to the higher-ranked team (lower FIFA
   * number) and finally to team 1 — so a knockout draw prediction always names an advancing team.
   */
  static Long advancingTeamId(String advancing, Match m, Team t1, Team t2) {
    if ("LOCAL".equalsIgnoreCase(advancing)) return m.getTeam1Id();
    if ("VISITANTE".equalsIgnoreCase(advancing)) return m.getTeam2Id();
    Integer r1 = t1 == null ? null : t1.getFifaRanking();
    Integer r2 = t2 == null ? null : t2.getFifaRanking();
    if (r1 != null && r2 != null && !r1.equals(r2)) {
      return r1 < r2 ? m.getTeam1Id() : m.getTeam2Id();
    }
    return m.getTeam1Id();
  }
```

- [ ] **Step 4: Rewire the job runner**

In `PaulJobService.startGenerate`, change `predictionService::generateAllGroup` to `predictionService::generateOpen`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionServiceIT test`
Expected: PASS (all six tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java \
        backend/src/main/java/io/quiniela/api/paul/PaulJobService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulPredictionServiceIT.java
git commit -m "feat(paul): generate candidates for open knockout matches with advancing"
```

---

### Task 7: Generalize ensemble synthesis to open matches (`synthesizeOpen`)

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java`
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulJobService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulEnsembleServiceIT.java`

**Interfaces:**
- Consumes: `MatchRepository.findBy…KickoffAtAfter…` (Task 5); `PaulPredictionService.advancingTeamId(...)` (Task 6); `PaulPredictionResult.advancing()` (Task 2).
- Produces: `PaulEnsembleService.synthesizeOpen()` / `synthesizeOpen(PaulProgress)`. Replaces `synthesizeAllGroup`.

- [ ] **Step 1: Add a knockout synthesis test**

In `PaulEnsembleServiceIT.java`, add (match the existing autowiring; add a `JdbcTemplate jdbc` field and `@AfterEach` if not present). The test seeds two CANDIDATE rows for an R32 match (each predicting a draw with the local advancing), forces a draw ensemble result, and asserts the OFFICIAL carries `predicted_winner_id`:

```java
  private static final long KO_MATCH = 9011L;

  @org.junit.jupiter.api.AfterEach
  void cleanupKo() {
    FakePaulOracleConfig.forcedResult.set(null);
    jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", KO_MATCH);
    jdbc.update("DELETE FROM match WHERE id = ?", KO_MATCH);
  }

  @org.junit.jupiter.api.Test
  void synthesizesKnockoutOfficialWithAdvancing() {
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        KO_MATCH);
    // Two candidates, both a 1-1 draw with the LOCAL (team 1) advancing.
    for (String model : new String[] {"gemini-2.5-pro", "gemini-2.5-flash"}) {
      repo.save(
          new PaulPrediction(
              KO_MATCH, "google", model, PaulPrediction.KIND_CANDIDATE,
              1, 1, null, "empate", "es", PaulPrediction.SOURCE_AI, 1L));
    }
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(1, 1, 0.6, "empate, avanza local", "LOCAL"));

    boolean did = service.synthesizeForMatch(KO_MATCH);

    assertThat(did).isTrue();
    var official = repo.findByMatchIdAndModelAndKind(KO_MATCH, "ensemble", PaulPrediction.KIND_OFFICIAL);
    assertThat(official).isPresent();
    assertThat(official.get().getScoreT1()).isEqualTo(1);
    assertThat(official.get().getPredictedWinnerId()).isEqualTo(1L);
  }
```

(If the existing IT references `synthesizeAllGroup()`, rename those calls to `synthesizeOpen()`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=PaulEnsembleServiceIT#synthesizesKnockoutOfficialWithAdvancing test`
Expected: COMPILE FAILURE (11-arg `PaulPrediction` already exists, but `service.synthesizeForMatch` now must set the winner) or assertion FAILURE — `getPredictedWinnerId()` is null.

- [ ] **Step 3: Implement open synthesis + advancing aggregation**

In `PaulEnsembleService.java`, add imports `import io.quiniela.api.match.Match;` (already present), `import io.quiniela.api.match.Round;`, `import java.time.Instant;`. Replace `synthesizeAllGroup` methods:

```java
  /** For each OPEN match with candidates, synthesize one OFFICIAL pick via the ensemble judge. */
  public int synthesizeOpen() {
    return synthesizeOpen(PaulProgress.NOOP);
  }

  public int synthesizeOpen(PaulProgress progress) {
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, Instant.now());
    progress.start(open.size());
    int created = 0;
    for (Match m : open) {
      Boolean did = tx.execute(s -> synthesizeForMatch(m.getId()));
      if (Boolean.TRUE.equals(did)) created++;
      progress.tick();
    }
    return created;
  }
```

Rewrite `synthesizeForMatch` to compute the winner and pass it through. Note the `official(...)` helper gains a `Long predictedWinnerId` param:

```java
  boolean synthesizeForMatch(Long matchId) {
    List<PaulPrediction> candidates =
        predictions.findByMatchIdAndKind(matchId, PaulPrediction.KIND_CANDIDATE);
    if (candidates.isEmpty()) return false;

    Match m = matches.findById(matchId).orElseThrow();
    Round r = rounds.findById(m.getRoundId()).orElseThrow();
    boolean knockout = !"GROUP".equals(r.getCode());

    PaulProperties.ModelSpec es = props.ensembleSpec();
    PaulPrediction official;
    try {
      PaulPredictionResult res =
          oracle.predict(systemPrompt(), candidatePrompt(candidates, m, knockout), es.provider(), es.model());
      int s1 = Math.max(0, res.scoreT1());
      int s2 = Math.max(0, res.scoreT2());
      Long pwid = (knockout && s1 == s2) ? officialAdvancing(res.advancing(), m, candidates) : null;
      official = official(matchId, s1, s2, clamp(res.confidence()), res.reasoning(), PaulPrediction.SOURCE_AI, pwid);
    } catch (RuntimeException e) {
      PaulPrediction pick = candidates.get(0);
      Long pwid =
          (knockout && pick.getScoreT1().equals(pick.getScoreT2())) ? pick.getPredictedWinnerId() : null;
      official =
          official(
              matchId,
              pick.getScoreT1(),
              pick.getScoreT2(),
              null,
              "Paul consultó a sus otros yo y se quedó con su instinto.",
              PaulPrediction.SOURCE_FALLBACK,
              pwid);
    }

    predictions
        .findByMatchIdAndModelAndKind(matchId, ENSEMBLE_MODEL_LABEL, PaulPrediction.KIND_OFFICIAL)
        .ifPresent(
            existing -> {
              predictions.delete(existing);
              predictions.flush();
            });
    predictions.save(official);
    return true;
  }

  /**
   * Resolve the official advancing team for a knockout draw: the judge's pick when valid, else the
   * first candidate's stored winner, else team 1 (via the shared {@link PaulPredictionService}
   * helper, which already encodes that fallback chain — pass null teams so it skips ranking).
   */
  private Long officialAdvancing(String advancing, Match m, List<PaulPrediction> candidates) {
    if ("LOCAL".equalsIgnoreCase(advancing)) return m.getTeam1Id();
    if ("VISITANTE".equalsIgnoreCase(advancing)) return m.getTeam2Id();
    for (PaulPrediction c : candidates) {
      if (c.getPredictedWinnerId() != null) return c.getPredictedWinnerId();
    }
    return m.getTeam1Id();
  }
```

Update `official(...)` to accept and set the winner:

```java
  private PaulPrediction official(
      Long matchId, int s1, int s2, BigDecimal conf, String reasoning, String source, Long pwid) {
    return new PaulPrediction(
        matchId,
        props.ensembleSpec().provider(),
        ENSEMBLE_MODEL_LABEL,
        PaulPrediction.KIND_OFFICIAL,
        s1,
        s2,
        conf,
        reasoning,
        "es",
        source,
        pwid);
  }
```

Update `candidatePrompt` to take the match + knockout flag and list each candidate's advancing pick:

```java
  private String candidatePrompt(List<PaulPrediction> candidates, Match m, boolean knockout) {
    StringBuilder sb = new StringBuilder("Mis predicciones previas:\n");
    for (PaulPrediction c : candidates) {
      sb.append("- ")
          .append(c.getModel())
          .append(": ")
          .append(c.getScoreT1())
          .append('-')
          .append(c.getScoreT2());
      if (knockout && c.getPredictedWinnerId() != null) {
        sb.append(" (avanza ")
            .append(c.getPredictedWinnerId().equals(m.getTeam1Id()) ? "LOCAL" : "VISITANTE")
            .append(')');
      }
      if (c.getReasoning() != null) sb.append(" (").append(c.getReasoning()).append(')');
      sb.append('\n');
    }
    sb.append(
        knockout
            ? "Da tu marcador oficial. Si es empate, indica \"advancing\" (LOCAL/VISITANTE)."
            : "Da tu marcador oficial.");
    return sb.toString();
  }
```

- [ ] **Step 4: Rewire the job runner**

In `PaulJobService.startSynthesize`, change `ensembleService::synthesizeAllGroup` to `ensembleService::synthesizeOpen`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=PaulEnsembleServiceIT test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java \
        backend/src/main/java/io/quiniela/api/paul/PaulJobService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulEnsembleServiceIT.java
git commit -m "feat(paul): synthesize open knockout officials with advancing team"
```

---

### Task 8: Surface the advancing team in per-match suggestions

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulServiceCachedIT.java`

**Interfaces:**
- Produces: `PaulService.Suggestion(Integer scoreT1, Integer scoreT2, String reasoning, Long predictedWinnerId)`.

- [ ] **Step 1: Add a failing test**

In `PaulServiceCachedIT.java`, add a test that seeds a knockout CANDIDATE with a winner and asserts `suggestForMatch` returns it. Add a `JdbcTemplate jdbc` field + cleanup if absent:

```java
  @org.junit.jupiter.api.Test
  void suggestForKnockoutMatchReturnsPredictedWinner() {
    long koMatch = 9021L;
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        koMatch);
    repo.save(
        new PaulPrediction(
            koMatch, "google", "gemini-2.5-pro", PaulPrediction.KIND_CANDIDATE,
            1, 1, null, "empate, avanza local", "es", PaulPrediction.SOURCE_AI, 1L));
    try {
      PaulService.Suggestion s = service.suggestForMatch(koMatch);
      assertThat(s.scoreT1()).isEqualTo(1);
      assertThat(s.predictedWinnerId()).isEqualTo(1L);
    } finally {
      jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", koMatch);
      jdbc.update("DELETE FROM match WHERE id = ?", koMatch);
    }
  }
```

(Match the existing autowired field names in this IT — `service`, `repo`, and add `jdbc`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=PaulServiceCachedIT#suggestForKnockoutMatchReturnsPredictedWinner test`
Expected: COMPILE FAILURE — `Suggestion` has no `predictedWinnerId()`.

- [ ] **Step 3: Extend `Suggestion` and the producers**

In `PaulService.java`, change the record and both producers:

```java
  public record Suggestion(Integer scoreT1, Integer scoreT2, String reasoning, Long predictedWinnerId) {}
```

In `suggestForMatch`, return the candidate's winner:

```java
    if (!candidates.isEmpty()) {
      PaulPrediction pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
      return new Suggestion(
          pick.getScoreT1(), pick.getScoreT2(), pick.getReasoning(), pick.getPredictedWinnerId());
    }
    return stub(matchId);
```

In `stub`, pass `null`:

```java
    return new Suggestion(
        t1, t2, "Paul cree que es un partido " + (t1 + t2 > 2 ? "abierto" : "cerrado") + ".", null);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=PaulServiceCachedIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulServiceCachedIT.java
git commit -m "feat(paul): include predictedWinnerId in per-match suggestion"
```

---

### Task 9: Fill the open round (not just group) for users

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulServiceCachedIT.java`

**Interfaces:**
- Consumes: `MatchRepository.findBy…KickoffAtAfter…` (Task 5); `Suggestion.predictedWinnerId()` (Task 8); `BracketService.SaveBetRequest(matchId, scoreT1, scoreT2, predictedWinnerId)`.
- Produces: `PaulService.fillAllForUser` now fills every open match the user has not bet (group pre-kickoff + the open knockout round), passing the winner through `bracket.saveBet`.

- [ ] **Step 1: Add a failing test**

In `PaulServiceCachedIT.java`, add a test that fills a user's bracket including an open R32 match seeded with a draw candidate, and asserts the resulting bet carries the winner. Create the user via the seeded users flow used elsewhere in this IT (reuse the existing helper if present; otherwise insert a user row and read its id):

```java
  @org.junit.jupiter.api.Test
  void fillAllIncludesOpenKnockoutRoundWithPredictedWinner() {
    long koMatch = 9031L;
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        koMatch);
    repo.save(
        new PaulPrediction(
            koMatch, "google", "gemini-2.5-pro", PaulPrediction.KIND_CANDIDATE,
            1, 1, null, "empate, avanza local", "es", PaulPrediction.SOURCE_AI, 1L));
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role) "
                + "VALUES ('filler-user', 'filler@test', 'Filler', 'player') RETURNING id",
            Long.class);
    try {
      service.fillAllForUser(userId);
      Long pwid =
          jdbc.queryForObject(
              "SELECT b.predicted_winner_id FROM bet b JOIN quiniela q ON q.id = b.quiniela_id"
                  + " WHERE q.user_id = ? AND b.match_id = ?",
              Long.class,
              userId,
              koMatch);
      assertThat(pwid).isEqualTo(1L);
    } finally {
      jdbc.update(
          "DELETE FROM bet WHERE quiniela_id IN (SELECT id FROM quiniela WHERE user_id = ?)", userId);
      jdbc.update("DELETE FROM quiniela WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
      jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", koMatch);
      jdbc.update("DELETE FROM match WHERE id = ?", koMatch);
    }
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=PaulServiceCachedIT#fillAllIncludesOpenKnockoutRoundWithPredictedWinner test`
Expected: FAIL — `fillAllForUser` still fills only group matches, so the knockout bet (and its winner) is absent.

- [ ] **Step 3: Rewrite `fillAllForUser` to use open matches**

In `PaulService.java`, add `import io.quiniela.api.match.Match;` (already present) and a constant `private static final Long TOURNAMENT_ID = 1L;`. Replace the body's match selection and the save call:

```java
  @Transactional
  public FillResult fillAllForUser(Long userId) {
    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, userId)
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, userId)));

    List<Bet> existing = bets.findByQuinielaId(q.getId());
    Set<Long> alreadyBet = new HashSet<>();
    existing.forEach(b -> alreadyBet.add(b.getMatchId()));

    // Fill every OPEN match (teams set, kickoff in the future) the user has not bet:
    // group matches pre-kickoff and the currently-open knockout round. Persisting goes
    // through bracket.saveBet so the deadline/kickoff lock and draw-only winner rule are
    // enforced in exactly one place.
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, java.time.Instant.now());

    int created = 0;
    for (Match m : open) {
      if (alreadyBet.contains(m.getId())) continue;
      Suggestion s = suggestForMatch(m.getId());
      bracket.saveBet(
          userId,
          new BracketService.SaveBetRequest(m.getId(), s.scoreT1(), s.scoreT2(), s.predictedWinnerId()));
      created++;
    }
    return new FillResult(created);
  }
```

Remove the now-unused `rounds` field + constructor param from `PaulService` (it was used only for the group lookup), and remove the now-unused `import io.quiniela.api.match.RoundRepository;`. Update the constructor and its assignment. (Spring injects the remaining beans; no manual `new PaulService(...)` exists in main code — confirm with `grep -rn "new PaulService(" backend/src`. If a test constructs it manually, drop the `rounds` arg there too.)

- [ ] **Step 4: Run the suite to verify it passes (and nothing regressed)**

Run: `cd backend && ./mvnw -q -Dtest=PaulServiceCachedIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulServiceCachedIT.java
git commit -m "feat(paul): fill the open round (group + knockout) with advancing"
```

---

### Task 10: Snapshot the advancing team into Paul's own bets on reveal

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulRevealIT.java`

**Interfaces:**
- Consumes: `PaulPrediction.getPredictedWinnerId()` (Task 1); `Bet.setPredictedWinnerId(...)`.
- Produces: `PaulService.reveal()` sets `predicted_winner_id` on Paul's bets from the OFFICIAL prediction.

- [ ] **Step 1: Add a failing test**

In `PaulRevealIT.java`, add a test that seeds a knockout OFFICIAL with a winner and asserts Paul's bet carries it (reuse the file's existing pattern for finding Paul's quiniela; add `jdbc` if needed):

```java
  @org.junit.jupiter.api.Test
  void revealSnapshotsKnockoutOfficialWinner() {
    long koMatch = 9041L;
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        koMatch);
    repo.save(
        new PaulPrediction(
            koMatch, "google", "ensemble", PaulPrediction.KIND_OFFICIAL,
            1, 1, null, "empate, avanza local", "es", PaulPrediction.SOURCE_AI, 1L));
    try {
      service.reveal();
      Long pwid =
          jdbc.queryForObject(
              "SELECT b.predicted_winner_id FROM bet b"
                  + " JOIN quiniela q ON q.id = b.quiniela_id"
                  + " JOIN users u ON u.id = q.user_id"
                  + " WHERE u.google_sub = 'paul-bot-oracle' AND b.match_id = ?",
              Long.class,
              koMatch);
      assertThat(pwid).isEqualTo(1L);
    } finally {
      jdbc.update(
          "DELETE FROM bet WHERE quiniela_id IN"
              + " (SELECT q.id FROM quiniela q JOIN users u ON u.id = q.user_id"
              + " WHERE u.google_sub = 'paul-bot-oracle')");
      jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", koMatch);
      jdbc.update("DELETE FROM match WHERE id = ?", koMatch);
    }
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=PaulRevealIT#revealSnapshotsKnockoutOfficialWinner test`
Expected: FAIL — `predicted_winner_id` on Paul's bet is null (reveal does not set it yet).

- [ ] **Step 3: Set the winner on the snapshotted bet**

In `PaulService.reveal`, change the bet creation loop body:

```java
    int created = 0;
    for (PaulPrediction official : predictions.findByKind(PaulPrediction.KIND_OFFICIAL)) {
      if (already.contains(official.getMatchId())) continue;
      Bet bet =
          new Bet(q.getId(), official.getMatchId(), official.getScoreT1(), official.getScoreT2());
      bet.setPredictedWinnerId(official.getPredictedWinnerId());
      bets.save(bet);
      created++;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=PaulRevealIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulRevealIT.java
git commit -m "feat(paul): snapshot advancing team into Paul's knockout bets on reveal"
```

---

### Task 11: Frontend — forward the advancing team when accepting a knockout suggestion

**Files:**
- Modify: `frontend/lib/api/paul.ts`
- Modify: `frontend/app/knockout/[roundId]/actions.ts`
- Test: `frontend/app/knockout/[roundId]/actions.test.ts` (create)

**Interfaces:**
- Produces: `PaulSuggestion` TS type gains `predictedWinnerId: number | null`; `acceptPaulSuggestionAction` forwards it to `saveBet`.

**Background (the exact gap):** `acceptPaulSuggestionAction` currently calls `saveBet(matchId, s.scoreT1, s.scoreT2)` — dropping the winner. `saveBet(matchId, scoreT1, scoreT2, predictedWinnerId?)` already accepts a 4th arg (used by `saveBetAction`). The group accept path has no advancing team and stays as-is. The UI re-renders from server data via `revalidatePath`, so persisting the winner is sufficient — no client selector change.

- [ ] **Step 1: Write the failing test**

Create `frontend/app/knockout/[roundId]/actions.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("@/lib/api/bracket", () => ({ saveBet: vi.fn().mockResolvedValue(undefined) }));
vi.mock("@/lib/api/paul", () => ({ suggestForMatch: vi.fn() }));

import { acceptPaulSuggestionAction } from "./actions";
import { saveBet } from "@/lib/api/bracket";
import { suggestForMatch } from "@/lib/api/paul";

describe("acceptPaulSuggestionAction", () => {
  beforeEach(() => vi.clearAllMocks());

  it("forwards Paul's predictedWinnerId to saveBet on a knockout draw", async () => {
    vi.mocked(suggestForMatch).mockResolvedValue({
      scoreT1: 1,
      scoreT2: 1,
      reasoning: "empate, avanza local",
      predictedWinnerId: 7,
    });

    const out = await acceptPaulSuggestionAction(123, "R32");

    expect(out).toEqual({ ok: true, scoreT1: 1, scoreT2: 1, reasoning: "empate, avanza local" });
    expect(saveBet).toHaveBeenCalledWith(123, 1, 1, 7);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test -- knockout/\\[roundId\\]/actions`
Expected: FAIL — `saveBet` was called with `(123, 1, 1)` (winner dropped); also a TS error that `predictedWinnerId` is not on `PaulSuggestion`.

- [ ] **Step 3: Add the field to the API type**

In `frontend/lib/api/paul.ts`, extend the type:

```ts
export type PaulSuggestion = {
  scoreT1: number;
  scoreT2: number;
  reasoning: string;
  predictedWinnerId: number | null;
};
```

- [ ] **Step 4: Forward the winner in the accept action**

In `frontend/app/knockout/[roundId]/actions.ts`, change the `saveBet` call inside `acceptPaulSuggestionAction`:

```ts
    const s = await suggestForMatch(matchId);
    await saveBet(matchId, s.scoreT1, s.scoreT2, s.predictedWinnerId);
```

(Leave the returned `AcceptOutcome` unchanged — the score+reasoning are what the toast shows; the advancing pick is reflected when the page revalidates.)

- [ ] **Step 5: Run tests + typecheck to verify they pass**

Run: `cd frontend && pnpm test -- knockout/\\[roundId\\]/actions && pnpm typecheck`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/api/paul.ts "frontend/app/knockout/[roundId]/actions.ts" \
        "frontend/app/knockout/[roundId]/actions.test.ts"
git commit -m "feat(paul): forward advancing team when accepting a knockout suggestion"
```

---

### Task 12: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend suite**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS — all tests green, including the new knockout candidate/ensemble/suggest/fill/reveal tests and the unchanged group, sync, freeze, and bracket suites.

- [ ] **Step 2: Backend format check**

Run: `cd backend && ./mvnw -q spotless:check`
Expected: BUILD SUCCESS (run `./mvnw spotless:apply` and re-commit if it fails).

- [ ] **Step 3: Frontend checks**

Run: `cd frontend && pnpm test && pnpm typecheck && pnpm lint`
Expected: PASS.

- [ ] **Step 4: Confirm the migration applies cleanly**

Run: `cd backend && ./mvnw -q -Dtest=PaulSchemaIT test` (and any Flyway/migration IT)
Expected: PASS — `V023` applies; `paul_prediction.predicted_winner_id` exists.

- [ ] **Step 5: Final status**

```bash
git status   # expect a clean working tree if Tasks 1-11 committed cleanly
```

---

## Notes for the implementer

- **Prod oracles:** the active ensemble is `VertexPaulOracle` (genai SDK, raw JSON) + `OpenAiCompatVertexOracle` (raw JSON) behind `RoutingPaulOracle` (`app.paul.provider=vertex`). `GeminiPaulOracle` (Spring AI `.entity`) is only used in keyed AI-Studio mode; its schema is derived from the record automatically — adding the nullable `advancing` field is sufficient.
- **Reveal visibility (no code):** other players see Paul's picks only through `CompareService`, gated by `LockClock.isMatchRevealable` (knockout: match played or knockout deadline passed). No new gating is added. **Ops caveat:** verify the production `tournament.knockout_deadline` value before running the first knockout reveal — if NULL, knockout picks reveal per match on `played` (the safe behavior).
- **Operational sequence each round:** after a knockout round's teams resolve (the post-match tail-refresh assigns them), run admin `generate` → `synthesize`, then `reveal`. The open-match rule predicts exactly the matches that are currently resolved and not yet kicked off.
- **Idempotency:** `generateOpen` / `synthesizeOpen` replace existing rows per (match, model, kind); re-running is safe.

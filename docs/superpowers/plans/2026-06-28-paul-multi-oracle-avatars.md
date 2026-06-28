# Multi-Oracle AI Avatars (Otto & Chitara) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generalize the single Paul bot into a config-driven oracle registry and add two single-model AI avatars — Otto la Nutria 🦦 (DeepSeek) and Chitara la Leoparda 🐆 (Llama) — that compete in the knockout for fun (not money), replacing two abandoned player accounts.

**Architecture:** Introduce an `Oracle` concept (key, googleSub, displayName, roster, optional ensemble model). Paul is the default oracle (5 models + judge); avatars are single-model (one model, no judge). Attribute every `paul_prediction` row to an oracle via a new `oracle` column. `generate` / `synthesize` / `reveal` loop over all configured oracles; single-model synthesis promotes the lone candidate instead of calling a judge. Paul's data and behavior are unchanged (he's just `oracle='paul'`, and existing rows backfill to it).

**Tech Stack:** Java 25 records, Spring Boot 4, Spring Data JPA, Flyway, JUnit 5 + AssertJ, Testcontainers (`AbstractIntegrationTest`), Maven (`./mvnw`); Next.js 16 + Vitest (frontend).

## Global Constraints

- Backend module root: `backend/`. Run backend commands from there (`cd backend`).
- Package: `io.quiniela.api.paul`. Tournament id `1L`, default pool id `1L`.
- Flyway plain SQL under `backend/src/main/resources/db/migration/`. Next free versions: **V024**, then **V025**.
- The avatars are `is_bot=TRUE`, role `player` (auto-excluded from the prize pot by `AdminPaymentService`).
- **Oracle loop variable is named `bot`** in services that already have a `PaulOracle oracle` field (`PaulPredictionService`, `PaulEnsembleService`) — do NOT shadow that field.
- `predicted_winner_id` rule (unchanged): set only when knockout AND predicted score is a draw; else null.
- Single-model oracle = `ensembleModel == null` → synthesis **promotes the lone candidate** (no LLM judge).
- UI copy stays Spanish (Spanish + English message keys).
- Format with Spotless / Google Java Format (2-space indent); run `./mvnw spotless:apply` if it fails.
- Test command: `./mvnw -q -Dtest=<Class> test`. IT classes extend `AbstractIntegrationTest` (Postgres 16 Testcontainer; slow). `AbstractIntegrationTest.@BeforeEach` deletes all users and re-seeds only `paul-bot-oracle`, so a test needing another bot user must re-seed it itself.
- Default test config (`backend/src/test/resources/application.yml`) has **no** `app.paul.oracles` → `allOracles()` returns `[paul]` → existing tests are unaffected.

---

### Task 1: Oracle registry in config

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/paul/Oracle.java`
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulProperties.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulPropertiesTest.java` (create)

**Interfaces:**
- Produces: `Oracle(String key, String googleSub, String displayName, List<PaulProperties.ModelSpec> roster, PaulProperties.ModelSpec ensembleSpec)` with `boolean isEnsemble()`.
- Produces: `PaulProperties.OracleSpec(String key, String googleSub, String displayName, List<String> models, String ensembleModel)`; `PaulProperties.allOracles()` → `List<Oracle>` = Paul + configured extras; new 6th record component `List<OracleSpec> oracles` (config key `app.paul.oracles`).

- [ ] **Step 1: Write the failing test**

Create `PaulPropertiesTest.java`:

```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaulPropertiesTest {

  @Test
  void allOraclesStartsWithPaulBuiltFromBaseFields() {
    PaulProperties p =
        new PaulProperties("google", List.of("gemini-2.5-pro", "gemini-2.5-flash"),
            "gemini-2.5-pro", "proj", "us-central1", List.of());
    List<Oracle> oracles = p.allOracles();
    assertThat(oracles).hasSize(1);
    Oracle paul = oracles.get(0);
    assertThat(paul.key()).isEqualTo("paul");
    assertThat(paul.googleSub()).isEqualTo("paul-bot-oracle");
    assertThat(paul.roster()).hasSize(2);
    assertThat(paul.isEnsemble()).isTrue();
    assertThat(paul.ensembleSpec().model()).isEqualTo("gemini-2.5-pro");
  }

  @Test
  void singleModelExtraOracleHasNoEnsemble() {
    PaulProperties.OracleSpec otto =
        new PaulProperties.OracleSpec(
            "otto", "otto-bot-oracle", "Otto la Nutria 🦦",
            List.of("deepseek:deepseek-ai/deepseek-v3.1"), null);
    PaulProperties p =
        new PaulProperties("google", List.of("gemini-2.5-pro"), "gemini-2.5-pro",
            "proj", "us-central1", List.of(otto));
    List<Oracle> oracles = p.allOracles();
    assertThat(oracles).hasSize(2);
    Oracle o = oracles.get(1);
    assertThat(o.key()).isEqualTo("otto");
    assertThat(o.roster()).hasSize(1);
    assertThat(o.roster().get(0).provider()).isEqualTo("deepseek");
    assertThat(o.roster().get(0).model()).isEqualTo("deepseek-ai/deepseek-v3.1");
    assertThat(o.isEnsemble()).isFalse();
    assertThat(o.ensembleSpec()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=PaulPropertiesTest test`
Expected: COMPILE FAILURE — `PaulProperties(...)` is 5-arg, `Oracle` / `OracleSpec` / `allOracles()` not defined.

- [ ] **Step 3: Create the `Oracle` record**

Create `Oracle.java`:

```java
package io.quiniela.api.paul;

import java.util.List;

/**
 * A prediction-bot identity: which user it plays as, and which model(s) form its brain.
 * Paul is an ensemble oracle (multi-model roster + a judge); avatar oracles are single-model
 * (a roster of one, no judge — synthesis just promotes the lone candidate).
 */
public record Oracle(
    String key,
    String googleSub,
    String displayName,
    List<PaulProperties.ModelSpec> roster,
    PaulProperties.ModelSpec ensembleSpec) {

  public boolean isEnsemble() {
    return ensembleSpec != null;
  }
}
```

- [ ] **Step 4: Add the `oracles` component, `OracleSpec`, and `allOracles()`**

In `PaulProperties.java`, add `import java.util.ArrayList;`, add the 6th component + default, the nested `OracleSpec`, and `allOracles()`:

```java
@ConfigurationProperties(prefix = "app.paul")
public record PaulProperties(
    String provider,
    List<String> models,
    String ensembleModel,
    String projectId,
    String location,
    List<OracleSpec> oracles) {

  public PaulProperties {
    if (provider == null) provider = "google";
    if (models == null || models.isEmpty()) models = List.of("gemini-2.5-flash");
    if (ensembleModel == null) ensembleModel = models.get(0);
    if (location == null) location = "us-central1";
    if (oracles == null) oracles = List.of();
  }

  /** Config entry for an extra (non-Paul) oracle bot. {@code ensembleModel} null => single-model. */
  public record OracleSpec(
      String key, String googleSub, String displayName, List<String> models, String ensembleModel) {}

  /** One roster entry: which provider serves which model id. */
  public record ModelSpec(String provider, String model) {}

  public List<ModelSpec> roster() {
    return models.stream().map(this::parseSpec).toList();
  }

  public ModelSpec ensembleSpec() {
    return parseSpec(ensembleModel);
  }

  /**
   * The full oracle registry: Paul (built from the base fields) followed by each configured extra
   * oracle. An extra oracle with no ensemble-model is single-model (no judge).
   */
  public List<Oracle> allOracles() {
    List<Oracle> all = new ArrayList<>();
    all.add(new Oracle("paul", "paul-bot-oracle", "Pulpo Paul 🐙", roster(), ensembleSpec()));
    for (OracleSpec o : oracles) {
      List<ModelSpec> r = o.models().stream().map(this::parseSpec).toList();
      ModelSpec ens = o.ensembleModel() == null ? null : parseSpec(o.ensembleModel());
      all.add(new Oracle(o.key(), o.googleSub(), o.displayName(), r, ens));
    }
    return all;
  }

  private ModelSpec parseSpec(String s) {
    int i = s.indexOf(':');
    return i < 0
        ? new ModelSpec(provider, s)
        : new ModelSpec(s.substring(0, i), s.substring(i + 1));
  }
}
```

- [ ] **Step 5: Check for other `new PaulProperties(` call sites**

Run: `grep -rn "new PaulProperties(" backend/src` — update any to the 6-arg form (pass `List.of()` for the new param). (Expected: none outside the new test.)

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=PaulPropertiesTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/Oracle.java \
        backend/src/main/java/io/quiniela/api/paul/PaulProperties.java \
        backend/src/test/java/io/quiniela/api/paul/PaulPropertiesTest.java
git commit -m "feat(paul): oracle registry (Paul + configurable extra oracles)"
```

---

### Task 2: Data model — `oracle` column, bot seed, entity, repo finders

**Files:**
- Create: `backend/src/main/resources/db/migration/V024__paul_prediction_oracle.sql`
- Create: `backend/src/main/resources/db/migration/V025__seed_avatar_bots.sql`
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulPrediction.java`
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionRepository.java`
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java` (call sites only)
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java` (call site only)
- Test: `backend/src/test/java/io/quiniela/api/paul/PaulPredictionRepositoryIT.java`

**Interfaces:**
- Produces: `PaulPrediction` constructor gains a **leading** `String oracle` param (now 12 args); accessor `getOracle()`.
- Produces: repo finders `findByOracleAndKind(String, String)`, `findByOracleAndMatchIdAndKind(String, Long, String)`, `findByOracleAndMatchIdAndModelAndKind(String, Long, String, String)`.

- [ ] **Step 1: Write the migrations**

`V024__paul_prediction_oracle.sql`:

```sql
-- V024: attribute each Paul prediction to an oracle bot (Paul, Otto, Chitara, …).
-- Existing rows backfill to 'paul' via the column default, so Paul's data is unchanged.
ALTER TABLE paul_prediction ADD COLUMN oracle VARCHAR(32) NOT NULL DEFAULT 'paul';

ALTER TABLE paul_prediction DROP CONSTRAINT paul_prediction_match_id_model_kind_key;
ALTER TABLE paul_prediction
    ADD CONSTRAINT paul_prediction_oracle_match_model_kind_key
    UNIQUE (oracle, match_id, model, kind);
```

`V025__seed_avatar_bots.sql`:

```sql
-- V025: seed the two avatar bot users (same pattern as Paul's V015 seed).
-- role 'player' so they show on the leaderboard; is_bot TRUE so never prize-eligible.
INSERT INTO users (google_sub, email, display_name, role, is_bot) VALUES
  ('otto-bot-oracle',    'otto@laquinieladelospanas.com',    'Otto la Nutria 🦦',    'player', TRUE),
  ('chitara-bot-oracle', 'chitara@laquinieladelospanas.com', 'Chitara la Leoparda 🐆','player', TRUE)
ON CONFLICT (google_sub) DO NOTHING;
```

- [ ] **Step 2: Add the failing repository test**

In `PaulPredictionRepositoryIT.java`, add (use seeded match id 1; the `oracle`-leading 12-arg constructor):

```java
  @Test
  void persistsAndFiltersByOracle() {
    repo.saveAndFlush(new PaulPrediction(
        "paul", 1L, "google", "gemini-2.5-pro", PaulPrediction.KIND_OFFICIAL,
        1, 0, null, "p", "es", PaulPrediction.SOURCE_AI, null));
    repo.saveAndFlush(new PaulPrediction(
        "otto", 1L, "deepseek", "deepseek-ai/deepseek-v3.1", PaulPrediction.KIND_OFFICIAL,
        2, 1, null, "o", "es", PaulPrediction.SOURCE_AI, null));

    assertThat(repo.findByOracleAndKind("otto", PaulPrediction.KIND_OFFICIAL)).hasSize(1);
    assertThat(repo.findByOracleAndMatchIdAndKind("paul", 1L, PaulPrediction.KIND_OFFICIAL))
        .singleElement()
        .satisfies(p -> assertThat(p.getOracle()).isEqualTo("paul"));
    assertThat(
            repo.findByOracleAndMatchIdAndModelAndKind(
                "otto", 1L, "deepseek-ai/deepseek-v3.1", PaulPrediction.KIND_OFFICIAL))
        .isPresent();
  }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionRepositoryIT#persistsAndFiltersByOracle test`
Expected: COMPILE FAILURE — constructor arity / `getOracle()` / finders missing.

- [ ] **Step 4: Add the entity field + getter + leading constructor param**

In `PaulPrediction.java`, add the column (place above `match_id`):

```java
  @Column(nullable = false, length = 32)
  private String oracle;
```

Change the constructor to take `oracle` first and assign it:

```java
  public PaulPrediction(
      String oracle,
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
    this.oracle = oracle;
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
  public String getOracle() {
    return oracle;
  }
```

- [ ] **Step 5: Add the repo finders**

In `PaulPredictionRepository.java`, add:

```java
  List<PaulPrediction> findByOracleAndKind(String oracle, String kind);

  List<PaulPrediction> findByOracleAndMatchIdAndKind(String oracle, Long matchId, String kind);

  Optional<PaulPrediction> findByOracleAndMatchIdAndModelAndKind(
      String oracle, Long matchId, String model, String kind);
```

- [ ] **Step 6: Patch the 3 existing `new PaulPrediction(...)` call sites to pass `"paul"` first**

In `PaulPredictionService.upsertCandidate` (both the AI branch and the fallback branch) prepend `"paul",` as the first constructor argument. In `PaulEnsembleService.official(...)` prepend `"paul",` as the first argument. (Correct behavior for today's single oracle; Tasks 3 & 4 replace `"paul"` with the oracle key.)

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionRepositoryIT test`
Expected: PASS (new test + existing repo tests).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V024__paul_prediction_oracle.sql \
        backend/src/main/resources/db/migration/V025__seed_avatar_bots.sql \
        backend/src/main/java/io/quiniela/api/paul/PaulPrediction.java \
        backend/src/main/java/io/quiniela/api/paul/PaulPredictionRepository.java \
        backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java \
        backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java \
        backend/src/test/java/io/quiniela/api/paul/PaulPredictionRepositoryIT.java
git commit -m "feat(paul): oracle column + avatar bot seed + oracle-scoped finders"
```

---

### Task 3: Generalize candidate generation to all oracles

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/MultiOracleIT.java` (create)

**Interfaces:**
- Consumes: `PaulProperties.allOracles()` (Task 1); `findByOracleAndMatchIdAndModelAndKind` (Task 2); the 12-arg `PaulPrediction` (Task 2).
- Produces: `generateOpen` now writes oracle-tagged candidates for every oracle; `upsertCandidate(String oracleKey, Match, Round, ModelSpec)`.

- [ ] **Step 1: Write the failing test (new IT with an extra single-model oracle)**

Create `MultiOracleIT.java`. The `@SpringBootTest(properties=…)` registers a single-model `otto` oracle for this class only; `FakePaulOracleConfig` returns a result for any model; `@BeforeEach` re-seeds the `otto-bot-oracle` user (the base class wipes users):

```java
package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(FakePaulOracleConfig.class)
@SpringBootTest(
    properties = {
      "app.paul.oracles[0].key=otto",
      "app.paul.oracles[0].google-sub=otto-bot-oracle",
      "app.paul.oracles[0].display-name=Otto la Nutria",
      "app.paul.oracles[0].models[0]=otto:otto-model-x"
    })
class MultiOracleIT extends AbstractIntegrationTest {

  private static final long KO_MATCH = 9301L;

  @Autowired PaulPredictionService predictionService;
  @Autowired PaulEnsembleService ensembleService;
  @Autowired PaulService paulService;
  @Autowired PaulPredictionRepository repo;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seedOttoAndMatch() {
    jdbc.update(
        "INSERT INTO users (google_sub,email,display_name,role,is_bot) "
            + "VALUES ('otto-bot-oracle','otto@test','Otto','player',true) "
            + "ON CONFLICT (google_sub) DO NOTHING");
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        KO_MATCH);
  }

  @AfterEach
  void cleanup() {
    FakePaulOracleConfig.failModel.set(null);
    FakePaulOracleConfig.forcedResult.set(null);
    jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", KO_MATCH);
    jdbc.update("DELETE FROM match WHERE id = ?", KO_MATCH);
  }

  @Test
  void generateTagsCandidatesPerOracle() {
    predictionService.generateOpen();
    // otto = 1 model; paul (test config) = 2 models.
    assertThat(repo.findByOracleAndMatchIdAndKind("otto", KO_MATCH, PaulPrediction.KIND_CANDIDATE))
        .hasSize(1)
        .allSatisfy(p -> assertThat(p.getModel()).isEqualTo("otto-model-x"));
    assertThat(repo.findByOracleAndMatchIdAndKind("paul", KO_MATCH, PaulPrediction.KIND_CANDIDATE))
        .hasSize(2);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=MultiOracleIT#generateTagsCandidatesPerOracle test`
Expected: FAIL — `findByOracleAndMatchIdAndKind("otto", …)` is empty (generation still writes only `oracle='paul'`).

- [ ] **Step 3: Loop oracles in `generateOpen`; thread the oracle key into `upsertCandidate`**

In `PaulPredictionService.java`, replace `generateOpen(PaulProgress)` and `upsertCandidate`:

```java
  public int generateOpen(PaulProgress progress) {
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, Instant.now());
    Map<Long, Round> roundById = new HashMap<>();
    rounds
        .findByTournamentIdOrderBySequenceAsc(TOURNAMENT_ID)
        .forEach(r -> roundById.put(r.getId(), r));

    List<Oracle> oracles = props.allOracles();
    int total = open.size() * oracles.stream().mapToInt(o -> o.roster().size()).sum();
    progress.start(total);
    int created = 0;
    for (Oracle bot : oracles) {
      for (Match m : open) {
        Round r = roundById.get(m.getRoundId());
        for (PaulProperties.ModelSpec spec : bot.roster()) {
          tx.executeWithoutResult(s -> upsertCandidate(bot.key(), m, r, spec));
          created++;
          progress.tick();
        }
      }
    }
    return created;
  }

  private void upsertCandidate(String oracleKey, Match m, Round r, PaulProperties.ModelSpec spec) {
    String model = spec.model();
    Team t1 = m.getTeam1Id() == null ? null : teams.findById(m.getTeam1Id()).orElse(null);
    Team t2 = m.getTeam2Id() == null ? null : teams.findById(m.getTeam2Id()).orElse(null);
    boolean knockout = !"GROUP".equals(r.getCode());

    PaulPrediction p;
    try {
      String userPrompt =
          context.userPrompt(
              r.getCode(), m.getGroupCode(), name(t1), code(t1), ranking(t1),
              name(t2), code(t2), ranking(t2));
      PaulPredictionResult res =
          oracle.predict(context.systemPrompt(), userPrompt, spec.provider(), model);
      int s1 = Math.max(0, res.scoreT1());
      int s2 = Math.max(0, res.scoreT2());
      Long pwid = (knockout && s1 == s2) ? advancingTeamId(res.advancing(), m, t1, t2) : null;
      p =
          new PaulPrediction(
              oracleKey, m.getId(), spec.provider(), model, PaulPrediction.KIND_CANDIDATE,
              s1, s2, clampConfidence(res.confidence()), res.reasoning(), "es",
              PaulPrediction.SOURCE_AI, pwid);
    } catch (RuntimeException e) {
      int[] s = deterministicStub(m.getId());
      Long pwid = (knockout && s[0] == s[1]) ? advancingTeamId(null, m, t1, t2) : null;
      p =
          new PaulPrediction(
              oracleKey, m.getId(), spec.provider(), model, PaulPrediction.KIND_CANDIDATE,
              s[0], s[1], null, "Paul prefirió no arriesgar esta vez.", "es",
              PaulPrediction.SOURCE_FALLBACK, pwid);
    }

    predictions
        .findByOracleAndMatchIdAndModelAndKind(oracleKey, m.getId(), model, PaulPrediction.KIND_CANDIDATE)
        .ifPresent(
            existing -> {
              predictions.delete(existing);
              predictions.flush();
            });
    predictions.save(p);
  }
```

(Add `import io.quiniela.api.paul.Oracle;`? Same package — no import needed.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q -Dtest=MultiOracleIT#generateTagsCandidatesPerOracle test`
Expected: PASS. Then run the existing service IT to confirm Paul is unchanged: `./mvnw -q -Dtest=PaulPredictionServiceIT test` (still 144 for the group count — default config has no extra oracles).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulPredictionService.java \
        backend/src/test/java/io/quiniela/api/paul/MultiOracleIT.java
git commit -m "feat(paul): generate candidates for every configured oracle"
```

---

### Task 4: Generalize synthesis (single-model promote, ensemble judge)

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/MultiOracleIT.java` (add a method)

**Interfaces:**
- Consumes: `PaulProperties.allOracles()`, `Oracle.isEnsemble()/key()/ensembleSpec()` (Task 1); oracle-scoped finders (Task 2).
- Produces: `synthesizeOpen` loops oracles; `synthesizeForMatch(Oracle bot, Long matchId)`; `official(String oracleKey, String provider, Long matchId, …)`.

- [ ] **Step 1: Add the failing test**

In `MultiOracleIT.java` add:

```java
  @Test
  void synthesizePromotesSingleModelCandidateToOfficial() {
    predictionService.generateOpen();
    ensembleService.synthesizeOpen();
    var ottoOfficial =
        repo.findByOracleAndMatchIdAndModelAndKind(
            "otto", KO_MATCH, "ensemble", PaulPrediction.KIND_OFFICIAL);
    assertThat(ottoOfficial).isPresent();
    // FakePaulOracleConfig default result is 2-1, so the promoted official copies that.
    assertThat(ottoOfficial.get().getScoreT1()).isEqualTo(2);
    assertThat(ottoOfficial.get().getScoreT2()).isEqualTo(1);
    assertThat(ottoOfficial.get().getOracle()).isEqualTo("otto");
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=MultiOracleIT#synthesizePromotesSingleModelCandidateToOfficial test`
Expected: FAIL — no `oracle='otto'` official (synthesis still single-oracle, judge-only).

- [ ] **Step 3: Loop oracles; single-model promote; oracle-scoped official**

In `PaulEnsembleService.java`, replace `synthesizeOpen(PaulProgress)`, `synthesizeForMatch`, and `official`:

```java
  public int synthesizeOpen(PaulProgress progress) {
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, Instant.now());
    List<Oracle> oracles = props.allOracles();
    progress.start(open.size() * oracles.size());
    int created = 0;
    for (Oracle bot : oracles) {
      for (Match m : open) {
        Boolean did = tx.execute(s -> synthesizeForMatch(bot, m.getId()));
        if (Boolean.TRUE.equals(did)) created++;
        progress.tick();
      }
    }
    return created;
  }

  boolean synthesizeForMatch(Oracle bot, Long matchId) {
    List<PaulPrediction> candidates =
        predictions.findByOracleAndMatchIdAndKind(bot.key(), matchId, PaulPrediction.KIND_CANDIDATE);
    if (candidates.isEmpty()) return false;

    Match m = matches.findById(matchId).orElseThrow();
    Round r = rounds.findById(m.getRoundId()).orElseThrow();
    boolean knockout = !"GROUP".equals(r.getCode());

    PaulPrediction official;
    if (!bot.isEnsemble()) {
      // Single-model oracle: promote its one candidate verbatim (no judge call).
      PaulPrediction c = candidates.get(0);
      official =
          official(
              bot.key(), c.getProvider(), matchId, c.getScoreT1(), c.getScoreT2(),
              c.getConfidence(), c.getReasoning(), c.getSource(), c.getPredictedWinnerId());
    } else {
      PaulProperties.ModelSpec es = bot.ensembleSpec();
      try {
        PaulPredictionResult res =
            oracle.predict(
                systemPrompt(), candidatePrompt(candidates, m, knockout), es.provider(), es.model());
        int s1 = Math.max(0, res.scoreT1());
        int s2 = Math.max(0, res.scoreT2());
        Long pwid =
            (knockout && s1 == s2) ? officialAdvancing(res.advancing(), m, candidates) : null;
        official =
            official(
                bot.key(), es.provider(), matchId, s1, s2, clamp(res.confidence()),
                res.reasoning(), PaulPrediction.SOURCE_AI, pwid);
      } catch (RuntimeException e) {
        PaulPrediction pick = candidates.get(0);
        Long pwid =
            (knockout && pick.getScoreT1().equals(pick.getScoreT2()))
                ? pick.getPredictedWinnerId()
                : null;
        official =
            official(
                bot.key(), es.provider(), matchId, pick.getScoreT1(), pick.getScoreT2(), null,
                "Paul consultó a sus otros yo y se quedó con su instinto.",
                PaulPrediction.SOURCE_FALLBACK, pwid);
      }
    }

    predictions
        .findByOracleAndMatchIdAndModelAndKind(
            bot.key(), matchId, ENSEMBLE_MODEL_LABEL, PaulPrediction.KIND_OFFICIAL)
        .ifPresent(
            existing -> {
              predictions.delete(existing);
              predictions.flush();
            });
    predictions.save(official);
    return true;
  }

  private PaulPrediction official(
      String oracleKey, String provider, Long matchId, int s1, int s2, BigDecimal conf,
      String reasoning, String source, Long pwid) {
    return new PaulPrediction(
        oracleKey, matchId, provider, ENSEMBLE_MODEL_LABEL, PaulPrediction.KIND_OFFICIAL,
        s1, s2, conf, reasoning, "es", source, pwid);
  }
```

Leave `officialAdvancing`, `systemPrompt`, `candidatePrompt`, `clamp` unchanged.

- [ ] **Step 3b: Fix the existing `PaulEnsembleServiceIT` caller (signature changed)**

`PaulEnsembleServiceIT` (from the knockout feature) calls the package-private
`service.synthesizeForMatch(KO_MATCH)`, which is now `synthesizeForMatch(Oracle bot, Long matchId)`.
Update that test: autowire `PaulProperties props;` and pass Paul's oracle, e.g. change
`service.synthesizeForMatch(KO_MATCH)` to `service.synthesizeForMatch(props.allOracles().get(0), KO_MATCH)`
(index 0 is always `paul`). Apply to every `synthesizeForMatch(...)` call in that file.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=MultiOracleIT test`
Expected: PASS (both methods). Then `./mvnw -q -Dtest=PaulEnsembleServiceIT test` to confirm Paul's judge path is unchanged.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulEnsembleService.java \
        backend/src/test/java/io/quiniela/api/paul/MultiOracleIT.java
git commit -m "feat(paul): synthesize per oracle (single-model promote, ensemble judge)"
```

---

### Task 5: Generalize reveal to all oracles

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/paul/PaulService.java`
- Test: `backend/src/test/java/io/quiniela/api/paul/MultiOracleIT.java` (add a method)

**Interfaces:**
- Consumes: `PaulProperties.allOracles()`, `Oracle.googleSub()/key()` (Task 1); `findByOracleAndKind` (Task 2).
- Produces: `reveal()` snapshots each oracle's officials into that oracle's own quiniela; `PaulService` gains a `PaulProperties props` dependency.

- [ ] **Step 1: Add the failing test**

In `MultiOracleIT.java` add:

```java
  @Test
  void revealCreatesBetsForEachOracleQuiniela() {
    predictionService.generateOpen();
    ensembleService.synthesizeOpen();
    paulService.reveal();

    Integer ottoBets =
        jdbc.queryForObject(
            "SELECT count(*) FROM bet b JOIN quiniela q ON q.id=b.quiniela_id "
                + "JOIN users u ON u.id=q.user_id "
                + "WHERE u.google_sub='otto-bot-oracle' AND b.match_id=?",
            Integer.class,
            KO_MATCH);
    assertThat(ottoBets).isEqualTo(1);
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q -Dtest=MultiOracleIT#revealCreatesBetsForEachOracleQuiniela test`
Expected: FAIL — reveal only snapshots Paul; Otto's quiniela has no bet (and `reveal` doesn't yet accept `PaulProperties`).

- [ ] **Step 3: Inject `PaulProperties` and loop oracles in `reveal`**

In `PaulService.java`: add `import io.quiniela.api.paul.Oracle;` is unnecessary (same package). Add the field + constructor param, and replace `reveal()`:

```java
  private final PaulProperties props;
```

Add `PaulProperties props` as the last constructor parameter and assign `this.props = props;`. Then:

```java
  @Transactional
  public RevealResult reveal() {
    int created = 0;
    for (Oracle bot : props.allOracles()) {
      User u =
          users
              .findByGoogleSub(bot.googleSub())
              .orElseThrow(
                  () -> new IllegalStateException("Bot user not seeded: " + bot.googleSub()));
      Quiniela q =
          quinielas
              .findByPoolIdAndUserId(DEFAULT_POOL_ID, u.getId())
              .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, u.getId())));

      Set<Long> already = new HashSet<>();
      bets.findByQuinielaId(q.getId()).forEach(b -> already.add(b.getMatchId()));

      for (PaulPrediction official :
          predictions.findByOracleAndKind(bot.key(), PaulPrediction.KIND_OFFICIAL)) {
        if (already.contains(official.getMatchId())) continue;
        Bet bet =
            new Bet(q.getId(), official.getMatchId(), official.getScoreT1(), official.getScoreT2());
        bet.setPredictedWinnerId(official.getPredictedWinnerId());
        bets.save(bet);
        created++;
      }
    }
    return new RevealResult(created);
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=MultiOracleIT test`
Expected: PASS (all three). Then `./mvnw -q -Dtest=PaulRevealIT test` to confirm Paul's reveal still works (default config → only the `paul` oracle, behavior unchanged).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/paul/PaulService.java \
        backend/src/test/java/io/quiniela/api/paul/MultiOracleIT.java
git commit -m "feat(paul): reveal each oracle into its own quiniela"
```

---

### Task 6: Production config — Otto & Chitara oracles

**Files:**
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:** none (config only).

- [ ] **Step 1: Add the oracle entries**

In `application.yml`, inside the `paul:` block (after `ensemble-model:`), add:

```yaml
    # Extra single-model oracle bots (compete for fun, never money — is_bot users).
    # Each entry has no ensemble-model => single model, no judge (its lone candidate
    # is promoted to the official pick). DeepSeek/Llama route via the Vertex
    # OpenAI-compatible endpoint (RoutingPaulOracle), same as gpt-oss/qwen.
    oracles:
      - key: otto
        google-sub: otto-bot-oracle
        display-name: "Otto la Nutria 🦦"
        models: ["${APP_PAUL_OTTO_MODEL:deepseek:deepseek-ai/deepseek-v3.1}"]
      - key: chitara
        google-sub: chitara-bot-oracle
        display-name: "Chitara la Leoparda 🐆"
        models: ["${APP_PAUL_CHITARA_MODEL:meta:meta/llama-4-maverick-17b-128e-instruct-maas}"]
```

(The exact DeepSeek/Llama ids are overridable by env so they can be corrected after the availability check in the rollout without a redeploy.)

- [ ] **Step 2: Verify the app still boots / binds**

Run: `cd backend && ./mvnw -q -Dtest=PaulPredictionServiceIT test`
Expected: PASS — the prod `application.yml` change does not affect the test profile (test config has its own `application.yml`), but this confirms nothing in the shared context broke.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "feat(paul): configure Otto (DeepSeek) and Chitara (Llama) oracles"
```

---

### Task 7: Frontend — replace the leaderboard banner copy

**Files:**
- Modify: `frontend/app/ranking/page.tsx`
- Modify: `frontend/messages/es-CO.json`
- Modify: `frontend/messages/en.json`

**Interfaces:** none (UI copy). Copy-only change — no new test (the ranking page is a server component; verification is typecheck + lint + the existing frontend suite).

> The banner is currently a hardcoded string at `frontend/app/ranking/page.tsx` rendered when `ranking.entries.some((e) => e.isBot)`. Move it to an i18n key; the trigger is unchanged (still true with more bots).

- [ ] **Step 1: Add the message keys**

In `messages/es-CO.json`, inside the `"ranking"` object, add after `"liveBanner"`:

```json
    "botsAnnouncement": "🐙 Paul notó que dos competidores se quedaron en blanco y llamó a sus amigos Otto la Nutria 🦦 y Chitara la Leoparda 🐆 para tomar sus dos puestos."
```

In `messages/en.json`, inside `"ranking"`, add after `"liveBanner"`:

```json
    "botsAnnouncement": "🐙 Paul noticed two players never showed up, so he called his friends Otto the Otter 🦦 and Chitara the Leopard 🐆 to take their two spots."
```

(Ensure correct comma placement — add a trailing comma to the previous `liveBanner` line.)

- [ ] **Step 2: Use the key in the page**

In `frontend/app/ranking/page.tsx`, replace the hardcoded banner line:

```tsx
                🐙 ¡Pulpo Paul decidió jugar! Compite por la gloria, no por el premio.
```

with:

```tsx
                {t("botsAnnouncement")}
```

(`t` is already the `ranking` translator in this file.)

- [ ] **Step 3: Verify typecheck + lint + tests**

Run: `cd frontend && pnpm typecheck && pnpm lint && pnpm test -- ranking`
Expected: PASS (typecheck clean; lint clean; any ranking tests pass). If no ranking page test exists, this just confirms nothing broke.

- [ ] **Step 4: Commit**

```bash
git add frontend/app/ranking/page.tsx frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(ranking): announce Otto & Chitara in the leaderboard banner"
```

---

### Task 8: One-off prod data op — delete Miguel & Daniel

**Files:**
- Create: `docs/ops/2026-06-28-delete-miguel-daniel.sql`

**Interfaces:** none. This is a **manual, gated production operation** — not run by CI, not a Flyway migration. It is committed for review/audit and executed by hand via the Cloud SQL proxy.

- [ ] **Step 1: Write the reviewed script**

Create `docs/ops/2026-06-28-delete-miguel-daniel.sql`:

```sql
-- One-off prod data op (run manually via the Cloud SQL proxy as quiniela_app).
-- Removes two abandoned competitors so Otto & Chitara take their spots.
-- Blast radius verified 2026-06-28: no invitees reference them, 0 payments, 0 bets,
-- captain is a role (not an FK). DRY-RUN FIRST: run with ROLLBACK, check counts, then COMMIT.
--
--   Miguel Angel Tona Gené  id 13  (captain)  migueltona@gmail.com
--   Daniel                  id 24  (player)   daniel.art.diaz05@gmail.com

BEGIN;

DELETE FROM bet            WHERE quiniela_id IN (SELECT id FROM quiniela WHERE user_id IN (13,24));
DELETE FROM quiniela       WHERE user_id IN (13,24);
DELETE FROM pool_membership WHERE user_id IN (13,24);
DELETE FROM users          WHERE id IN (13,24);

-- Sanity: expect 0 rows remaining for both ids across all of these.
SELECT 'users' t, count(*) FROM users WHERE id IN (13,24)
UNION ALL SELECT 'quiniela', count(*) FROM quiniela WHERE user_id IN (13,24)
UNION ALL SELECT 'pool_membership', count(*) FROM pool_membership WHERE user_id IN (13,24);

-- ROLLBACK;   -- use this on the dry run
-- COMMIT;     -- use this once the dry run looks correct
```

- [ ] **Step 2: Commit the script (execution happens in the rollout, not here)**

```bash
git add docs/ops/2026-06-28-delete-miguel-daniel.sql
git commit -m "ops: reviewed script to delete two abandoned accounts (Miguel, Daniel)"
```

---

### Task 9: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend suite**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS — all tests green, including `PaulPropertiesTest`, `PaulPredictionRepositoryIT`, `MultiOracleIT`, and the unchanged Paul suites (`PaulPredictionServiceIT`, `PaulEnsembleServiceIT`, `PaulServiceCachedIT`, `PaulRevealIT`) plus migration/schema ITs (V024/V025 apply).

- [ ] **Step 2: Backend format check**

Run: `cd backend && ./mvnw -q spotless:check`
Expected: BUILD SUCCESS (run `./mvnw spotless:apply` + re-commit if it fails).

- [ ] **Step 3: Frontend checks**

Run: `cd frontend && pnpm test && pnpm typecheck && pnpm lint`
Expected: PASS.

- [ ] **Step 4: Final status**

```bash
git status   # expect clean working tree if Tasks 1-8 committed cleanly
```

---

## Notes for the implementer / rollout

- **Paul is unchanged.** `V024` backfills existing rows to `oracle='paul'`; with no extra oracles configured the pipeline behaves exactly as today. The avatars are purely additive.
- **Money:** Otto & Chitara are `is_bot=TRUE`, so `AdminPaymentService` (`WHERE u.role <> 'admin' AND u.is_bot = false`) already excludes them — no prize-split changes.
- **Rollout order (after merge + deploy):**
  1. Verify DeepSeek & Llama are enabled in Vertex Model Garden with a one-shot OpenAI-compat chat-completions call to each model id; if one isn't enabled, set `APP_PAUL_OTTO_MODEL` / `APP_PAUL_CHITARA_MODEL` to a Mistral id (`mistralai/mistral-large-...`) and redeploy/restart.
  2. Run `docs/ops/2026-06-28-delete-miguel-daniel.sql` (dry-run with `ROLLBACK`, verify counts, then `COMMIT`).
  3. From the admin panel: **Generate → Synthesize → Reveal** (now covers Paul + Otto + Chitara) for R32 **before its matches kick off** (knockout deadline 2026-06-28 19:00 UTC; reveal bypasses the lock but do it before kickoffs for fairness).
- **Prompt personality:** the generation/judge prompts still say "Pulpo Paul"; the avatars differ by *model*, not prompt voice. Personalizing per-oracle prompt voice is deliberately out of scope (future polish).

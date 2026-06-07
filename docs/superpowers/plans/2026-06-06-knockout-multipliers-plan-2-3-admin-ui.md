# Knockout Multipliers — Plan 2+3: Admin API + UI + Scoring Page

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin view and edit the per-round knockout multipliers through the app, and explain the configured multipliers on the public scoring page. (Plan 1 already made the scoring engine read `round.points_multiplier`; this exposes it.)

**Architecture:** A new `AdminGuard`-protected service + controller (mirroring `AdminPoolConfigService`/`AdminPoolConfigController`) reads/writes `round.points_multiplier` via a new field on the `Round` JPA entity. `PublicSummaryController` exposes the knockout multipliers for the scoring page. The frontend adds a `RoundMultiplierPanel` to the existing `/admin/config` page and renders the multipliers on `/scoring`.

**Tech Stack:** Spring Boot 4 + Java 25 + Maven (Testcontainers ITs, Docker available) on the backend; Next.js 16 + React 19 + TypeScript + next-intl + Vitest on the frontend. Backend commands from `.worktrees/knockout-mult-ui/backend`; frontend from `.worktrees/knockout-mult-ui/frontend`.

**Depends on Plan 1** (already merged to master `d37f82f`): the `round.points_multiplier` column exists (GROUP=1, knockouts=2, `CHECK ≥ 1`).

---

## File Structure

**Backend — create**
- `backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierService.java`
- `backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierController.java`
- `backend/src/test/java/io/quiniela/api/admin/AdminRoundMultiplierControllerIT.java`
- `backend/src/test/java/io/quiniela/api/match/RoundMultiplierMappingIT.java`

**Backend — modify**
- `backend/src/main/java/io/quiniela/api/match/Round.java` — add `pointsMultiplier` field + accessors
- `backend/src/main/java/io/quiniela/api/tournament/PublicSummaryController.java` — expose `roundMultipliers`

**Frontend — create**
- `frontend/lib/api/round-multipliers.ts` — admin API client + types
- `frontend/components/admin/RoundMultiplierPanel.tsx` (+ `.test.tsx`)

**Frontend — modify**
- `frontend/lib/api/summary.ts` — add `roundMultipliers` to `PublicSummary`
- `frontend/app/admin/config/actions.ts` — add `saveRoundMultipliersAction`
- `frontend/app/admin/config/page.tsx` — fetch + render the panel
- `frontend/app/scoring/page.tsx` — render the configured multipliers
- `frontend/messages/es-CO.json`, `frontend/messages/en.json` — new copy

---

## Task 1: `Round` entity carries the multiplier

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/match/Round.java`
- Create: `backend/src/test/java/io/quiniela/api/match/RoundMultiplierMappingIT.java`

- [ ] **Step 1: Write the failing mapping test**

Create `backend/src/test/java/io/quiniela/api/match/RoundMultiplierMappingIT.java`:

```java
package io.quiniela.api.match;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RoundMultiplierMappingIT extends AbstractIntegrationTest {

  @Autowired RoundRepository rounds;

  @Test
  void readsSeededMultipliers() {
    int group = rounds.findByTournamentIdAndCode(1L, "GROUP").orElseThrow().getPointsMultiplier();
    int r32 = rounds.findByTournamentIdAndCode(1L, "R32").orElseThrow().getPointsMultiplier();
    assertThat(group).isEqualTo(1);
    assertThat(r32).isEqualTo(2);
  }
}
```

- [ ] **Step 2: Run it, expect FAIL** (no `getPointsMultiplier`)

Run: `./mvnw -q -Dtest=RoundMultiplierMappingIT test`
Expected: FAIL — compile error, method `getPointsMultiplier()` undefined.

- [ ] **Step 3: Add the field + accessors**

In `backend/src/main/java/io/quiniela/api/match/Round.java`, add the field after the existing `sequence` field (`private Integer sequence;`):

```java
  @Column(name = "points_multiplier", nullable = false)
  private Integer pointsMultiplier;
```

And add accessors after `getSequence()`:

```java
  public Integer getPointsMultiplier() {
    return pointsMultiplier;
  }

  public void setPointsMultiplier(Integer pointsMultiplier) {
    this.pointsMultiplier = pointsMultiplier;
  }
```

- [ ] **Step 4: Run it, expect PASS**

Run: `./mvnw -q -Dtest=RoundMultiplierMappingIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/match/Round.java backend/src/test/java/io/quiniela/api/match/RoundMultiplierMappingIT.java
git commit -m "feat(round): map points_multiplier on the Round entity"
```

---

## Task 2: Admin service + controller (read/write multipliers)

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierService.java`
- Create: `backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierController.java`
- Create: `backend/src/test/java/io/quiniela/api/admin/AdminRoundMultiplierControllerIT.java`

TDD via the controller IT (mirrors `AdminPoolConfigControllerIT`: MockMvc + `JwtService.issue` + admin/captain users + `@AfterEach` restore of mutated reference data).

- [ ] **Step 1: Write the failing IT**

Create `backend/src/test/java/io/quiniela/api/admin/AdminRoundMultiplierControllerIT.java`:

```java
package io.quiniela.api.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

class AdminRoundMultiplierControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  User admin;
  User captain;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    admin = saveUser("rm-adm", "RM Admin", UserRole.ADMIN);
    captain = saveUser("rm-cap", "RM Captain", UserRole.CAPTAIN);
  }

  // `round` is seeded reference data not reset by cleanWritableTables; update tests mutate it.
  @AfterEach
  void restoreSeededMultipliers() {
    new JdbcTemplate(dataSource)
        .update("UPDATE round SET points_multiplier = CASE WHEN code = 'GROUP' THEN 1 ELSE 2 END");
  }

  private User saveUser(String slug, String name, UserRole role) {
    var u = new User("g-" + slug, slug + "@example.com", name, null, role);
    u.setInvitePath(slug);
    return users.save(u);
  }

  @Test
  void getRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/admin/round-multipliers")).andExpect(status().isUnauthorized());
  }

  @Test
  void getRequiresAdmin() throws Exception {
    String token = jwt.issue(captain);
    mockMvc
        .perform(get("/api/admin/round-multipliers").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void getReturnsKnockoutRoundsOnly() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(get("/api/admin/round-multipliers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        // 6 knockout rounds (R32, R16, QF, SF, THIRD_PLACE, FINAL); GROUP excluded.
        .andExpect(jsonPath("$.rounds.length()").value(6))
        .andExpect(jsonPath("$.rounds[0].code").value("R32"))
        .andExpect(jsonPath("$.rounds[0].multiplier").value(2));
  }

  @Test
  void updateRequiresAdmin() throws Exception {
    String token = jwt.issue(captain);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":[{\"code\":\"R32\",\"multiplier\":3}]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminUpdatesMultipliers() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rounds\":[{\"code\":\"R16\",\"multiplier\":3},"
                        + "{\"code\":\"FINAL\",\"multiplier\":5}]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/admin/round-multipliers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds[?(@.code == 'R16')].multiplier").value(3))
        .andExpect(jsonPath("$.rounds[?(@.code == 'FINAL')].multiplier").value(5));
  }

  @Test
  void rejectsMultiplierBelowOne() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":[{\"code\":\"R32\",\"multiplier\":0}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsEditingTheGroupStage() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":[{\"code\":\"GROUP\",\"multiplier\":2}]}"))
        .andExpect(status().isBadRequest());
  }
}
```

- [ ] **Step 2: Run it, expect FAIL** (no endpoint)

Run: `./mvnw -q -Dtest=AdminRoundMultiplierControllerIT test`
Expected: FAIL — 404s / context has no such controller.

- [ ] **Step 3: Implement the service**

Create `backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierService.java`:

```java
package io.quiniela.api.admin;

import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.user.AdminGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only per-round score multipliers. Reads/writes {@code round.points_multiplier} (the column
 * the scoring engine already honours — see Plan 1). Only knockout rounds are editable; the group
 * stage is fixed at 1. Role check uses the shared {@link AdminGuard}.
 *
 * <p>Changing a multiplier does NOT retroactively rescore: the DB trigger recomputes points only on
 * match-result updates. Admins must set values before the first knockout match.
 */
@Service
public class AdminRoundMultiplierService {

  private static final Long ACTIVE_TOURNAMENT_ID = 1L;
  private static final String GROUP_CODE = "GROUP";
  static final int MAX_MULTIPLIER = 10;

  private final RoundRepository rounds;
  private final AdminGuard adminGuard;

  public AdminRoundMultiplierService(RoundRepository rounds, AdminGuard adminGuard) {
    this.rounds = rounds;
    this.adminGuard = adminGuard;
  }

  public record RoundMultiplierRow(String code, String name, int multiplier) {}

  public record MultipliersView(List<RoundMultiplierRow> rounds) {}

  public record UpdateRow(String code, int multiplier) {}

  public record UpdateRequest(List<UpdateRow> rounds) {}

  @Transactional(readOnly = true)
  public MultipliersView getMultipliers(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return view();
  }

  @Transactional
  public MultipliersView updateMultipliers(Long callerId, UpdateRequest req) {
    adminGuard.requireAdmin(callerId);
    if (req == null || req.rounds() == null || req.rounds().isEmpty()) {
      throw new IllegalArgumentException("rounds must not be empty");
    }
    for (UpdateRow row : req.rounds()) {
      if (row.multiplier() < 1 || row.multiplier() > MAX_MULTIPLIER) {
        throw new IllegalArgumentException(
            "multiplier for " + row.code() + " must be between 1 and " + MAX_MULTIPLIER);
      }
      Round round =
          rounds
              .findByTournamentIdAndCode(ACTIVE_TOURNAMENT_ID, row.code())
              .orElseThrow(() -> new IllegalArgumentException("unknown round " + row.code()));
      if (GROUP_CODE.equals(round.getCode())) {
        throw new IllegalArgumentException("the group stage multiplier is fixed at 1");
      }
      round.setPointsMultiplier(row.multiplier());
      rounds.save(round);
    }
    return view();
  }

  private MultipliersView view() {
    List<RoundMultiplierRow> rows =
        rounds.findByTournamentIdOrderBySequenceAsc(ACTIVE_TOURNAMENT_ID).stream()
            .filter(r -> !GROUP_CODE.equals(r.getCode()))
            .map(r -> new RoundMultiplierRow(r.getCode(), r.getName(), r.getPointsMultiplier()))
            .toList();
    return new MultipliersView(rows);
  }
}
```

- [ ] **Step 4: Implement the controller**

Create `backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierController.java`:

```java
package io.quiniela.api.admin;

import io.quiniela.api.admin.AdminRoundMultiplierService.MultipliersView;
import io.quiniela.api.admin.AdminRoundMultiplierService.UpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only per-round score multipliers (knockout rounds). */
@RestController
@RequestMapping("/api/admin/round-multipliers")
public class AdminRoundMultiplierController {

  private final AdminRoundMultiplierService service;

  public AdminRoundMultiplierController(AdminRoundMultiplierService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<MultipliersView> get(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getMultipliers(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping
  public ResponseEntity<MultipliersView> update(
      @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.updateMultipliers(Long.parseLong(jwt.getSubject()), req));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
```

- [ ] **Step 5: Run the IT, expect PASS**

Run: `./mvnw -q -Dtest=AdminRoundMultiplierControllerIT test`
Expected: PASS (7 tests). If a `jsonPath` filter assertion errors, confirm the JsonPath syntax matches the project's version (the existing `AdminPoolConfigControllerIT` uses simple paths; the `[?(@.code == 'R16')]` filter is standard Jayway JsonPath bundled with spring-test).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierService.java backend/src/main/java/io/quiniela/api/admin/AdminRoundMultiplierController.java backend/src/test/java/io/quiniela/api/admin/AdminRoundMultiplierControllerIT.java
git commit -m "feat(admin): read/write per-round score multipliers"
```

---

## Task 3: Public summary exposes the multipliers

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/tournament/PublicSummaryController.java`

The scoring page (everyone, not just admins) needs the configured multipliers. Add them to the existing public summary.

- [ ] **Step 1: Add a failing assertion to an existing summary IT**

Find the public-summary IT: run `ls backend/src/test/java/io/quiniela/api/tournament/`. It should contain a `PublicSummary*IT.java`. Open it and add this test method (it already has `mockMvc` + a public GET on `/api/public/summary`; mirror the existing test's request style — no auth needed):

```java
  @Test
  void summaryIncludesKnockoutMultipliers() throws Exception {
    mockMvc
        .perform(get("/api/public/summary"))
        .andExpect(status().isOk())
        // 6 knockout rounds, group excluded; seeded ×2.
        .andExpect(jsonPath("$.roundMultipliers.length()").value(6))
        .andExpect(jsonPath("$.roundMultipliers[0].code").value("R32"))
        .andExpect(jsonPath("$.roundMultipliers[0].multiplier").value(2));
  }
```

> If no `PublicSummary*IT.java` exists, create `backend/src/test/java/io/quiniela/api/tournament/PublicSummaryMultiplierIT.java` extending `AbstractIntegrationTest` with the MockMvc setup from `AdminPoolConfigControllerIT` (the `@BeforeEach` `mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();` plus the static imports for `get`, `status`, `jsonPath`) and put the test above in it.

- [ ] **Step 2: Run it, expect FAIL** (no `roundMultipliers` field)

Run: `./mvnw -q -Dtest='PublicSummary*IT' test`
Expected: FAIL — `roundMultipliers` path missing.

- [ ] **Step 3: Add `roundMultipliers` to the controller**

In `PublicSummaryController.java`:

(a) Add a record next to the other records (after `PrizeSplitEntry`):

```java
  public record RoundMultiplier(String code, String name, int multiplier) {}
```

(b) Add the field to `SummaryResponse` (after `prizeSplit`):

```java
  public record SummaryResponse(
      TournamentSummary tournament,
      PoolSummary pool,
      List<PrizeSplitEntry> prizeSplit,
      List<RoundMultiplier> roundMultipliers,
      boolean testMode) {}
```

(c) In `get()`, build the list from the rounds (the `rounds` repository is already injected) just before the `return`:

```java
    List<RoundMultiplier> roundMultipliers =
        rounds.findByTournamentIdOrderBySequenceAsc(tournament.getId()).stream()
            .filter(r -> !GROUP_STAGE_CODE.equals(r.getCode()))
            .map(r -> new RoundMultiplier(r.getCode(), r.getName(), r.getPointsMultiplier()))
            .toList();
```

(d) Pass it into the `new SummaryResponse(...)` — add `roundMultipliers` as the 4th arg, before `tournament.isTestMode()`:

```java
            prizeSplit,
            roundMultipliers,
            tournament.isTestMode()));
```

- [ ] **Step 4: Run it, expect PASS**

Run: `./mvnw -q -Dtest='PublicSummary*IT' test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/tournament/PublicSummaryController.java backend/src/test/java/io/quiniela/api/tournament/
git commit -m "feat(summary): expose per-round knockout multipliers"
```

---

## Task 4: Backend verify

**Files:** none (gate only).

- [ ] **Step 1:** Run `./mvnw -q verify` → BUILD SUCCESS. If Spotless fails, `./mvnw -q spotless:apply` then re-run. (Adding the `roundMultipliers` field to `SummaryResponse` may break a frontend fallback contract but no backend caller — confirm no other Java constructs `SummaryResponse`.)
- [ ] **Step 2:** `git add -A && git commit -m "chore: backend verify fixups" || echo clean`

---

## Task 5: Frontend API client + summary type

**Files:**
- Create: `frontend/lib/api/round-multipliers.ts`
- Modify: `frontend/lib/api/summary.ts`

- [ ] **Step 1: Create the admin API client**

Create `frontend/lib/api/round-multipliers.ts`:

```ts
import { api } from "./client";

export type RoundMultiplierRow = { code: string; name: string; multiplier: number };

export type RoundMultipliers = { rounds: RoundMultiplierRow[] };

export async function getRoundMultipliers(): Promise<RoundMultipliers> {
  return api<RoundMultipliers>("/api/admin/round-multipliers");
}

export async function updateRoundMultipliers(
  input: { rounds: { code: string; multiplier: number }[] },
): Promise<RoundMultipliers> {
  return api<RoundMultipliers>("/api/admin/round-multipliers", {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
```

- [ ] **Step 2: Add `roundMultipliers` to the public summary type**

In `frontend/lib/api/summary.ts`, add to the `PublicSummary` type (after `prizeSplit: PrizeSplitEntry[];`):

```ts
  roundMultipliers: { code: string; name: string; multiplier: number }[];
```

And in the `FALLBACK` constant (used when the backend is unreachable), add the field so the shape stays valid — set it right after the `prizeSplit: [...]` array:

```ts
  roundMultipliers: [
    { code: "R32", name: "Dieciseisavos", multiplier: 2 },
    { code: "R16", name: "Octavos", multiplier: 2 },
    { code: "QF", name: "Cuartos", multiplier: 2 },
    { code: "SF", name: "Semifinales", multiplier: 2 },
    { code: "THIRD_PLACE", name: "Tercer puesto", multiplier: 2 },
    { code: "FINAL", name: "Final", multiplier: 2 },
  ],
```

- [ ] **Step 3: Typecheck**

Run (from `frontend/`): `pnpm typecheck`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/api/round-multipliers.ts frontend/lib/api/summary.ts
git commit -m "feat(api): round-multipliers client + summary type"
```

---

## Task 6: i18n copy

**Files:**
- Modify: `frontend/messages/es-CO.json`, `frontend/messages/en.json`

- [ ] **Step 1: Add a `roundMultipliers` namespace + scoring keys to `es-CO.json`**

Add a new top-level `"roundMultipliers"` object (sibling to `"moneyConfig"`):

```json
  "roundMultipliers": {
    "title": "Multiplicadores por ronda",
    "intro": "Cada ronda de eliminatorias multiplica los puntos del partido. Déjalos listos antes del primer partido de eliminatorias — no se recalculan partidos ya jugados.",
    "lockWarning": "⚠️ Fija los multiplicadores ANTES del primer partido de eliminatorias. Cambiarlos después no recalcula partidos ya puntuados.",
    "multiplierLabel": "Multiplicador (×)",
    "save": "Guardar multiplicadores",
    "saved": "Guardado",
    "rangeError": "Cada multiplicador debe estar entre 1 y 10."
  },
```

And add these keys inside the existing `"scoring"` object:

```json
    "multipliersTitle": "Multiplicadores de eliminatorias",
    "multipliersIntro": "Los puntos de cada partido se multiplican según la ronda:",
    "multiplierRow": "{name}: ×{n}"
```

- [ ] **Step 2: Add the parallel keys to `en.json`**

New `"roundMultipliers"` object:

```json
  "roundMultipliers": {
    "title": "Per-round multipliers",
    "intro": "Each knockout round multiplies the match points. Set them before the first knockout match — already-played matches are not recalculated.",
    "lockWarning": "⚠️ Lock the multipliers BEFORE the first knockout match. Changing them later does not re-score already-played matches.",
    "multiplierLabel": "Multiplier (×)",
    "save": "Save multipliers",
    "saved": "Saved",
    "rangeError": "Each multiplier must be between 1 and 10."
  },
```

Inside the existing `"scoring"` object in `en.json`:

```json
    "multipliersTitle": "Knockout multipliers",
    "multipliersIntro": "Each match's points are multiplied by the round:",
    "multiplierRow": "{name}: ×{n}"
```

> Watch the trailing commas: the key before each inserted block needs one; the last key in each object must not.

- [ ] **Step 3: Verify JSON validity**

Run (from `frontend/`):
```bash
node -e "const e=require('./messages/es-CO.json'),n=require('./messages/en.json'); for(const m of [e,n]){ if(!m.roundMultipliers||!m.roundMultipliers.save||!m.scoring.multipliersTitle||!m.scoring.multiplierRow) throw new Error('missing keys'); } console.log('ok');"
```
Expected: prints `ok`.

- [ ] **Step 4: Commit**

```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "i18n: per-round multiplier admin + scoring copy"
```

---

## Task 7: `RoundMultiplierPanel` component

**Files:**
- Create: `frontend/components/admin/RoundMultiplierPanel.tsx`
- Create: `frontend/components/admin/RoundMultiplierPanel.test.tsx`

Mirrors `PoolConfigPanel` (client component, `useTransition`, local state, save action) but simpler — a row of integer inputs.

- [ ] **Step 1: Write the failing test**

Create `frontend/components/admin/RoundMultiplierPanel.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { RoundMultiplierPanel } from "./RoundMultiplierPanel";

const messages = {
  roundMultipliers: {
    title: "Per-round multipliers",
    intro: "intro",
    lockWarning: "Lock before knockouts",
    multiplierLabel: "Multiplier",
    save: "Save multipliers",
    saved: "Saved",
    rangeError: "Each multiplier must be between 1 and 10.",
  },
};

const rounds = [
  { code: "R32", name: "Dieciseisavos", multiplier: 2 },
  { code: "FINAL", name: "Final", multiplier: 2 },
];

function renderPanel(saveAction = vi.fn().mockResolvedValue({ ok: true })) {
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <RoundMultiplierPanel rounds={rounds} saveAction={saveAction} />
    </NextIntlClientProvider>,
  );
  return saveAction;
}

describe("RoundMultiplierPanel", () => {
  it("renders an input per round and the lock warning", () => {
    renderPanel();
    expect(screen.getByText(/lock before knockouts/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/dieciseisavos/i)).toHaveValue(2);
    expect(screen.getByLabelText(/final/i)).toHaveValue(2);
  });

  it("saves the edited multipliers by code", async () => {
    const save = renderPanel();
    const final = screen.getByLabelText(/final/i);
    await userEvent.clear(final);
    await userEvent.type(final, "5");
    await userEvent.click(screen.getByRole("button", { name: /save multipliers/i }));
    expect(save).toHaveBeenCalledWith({
      rounds: [
        { code: "R32", multiplier: 2 },
        { code: "FINAL", multiplier: 5 },
      ],
    });
  });

  it("disables save when a value is out of range", async () => {
    renderPanel();
    const r32 = screen.getByLabelText(/dieciseisavos/i);
    await userEvent.clear(r32);
    await userEvent.type(r32, "0");
    expect(screen.getByRole("button", { name: /save multipliers/i })).toBeDisabled();
  });
}); 
```

- [ ] **Step 2: Run it, expect FAIL** (module missing)

Run (from `frontend/`): `pnpm vitest run components/admin/RoundMultiplierPanel.test.tsx`
Expected: FAIL — cannot resolve `./RoundMultiplierPanel`.

- [ ] **Step 3: Implement the panel**

Create `frontend/components/admin/RoundMultiplierPanel.tsx`:

```tsx
"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { RoundMultiplierRow } from "@/lib/api/round-multipliers";
import type { SaveResult } from "@/app/admin/config/actions";

type Props = {
  rounds: RoundMultiplierRow[];
  saveAction: (input: {
    rounds: { code: string; multiplier: number }[];
  }) => Promise<SaveResult>;
};

const fieldClass =
  "w-20 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2 text-center font-display text-base font-extrabold text-[var(--color-text-primary)]";
const sectionClass =
  "flex flex-col gap-3 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4";

export function RoundMultiplierPanel({ rounds, saveAction }: Props) {
  const t = useTranslations("roundMultipliers");

  const [values, setValues] = useState<number[]>(rounds.map((r) => r.multiplier));
  const [isPending, startTransition] = useTransition();
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const valid = values.every((v) => Number.isInteger(v) && v >= 1 && v <= 10);

  function setValue(i: number, v: number) {
    setSaved(false);
    setValues((prev) => prev.map((p, idx) => (idx === i ? v : p)));
  }

  function save() {
    setError(null);
    startTransition(async () => {
      const res = await saveAction({
        rounds: rounds.map((r, i) => ({ code: r.code, multiplier: values[i] })),
      });
      if (res.ok) setSaved(true);
      else setError(res.error);
    });
  }

  return (
    <section className={sectionClass}>
      <div className="chrome-label chrome-label-muted">{t("title")}</div>
      <p className="text-xs text-[var(--color-text-muted)]">{t("intro")}</p>
      <p className="text-xs font-semibold text-[var(--color-accent-red)]">{t("lockWarning")}</p>

      <ul className="flex flex-col gap-2">
        {rounds.map((r, i) => (
          <li key={r.code} className="flex items-center justify-between gap-3">
            <label
              htmlFor={`mult-${r.code}`}
              className="font-sans text-sm text-[var(--color-text-primary)]"
            >
              {r.name}
            </label>
            <input
              id={`mult-${r.code}`}
              type="number"
              min={1}
              max={10}
              step={1}
              value={Number.isFinite(values[i]) ? values[i] : ""}
              onChange={(e) => setValue(i, Math.trunc(Number(e.target.value)))}
              className={fieldClass}
            />
          </li>
        ))}
      </ul>

      {!valid && <p className="text-xs text-[var(--color-accent-red)]">{t("rangeError")}</p>}
      {error && <p className="text-xs text-[var(--color-accent-red)]">{error}</p>}

      <button
        type="button"
        onClick={save}
        disabled={!valid || isPending}
        className="bg-[var(--color-accent-red)] px-4 py-3 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-bg-ink)]"
      >
        {saved ? t("saved") : t("save")}
      </button>
    </section>
  );
}
```

- [ ] **Step 4: Run it, expect PASS (3 tests)**

Run (from `frontend/`): `pnpm vitest run components/admin/RoundMultiplierPanel.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/admin/RoundMultiplierPanel.tsx frontend/components/admin/RoundMultiplierPanel.test.tsx
git commit -m "feat(admin): RoundMultiplierPanel component"
```

---

## Task 8: Wire the panel into the admin config page

**Files:**
- Modify: `frontend/app/admin/config/actions.ts`
- Modify: `frontend/app/admin/config/page.tsx`

- [ ] **Step 1: Add the save action**

In `frontend/app/admin/config/actions.ts`, add the import and a new action (the existing `SaveResult` type is reused):

```ts
import { updateRoundMultipliers } from "@/lib/api/round-multipliers";
```

```ts
export async function saveRoundMultipliersAction(input: {
  rounds: { code: string; multiplier: number }[];
}): Promise<SaveResult> {
  try {
    await updateRoundMultipliers(input);
    revalidatePath("/admin/config");
    return { ok: true };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, error: e.message };
    throw e;
  }
}
```

- [ ] **Step 2: Fetch + render the panel on the page**

In `frontend/app/admin/config/page.tsx`, add imports:

```ts
import { getRoundMultipliers } from "@/lib/api/round-multipliers";
import { RoundMultiplierPanel } from "@/components/admin/RoundMultiplierPanel";
import { savePoolConfigAction, saveRoundMultipliersAction } from "./actions";
```

(Replace the existing `import { savePoolConfigAction } from "./actions";` line with the combined one above.)

Fetch both configs (after the existing `const config = await getPoolConfig();`):

```ts
  const multipliers = await getRoundMultipliers();
```

And render the panel below the existing `PoolConfigPanel` (inside the same `<div>`):

```tsx
        <PoolConfigPanel config={config} saveAction={savePoolConfigAction} />
        <div className="mt-4">
          <RoundMultiplierPanel
            rounds={multipliers.rounds}
            saveAction={saveRoundMultipliersAction}
          />
        </div>
```

- [ ] **Step 3: Typecheck + lint**

Run (from `frontend/`): `pnpm typecheck` (exit 0) and `pnpm lint` (0 errors; the pre-existing `layout.tsx` font warning is OK).

- [ ] **Step 4: Commit**

```bash
git add "frontend/app/admin/config/actions.ts" "frontend/app/admin/config/page.tsx"
git commit -m "feat(admin): show the multiplier panel on /admin/config"
```

---

## Task 9: Scoring page shows the configured multipliers

**Files:**
- Modify: `frontend/app/scoring/page.tsx`

Replace the static "knockouts double them" section with the live per-round values.

- [ ] **Step 1: Fetch the summary + render the multipliers**

In `frontend/app/scoring/page.tsx`:

(a) Add the import:
```ts
import { getPublicSummaryOrFallback } from "@/lib/api/summary";
```

(b) After `const t = await getTranslations("scoring");`, fetch the summary:
```ts
  const summary = await getPublicSummaryOrFallback();
```

(c) Replace the existing knockout section:
```tsx
        <section className={sectionClass}>
          <div className={headClass}>{t("knockoutTitle")}</div>
          <p className="font-sans text-sm text-[var(--color-text-primary)]">{t("knockoutBody")}</p>
        </section>
```
with the multiplier-driven version:
```tsx
        <section className={sectionClass}>
          <div className={headClass}>{t("multipliersTitle")}</div>
          <p className="mb-2 font-sans text-sm text-[var(--color-text-primary)]">
            {t("multipliersIntro")}
          </p>
          <ul className="flex flex-col gap-2">
            {summary.roundMultipliers.map((r) => (
              <li key={r.code} className="flex items-baseline justify-between gap-3">
                <span className="font-sans text-sm text-[var(--color-text-primary)]">{r.name}</span>
                <span className="shrink-0 font-display text-base font-extrabold text-[var(--color-accent-red)]">
                  ×{r.multiplier}
                </span>
              </li>
            ))}
          </ul>
        </section>
```

> `getPublicSummaryOrFallback` never throws (returns the static fallback on backend error), so the scoring page can't 500 on a summary outage. The `knockoutTitle`/`knockoutBody` i18n keys become unused — leave them (harmless) or remove them in both locales if lint flags unused keys (it won't; next-intl doesn't).

- [ ] **Step 2: Typecheck + lint**

Run (from `frontend/`): `pnpm typecheck` (exit 0), `pnpm lint` (0 errors).

- [ ] **Step 3: Commit**

```bash
git add "frontend/app/scoring/page.tsx"
git commit -m "feat(scoring): explain the configured per-round multipliers"
```

---

## Task 10: Full verification

**Files:** none (gate only).

- [ ] **Step 1: Frontend** (from `frontend/`): `pnpm vitest run` (all green), `pnpm typecheck` (exit 0), `pnpm lint` (0 errors).
- [ ] **Step 2: Backend** (from `backend/`): `./mvnw -q verify` (BUILD SUCCESS).
- [ ] **Step 3: Manual smoke (optional):** sign in as admin → `/admin/config` → edit a knockout multiplier (e.g. FINAL ×4) → save → reload shows ×4. Visit `/scoring` → the FINAL row shows ×4. Switch locale → labels localize, the ×N values stay.
- [ ] **Step 4:** `git add -A && git commit -m "chore: verification fixups" || echo clean`

---

## Self-Review (completed during planning)

- **Spec coverage:** admin read/write API with `AdminGuard` (Task 2) ✓; `round.points_multiplier` via the entity (Task 1) ✓; public read for the scoring page (Task 3) ✓; admin UI panel alongside money-config (Tasks 7–8) ✓; scoring page explains per-round multipliers, config-driven (Task 9) ✓; lock-before-knockouts warning in the UI (Task 7 panel + i18n) ✓; bilingual copy es-CO+en (Task 6) ✓; group stays fixed at 1 (service rejects editing GROUP; panel lists knockout rounds only) ✓; no retroactive rescore (documented; nothing recomputes on edit) ✓.
- **Placeholder scan:** none — every code step is complete. The one conditional ("if no PublicSummary IT exists, create one") gives the exact fallback file + content.
- **Type consistency:** `RoundMultiplierRow {code,name,multiplier}` is identical across Java (`AdminRoundMultiplierService.RoundMultiplierRow`, `PublicSummaryController.RoundMultiplier`), the TS client (`round-multipliers.ts`), the summary type, the panel props, and the scoring page. The update payload shape `{rounds:[{code,multiplier}]}` matches `UpdateRequest`/`UpdateRow` on the backend and the panel's `saveAction` call + `saveRoundMultipliersAction`. `SaveResult` is reused from the existing money-config action.

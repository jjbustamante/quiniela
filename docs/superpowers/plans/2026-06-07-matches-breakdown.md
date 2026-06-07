# Matches Breakdown + Scorecard Header Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the Matches list, tap the gold `+N PTS` badge to reveal the point breakdown for that match; and on the player scorecard, add a big "whose points" header.

**Architecture:** The Matches API gains a `breakdown` per match (computed in Java via the DB-pinned `ScoreBreakdown`, deriving `pointsEarned` from it — removing the redundant SQL scoring call). A shared `breakdownParts` helper formats the line; `MatchScoreRow` (scorecard) is refactored onto it; `MatchListItem` becomes a client component that toggles the line on the badge. The scorecard page adds a `{name} · {total} pts` header.

**Tech Stack:** Spring Boot 4 + Postgres (Testcontainers ITs, Docker) backend; Next.js 16 + React 19 + TS + Vitest/RTL frontend. Backend from `.worktrees/matches-breakdown/backend`; frontend from `.worktrees/matches-breakdown/frontend`.

---

## File Structure

**Backend — modify**
- `backend/src/main/java/io/quiniela/api/matches/MatchesService.java` — `MatchRow.breakdown`; compute in mapper; derive `pointsEarned`
- `backend/src/test/java/io/quiniela/api/matches/MatchesControllerIT.java` — breakdown assertion

**Frontend — create**
- `frontend/lib/breakdown-format.ts` (+ `.test.ts`) — shared `breakdownParts`

**Frontend — modify**
- `frontend/lib/api/matches.ts` — `MatchView.breakdown`
- `frontend/components/ranking/MatchScoreRow.tsx` — use the shared helper
- `frontend/components/matches/MatchListItem.tsx` (+ `.test.tsx`) — client toggle
- `frontend/components/matches/MatchTabs.tsx` — pass breakdown labels
- `frontend/messages/es-CO.json`, `frontend/messages/en.json` — `scorecard.toggleBreakdown`
- `frontend/app/ranking/[userId]/page.tsx` — player header

---

## Task 1: Backend — `MatchRow.breakdown`

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/matches/MatchesService.java`
- Modify: `backend/src/test/java/io/quiniela/api/matches/MatchesControllerIT.java`

- [ ] **Step 1: Add a failing IT assertion**

In `MatchesControllerIT.java`, add this test (mirrors the file's existing user/bet setup style; match 1 is a seeded GROUP match; `@AfterEach restoreMatch1` already resets it):

```java
  @Test
  void playedBetMatchExposesAPointsBreakdown() throws Exception {
    var u = users.save(new io.quiniela.api.user.User("g-mb", "mb@example.com", "MB", null, io.quiniela.api.user.UserRole.CAPTAIN));
    jdbc.update("INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?)", u.getId());
    Long qid = jdbc.queryForObject("SELECT id FROM quiniela WHERE user_id = ?", Long.class, u.getId());
    jdbc.update("INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2) VALUES (?,1,2,1)", qid);
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = 1");

    String token = jwt.issue(u);
    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        // match 1 (bet 2-1, actual 2-1, group) → outcome 3 + each-team 2 = total 7.
        .andExpect(jsonPath("$..[?(@.id == 1)].breakdown.outcome").value(org.hamcrest.Matchers.hasItem(3)))
        .andExpect(jsonPath("$..[?(@.id == 1)].breakdown.total").value(org.hamcrest.Matchers.hasItem(7)))
        .andExpect(jsonPath("$..[?(@.id == 1)].pointsEarned").value(org.hamcrest.Matchers.hasItem(7)));
  }
```

> The `$..` recursive descent matches the match in whichever bucket (`past`/`today`/`upcoming`) it lands in. `hasItem` because the filtered path yields a list.

- [ ] **Step 2: Run it, expect FAIL** (no `breakdown` field)

Run: `./mvnw -q -Dtest=MatchesControllerIT test`
Expected: FAIL — `breakdown.outcome` path missing.

- [ ] **Step 3: Add `breakdown` to `MatchRow`**

In `MatchesService.java`, add the import:
```java
import io.quiniela.api.scoring.ScoreBreakdown;
```
Add `ScoreBreakdown breakdown` as the last field of the `MatchRow` record:
```java
  public record MatchRow(
      Long id,
      String roundCode,
      String groupCode,
      Instant kickoffAt,
      TeamRef team1,
      TeamRef team2,
      ScorePair score,
      boolean played,
      ScorePair yourPick,
      Integer pointsEarned,
      TeamRef pickWinner,
      TeamRef winner,
      ScoreBreakdown breakdown) {}
```

- [ ] **Step 4: Update the query + mapper**

In the SQL string, remove the `points_earned` CASE expression and add the raw inputs. Replace this block (the `CASE … END AS points_earned`):
```java
              CASE
                WHEN m.played AND b.score_t1 IS NOT NULL THEN
                  score_match_for_bet(
                    r.code <> 'GROUP',
                    b.score_t1, b.score_t2,
                    m.score_t1, m.score_t2,
                    b.predicted_winner_id, m.advanced_team_id,
                    r.points_multiplier)
                ELSE NULL
              END               AS points_earned
```
with:
```java
              b.predicted_winner_id AS pred_winner,
              m.advanced_team_id    AS adv_team,
              r.points_multiplier   AS mult
```
(adjust the trailing comma on the line before so the SELECT list stays valid — the line before this block currently ends `AS bet_t2,`.)

Then in the row-mapper lambda, replace the `pointsEarned` read in the `new MatchRow(...)` call. First, just before `return new MatchRow(`, compute:
```java
              Long predWinner = (Long) rs.getObject("pred_winner");
              Long advTeam = (Long) rs.getObject("adv_team");
              ScoreBreakdown breakdown =
                  (rs.getBoolean("played") && rs.getObject("bet_t1") != null)
                      ? ScoreBreakdown.of(
                          !"GROUP".equals(rs.getString("round_code")),
                          rs.getInt("bet_t1"),
                          rs.getInt("bet_t2"),
                          (Integer) rs.getObject("m_score_t1"),
                          (Integer) rs.getObject("m_score_t2"),
                          predWinner,
                          advTeam,
                          rs.getInt("mult"))
                      : null;
              Integer pointsEarned = breakdown == null ? null : breakdown.total();
```
Then change the `new MatchRow(...)` call: replace `(Integer) rs.getObject("points_earned"),` with `pointsEarned,` and add `breakdown` as the final argument (after `winner`):
```java
                  yourPick,
                  pointsEarned,
                  pickWinner,
                  winner,
                  breakdown);
```

- [ ] **Step 5: Run the IT, expect PASS**

Run: `./mvnw -q -Dtest=MatchesControllerIT test`
Expected: PASS — the new test + all existing matches tests (the derived `pointsEarned` equals the old SQL value, since `ScoreBreakdown.total()` is pinned to `score_match_for_bet`). Run `./mvnw -q spotless:apply` first if formatting is rejected.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/matches/MatchesService.java backend/src/test/java/io/quiniela/api/matches/MatchesControllerIT.java
git commit -m "feat(matches): expose per-match ScoreBreakdown; derive pointsEarned from it"
```

---

## Task 2: Shared `breakdownParts` helper + refactor `MatchScoreRow`

**Files:**
- Create: `frontend/lib/breakdown-format.ts`, `frontend/lib/breakdown-format.test.ts`
- Modify: `frontend/components/ranking/MatchScoreRow.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/lib/breakdown-format.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { breakdownParts } from "./breakdown-format";

const labels = {
  bdOutcome: "resultado",
  bdTeam1: "local",
  bdTeam2: "visitante",
  bdDiff: "diferencia",
  multiplier: (n: number) => `×${n}`,
  pts: (n: number) => `+${n}`,
};

describe("breakdownParts", () => {
  it("lists only the non-zero components, then the multiplier when > 1", () => {
    expect(
      breakdownParts(
        { outcome: 3, team1Exact: 2, team2Exact: 2, goalDiff: 0, multiplier: 2, total: 14 },
        labels,
      ),
    ).toEqual(["resultado +3", "local +2", "visitante +2", "×2"]);
  });

  it("omits the multiplier when it is 1", () => {
    expect(
      breakdownParts(
        { outcome: 3, team1Exact: 0, team2Exact: 0, goalDiff: 1, multiplier: 1, total: 4 },
        labels,
      ),
    ).toEqual(["resultado +3", "diferencia +1"]);
  });

  it("returns [] for an all-zero breakdown", () => {
    expect(
      breakdownParts(
        { outcome: 0, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 1, total: 0 },
        labels,
      ),
    ).toEqual([]);
  });

  it("omits the multiplier when nothing scored (a bare ×N is meaningless)", () => {
    // A 0-point knockout: all components 0, multiplier 3 → no '×3' alone.
    expect(
      breakdownParts(
        { outcome: 0, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 3, total: 0 },
        labels,
      ),
    ).toEqual([]);
  });
});
```

- [ ] **Step 2: Run it, expect FAIL**

Run: `pnpm vitest run lib/breakdown-format.test.ts`

- [ ] **Step 3: Implement**

Create `frontend/lib/breakdown-format.ts`:

```ts
import type { ScoreBreakdown } from "@/lib/api/scorecard";

export type BreakdownLabels = {
  bdOutcome: string;
  bdTeam1: string;
  bdTeam2: string;
  bdDiff: string;
  multiplier: (n: number) => string;
  pts: (n: number) => string;
};

/** The non-zero point components as display strings, then `×mult` when > 1. */
export function breakdownParts(b: ScoreBreakdown, labels: BreakdownLabels): string[] {
  const parts: string[] = [];
  if (b.outcome) parts.push(`${labels.bdOutcome} ${labels.pts(b.outcome)}`);
  if (b.team1Exact) parts.push(`${labels.bdTeam1} ${labels.pts(b.team1Exact)}`);
  if (b.team2Exact) parts.push(`${labels.bdTeam2} ${labels.pts(b.team2Exact)}`);
  if (b.goalDiff) parts.push(`${labels.bdDiff} ${labels.pts(b.goalDiff)}`);
  // Only show the multiplier when something actually scored — a 0-point knockout
  // would otherwise render a lone, meaningless "×3".
  if (parts.length > 0 && b.multiplier > 1) parts.push(labels.multiplier(b.multiplier));
  return parts;
}
```

- [ ] **Step 4: Refactor `MatchScoreRow` onto it**

In `frontend/components/ranking/MatchScoreRow.tsx`, add the import:
```tsx
import { breakdownParts } from "@/lib/breakdown-format";
```
Replace the inline `const parts: string[] = []; … if (b.multiplier > 1) …` block (the six lines building `parts`) with:
```tsx
  const b = match.breakdown;
  const parts = breakdownParts(b, labels);
```
(Leave the rest — the JSX using `parts.length` and `parts.join(" · ")` — unchanged. The component's `Labels` type already has the `bd*` / `multiplier` / `pts` fields the helper needs.)

- [ ] **Step 5: Run tests, expect PASS**

Run: `pnpm vitest run lib/breakdown-format.test.ts components/ranking/MatchScoreRow.test.tsx`
Expected: both green (helper + the unchanged MatchScoreRow behavior).

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/breakdown-format.ts frontend/lib/breakdown-format.test.ts frontend/components/ranking/MatchScoreRow.tsx
git commit -m "refactor(scorecard): shared breakdownParts helper"
```

---

## Task 3: `MatchView.breakdown` + tappable `MatchListItem`

**Files:**
- Modify: `frontend/lib/api/matches.ts`, `frontend/components/matches/MatchListItem.tsx` (+ `.test.tsx`), `frontend/components/matches/MatchTabs.tsx`
- Modify: `frontend/messages/es-CO.json`, `frontend/messages/en.json`

- [ ] **Step 1: Add `breakdown` to the type + i18n key**

In `frontend/lib/api/matches.ts`, add the import + field to `MatchView` (after `pointsEarned`):
```ts
import type { ScoreBreakdown } from "./scorecard";
```
```ts
  /** Per-match point components (caller's own pick). Null when unplayed or no bet. */
  breakdown: ScoreBreakdown | null;
```

In `messages/es-CO.json` `scorecard` object add `"toggleBreakdown": "Ver desglose",` and in `messages/en.json` add `"toggleBreakdown": "Show breakdown",` (keep JSON valid).

- [ ] **Step 2: Update the `MatchListItem` test**

In `frontend/components/matches/MatchListItem.test.tsx`: add `breakdown` to the `match()` factory defaults (after `pointsEarned: 4,`):
```ts
    breakdown: { outcome: 3, team1Exact: 0, team2Exact: 0, goalDiff: 1, multiplier: 1, total: 4 },
```
Add to the `labels` const:
```ts
  toggleBreakdown: "Ver desglose",
  breakdown: {
    bdOutcome: "resultado",
    bdTeam1: "local",
    bdTeam2: "visitante",
    bdDiff: "diferencia",
    multiplier: (n: number) => `×${n}`,
    pts: (n: number) => `+${n}`,
  },
```
Add a new describe block:
```tsx
import userEvent from "@testing-library/user-event";

describe("MatchListItem — breakdown toggle", () => {
  it("reveals the breakdown line on tapping the points badge", async () => {
    render(
      <MatchListItem
        match={match()}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    expect(screen.queryByText(/resultado \+3/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /ver desglose/i }));
    expect(screen.getByText(/resultado \+3/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /ver desglose/i }));
    expect(screen.queryByText(/resultado \+3/)).not.toBeInTheDocument();
  });

  it("renders no badge when there are no points", () => {
    render(
      <MatchListItem
        match={match({ pointsEarned: null, breakdown: null })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    expect(screen.queryByRole("button", { name: /ver desglose/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Run it, expect FAIL**

Run: `pnpm vitest run components/matches/MatchListItem.test.tsx`
Expected: FAIL — no toggle button / breakdown line.

- [ ] **Step 4: Make `MatchListItem` a client toggle**

In `frontend/components/matches/MatchListItem.tsx`:

(a) Add at the very top (before the imports):
```tsx
"use client";
```
(b) Add imports:
```tsx
import { useState } from "react";
import { breakdownParts, type BreakdownLabels } from "@/lib/breakdown-format";
```
(c) Extend the `Labels` type (add two fields):
```tsx
  toggleBreakdown: string;
  breakdown: BreakdownLabels;
```
(d) Inside the component body, add state near the top (after the `now` destructure / before `isLive`):
```tsx
  const [showBreakdown, setShowBreakdown] = useState(false);
```
(e) Replace the points badge:
```tsx
        {match.pointsEarned != null && (
          <span className="bg-[var(--color-accent-gold)] px-1.5 py-0.5 text-[var(--color-text-primary)]">
            {labels.formatPoints(match.pointsEarned)}
          </span>
        )}
```
with a button:
```tsx
        {match.pointsEarned != null && (
          <button
            type="button"
            onClick={() => setShowBreakdown((v) => !v)}
            aria-expanded={showBreakdown}
            aria-label={labels.toggleBreakdown}
            className="bg-[var(--color-accent-gold)] px-1.5 py-0.5 text-[var(--color-text-primary)]"
          >
            {labels.formatPoints(match.pointsEarned)}
          </button>
        )}
```
(f) Render the breakdown line — add this right AFTER the closing `</div>` of the pick row and BEFORE the component's final outer `</div>`:
```tsx
      {showBreakdown && match.breakdown && breakdownParts(match.breakdown, labels.breakdown).length > 0 && (
        <div className="border-t-[1.5px] border-dashed border-[var(--color-line-ink)] px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.04em] text-[var(--color-text-muted)]">
          {breakdownParts(match.breakdown, labels.breakdown).join(" · ")}
        </div>
      )}
```

- [ ] **Step 5: Pass the labels from `MatchTabs`**

In `frontend/components/matches/MatchTabs.tsx`, add near the other `useTranslations`:
```tsx
  const tScore = useTranslations("scorecard");
```
In the `labelsFor(m)` object, add:
```tsx
    toggleBreakdown: tScore("toggleBreakdown"),
    breakdown: {
      bdOutcome: tScore("bdOutcome"),
      bdTeam1: tScore("bdTeam1"),
      bdTeam2: tScore("bdTeam2"),
      bdDiff: tScore("bdDiff"),
      multiplier: (n: number) => tScore("multiplier", { n }),
      pts: (n: number) => tScore("pts", { n }),
    },
```

- [ ] **Step 6: Run it, expect PASS**

Run: `pnpm vitest run components/matches/MatchListItem.test.tsx` → PASS. Then `pnpm typecheck` → exit 0.

- [ ] **Step 7: Commit**

```bash
git add frontend/lib/api/matches.ts frontend/components/matches/MatchListItem.tsx frontend/components/matches/MatchListItem.test.tsx frontend/components/matches/MatchTabs.tsx frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(matches): tap the points badge to reveal the breakdown"
```

---

## Task 4: Scorecard player header

**Files:**
- Modify: `frontend/app/ranking/[userId]/page.tsx`

- [ ] **Step 1: Add the header**

In `frontend/app/ranking/[userId]/page.tsx`, find the back-link block:
```tsx
        <div className="px-3 pt-3">
          <Link
            href="/ranking"
            className="chrome-label inline-flex items-center gap-1 text-[var(--color-text-primary)] hover:text-[var(--color-accent-red)]"
          >
            ← {t("backToTable")}
          </Link>
        </div>
```
Add a header block right after it (before the `{stages.length === 0 ? …}`):
```tsx
        <div className="mx-3 mt-2 flex items-baseline justify-between gap-3 border-b-[1.5px] border-[var(--color-line-ink)] pb-2">
          <h1 className="truncate font-display text-2xl font-black uppercase tracking-[-0.03em]">
            {card.displayName ?? "—"}
          </h1>
          <span className="shrink-0 font-display text-lg font-extrabold text-[var(--color-accent-red)]">
            {t("totalPoints", { n: card.totalPoints })}
          </span>
        </div>
```

- [ ] **Step 2: Typecheck + lint**

Run (from `frontend/`): `pnpm typecheck` (exit 0); `pnpm lint` (0 errors; pre-existing `layout.tsx` warning OK).

- [ ] **Step 3: Commit**

```bash
git add "frontend/app/ranking/[userId]/page.tsx"
git commit -m "feat(scorecard): big player name + total header"
```

---

## Task 5: Full verification

**Files:** none (gate only).

- [ ] **Step 1: Frontend** (from `frontend/`): `pnpm vitest run` (all green), `pnpm typecheck` (exit 0), `pnpm lint` (0 errors).
- [ ] **Step 2: Backend** (from `backend/`): `./mvnw -q verify` (BUILD SUCCESS).
- [ ] **Step 3 (optional manual):** `/matches` → tap a `+N PTS` badge → breakdown line toggles; `/ranking/{id}` → big name + total header at top.
- [ ] **Step 4:** `git add -A && git commit -m "chore: verification fixups" || echo clean`

---

## Self-Review (completed during planning)

- **Spec coverage:** (A) `MatchRow.breakdown` via DB-pinned `ScoreBreakdown`, `pointsEarned` derived from it (Task 1) ✓; shared `breakdownParts` + `MatchScoreRow` refactor (Task 2) ✓; `MatchView.breakdown` + tappable `MatchListItem` toggle + label passthrough (Task 3) ✓; (B) scorecard `{name} · {total} pts` header (Task 4) ✓; reuse of `scorecard` i18n labels + new `toggleBreakdown` ✓; caller's-own-picks only ✓; no hover (tap toggle) ✓.
- **Bug fix folded in:** `breakdownParts` only appends the multiplier when a component scored — so a 0-point knockout no longer shows a bare `×3` (a live scorecard bug). Because Task 2 refactors `MatchScoreRow` onto the helper, that fix lands on the existing scorecard too.
- **Placeholder scan:** none — full code in every step.
- **Type consistency:** `ScoreBreakdown` (scorecard.ts) is the type on `MatchView.breakdown` (Task 3) and the arg to `breakdownParts` (Task 2). `BreakdownLabels` (Task 2) is the shape used by `MatchScoreRow.Labels`, `MatchListItem.Labels.breakdown`, and built in `MatchTabs` (Task 3) — same `bd*`/`multiplier`/`pts` fields. Backend `MatchRow.breakdown` (Task 1) serializes to the JSON `breakdown` the frontend type expects (`outcome`/`team1Exact`/`team2Exact`/`goalDiff`/`multiplier`/`total`).

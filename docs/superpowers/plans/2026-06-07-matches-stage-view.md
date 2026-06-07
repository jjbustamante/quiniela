# Matches Stage View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Por fecha / Por fase" toggle to the Matches page — keep today's date tabs, and add a stage-grouped view (most-recent stage first) for easy navigation.

**Architecture:** Frontend-only. A pure `groupMatchesByStage(matches, nowMs)` helper groups the merged match list by `roundCode` and orders the sections; the Matches client component gains a `mode: "date" | "stage"` toggle that renders either the existing date tabs or the stage sections. Stage headers reuse the existing `home.chip*` labels.

**Tech Stack:** Next.js 16 + React 19 + TypeScript + next-intl + Vitest/RTL. Run from `.worktrees/matches-stage-view/frontend` (if `node_modules` missing: `pnpm install --frozen-lockfile`).

---

## File Structure

**Create**
- `frontend/lib/matches-by-stage.ts` — pure grouping/ordering helper (+ `.test.ts`)
- `frontend/components/matches/MatchTabs.test.tsx` — toggle behavior test

**Modify**
- `frontend/components/matches/MatchTabs.tsx` — add the `mode` toggle + stage view
- `frontend/messages/es-CO.json`, `frontend/messages/en.json` — toggle labels

---

## Task 1: `groupMatchesByStage` helper

**Files:**
- Create: `frontend/lib/matches-by-stage.ts`
- Create: `frontend/lib/matches-by-stage.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/lib/matches-by-stage.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { groupMatchesByStage } from "./matches-by-stage";
import type { MatchView } from "@/lib/api/matches";

function m(over: Partial<MatchView> & { id: number; roundCode: string; kickoffAt: string }): MatchView {
  return {
    groupCode: null,
    team1: { code: "A", name: "A", flag: "🏳" },
    team2: { code: "B", name: "B", flag: "🏳" },
    score: null,
    played: false,
    yourPick: null,
    pointsEarned: null,
    pickWinner: null,
    winner: null,
    ...over,
  };
}

const NOW = Date.parse("2026-06-29T12:00:00Z");

describe("groupMatchesByStage", () => {
  it("returns [] for no matches", () => {
    expect(groupMatchesByStage([], NOW)).toEqual([]);
  });

  it("groups matches by roundCode", () => {
    const groups = groupMatchesByStage(
      [
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
        m({ id: 2, roundCode: "GROUP", kickoffAt: "2026-06-12T15:00:00Z" }),
        m({ id: 3, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z" }),
      ],
      NOW,
    );
    const group = groups.find((g) => g.roundCode === "GROUP")!;
    expect(group.matches.map((x) => x.id)).toEqual([1, 2]);
    expect(groups.find((g) => g.roundCode === "R32")!.matches.map((x) => x.id)).toEqual([3]);
  });

  it("orders started stages most-recent first (16vos above grupos after the group phase)", () => {
    // GROUP started June 11; R32 started June 28 — both before NOW (June 29).
    const groups = groupMatchesByStage(
      [
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
        m({ id: 2, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z" }),
      ],
      NOW,
    );
    expect(groups.map((g) => g.roundCode)).toEqual(["R32", "GROUP"]);
  });

  it("places not-yet-started stages after started ones, soonest first", () => {
    // GROUP started; R16 (June 30) and QF (July 2) are future.
    const groups = groupMatchesByStage(
      [
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
        m({ id: 3, roundCode: "QF", kickoffAt: "2026-07-02T15:00:00Z" }),
        m({ id: 2, roundCode: "R16", kickoffAt: "2026-06-30T15:00:00Z" }),
      ],
      NOW,
    );
    expect(groups.map((g) => g.roundCode)).toEqual(["GROUP", "R16", "QF"]);
  });

  it("sorts matches within a stage chronologically", () => {
    const groups = groupMatchesByStage(
      [
        m({ id: 2, roundCode: "GROUP", kickoffAt: "2026-06-12T15:00:00Z" }),
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
      ],
      NOW,
    );
    expect(groups[0].matches.map((x) => x.id)).toEqual([1, 2]);
  });
});
```

- [ ] **Step 2: Run it, expect FAIL** (module missing)

Run: `pnpm vitest run lib/matches-by-stage.test.ts`
Expected: FAIL — cannot resolve `./matches-by-stage`.

- [ ] **Step 3: Implement**

Create `frontend/lib/matches-by-stage.ts`:

```ts
import type { MatchView } from "@/lib/api/matches";

export type StageGroup = { roundCode: string; matches: MatchView[] };

/**
 * Group matches by stage (roundCode) for the "Por fase" view. Sections are
 * ordered most-recent-activity first: a stage whose latest STARTED match
 * (kickoff <= now) is most recent floats to the top (so after the group phase,
 * 16vos sits above Grupos). Stages with no started match sort after the started
 * ones, by their soonest kickoff. Matches within a stage are chronological.
 */
export function groupMatchesByStage(matches: MatchView[], nowMs: number): StageGroup[] {
  const byCode = new Map<string, MatchView[]>();
  for (const m of matches) {
    const arr = byCode.get(m.roundCode);
    if (arr) arr.push(m);
    else byCode.set(m.roundCode, [m]);
  }

  type Tagged = {
    roundCode: string;
    matches: MatchView[];
    latestStarted: number | null; // max kickoff <= now, or null if none started
    earliest: number; // min kickoff
  };

  const groups: Tagged[] = [];
  for (const [roundCode, ms] of byCode) {
    const sorted = [...ms].sort((a, b) => Date.parse(a.kickoffAt) - Date.parse(b.kickoffAt));
    let latestStarted: number | null = null;
    let earliest = Infinity;
    for (const m of sorted) {
      const k = Date.parse(m.kickoffAt);
      earliest = Math.min(earliest, k);
      if (k <= nowMs) latestStarted = latestStarted == null ? k : Math.max(latestStarted, k);
    }
    groups.push({ roundCode, matches: sorted, latestStarted, earliest });
  }

  groups.sort((a, b) => {
    const aStarted = a.latestStarted != null;
    const bStarted = b.latestStarted != null;
    if (aStarted && bStarted) return b.latestStarted! - a.latestStarted!; // recent started first
    if (aStarted) return -1; // started before not-started
    if (bStarted) return 1;
    return a.earliest - b.earliest; // both future: soonest first
  });

  return groups.map(({ roundCode, matches }) => ({ roundCode, matches }));
}
```

- [ ] **Step 4: Run it, expect PASS (5 tests)**

Run: `pnpm vitest run lib/matches-by-stage.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/matches-by-stage.ts frontend/lib/matches-by-stage.test.ts
git commit -m "feat(matches): pure groupMatchesByStage helper"
```

---

## Task 2: i18n toggle labels

**Files:**
- Modify: `frontend/messages/es-CO.json`, `frontend/messages/en.json`

- [ ] **Step 1: Add keys to the `matches` object in `es-CO.json`**

Inside the existing `"matches"` object, append these keys (add a comma to the prior last key):

```json
    "viewByDate": "Por fecha",
    "viewByStage": "Por fase"
```

- [ ] **Step 2: Add to `en.json`** (inside `"matches"`):

```json
    "viewByDate": "By date",
    "viewByStage": "By stage"
```

> Mind trailing commas — the key before each block needs one; the last key in the object must not.

- [ ] **Step 3: Verify JSON + keys**

Run (from `frontend/`):
```bash
node -e "const e=require('./messages/es-CO.json'),n=require('./messages/en.json'); for(const x of [e,n]){ if(!x.matches.viewByDate||!x.matches.viewByStage) throw new Error('missing'); } console.log('ok');"
```
Expected: prints `ok`.

- [ ] **Step 4: Commit**

```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "i18n(matches): por-fecha/por-fase toggle labels"
```

---

## Task 3: Add the toggle + stage view to `MatchTabs`

**Files:**
- Modify: `frontend/components/matches/MatchTabs.tsx`
- Create: `frontend/components/matches/MatchTabs.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/components/matches/MatchTabs.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import type { MatchesView, MatchView } from "@/lib/api/matches";
import { MatchTabs } from "./MatchTabs";

function match(over: Partial<MatchView> & { id: number; roundCode: string; kickoffAt: string }): MatchView {
  return {
    groupCode: null,
    team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" },
    team2: { code: "MEX", name: "México", flag: "🇲🇽" },
    score: { t1: 2, t2: 1 },
    played: true,
    yourPick: null,
    pointsEarned: null,
    pickWinner: null,
    winner: null,
    ...over,
  };
}

const view: MatchesView = {
  serverTime: "2026-06-29T12:00:00Z",
  past: [
    match({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
    match({ id: 2, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z" }),
  ],
  today: [],
  upcoming: [],
};

const messages = {
  matches: {
    tabPast: "Pasados", tabToday: "Hoy", tabUpcoming: "Próximos",
    emptyPast: "—", emptyToday: "—", emptyUpcoming: "—",
    yourPick: "TU PICK", result: "RESULTADO", noPick: "—", liveDot: "EN VIVO",
    pointsEarned: "+{n} PTS", groupLabel: "GRUPO {code}",
    viewByDate: "Por fecha", viewByStage: "Por fase",
  },
  home: { chipGROUP: "Grupos", chipR32: "16vos" },
};

function renderTabs() {
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <MatchTabs view={view} timeZone="America/Bogota" />
    </NextIntlClientProvider>,
  );
}

describe("MatchTabs", () => {
  it("shows the date tabs by default", () => {
    renderTabs();
    expect(screen.getByRole("button", { name: /pasados/i })).toBeInTheDocument();
    // No stage headers in date mode.
    expect(screen.queryByText("16vos")).not.toBeInTheDocument();
  });

  it("switches to stage sections, most-recent stage first", async () => {
    renderTabs();
    await userEvent.click(screen.getByRole("button", { name: /por fase/i }));
    // Both stage headers present; R32 (16vos, started June 28) before GROUP (Grupos).
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
  });
});
```

- [ ] **Step 2: Run it, expect FAIL** (no toggle / no stage headers)

Run: `pnpm vitest run components/matches/MatchTabs.test.tsx`
Expected: FAIL — "Por fase" button not found / no `stage-header` testids.

- [ ] **Step 3: Rewrite `MatchTabs.tsx`**

Replace the entire file `frontend/components/matches/MatchTabs.tsx` with:

```tsx
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { MatchesView, MatchView } from "@/lib/api/matches";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { MatchListItem } from "./MatchListItem";

type Tab = "past" | "today" | "upcoming";
type Mode = "date" | "stage";

export function MatchTabs({ view, timeZone }: { view: MatchesView; timeZone: string }) {
  const t = useTranslations("matches");
  // Reuse the phase-rail round labels so the match list and the rail never drift
  // (both show e.g. "16vos" for R32, never the raw code).
  const tRound = useTranslations("home");

  const [mode, setMode] = useState<Mode>("date");

  const initial: Tab =
    view.today.length > 0 ? "today" : view.upcoming.length > 0 ? "upcoming" : "past";
  const [tab, setTab] = useState<Tab>(initial);

  // Authoritative "now" from the backend so SSR + hydration agree on live/started.
  const now = new Date(view.serverTime).getTime();

  // Per-match labels, shared by both views.
  const labelsFor = (m: MatchView) => ({
    yourPick: t("yourPick"),
    result: t("result"),
    noPick: t("noPick"),
    live: t("liveDot"),
    formatPoints: (n: number) => t("pointsEarned", { n }),
    kickoff: formatMatchDateTime(m.kickoffAt, timeZone),
    groupLabel: m.groupCode ? t("groupLabel", { code: m.groupCode }) : null,
    roundLabel: tRound(`chip${m.roundCode}` as never),
  });

  const list = tab === "past" ? view.past : tab === "today" ? view.today : view.upcoming;
  const emptyLabel =
    tab === "past" ? t("emptyPast") : tab === "today" ? t("emptyToday") : t("emptyUpcoming");

  return (
    <div className="flex flex-col">
      {/* View toggle: date tabs vs stage sections */}
      <div className="mb-3 flex gap-1.5">
        <ModeButton active={mode === "date"} onClick={() => setMode("date")}>
          {t("viewByDate")}
        </ModeButton>
        <ModeButton active={mode === "stage"} onClick={() => setMode("stage")}>
          {t("viewByStage")}
        </ModeButton>
      </div>

      {mode === "date" ? (
        <>
          <div className="flex gap-1 border-b-[1.5px] border-[var(--color-line-ink)] px-1">
            <TabButton active={tab === "past"} onClick={() => setTab("past")}>
              {t("tabPast")} · {view.past.length}
            </TabButton>
            <TabButton active={tab === "today"} onClick={() => setTab("today")}>
              {t("tabToday")} · {view.today.length}
            </TabButton>
            <TabButton active={tab === "upcoming"} onClick={() => setTab("upcoming")}>
              {t("tabUpcoming")} · {view.upcoming.length}
            </TabButton>
          </div>

          {list.length === 0 ? (
            <div className="mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
              <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">
                {emptyLabel}
              </p>
            </div>
          ) : (
            <div className="mt-3 flex flex-col gap-2">
              {list.map((m) => (
                <MatchListItem
                  key={m.id}
                  match={m}
                  showResult={tab !== "upcoming"}
                  now={now}
                  labels={labelsFor(m)}
                />
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="flex flex-col gap-5">
          {groupMatchesByStage([...view.past, ...view.today, ...view.upcoming], now).map((g) => (
            <section key={g.roundCode} className="flex flex-col gap-2">
              <div
                data-testid="stage-header"
                className="chrome-label chrome-label-muted border-b-[1.5px] border-[var(--color-line-ink)] pb-1"
              >
                {tRound(`chip${g.roundCode}` as never)}
              </div>
              {g.matches.map((m) => (
                <MatchListItem
                  key={m.id}
                  match={m}
                  showResult={new Date(m.kickoffAt).getTime() <= now}
                  now={now}
                  labels={labelsFor(m)}
                />
              ))}
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

function ModeButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`border-[1.5px] px-3 py-1.5 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${
        active
          ? "border-[var(--color-line-ink)] bg-[var(--color-accent-gold)] text-[var(--color-text-primary)]"
          : "border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
      }`}
    >
      {children}
    </button>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`-mb-[1.5px] border-b-[3px] px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${
        active
          ? "border-[var(--color-accent-red)] text-[var(--color-text-primary)]"
          : "border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
      }`}
    >
      {children}
    </button>
  );
}
```

> This preserves the date-view markup exactly (same tabs, same `MatchListItem` props) — the only date-path change is factoring the inline `labels={{…}}` into `labelsFor(m)`. The stage path is additive.

- [ ] **Step 4: Run the test, expect PASS (2 tests)**

Run: `pnpm vitest run components/matches/MatchTabs.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/matches/MatchTabs.tsx frontend/components/matches/MatchTabs.test.tsx
git commit -m "feat(matches): por-fecha/por-fase toggle with stage sections"
```

---

## Task 4: Full verification

**Files:** none (gate only). From `frontend/`:

- [ ] **Step 1:** `pnpm vitest run` → all green (new: matches-by-stage 5, MatchTabs 2; existing MatchListItem unchanged).
- [ ] **Step 2:** `pnpm typecheck` → exit 0.
- [ ] **Step 3:** `pnpm lint` → 0 errors (pre-existing `layout.tsx` font warning OK).
- [ ] **Step 4 (optional manual):** run the app, open `/matches`, confirm the default date tabs are unchanged, switch to "Por fase" → stage sections appear with the current/most-recent stage on top, future stages at the bottom; played matches show results.
- [ ] **Step 5:** `git add -A && git commit -m "chore(matches): verification fixups" || echo clean`

---

## Self-Review (completed during planning)

- **Spec coverage:** toggle Por fecha (default, date tabs untouched) / Por fase (Task 3) ✓; stage grouping + most-recent-started-first ordering + future-at-bottom (Task 1 helper, pinned by tests) ✓; within-stage chronological ✓; reuse `MatchListItem` + `home.chip*` headers ✓; frontend-only, no backend ✓; i18n es-CO + en (Task 2) ✓; pure tested core (Task 1) ✓; out-of-scope (no group sub-grouping, no URL persistence, no backend) respected ✓.
- **Placeholder scan:** none — full code in every step.
- **Type consistency:** `groupMatchesByStage(matches: MatchView[], nowMs: number): StageGroup[]` defined in Task 1, called identically in Task 3 with `[...view.past, ...view.today, ...view.upcoming]` + `now`. `labelsFor(m)` produces the exact `Labels` shape `MatchListItem` already accepts (unchanged from the current inline object). `mode` state is distinct from the `view` prop (no shadowing).

# Collapsible Stage Groups (Matches + Compare) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group the Compare results (H2H + Group Consensus) by stage like the Matches page, and make every stage group a collapsible `<details>` with the most-recent stage open and older ones collapsed — across Matches and both Compare views.

**Architecture:** Generalize the existing `groupMatchesByStage` helper to be generic. Add a shared, zero-JS `StageSection` (`<details>/<summary>`) component. Use it in the Matches stage view (client component) and both Compare views (server components, ordered with server-side `Date.now()`).

**Tech Stack:** Next.js 16 + React 19 + TypeScript + Tailwind v4 + next-intl + Vitest/RTL. Run from `.worktrees/compare-by-stage/frontend` (if `node_modules` missing: `pnpm install --frozen-lockfile`).

---

## File Structure

**Create**
- `frontend/components/shared/StageSection.tsx` (+ `.test.tsx`)

**Modify**
- `frontend/lib/matches-by-stage.ts` (+ `.test.ts`) — make generic
- `frontend/components/matches/MatchTabs.tsx` (+ `.test.tsx`) — use StageSection
- `frontend/components/compare/GroupConsensus.tsx` (+ `.test.tsx`) — stage groups
- `frontend/components/compare/H2HCompare.tsx` (+ new `.test.tsx`) — stage groups

---

## Task 1: Make `groupMatchesByStage` generic

**Files:**
- Modify: `frontend/lib/matches-by-stage.ts`
- Modify: `frontend/lib/matches-by-stage.test.ts`

- [ ] **Step 1: Add a failing generic-contract test**

In `frontend/lib/matches-by-stage.test.ts`, add this test inside the existing `describe` block (it calls the helper with a minimal object literal, NOT a `MatchView`):

```ts
  it("works on any object with roundCode + kickoffAt (generic)", () => {
    const groups = groupMatchesByStage(
      [
        { roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z", label: "g" },
        { roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z", label: "k" },
      ],
      Date.parse("2026-06-29T12:00:00Z"),
    );
    expect(groups.map((g) => g.roundCode)).toEqual(["R32", "GROUP"]);
    expect(groups[0].matches[0].label).toBe("k");
  });
```

- [ ] **Step 2: Run it, expect FAIL** (type error / `.label` not on `MatchView`)

Run: `pnpm vitest run lib/matches-by-stage.test.ts`
Expected: FAIL — `groupMatchesByStage` only accepts `MatchView[]`, so the literal with `label` is a type error (and `groups[0].matches[0].label` won't exist).

> If vitest passes at runtime but `pnpm typecheck` fails, that still counts as RED for this typing task — the goal is the generic signature.

- [ ] **Step 3: Make the helper generic**

In `frontend/lib/matches-by-stage.ts`, replace the import + type + signature:

Replace:
```ts
import type { MatchView } from "@/lib/api/matches";

export type StageGroup = { roundCode: string; matches: MatchView[] };

export function groupMatchesByStage(matches: MatchView[], nowMs: number): StageGroup[] {
```
with:
```ts
export type StageGroup<T> = { roundCode: string; matches: T[] };

/** Items only need a stage code and a kickoff timestamp to be grouped/ordered. */
type StageItem = { roundCode: string; kickoffAt: string };

export function groupMatchesByStage<T extends StageItem>(
  matches: T[],
  nowMs: number,
): StageGroup<T>[] {
```

The body is unchanged EXCEPT the internal `Tagged` type's `matches` field becomes `T[]`:
```ts
  type Tagged = {
    roundCode: string;
    matches: T[];
    latestStarted: number | null;
    earliest: number;
  };
```
(The `MatchView` import is removed — it's no longer referenced.)

- [ ] **Step 4: Run tests + typecheck, expect PASS**

Run: `pnpm vitest run lib/matches-by-stage.test.ts` → PASS (6 tests).
Run: `pnpm typecheck` → exit 0 (MatchTabs still calls it with `MatchView`, now inferred as `StageGroup<MatchView>`).

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/matches-by-stage.ts frontend/lib/matches-by-stage.test.ts
git commit -m "refactor(matches): make groupMatchesByStage generic"
```

---

## Task 2: `StageSection` collapsible component

**Files:**
- Create: `frontend/components/shared/StageSection.tsx`
- Create: `frontend/components/shared/StageSection.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/components/shared/StageSection.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StageSection } from "./StageSection";

describe("StageSection", () => {
  it("renders the header + count", () => {
    render(
      <StageSection header="16vos" count={4}>
        <div>child</div>
      </StageSection>,
    );
    expect(screen.getByTestId("stage-header")).toHaveTextContent("16vos");
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("child")).toBeInTheDocument();
  });

  it("is open when defaultOpen and closed otherwise", () => {
    const { container, rerender } = render(
      <StageSection header="A" count={1} defaultOpen>
        <div>x</div>
      </StageSection>,
    );
    expect(container.querySelector("details")).toHaveAttribute("open");

    rerender(
      <StageSection header="A" count={1} defaultOpen={false}>
        <div>x</div>
      </StageSection>,
    );
    expect(container.querySelector("details")).not.toHaveAttribute("open");
  });
});
```

- [ ] **Step 2: Run it, expect FAIL** (module missing)

Run: `pnpm vitest run components/shared/StageSection.test.tsx`

- [ ] **Step 3: Implement**

Create `frontend/components/shared/StageSection.tsx`:

```tsx
import type { ReactNode } from "react";

/**
 * Collapsible stage group. Pure presentational, zero-JS (native <details>) so it
 * works in both server and client component trees. The most-recent group passes
 * `defaultOpen` so it starts expanded while older ones stay collapsed.
 */
export function StageSection({
  header,
  count,
  defaultOpen = false,
  children,
}: {
  header: string;
  count: number;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  return (
    <details
      open={defaultOpen}
      className="group border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]"
    >
      <summary className="flex cursor-pointer list-none items-center justify-between gap-2 px-3 py-2 [&::-webkit-details-marker]:hidden">
        <span className="flex items-center gap-2">
          <span className="font-mono text-[10px] text-[var(--color-text-muted)] transition-transform group-open:rotate-90">
            ▸
          </span>
          <span
            data-testid="stage-header"
            className="font-display text-sm font-extrabold uppercase tracking-tight text-[var(--color-text-primary)]"
          >
            {header}
          </span>
        </span>
        <span className="font-mono text-xs font-bold text-[var(--color-text-muted)]">{count}</span>
      </summary>
      <div className="flex flex-col gap-2 px-3 pb-3">{children}</div>
    </details>
  );
}
```

- [ ] **Step 4: Run it, expect PASS (2 tests)**

Run: `pnpm vitest run components/shared/StageSection.test.tsx`

- [ ] **Step 5: Commit**

```bash
git add frontend/components/shared/StageSection.tsx frontend/components/shared/StageSection.test.tsx
git commit -m "feat(ui): StageSection collapsible group component"
```

---

## Task 3: Matches stage view uses `StageSection`

**Files:**
- Modify: `frontend/components/matches/MatchTabs.tsx`
- Modify: `frontend/components/matches/MatchTabs.test.tsx`

- [ ] **Step 1: Update the test**

In `frontend/components/matches/MatchTabs.test.tsx`, replace the second test (`"switches to stage sections, most-recent stage first"`) with this version (same ordering assertion + the first section starts open):

```tsx
  it("switches to collapsible stage sections, most-recent first, first open", async () => {
    renderTabs();
    await userEvent.click(screen.getByRole("button", { name: /por fase/i }));
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
    // Most-recent stage (R32 = 16vos) starts expanded; the older one collapsed.
    const sections = document.querySelectorAll("details");
    expect(sections[0]).toHaveAttribute("open");
    expect(sections[1]).not.toHaveAttribute("open");
  });
```

- [ ] **Step 2: Run it, expect FAIL** (no `<details>` yet — current stage view uses `<section>`)

Run: `pnpm vitest run components/matches/MatchTabs.test.tsx`

- [ ] **Step 3: Update `MatchTabs.tsx`**

Add the import (next to the other imports):
```tsx
import { StageSection } from "@/components/shared/StageSection";
```

Replace the stage-view block — the current:
```tsx
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
```
with:
```tsx
        <div className="flex flex-col gap-2">
          {groupMatchesByStage([...view.past, ...view.today, ...view.upcoming], now).map((g, i) => (
            <StageSection
              key={g.roundCode}
              header={tRound(`chip${g.roundCode}` as never)}
              count={g.matches.length}
              defaultOpen={i === 0}
            >
              {g.matches.map((m) => (
                <MatchListItem
                  key={m.id}
                  match={m}
                  showResult={new Date(m.kickoffAt).getTime() <= now}
                  now={now}
                  labels={labelsFor(m)}
                />
              ))}
            </StageSection>
          ))}
        </div>
```

- [ ] **Step 4: Run it, expect PASS (2 tests)**

Run: `pnpm vitest run components/matches/MatchTabs.test.tsx`

- [ ] **Step 5: Commit**

```bash
git add frontend/components/matches/MatchTabs.tsx frontend/components/matches/MatchTabs.test.tsx
git commit -m "feat(matches): collapsible stage sections in the por-fase view"
```

---

## Task 4: Group Consensus stage groups

**Files:**
- Modify: `frontend/components/compare/GroupConsensus.tsx`
- Modify: `frontend/components/compare/GroupConsensus.test.tsx`

- [ ] **Step 1: Update the test mock + add an ordering test**

In `frontend/components/compare/GroupConsensus.test.tsx`, extend the mocked label map (inside `vi.mock("next-intl/server", …)`) so stage headers are readable — add these entries to the `map`:
```ts
      chipGROUP: "Grupos",
      chipR32: "16vos",
```

Then add this test (a fixture helper keeps it short — define it above the tests):
```ts
  function revealedMatch(over: Partial<import("@/lib/api/compare").MatchConsensus> & { matchId: number; roundCode: string; kickoffAt: string }) {
    return {
      team1Code: "A", team1Flag: "🏳", team2Code: "B", team2Flag: "🏳",
      actualScoreT1: null, actualScoreT2: null, played: true, revealed: true,
      myScoreT1: 1, myScoreT2: 0, distribution: [{ scoreT1: 1, scoreT2: 0, count: 2 }],
      totalPicks: 2, majority: true, rebel: false,
      ...over,
    } as import("@/lib/api/compare").MatchConsensus;
  }

  it("groups revealed matches into stage sections, most-recent first, first open", async () => {
    await renderGC({
      matches: [
        revealedMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
        revealedMatch({ matchId: 2, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z" }),
      ],
    });
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
    const sections = document.querySelectorAll("details");
    expect(sections[0]).toHaveAttribute("open");
    expect(sections[1]).not.toHaveAttribute("open");
  });
```

- [ ] **Step 2: Run it, expect FAIL** (no stage sections / no `stage-header`)

Run: `pnpm vitest run components/compare/GroupConsensus.test.tsx`

- [ ] **Step 3: Update `GroupConsensus.tsx`**

Add imports:
```tsx
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { StageSection } from "@/components/shared/StageSection";
```
Add the `home` translations alongside `compare` (at the top of the component, after the existing `const t = await getTranslations("compare");`):
```tsx
  const tRound = await getTranslations("home");
```
Replace the revealed-list render (the `return (<section …>{revealed.map(...ConsensusCard...)}</section>)`) with stage groups:
```tsx
  const groups = groupMatchesByStage(revealed, Date.now());

  return (
    <section className="mx-3 mt-3 flex flex-col gap-2">
      {groups.map((g, i) => (
        <StageSection
          key={g.roundCode}
          header={tRound(`chip${g.roundCode}` as never)}
          count={g.matches.length}
          defaultOpen={i === 0}
        >
          {g.matches.map((m) => (
            <ConsensusCard
              key={m.matchId}
              m={m}
              majorityLabel={t("majority")}
              rebelLabel={t("rebel")}
              youLabel={t("youTag")}
            />
          ))}
        </StageSection>
      ))}
    </section>
  );
```
(`ConsensusCard` and the locked-state early return are unchanged.)

- [ ] **Step 4: Run it, expect PASS**

Run: `pnpm vitest run components/compare/GroupConsensus.test.tsx`
Expected: PASS — the new ordering test + the two existing tests (locked-state; rebel/consensus-bar; the revealed one now renders inside an open first section, so its `Rebelde`/`1–0` text is still present).

- [ ] **Step 5: Commit**

```bash
git add frontend/components/compare/GroupConsensus.tsx frontend/components/compare/GroupConsensus.test.tsx
git commit -m "feat(compare): collapsible stage sections in group consensus"
```

---

## Task 5: H2H stage groups (per-stage tables)

**Files:**
- Modify: `frontend/components/compare/H2HCompare.tsx`
- Create: `frontend/components/compare/H2HCompare.test.tsx`

Each stage becomes a `StageSection` whose body is a self-contained per-stage `<table>` (its own header row, so columns self-align — simpler and more robust than one shared header). Within a stage: differ rows first (highlighted), then agree (dimmed).

- [ ] **Step 1: Write the failing test**

Create `frontend/components/compare/H2HCompare.test.tsx`:

```tsx
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import type { H2HView, H2HMatch } from "@/lib/api/compare";
import { H2HCompare } from "./H2HCompare";

vi.mock("next-intl/server", () => ({
  getTranslations: async () => (key: string, vars?: Record<string, unknown>) => {
    const map: Record<string, string> = {
      colMatch: "Partido", colYou: "Tú", colReal: "Real",
      summary: "{agree} de acuerdo, {differ} distintos",
      summaryWinning: "{agree}/{differ} · {points}",
      lockedTitle: "AÚN NO", lockedHelp: "se revela al cerrar", noRival: "Elige rival",
      chipGROUP: "Grupos", chipR32: "16vos",
    };
    let s = map[key] ?? key;
    if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, String(v));
    return s;
  },
}));

function h2hMatch(over: Partial<H2HMatch> & { matchId: number; roundCode: string; kickoffAt: string; state: H2HMatch["state"] }): H2HMatch {
  return {
    team1Code: "A", team1Flag: "🏳", team2Code: "B", team2Flag: "🏳",
    actualScoreT1: 1, actualScoreT2: 0, played: true, revealed: true,
    myScoreT1: 2, myScoreT2: 1, rivalScoreT1: 0, rivalScoreT2: 0,
    ...over,
  };
}

async function renderH2H(data: H2HView) {
  const ui = await H2HCompare({ data });
  return render(<NextIntlClientProvider locale="es-CO" messages={{}}>{ui}</NextIntlClientProvider>);
}

describe("H2HCompare", () => {
  it("groups by stage (most-recent first, first open) with differ before agree in a stage", async () => {
    await renderH2H({
      rivalUserId: 7, rivalDisplayName: "Rival", agreeCount: 1, differCount: 2,
      myPoints: null, rivalPoints: null,
      matches: [
        h2hMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z", state: "differ" }),
        h2hMatch({ matchId: 2, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z", state: "agree", team1Code: "AG" }),
        h2hMatch({ matchId: 3, roundCode: "R32", kickoffAt: "2026-06-28T18:00:00Z", state: "differ", team1Code: "DF" }),
      ],
    });
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
    const sections = document.querySelectorAll("details");
    expect(sections[0]).toHaveAttribute("open");
    // In the R32 section, the differ row (DF) comes before the agree row (AG).
    const r32 = sections[0] as HTMLElement;
    const text = within(r32).getAllByRole("row").map((r) => r.textContent).join(" | ");
    expect(text.indexOf("DF")).toBeLessThan(text.indexOf("AG"));
  });
});
```

- [ ] **Step 2: Run it, expect FAIL** (no stage sections)

Run: `pnpm vitest run components/compare/H2HCompare.test.tsx`

- [ ] **Step 3: Rewrite `H2HCompare.tsx`**

Replace the whole file with (keeps `teamLabel`, `score`, `Row` unchanged; replaces the single-table body with per-stage `StageSection` tables):

```tsx
import { getTranslations } from "next-intl/server";
import type { H2HView, H2HMatch } from "@/lib/api/compare";
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { StageSection } from "@/components/shared/StageSection";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

function score(t1: number | null, t2: number | null): string {
  return t1 === null || t2 === null ? "—" : `${t1}–${t2}`;
}

export async function H2HCompare({ data }: { data: H2HView | null }) {
  const t = await getTranslations("compare");
  const tRound = await getTranslations("home");

  if (!data) {
    return (
      <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] p-6 text-center">
        <p className="font-display text-base font-extrabold uppercase text-[var(--color-text-muted)]">
          {t("noRival")}
        </p>
      </section>
    );
  }

  const visible = data.matches.filter((m) => m.revealed);

  if (visible.length === 0) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-start justify-center gap-4 px-6 py-16">
        <h1 className="headline-display whitespace-pre-line text-[44px] sm:text-6xl">{t("lockedTitle")}</h1>
        <p className="font-sans text-base text-[var(--color-text-muted)]">{t("lockedHelp")}</p>
      </section>
    );
  }

  const points =
    data.myPoints !== null && data.rivalPoints !== null
      ? `${data.myPoints}–${data.rivalPoints}`
      : null;
  const rival = data.rivalDisplayName ?? `#${data.rivalUserId}`;
  const groups = groupMatchesByStage(visible, Date.now());

  return (
    <div className="mx-3 mt-2 flex flex-col gap-2">
      <p className="px-1 py-2 text-xs font-semibold text-[var(--color-text-muted)]">
        {points
          ? t("summaryWinning", { agree: data.agreeCount, differ: data.differCount, points })
          : t("summary", { agree: data.agreeCount, differ: data.differCount })}
      </p>
      {groups.map((g, i) => {
        const differ = g.matches.filter((m) => m.state === "differ");
        const agree = g.matches.filter((m) => m.state === "agree");
        const rows = [...differ, ...agree];
        return (
          <StageSection
            key={g.roundCode}
            header={tRound(`chip${g.roundCode}` as never)}
            count={g.matches.length}
            defaultOpen={i === 0}
          >
            <table className="w-full table-fixed border-collapse">
              <thead>
                <tr className="border-b-[1.5px] border-[var(--color-line-ink)] text-[9px] uppercase text-[var(--color-text-muted)]">
                  <th scope="col" className="w-[40%] py-1.5 text-left">{t("colMatch")}</th>
                  <th scope="col" className="py-1.5">{t("colYou")}</th>
                  <th scope="col" className="py-1.5">{rival}</th>
                  <th scope="col" className="py-1.5">{t("colReal")}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((m) => (
                  <Row key={m.matchId} m={m} highlight={m.state === "differ"} />
                ))}
              </tbody>
            </table>
          </StageSection>
        );
      })}
    </div>
  );
}

function Row({ m, highlight }: { m: H2HMatch; highlight: boolean }) {
  return (
    <tr
      className={`border-b border-[var(--color-line-soft,#e7ddcc)] text-xs ${highlight ? "bg-[#fff4d6]" : "opacity-60"}`}
    >
      <td className="py-2 text-left font-extrabold">
        {teamLabel(m.team1Flag, m.team1Code)}–{teamLabel(m.team2Flag, m.team2Code)}
      </td>
      <td className="py-2 text-center font-extrabold">{score(m.myScoreT1, m.myScoreT2)}</td>
      <td className="py-2 text-center font-extrabold text-[var(--color-accent-red)]">
        {score(m.rivalScoreT1, m.rivalScoreT2)}
      </td>
      <td className="py-2 text-center text-[var(--color-text-muted)]">
        {score(m.actualScoreT1, m.actualScoreT2)}
      </td>
    </tr>
  );
}
```

> Note vs spec: the spec said "single column-header row at the top". This uses a per-stage `<thead>` instead — self-aligning and robust (no fragile shared fixed-width matching), and only the expanded section's header is visible. Same user-facing result.

- [ ] **Step 4: Run it, expect PASS**

Run: `pnpm vitest run components/compare/H2HCompare.test.tsx`

- [ ] **Step 5: Commit**

```bash
git add frontend/components/compare/H2HCompare.tsx frontend/components/compare/H2HCompare.test.tsx
git commit -m "feat(compare): collapsible stage sections in H2H"
```

---

## Task 6: Full verification

**Files:** none (gate only). From `frontend/`:

- [ ] **Step 1:** `pnpm vitest run` → all green (new: StageSection 2, H2HCompare 1, generic helper +1; updated: MatchTabs 2, GroupConsensus +1).
- [ ] **Step 2:** `pnpm typecheck` → exit 0.
- [ ] **Step 3:** `pnpm lint` → 0 errors (pre-existing `layout.tsx` font warning OK). If lint flags the unused `MatchView` import removed in Task 1, it's already gone; fix any new unused import.
- [ ] **Step 4 (optional manual):** run the app — `/matches` "Por fase" and `/compare` (both modes) show collapsible stage sections, most-recent open, older collapsed; the date view and the H2H summary line are unchanged.
- [ ] **Step 5:** `git add -A && git commit -m "chore: verification fixups" || echo clean`

---

## Self-Review (completed during planning)

- **Spec coverage:** generic helper (Task 1) ✓; shared `StageSection` native-details, defaultOpen-first (Task 2) ✓; Matches por-fase uses it (Task 3) ✓; Group Consensus stage sections (Task 4) ✓; H2H stage sections, differ-before-agree within stage (Task 5) ✓; `home.chip*` headers + server-side `Date.now()` ✓; no backend change ✓; most-recent-open / older-collapsed via `defaultOpen={i === 0}` everywhere ✓.
- **Refinement noted:** H2H uses per-stage `<thead>` instead of one shared header (self-aligning) — flagged in Task 5.
- **Placeholder scan:** none — full code in every step.
- **Type consistency:** `groupMatchesByStage<T extends {roundCode,kickoffAt}>` (Task 1) is called with `MatchView` (Task 3), `MatchConsensus` (Task 4), `H2HMatch` (Task 5) — all satisfy the constraint. `StageSection` props `{header, count, defaultOpen?, children}` are used identically in Tasks 3–5. `data-testid="stage-header"` lives on the StageSection header span and every component test queries it.

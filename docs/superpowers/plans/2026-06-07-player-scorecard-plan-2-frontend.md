# Player Scorecard — Plan 2 of 2: Frontend (`/ranking/[userId]` page)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tap any ranking row → a player scorecard at `/ranking/[userId]` showing points by stage (collapsible) with a per-match point breakdown, plus a "← Tabla" back link.

**Architecture:** A new `scorecard.ts` API client consumes the Plan-1 endpoint. A presentational `MatchScoreRow` renders one match's teams, pick, result, and the breakdown line. The scorecard page (server component) groups them with the existing collapsible `StageSection` (most-recent stage first, first open) and shows the stage total in the header. Ranking rows become tappable `Link`s.

**Tech Stack:** Next.js 16 + React 19 + TypeScript + next-intl + Vitest/RTL. Run from `.worktrees/scorecard-ui/frontend` (if `node_modules` missing: `pnpm install --frozen-lockfile`).

**Depends on Plan 1** (merged to master `b5cfeaa`): `GET /api/ranking/{userId}/scorecard` returns
`{ userId, displayName, totalPoints, stages: [{ roundCode, roundName, points, matches: [{ matchId, team1, team2, kickoffAt, betScoreT1, betScoreT2, actualScoreT1, actualScoreT2, breakdown: { outcome, team1Exact, team2Exact, goalDiff, multiplier, total } }] }] }`.

---

## File Structure

**Create**
- `frontend/lib/api/scorecard.ts` — API client + types
- `frontend/components/ranking/MatchScoreRow.tsx` (+ `.test.tsx`)
- `frontend/app/ranking/[userId]/page.tsx` — the scorecard page

**Modify**
- `frontend/app/ranking/page.tsx` — wrap each `RankingRow` in a `Link`
- `frontend/messages/es-CO.json`, `frontend/messages/en.json` — `scorecard` namespace

---

## Task 1: Scorecard API client + types

**Files:**
- Create: `frontend/lib/api/scorecard.ts`

- [ ] **Step 1: Create the client**

Create `frontend/lib/api/scorecard.ts`:

```ts
import { api } from "./client";

export type ScoreBreakdown = {
  outcome: number;
  team1Exact: number;
  team2Exact: number;
  goalDiff: number;
  multiplier: number;
  total: number;
};

export type TeamRef = { code: string | null; name: string | null; flag: string | null };

export type MatchScore = {
  matchId: number;
  team1: TeamRef;
  team2: TeamRef;
  kickoffAt: string;
  betScoreT1: number | null;
  betScoreT2: number | null;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  breakdown: ScoreBreakdown;
};

export type StageScore = {
  roundCode: string;
  roundName: string;
  points: number;
  matches: MatchScore[];
};

export type Scorecard = {
  userId: number;
  displayName: string | null;
  totalPoints: number;
  stages: StageScore[];
};

export async function getScorecard(userId: number): Promise<Scorecard> {
  return api<Scorecard>(`/api/ranking/${userId}/scorecard`);
}
```

- [ ] **Step 2: Typecheck**

Run (from `frontend/`): `pnpm typecheck`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/api/scorecard.ts
git commit -m "feat(scorecard): API client + types"
```

---

## Task 2: i18n `scorecard` namespace

**Files:**
- Modify: `frontend/messages/es-CO.json`, `frontend/messages/en.json`

- [ ] **Step 1: Add to `es-CO.json`**

Add a new top-level `"scorecard"` object (sibling to `"ranking"`):

```json
  "scorecard": {
    "backToTable": "Tabla",
    "totalPoints": "{n} pts",
    "pick": "Pick",
    "result": "Real",
    "bdOutcome": "resultado",
    "bdTeam1": "local",
    "bdTeam2": "visitante",
    "bdDiff": "diferencia",
    "multiplier": "×{n}",
    "pts": "+{n}",
    "noPoints": "Aún sin puntos"
  },
```

- [ ] **Step 2: Add to `en.json`**

```json
  "scorecard": {
    "backToTable": "Table",
    "totalPoints": "{n} pts",
    "pick": "Pick",
    "result": "Real",
    "bdOutcome": "result",
    "bdTeam1": "home",
    "bdTeam2": "away",
    "bdDiff": "goal diff",
    "multiplier": "×{n}",
    "pts": "+{n}",
    "noPoints": "No points yet"
  },
```

> Mind trailing commas — the key/object before each insert needs one; the last key in each object must not.

- [ ] **Step 3: Verify**

Run (from `frontend/`):
```bash
node -e "const e=require('./messages/es-CO.json'),n=require('./messages/en.json'); for(const x of [e,n]){ if(!x.scorecard||!x.scorecard.backToTable||!x.scorecard.bdOutcome||!x.scorecard.pts) throw new Error('missing'); } console.log('ok');"
```
Expected: prints `ok`.

- [ ] **Step 4: Commit**

```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "i18n(scorecard): breakdown + nav copy"
```

---

## Task 3: `MatchScoreRow` component

**Files:**
- Create: `frontend/components/ranking/MatchScoreRow.tsx`
- Create: `frontend/components/ranking/MatchScoreRow.test.tsx`

Presentational (pre-resolved label props, like `RankingRow`) — testable without an i18n provider.

- [ ] **Step 1: Write the failing test**

Create `frontend/components/ranking/MatchScoreRow.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { MatchScore } from "@/lib/api/scorecard";
import { MatchScoreRow } from "./MatchScoreRow";

function match(over: Partial<MatchScore> = {}): MatchScore {
  return {
    matchId: 1,
    team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" },
    team2: { code: "MEX", name: "México", flag: "🇲🇽" },
    kickoffAt: "2026-06-28T15:00:00Z",
    betScoreT1: 2,
    betScoreT2: 1,
    actualScoreT1: 2,
    actualScoreT2: 1,
    breakdown: { outcome: 3, team1Exact: 2, team2Exact: 2, goalDiff: 0, multiplier: 2, total: 14 },
    ...over,
  };
}

const labels = {
  pick: "PICK",
  result: "REAL",
  bdOutcome: "resultado",
  bdTeam1: "local",
  bdTeam2: "visitante",
  bdDiff: "diferencia",
  multiplier: (n: number) => `×${n}`,
  pts: (n: number) => `+${n}`,
};

describe("MatchScoreRow", () => {
  it("shows the total, the contributing components, and the multiplier", () => {
    render(<MatchScoreRow match={match()} labels={labels} />);
    expect(screen.getByText("+14")).toBeInTheDocument();
    expect(screen.getByText(/resultado \+3/)).toBeInTheDocument();
    expect(screen.getByText(/local \+2/)).toBeInTheDocument();
    expect(screen.getByText(/visitante \+2/)).toBeInTheDocument();
    expect(screen.getByText(/×2/)).toBeInTheDocument();
    // goalDiff is 0 → not listed
    expect(screen.queryByText(/diferencia/)).not.toBeInTheDocument();
  });

  it("omits the breakdown line for a zero-point match", () => {
    render(
      <MatchScoreRow
        match={match({
          breakdown: { outcome: 0, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 1, total: 0 },
        })}
        labels={labels}
      />,
    );
    expect(screen.getByText("+0")).toBeInTheDocument();
    expect(screen.queryByText(/resultado/)).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run it, expect FAIL** (module missing)

Run: `pnpm vitest run components/ranking/MatchScoreRow.test.tsx`

- [ ] **Step 3: Implement**

Create `frontend/components/ranking/MatchScoreRow.tsx`:

```tsx
import type { MatchScore, TeamRef } from "@/lib/api/scorecard";

type Labels = {
  pick: string;
  result: string;
  bdOutcome: string;
  bdTeam1: string;
  bdTeam2: string;
  bdDiff: string;
  multiplier: (n: number) => string;
  pts: (n: number) => string;
};

function team(t: TeamRef): string {
  return `${t.flag ?? ""} ${t.code ?? "—"}`.trim();
}

function score(t1: number | null, t2: number | null): string {
  return t1 === null || t2 === null ? "—" : `${t1}–${t2}`;
}

export function MatchScoreRow({ match, labels }: { match: MatchScore; labels: Labels }) {
  const b = match.breakdown;
  const parts: string[] = [];
  if (b.outcome) parts.push(`${labels.bdOutcome} ${labels.pts(b.outcome)}`);
  if (b.team1Exact) parts.push(`${labels.bdTeam1} ${labels.pts(b.team1Exact)}`);
  if (b.team2Exact) parts.push(`${labels.bdTeam2} ${labels.pts(b.team2Exact)}`);
  if (b.goalDiff) parts.push(`${labels.bdDiff} ${labels.pts(b.goalDiff)}`);
  if (b.multiplier > 1) parts.push(labels.multiplier(b.multiplier));

  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2">
      <div className="flex items-baseline justify-between gap-2">
        <span className="truncate font-display text-sm font-extrabold uppercase tracking-tight">
          {team(match.team1)}–{team(match.team2)}
        </span>
        <span className="shrink-0 font-display text-base font-extrabold text-[var(--color-accent-red)]">
          {labels.pts(b.total)}
        </span>
      </div>
      <div className="mt-0.5 flex items-baseline justify-between gap-2 text-xs text-[var(--color-text-muted)]">
        <span>
          {labels.pick}: <span className="font-bold">{score(match.betScoreT1, match.betScoreT2)}</span>
        </span>
        <span>
          {labels.result}:{" "}
          <span className="font-bold">{score(match.actualScoreT1, match.actualScoreT2)}</span>
        </span>
      </div>
      {parts.length > 0 && (
        <div className="mt-1 font-mono text-[10px] uppercase tracking-[0.04em] text-[var(--color-text-muted)]">
          {parts.join(" · ")}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run it, expect PASS (2 tests)**

Run: `pnpm vitest run components/ranking/MatchScoreRow.test.tsx`

- [ ] **Step 5: Commit**

```bash
git add frontend/components/ranking/MatchScoreRow.tsx frontend/components/ranking/MatchScoreRow.test.tsx
git commit -m "feat(scorecard): MatchScoreRow breakdown component"
```

---

## Task 4: Scorecard page + tappable ranking rows

**Files:**
- Create: `frontend/app/ranking/[userId]/page.tsx`
- Modify: `frontend/app/ranking/page.tsx`

- [ ] **Step 1: Create the scorecard page**

Create `frontend/app/ranking/[userId]/page.tsx`:

```tsx
import Link from "next/link";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getScorecard } from "@/lib/api/scorecard";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { StageSection } from "@/components/shared/StageSection";
import { MatchScoreRow } from "@/components/ranking/MatchScoreRow";

export default async function ScorecardPage({
  params,
}: {
  params: Promise<{ userId: string }>;
}) {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const { userId } = await params;
  const card = await getScorecard(Number(userId));
  const t = await getTranslations("scorecard");
  const tRound = await getTranslations("home");

  // Most-recent stage first (backend returns round-sequence ascending).
  const stages = [...card.stages].reverse();

  const rowLabels = {
    pick: t("pick"),
    result: t("result"),
    bdOutcome: t("bdOutcome"),
    bdTeam1: t("bdTeam1"),
    bdTeam2: t("bdTeam2"),
    bdDiff: t("bdDiff"),
    multiplier: (n: number) => t("multiplier", { n }),
    pts: (n: number) => t("pts", { n }),
  };

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={(card.displayName ?? "—").toUpperCase()} meta={t("totalPoints", { n: card.totalPoints })} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 pt-3">
          <Link
            href="/ranking"
            className="chrome-label inline-flex items-center gap-1 text-[var(--color-text-primary)] hover:text-[var(--color-accent-red)]"
          >
            ← {t("backToTable")}
          </Link>
        </div>

        {stages.length === 0 ? (
          <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
            <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">
              {t("noPoints")}
            </p>
          </section>
        ) : (
          <section className="mx-3 mt-3 flex flex-col gap-2">
            {stages.map((s, i) => (
              <StageSection
                key={s.roundCode}
                header={tRound(`chip${s.roundCode}` as never)}
                count={s.points}
                defaultOpen={i === 0}
              >
                {s.matches.map((m) => (
                  <MatchScoreRow key={m.matchId} match={m} labels={rowLabels} />
                ))}
              </StageSection>
            ))}
          </section>
        )}
      </div>

      <BottomNav activeKey="ranking" />
    </main>
  );
}
```

> The `StageSection` `count` slot shows the stage's **points** here (e.g. "16VOS  50") — that's the per-stage total (#4); the number badge is just "a number to show", so reusing it for points is intentional, no component change.

- [ ] **Step 2: Make ranking rows tappable**

In `frontend/app/ranking/page.tsx`, add the import:
```tsx
import Link from "next/link";
```
Then wrap each `RankingRow` in a `Link`. Replace:
```tsx
            {ranking.entries.map((e) => (
              <RankingRow
                key={e.userId}
                entry={e}
                youLabel={t("you")}
                trendUp={t("trendUp")}
                trendDown={t("trendDown")}
                trendFlat={t("trendFlat")}
                payoutLabel={payoutByRank.get(e.rank)}
              />
            ))}
```
with:
```tsx
            {ranking.entries.map((e) => (
              <Link key={e.userId} href={`/ranking/${e.userId}`} className="block">
                <RankingRow
                  entry={e}
                  youLabel={t("you")}
                  trendUp={t("trendUp")}
                  trendDown={t("trendDown")}
                  trendFlat={t("trendFlat")}
                  payoutLabel={payoutByRank.get(e.rank)}
                />
              </Link>
            ))}
```

- [ ] **Step 3: Typecheck + lint**

Run (from `frontend/`): `pnpm typecheck` (exit 0); `pnpm lint` (0 errors; pre-existing `layout.tsx` font warning OK).

- [ ] **Step 4: Commit**

```bash
git add "frontend/app/ranking/[userId]/page.tsx" "frontend/app/ranking/page.tsx"
git commit -m "feat(scorecard): /ranking/[userId] page + tappable ranking rows"
```

---

## Task 5: Full verification

**Files:** none (gate only). From `frontend/`:

- [ ] **Step 1:** `pnpm vitest run` → all green (new: MatchScoreRow 2; existing unchanged).
- [ ] **Step 2:** `pnpm typecheck` → exit 0.
- [ ] **Step 3:** `pnpm lint` → 0 errors.
- [ ] **Step 4 (optional manual):** run the app — `/ranking`, tap a player → their scorecard shows stage sections (most-recent first, first open) with the stage points in the header and per-match breakdown rows; "← Tabla" returns to the table.
- [ ] **Step 5:** `git add -A && git commit -m "chore(scorecard): verification fixups" || echo clean`

---

## Self-Review (completed during planning)

- **Spec coverage (Plan 2 portion):** API client consuming the Plan-1 endpoint (Task 1) ✓; scorecard page at `/ranking/[userId]` with stage totals in collapsible `StageSection` headers + per-match breakdown rows + "← Tabla" back link (Task 4) ✓; `MatchScoreRow` breakdown line `{component} +{pts} · … · ×{mult}` (Task 3) ✓; ranking rows navigate to `/ranking/{userId}` (Task 4) ✓; bilingual i18n (Task 2) ✓; any player / played-only is enforced by the Plan-1 backend; most-recent-stage-first via `[...stages].reverse()` + `defaultOpen={i===0}`.
- **Placeholder scan:** none — full code in every step.
- **Type consistency:** `Scorecard`/`StageScore`/`MatchScore`/`ScoreBreakdown`/`TeamRef` (Task 1) match the Plan-1 JSON field names and are consumed identically by `MatchScoreRow` (Task 3) + the page (Task 4). The `Labels` shape built in the page (Task 4) matches `MatchScoreRow`'s `Labels` prop (Task 3) field-for-field.

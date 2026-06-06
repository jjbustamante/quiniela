# Home Adaptive Phase UX — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the home page into an adaptive dashboard that surfaces the currently-relevant tournament phase (group fill → group live → each knockout round → champion), with all phases reachable via a phase rail, your standing, and a results/next recap — all over existing APIs, no live scores.

**Architecture:** A pure `computeHomeState()` function maps the four existing API payloads (bracket, ranking, matches, summary) + a `now` instant into a `HomeState` (focus variant, phase chips, standing, recap). Four presentational components render the blocks; the home page just fetches + composes. The 12 group cards + "Paul fills it all" move to a new `/groups` index page so the home is a pure dashboard.

**Tech Stack:** Next.js 16 App Router (RSC), TypeScript, Tailwind + CSS vars, next-intl, Vitest + Testing Library.

**Reference spec:** `docs/superpowers/specs/2026-06-06-home-adaptive-phase-ux-design.md`

---

## File structure

| File | Responsibility |
|------|----------------|
| `lib/home-phase.ts` (new) | Pure `computeHomeState()` + the `HomeState` types. The whole phase-priority rule lives here. |
| `lib/home-phase.test.ts` (new) | Unit tests for every phase state + edge cases. |
| `components/lobby/FocusCard.tsx` (new) | Adaptive focus poster (fill / live / champion). |
| `components/lobby/PhaseRail.tsx` (new) | Horizontal phase chips with drill-in links. |
| `components/lobby/StandingStrip.tsx` (new) | Your rank + points + link to `/ranking`. |
| `components/lobby/ResultsRecap.tsx` (new) | Recent finals (+points) + next fixture, or pre-kickoff pot/countdown. |
| `app/groups/page.tsx` (new) | Group index: 12 `GroupCard`s + `PaulFillAllButton` (moved off home). |
| `app/home/page.tsx` (modify) | Fetch 4 sources, call `computeHomeState`, render the 4 blocks + invite. |
| `messages/es-CO.json`, `messages/en.json` (modify) | New `home` i18n namespace. |
| `e2e/smoke.e2e.ts` (modify) | Assert the home renders the focus card + rail. |

Conventions to follow (already in this repo): pure tested helpers like `lib/ranking-payouts.ts` and `lib/paul-feedback.ts`; RSC pages that `Promise.all` their fetches and `redirect("/")` when unauthed; Spanish UI copy with a parallel `en.json`; `bg-[var(--color-...)]` styling.

---

## Task 1: i18n — the `home` namespace

**Files:**
- Modify: `frontend/messages/es-CO.json`
- Modify: `frontend/messages/en.json`

- [ ] **Step 1: Add the `home` block to `es-CO.json`**

Insert this object as a new top-level key (e.g. right after the existing `"lobby": { ... },` block):

```json
  "home": {
    "fillGroupKicker": "Cierra en {when}",
    "fillGroupHeadline": "Llena tus grupos",
    "fillSub": "{filled}/{total} listos",
    "fillGroupCta": "Seguir llenando →",
    "fillGroupCtaEmpty": "Empezar →",
    "fillDoneKicker": "Listo ✓",
    "fillGroupDoneHeadline": "Grupos completos",
    "fillDoneSub": "Puedes ajustar hasta {when}",
    "fillKnockoutKicker": "Ronda abierta · cierra en {when}",
    "fillKnockoutHeadline": "{round} · llena tu llave",
    "fillKnockoutCta": "Llenar {round} →",
    "fillKnockoutDoneHeadline": "{round} · lista",
    "liveHeadline": "En juego",
    "livePhaseGroup": "Fase de grupos",
    "livePhaseKnockout": "Eliminatorias",
    "liveSub": "Tu puesto #{rank} · {points} pts",
    "liveSubNoScore": "Aún sin puntos",
    "championKicker": "Torneo terminado",
    "championHeadline": "{rank}º puesto",
    "championSub": "{points} pts",
    "championSubPayout": "{points} pts · ganaste {amount}",
    "tuPuesto": "Tu puesto",
    "verTabla": "Ver tabla →",
    "standingNoScore": "Aún sin puntos",
    "resultadosProximos": "Resultados & próximos",
    "verPartidos": "Ver partidos →",
    "tagFinal": "Final",
    "nextFixturePrefix": "",
    "preKickoffPot": "Pozo {amount} · {panas} panas",
    "preKickoffCountdown": "Faltan {days} días para el pitazo",
    "chipGROUP": "Grupos",
    "chipR32": "16vos",
    "chipR16": "8vos",
    "chipQF": "4tos",
    "chipSF": "Semis",
    "chipTHIRD_PLACE": "3er",
    "chipFINAL": "Final"
  },
```

- [ ] **Step 2: Add the parallel `home` block to `en.json`** (same keys, English copy)

```json
  "home": {
    "fillGroupKicker": "Closes in {when}",
    "fillGroupHeadline": "Fill your groups",
    "fillSub": "{filled}/{total} done",
    "fillGroupCta": "Keep filling →",
    "fillGroupCtaEmpty": "Get started →",
    "fillDoneKicker": "Done ✓",
    "fillGroupDoneHeadline": "Groups complete",
    "fillDoneSub": "You can adjust until {when}",
    "fillKnockoutKicker": "Round open · closes in {when}",
    "fillKnockoutHeadline": "{round} · fill your bracket",
    "fillKnockoutCta": "Fill {round} →",
    "fillKnockoutDoneHeadline": "{round} · done",
    "liveHeadline": "In play",
    "livePhaseGroup": "Group stage",
    "livePhaseKnockout": "Knockouts",
    "liveSub": "Your rank #{rank} · {points} pts",
    "liveSubNoScore": "No points yet",
    "championKicker": "Tournament over",
    "championHeadline": "{rank}th place",
    "championSub": "{points} pts",
    "championSubPayout": "{points} pts · you won {amount}",
    "tuPuesto": "Your standing",
    "verTabla": "View table →",
    "standingNoScore": "No points yet",
    "resultadosProximos": "Results & up next",
    "verPartidos": "View matches →",
    "tagFinal": "Final",
    "nextFixturePrefix": "",
    "preKickoffPot": "Pot {amount} · {panas} panas",
    "preKickoffCountdown": "{days} days to kickoff",
    "chipGROUP": "Groups",
    "chipR32": "R32",
    "chipR16": "R16",
    "chipQF": "QF",
    "chipSF": "SF",
    "chipTHIRD_PLACE": "3rd",
    "chipFINAL": "Final"
  },
```

- [ ] **Step 3: Verify both files still parse**

Run: `cd frontend && node -e "require('./messages/es-CO.json'); require('./messages/en.json'); console.log('JSON OK')"`
Expected: `JSON OK`

- [ ] **Step 4: Commit**

```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "i18n(home): add adaptive home dashboard namespace"
```

---

## Task 2: Pure state function `lib/home-phase.ts`

**Files:**
- Create: `frontend/lib/home-phase.ts`
- Test: `frontend/lib/home-phase.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/lib/home-phase.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { computeHomeState } from "./home-phase";
import type { BracketView } from "./api/bracket";
import type { RankingView } from "./api/ranking";
import type { MatchesView } from "./api/matches";
import type { PublicSummary } from "./api/summary";

const T0 = Date.parse("2026-06-11T17:00:00Z"); // group deadline / kickoff
const KO = Date.parse("2026-06-28T17:00:00Z"); // knockout deadline

function bracket(over: Partial<BracketView> = {}): BracketView {
  return {
    quinielaId: 1,
    totalMatches: 104,
    totalBets: 0,
    groupStageDeadline: new Date(T0).toISOString(),
    knockoutDeadline: new Date(KO).toISOString(),
    groups: [{ code: "A", filled: 0, total: 6, locked: false, matches: [] }],
    knockouts: [
      { code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, locked: false, matches: [] },
      { code: "FINAL", name: "Final", filled: 0, total: 1, unlocked: false, locked: false, matches: [] },
    ],
    ...over,
  };
}
function ranking(over: Partial<RankingView> = {}): RankingView {
  return {
    entries: [
      { rank: 1, userId: 9, displayName: "María", points: 0, delta: null, isYou: false, isBot: false },
      { rank: 1, userId: 1, displayName: "Tú", points: 0, delta: null, isYou: true, isBot: false },
    ],
    updatedAt: "2026-06-01T00:00:00Z",
    ...over,
  };
}
function matches(over: Partial<MatchesView> = {}): MatchesView {
  return { serverTime: new Date(T0 - 5 * 86400000).toISOString(), past: [], today: [], upcoming: [], ...over };
}
function summary(over: Partial<PublicSummary> = {}): PublicSummary {
  return {
    tournament: { slug: "x", name: "WC", startDate: "2026-06-11", endDate: "2026-07-19", hostCountryCodes: [], openingVenue: null, totalGroupStageMatches: 72, totalGroups: 12 },
    pool: { currency: "USD", entryFeeCents: 2000, potCents: 26000, panaCount: 13 },
    prizeSplit: [{ rank: 1, percentage: 80, payoutCents: 20800 }, { rank: 2, percentage: 15, payoutCents: 3900 }, { rank: 3, percentage: 5, payoutCents: 1300 }],
    testMode: false,
  };
}
const nowBefore = T0 - 5 * 86400000; // 5 days before group lock
const nowGroupsLive = T0 + 86400000; // after group lock, before KO

it("FILL_GROUP before the group deadline", () => {
  const s = computeHomeState({ bracket: bracket({ totalBets: 40, groups: [{ code: "A", filled: 4, total: 6, locked: false, matches: [] }] }), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.focus.kind).toBe("fillGroup");
  if (s.focus.kind === "fillGroup") {
    expect(s.focus.full).toBe(false);
    expect(s.focus.href).toBe("/groups");
  }
  // pre-kickoff: no match played yet -> recap is the pot/countdown fallback
  expect(s.recap.kind).toBe("preKickoff");
});

it("FILL_GROUP softens to full when all group bets are in", () => {
  const s = computeHomeState({ bracket: bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: false, matches: [] }] }), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.focus.kind).toBe("fillGroup");
  if (s.focus.kind === "fillGroup") expect(s.focus.full).toBe(true);
});

it("FILL_KNOCKOUT picks the earliest open round once groups are locked", () => {
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [
      { code: "R32", name: "Dieciseisavos", filled: 3, total: 16, unlocked: true, locked: false, matches: [] },
      { code: "R16", name: "Octavos", filled: 0, total: 8, unlocked: false, locked: false, matches: [] },
    ],
  });
  const s = computeHomeState({ bracket: b, ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowGroupsLive });
  expect(s.focus.kind).toBe("fillKnockout");
  if (s.focus.kind === "fillKnockout") {
    expect(s.focus.roundCode).toBe("R32");
    expect(s.focus.href).toBe("/knockout/R32");
  }
});

it("LIVE when groups are locked and no round is open to fill", () => {
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [{ code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, locked: false, matches: [] }],
  });
  const m = matches({ past: [{ id: 1, roundCode: "GROUP", groupCode: "A", kickoffAt: new Date(nowGroupsLive - 3600000).toISOString(), team1: { code: "BRA", name: "Brasil", flag: "🇧🇷" }, team2: { code: "GHA", name: "Ghana", flag: "🇬🇭" }, score: { t1: 2, t2: 0 }, played: true, yourPick: { t1: 2, t2: 0 }, pointsEarned: 7, pickWinner: null, winner: null }] });
  const r = ranking({ entries: [{ rank: 1, userId: 1, displayName: "Tú", points: 12, delta: null, isYou: true, isBot: false }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: nowGroupsLive });
  expect(s.focus.kind).toBe("live");
  // a match is played -> recap shows results, not the pre-kickoff fallback
  expect(s.recap.kind).toBe("results");
  if (s.recap.kind === "results") expect(s.recap.recent[0].pointsEarned).toBe(7);
});

it("CHAMPION when every match is played, with payout for a prize rank", () => {
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({ entries: [{ rank: 2, userId: 1, displayName: "Tú", points: 96, delta: null, isYou: true, isBot: false }] });
  const m = matches({ past: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, yourPick: null, pointsEarned: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: Date.parse("2026-07-20T00:00:00Z") });
  expect(s.focus.kind).toBe("champion");
  if (s.focus.kind === "champion") expect(s.focus.payoutCents).toBe(3900);
});

it("standing reports your rank and whether anyone has scored", () => {
  const s = computeHomeState({ bracket: bracket(), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.standing).not.toBeNull();
  expect(s.standing!.hasScored).toBe(false); // everyone at 0 pts
});

it("phase chips mark groups open / done and knockouts locked", () => {
  const s = computeHomeState({ bracket: bracket({ groups: [{ code: "A", filled: 0, total: 6, locked: false, matches: [] }] }), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  const group = s.chips.find((c) => c.code === "GROUP")!;
  expect(group.state).toBe("open");
  expect(group.href).toBe("/groups");
  expect(s.chips.find((c) => c.code === "R32")!.state).toBe("locked");
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd frontend && pnpm vitest run lib/home-phase.test.ts`
Expected: FAIL — `computeHomeState` is not defined.

- [ ] **Step 3: Implement `lib/home-phase.ts`**

Create `frontend/lib/home-phase.ts`:

```ts
import type { BracketView } from "./api/bracket";
import type { RankingView } from "./api/ranking";
import type { MatchesView, MatchView } from "./api/matches";
import type { PublicSummary } from "./api/summary";

export type ChipState = "done" | "open" | "locked";
export type PhaseChip = { code: string; state: ChipState; href: string };

export type FocusState =
  | { kind: "fillGroup"; filled: number; total: number; full: boolean; deadline: string | null; href: string }
  | { kind: "fillKnockout"; roundCode: string; roundName: string; filled: number; total: number; full: boolean; deadline: string | null; href: string }
  | { kind: "live"; phase: "group" | "knockout"; rank: number | null; points: number | null }
  | { kind: "champion"; rank: number | null; points: number | null; payoutCents: number | null };

export type Standing = { rank: number; points: number; hasScored: boolean } | null;

export type RecapResult = {
  matchId: number;
  t1Code: string | null; t1Flag: string | null;
  t2Code: string | null; t2Flag: string | null;
  s1: number | null; s2: number | null;
  pointsEarned: number | null;
};
export type RecapNext = {
  t1Code: string | null; t1Flag: string | null;
  t2Code: string | null; t2Flag: string | null;
  kickoffAt: string;
} | null;
export type Recap =
  | { kind: "results"; recent: RecapResult[]; next: RecapNext }
  | { kind: "preKickoff"; potCents: number; currency: string; panaCount: number; startDate: string };

export type HomeState = { focus: FocusState; chips: PhaseChip[]; standing: Standing; recap: Recap };

function before(deadlineIso: string | null, nowMs: number): boolean {
  return deadlineIso == null || nowMs < Date.parse(deadlineIso);
}

export function computeHomeState(args: {
  bracket: BracketView;
  ranking: RankingView;
  matches: MatchesView;
  summary: PublicSummary;
  nowMs: number;
}): HomeState {
  const { bracket, ranking, matches, summary, nowMs } = args;

  const me = ranking.entries.find((e) => e.isYou) ?? null;
  const hasScored = ranking.entries.some((e) => e.points > 0);
  const standing: Standing = me ? { rank: me.rank, points: me.points, hasScored } : null;

  const groupOpen = before(bracket.groupStageDeadline, nowMs);
  const groupFilled = bracket.groups.reduce((a, g) => a + g.filled, 0);
  const groupTotal = bracket.groups.reduce((a, g) => a + g.total, 0);

  // First knockout round (by array order = sequence) that is open to bet.
  const openKnockout = bracket.knockouts.find((k) => k.unlocked && !k.locked);
  const allPlayed = matches.past.length > 0 && matches.today.length === 0 && matches.upcoming.length === 0;

  // ── Focus (priority order) ────────────────────────────────────────────────
  let focus: FocusState;
  if (groupOpen) {
    focus = { kind: "fillGroup", filled: groupFilled, total: groupTotal, full: groupTotal > 0 && groupFilled >= groupTotal, deadline: bracket.groupStageDeadline, href: "/groups" };
  } else if (openKnockout) {
    focus = { kind: "fillKnockout", roundCode: openKnockout.code, roundName: openKnockout.name, filled: openKnockout.filled, total: openKnockout.total, full: openKnockout.total > 0 && openKnockout.filled >= openKnockout.total, deadline: bracket.knockoutDeadline, href: `/knockout/${openKnockout.code}` };
  } else if (allPlayed) {
    const payoutCents = me && me.rank <= summary.prizeSplit.length ? summary.prizeSplit[me.rank - 1].payoutCents : null;
    focus = { kind: "champion", rank: me?.rank ?? null, points: me?.points ?? null, payoutCents };
  } else {
    const inKnockout = bracket.knockouts.some((k) => k.unlocked);
    focus = { kind: "live", phase: inKnockout ? "knockout" : "group", rank: me?.rank ?? null, points: me?.points ?? null };
  }

  // ── Phase chips ───────────────────────────────────────────────────────────
  const chips: PhaseChip[] = [];
  chips.push({ code: "GROUP", state: groupOpen ? "open" : "done", href: "/groups" });
  for (const k of bracket.knockouts) {
    let state: ChipState;
    if (k.locked || (k.total > 0 && k.filled >= k.total && !openKnockout)) state = "done";
    else if (openKnockout && k.code === openKnockout.code) state = "open";
    else if (k.unlocked) state = "done"; // unlocked, past — treat as reachable/done
    else state = "locked";
    chips.push({ code: k.code, state, href: `/knockout/${k.code}` });
  }

  // ── Recap ─────────────────────────────────────────────────────────────────
  let recap: Recap;
  if (matches.past.length === 0) {
    recap = { kind: "preKickoff", potCents: summary.pool.potCents, currency: summary.pool.currency, panaCount: summary.pool.panaCount, startDate: summary.tournament.startDate };
  } else {
    const recent = [...matches.past]
      .sort((a, b) => Date.parse(b.kickoffAt) - Date.parse(a.kickoffAt))
      .slice(0, 2)
      .map((m: MatchView): RecapResult => ({ matchId: m.id, t1Code: m.team1.code, t1Flag: m.team1.flag, t2Code: m.team2.code, t2Flag: m.team2.flag, s1: m.score?.t1 ?? null, s2: m.score?.t2 ?? null, pointsEarned: m.pointsEarned }));
    const upcoming = [...matches.today, ...matches.upcoming].sort((a, b) => Date.parse(a.kickoffAt) - Date.parse(b.kickoffAt));
    const n = upcoming[0];
    const next: RecapNext = n ? { t1Code: n.team1.code, t1Flag: n.team1.flag, t2Code: n.team2.code, t2Flag: n.team2.flag, kickoffAt: n.kickoffAt } : null;
    recap = { kind: "results", recent, next };
  }

  return { focus, chips, standing, recap };
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd frontend && pnpm vitest run lib/home-phase.test.ts`
Expected: PASS — all 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/home-phase.ts frontend/lib/home-phase.test.ts
git commit -m "feat(home): pure computeHomeState phase-priority function"
```

---

## Task 3: `StandingStrip` component

**Files:**
- Create: `frontend/components/lobby/StandingStrip.tsx`
- Test: `frontend/components/lobby/StandingStrip.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/components/lobby/StandingStrip.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import { StandingStrip } from "./StandingStrip";

const messages = { home: { tuPuesto: "Tu puesto", verTabla: "Ver tabla →", standingNoScore: "Aún sin puntos" } };
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("shows rank, points and a medal once someone has scored", () => {
  r(<StandingStrip standing={{ rank: 2, points: 88, hasScored: true }} />);
  expect(screen.getByText(/#2/)).toBeInTheDocument();
  expect(screen.getByText(/88/)).toBeInTheDocument();
  expect(screen.getByText("🥈", { exact: false })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /ver tabla/i })).toHaveAttribute("href", "/ranking");
});

it("shows no medal before anyone has scored", () => {
  r(<StandingStrip standing={{ rank: 1, points: 0, hasScored: false }} />);
  expect(screen.queryByText("🥇", { exact: false })).not.toBeInTheDocument();
});

it("renders nothing when there is no standing", () => {
  const { container } = r(<StandingStrip standing={null} />);
  expect(container).toBeEmptyDOMElement();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm vitest run components/lobby/StandingStrip.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `StandingStrip.tsx`**

Create `frontend/components/lobby/StandingStrip.tsx`:

```tsx
import Link from "next/link";
import { useTranslations } from "next-intl";
import type { Standing } from "@/lib/home-phase";

const MEDAL: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

export function StandingStrip({ standing }: { standing: Standing }) {
  const t = useTranslations("home");
  if (!standing) return null;
  const medal = standing.hasScored ? MEDAL[standing.rank] : undefined;

  return (
    <section className="mx-3 mt-4">
      <div className="chrome-label chrome-label-muted">{t("tuPuesto")}</div>
      <div className="mt-1 flex items-center justify-between border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5">
        <span className="font-display text-lg font-extrabold tracking-tight text-[var(--color-text-primary)]">
          {medal ? `${medal} ` : ""}#{standing.rank} · {standing.points} pts
        </span>
        <Link href="/ranking" className="chrome-label text-[var(--color-accent-red)]">
          {t("verTabla")}
        </Link>
      </div>
    </section>
  );
}
```

Note: `useTranslations` works in a Client Component or a Server Component via next-intl; this component has no client interactivity, so it renders fine as a server component within the home page.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && pnpm vitest run components/lobby/StandingStrip.test.tsx`
Expected: PASS — 3 tests green.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/lobby/StandingStrip.tsx frontend/components/lobby/StandingStrip.test.tsx
git commit -m "feat(home): StandingStrip component"
```

---

## Task 4: `PhaseRail` component

**Files:**
- Create: `frontend/components/lobby/PhaseRail.tsx`
- Test: `frontend/components/lobby/PhaseRail.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/components/lobby/PhaseRail.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { PhaseRail } from "./PhaseRail";
import type { PhaseChip } from "@/lib/home-phase";

const messages = { home: { chipGROUP: "Grupos", chipR32: "16vos", chipFINAL: "Final" } };
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("renders a chip per phase with the right link and a status glyph", () => {
  const chips: PhaseChip[] = [
    { code: "GROUP", state: "done", href: "/groups" },
    { code: "R32", state: "open", href: "/knockout/R32" },
    { code: "FINAL", state: "locked", href: "/knockout/FINAL" },
  ];
  r(<PhaseRail chips={chips} />);
  expect(screen.getByRole("link", { name: /grupos/i })).toHaveAttribute("href", "/groups");
  expect(screen.getByRole("link", { name: /16vos/i })).toHaveAttribute("href", "/knockout/R32");
  expect(screen.getByRole("link", { name: /final/i })).toHaveAttribute("href", "/knockout/FINAL");
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm vitest run components/lobby/PhaseRail.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `PhaseRail.tsx`**

Create `frontend/components/lobby/PhaseRail.tsx`:

```tsx
import Link from "next/link";
import { useTranslations } from "next-intl";
import type { PhaseChip } from "@/lib/home-phase";

const GLYPH: Record<PhaseChip["state"], string> = { done: "✓", open: "●", locked: "🔒" };

export function PhaseRail({ chips }: { chips: PhaseChip[] }) {
  const t = useTranslations("home");
  return (
    <section className="mx-3 mt-4">
      <div className="flex flex-wrap gap-1.5">
        {chips.map((c) => {
          const tone =
            c.state === "open"
              ? "bg-[var(--color-accent-gold)] text-[var(--color-text-primary)] border-[var(--color-line-ink)]"
              : c.state === "done"
                ? "bg-[var(--color-accent-green)] text-[var(--color-text-inverse)] border-[var(--color-accent-green)]"
                : "bg-[var(--color-bg-paper)] text-[var(--color-text-muted)] border-[#bbb]";
          return (
            <Link
              key={c.code}
              href={c.href}
              className={`border-[1.5px] px-2 py-1 font-display text-[10px] font-extrabold uppercase tracking-[0.04em] ${tone}`}
            >
              {t(`chip${c.code}` as never)} {GLYPH[c.state]}
            </Link>
          );
        })}
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && pnpm vitest run components/lobby/PhaseRail.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/lobby/PhaseRail.tsx frontend/components/lobby/PhaseRail.test.tsx
git commit -m "feat(home): PhaseRail component"
```

---

## Task 5: `ResultsRecap` component

**Files:**
- Create: `frontend/components/lobby/ResultsRecap.tsx`
- Test: `frontend/components/lobby/ResultsRecap.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/components/lobby/ResultsRecap.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { ResultsRecap } from "./ResultsRecap";
import type { Recap } from "@/lib/home-phase";

const messages = { home: { resultadosProximos: "Resultados & próximos", verPartidos: "Ver partidos →", tagFinal: "Final", preKickoffPot: "Pozo {amount} · {panas} panas", preKickoffCountdown: "Faltan {days} días para el pitazo" } };
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("renders recent finals with points earned and the next fixture", () => {
  const recap: Recap = {
    kind: "results",
    recent: [{ matchId: 1, t1Code: "ARG", t1Flag: "🇦🇷", t2Code: "ITA", t2Flag: "🇮🇹", s1: 3, s2: 1, pointsEarned: 10 }],
    next: { t1Code: "FRA", t1Flag: "🇫🇷", t2Code: "JPN", t2Flag: "🇯🇵", kickoffAt: "2026-06-20T20:00:00Z" },
  };
  r(<ResultsRecap recap={recap} timeZone="America/Bogota" />);
  expect(screen.getByText(/ARG/)).toBeInTheDocument();
  expect(screen.getByText(/\+10/)).toBeInTheDocument();
  expect(screen.getByText(/FRA/)).toBeInTheDocument();
});

it("renders pot + countdown before any match is played", () => {
  const recap: Recap = { kind: "preKickoff", potCents: 26000, currency: "USD", panaCount: 13, startDate: "2026-06-11" };
  r(<ResultsRecap recap={recap} timeZone="America/Bogota" />);
  expect(screen.getByText(/Pozo/)).toBeInTheDocument();
  expect(screen.getByText(/13 panas/)).toBeInTheDocument();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm vitest run components/lobby/ResultsRecap.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `ResultsRecap.tsx`**

Create `frontend/components/lobby/ResultsRecap.tsx`. Reuse the existing formatters: `formatMatchDateTime(iso, tz)` from `@/lib/format-datetime`, `formatPot(cents, currency)` and `daysUntil(dateStr)` from `@/lib/tournament-format`.

```tsx
import Link from "next/link";
import { useTranslations } from "next-intl";
import type { Recap } from "@/lib/home-phase";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { formatPot, daysUntil } from "@/lib/tournament-format";

export function ResultsRecap({ recap, timeZone }: { recap: Recap; timeZone: string }) {
  const t = useTranslations("home");

  if (recap.kind === "preKickoff") {
    return (
      <section className="mx-3 mt-4">
        <div className="flex items-center justify-between border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-sm font-extrabold text-[var(--color-text-primary)]">
          <span>{t("preKickoffPot", { amount: formatPot(recap.potCents, recap.currency), panas: recap.panaCount })}</span>
          <span className="text-[var(--color-accent-red)]">{t("preKickoffCountdown", { days: daysUntil(recap.startDate) })}</span>
        </div>
      </section>
    );
  }

  return (
    <section className="mx-3 mt-4">
      <div className="flex items-baseline justify-between">
        <span className="chrome-label chrome-label-muted">{t("resultadosProximos")}</span>
        <Link href="/matches" className="chrome-label text-[var(--color-accent-red)]">{t("verPartidos")}</Link>
      </div>
      <div className="mt-1 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
        {recap.recent.map((m) => (
          <div key={m.matchId} className="flex items-center justify-between border-b border-dashed border-[#ccc] px-3 py-2 text-sm font-bold last:border-b-0">
            <span>
              <span className="chrome-label chrome-label-muted mr-1.5">{t("tagFinal")}</span>
              {m.t1Flag} {m.t1Code} {m.s1}–{m.s2} {m.t2Code} {m.t2Flag}
            </span>
            {m.pointsEarned != null && (
              <span className="font-display font-black text-[var(--color-accent-green)]">+{m.pointsEarned}</span>
            )}
          </div>
        ))}
        {recap.next && (
          <div className="flex items-center justify-between bg-[#faf7ef] px-3 py-2 text-sm font-bold">
            <span>
              <span className="chrome-label chrome-label-muted mr-1.5">{formatMatchDateTime(recap.next.kickoffAt, timeZone)}</span>
              {recap.next.t1Flag} {recap.next.t1Code} – {recap.next.t2Code} {recap.next.t2Flag}
            </span>
            <span className="text-[var(--color-text-muted)]">·</span>
          </div>
        )}
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Verify the helper signatures before running**

Run: `cd frontend && grep -nE "export function (formatMatchDateTime|formatPot|daysUntil)" lib/format-datetime.ts lib/tournament-format.ts`
Expected: all three exist. If `formatMatchDateTime` lives elsewhere or has a different arg order, adjust the import/call to match (it is already used this way in `components/group/GroupDrillIn.tsx`).

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd frontend && pnpm vitest run components/lobby/ResultsRecap.test.tsx`
Expected: PASS — 2 tests green.

- [ ] **Step 6: Commit**

```bash
git add frontend/components/lobby/ResultsRecap.tsx frontend/components/lobby/ResultsRecap.test.tsx
git commit -m "feat(home): ResultsRecap component (finals + next, pre-kickoff fallback)"
```

---

## Task 6: `FocusCard` component

**Files:**
- Create: `frontend/components/lobby/FocusCard.tsx`
- Test: `frontend/components/lobby/FocusCard.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/components/lobby/FocusCard.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { FocusCard } from "./FocusCard";
import type { FocusState } from "@/lib/home-phase";

const messages = {
  home: {
    fillGroupKicker: "Cierra en {when}", fillGroupHeadline: "Llena tus grupos", fillSub: "{filled}/{total} listos",
    fillGroupCta: "Seguir llenando →", fillGroupCtaEmpty: "Empezar →",
    fillKnockoutKicker: "Ronda abierta · cierra en {when}", fillKnockoutHeadline: "{round} · llena tu llave", fillKnockoutCta: "Llenar {round} →",
    liveHeadline: "En juego", livePhaseGroup: "Fase de grupos", livePhaseKnockout: "Eliminatorias", liveSub: "Tu puesto #{rank} · {points} pts", liveSubNoScore: "Aún sin puntos",
    championKicker: "Torneo terminado", championHeadline: "{rank}º puesto", championSub: "{points} pts", championSubPayout: "{points} pts · ganaste {amount}",
  },
};
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("fillGroup shows the headline + a CTA to /groups", () => {
  const f: FocusState = { kind: "fillGroup", filled: 4, total: 6, full: false, deadline: "2026-06-11T17:00:00Z", href: "/groups" };
  r(<FocusCard focus={f} timeZone="America/Bogota" />);
  expect(screen.getByText("Llena tus grupos")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /llenando/i })).toHaveAttribute("href", "/groups");
});

it("fillKnockout interpolates the round name into headline and CTA", () => {
  const f: FocusState = { kind: "fillKnockout", roundCode: "R32", roundName: "Dieciseisavos", filled: 3, total: 16, full: false, deadline: "2026-06-28T17:00:00Z", href: "/knockout/R32" };
  r(<FocusCard focus={f} timeZone="America/Bogota" />);
  expect(screen.getByText(/Dieciseisavos · llena tu llave/)).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /llenar dieciseisavos/i })).toHaveAttribute("href", "/knockout/R32");
});

it("champion shows place + payout and has no CTA", () => {
  const f: FocusState = { kind: "champion", rank: 2, points: 96, payoutCents: 3900 };
  r(<FocusCard focus={f} timeZone="America/Bogota" />);
  expect(screen.getByText("2º puesto")).toBeInTheDocument();
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm vitest run components/lobby/FocusCard.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `FocusCard.tsx`**

Create `frontend/components/lobby/FocusCard.tsx`. Uses `deadlineShort(iso, tz)` and `formatPot` from `@/lib/tournament-format` (already used by the current home).

```tsx
import Link from "next/link";
import { useTranslations } from "next-intl";
import type { FocusState } from "@/lib/home-phase";
import { deadlineShort } from "@/lib/tournament-format";

function Shell({ kicker, headline, sub, cta, ghost }: { kicker: string; headline: string; sub?: string; cta?: { label: string; href: string }; ghost?: string }) {
  return (
    <section className="relative mx-3 mt-4 overflow-hidden bg-[var(--color-bg-ink)] p-4 text-[var(--color-text-inverse)]">
      {ghost && (
        <div aria-hidden="true" className="pointer-events-none absolute right-[-8px] top-[-16px] font-display text-[120px] font-black leading-none tracking-[-0.08em] text-[var(--color-accent-red)] opacity-90">{ghost}</div>
      )}
      <div className="relative">
        <span className="chrome-label text-[var(--color-accent-gold)]">{kicker}</span>
        <div className="mt-1 font-display text-[28px] font-black uppercase leading-[0.95] tracking-[-0.03em]">{headline}</div>
        {sub && <div className="chrome-label mt-2 text-white/70">{sub}</div>}
        {cta && (
          <Link href={cta.href} className="mt-3 block bg-[var(--color-accent-red)] px-4 py-2.5 text-center font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-gold)] hover:text-[var(--color-text-primary)]">
            {cta.label}
          </Link>
        )}
      </div>
    </section>
  );
}

export function FocusCard({ focus, timeZone }: { focus: FocusState; timeZone: string }) {
  const t = useTranslations("home");

  switch (focus.kind) {
    case "fillGroup": {
      const when = focus.deadline ? deadlineShort(focus.deadline, timeZone) : "";
      if (focus.full) {
        return <Shell kicker={t("fillDoneKicker")} headline={t("fillGroupDoneHeadline")} sub={t("fillDoneSub", { when })} />;
      }
      return <Shell kicker={t("fillGroupKicker", { when })} headline={t("fillGroupHeadline")} sub={t("fillSub", { filled: focus.filled, total: focus.total })} cta={{ label: focus.filled === 0 ? t("fillGroupCtaEmpty") : t("fillGroupCta"), href: focus.href }} />;
    }
    case "fillKnockout": {
      const when = focus.deadline ? deadlineShort(focus.deadline, timeZone) : "";
      if (focus.full) {
        return <Shell kicker={t("fillKnockoutKicker", { when })} headline={t("fillKnockoutDoneHeadline", { round: focus.roundName })} sub={t("fillSub", { filled: focus.filled, total: focus.total })} />;
      }
      return <Shell kicker={t("fillKnockoutKicker", { when })} headline={t("fillKnockoutHeadline", { round: focus.roundName })} sub={t("fillSub", { filled: focus.filled, total: focus.total })} cta={{ label: t("fillKnockoutCta", { round: focus.roundName }), href: focus.href }} />;
    }
    case "live": {
      const phase = focus.phase === "group" ? t("livePhaseGroup") : t("livePhaseKnockout");
      const sub = focus.rank != null ? t("liveSub", { rank: focus.rank, points: focus.points ?? 0 }) : t("liveSubNoScore");
      return <Shell kicker={phase} headline={t("liveHeadline")} sub={sub} />;
    }
    case "champion": {
      const headline = focus.rank != null ? t("championHeadline", { rank: focus.rank }) : t("liveHeadline");
      const sub = focus.payoutCents != null
        ? t("championSubPayout", { points: focus.points ?? 0, amount: `$${(focus.payoutCents / 100).toFixed(0)}` })
        : t("championSub", { points: focus.points ?? 0 });
      return <Shell kicker={t("championKicker")} headline={headline} sub={sub} ghost="🏆" />;
    }
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && pnpm vitest run components/lobby/FocusCard.test.tsx`
Expected: PASS — 3 tests green.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/lobby/FocusCard.tsx frontend/components/lobby/FocusCard.test.tsx
git commit -m "feat(home): FocusCard adaptive component"
```

---

## Task 7: `/groups` index page (move group grid + Paul-fill off the home)

**Files:**
- Create: `frontend/app/groups/page.tsx`

This page reproduces exactly the group grid + action row the home renders today, so nothing is lost when the home becomes a dashboard. It reuses `GroupCard`, `PaulFillAllButton`, `InviteFriendsButton`, and the `teamPreview` helper.

- [ ] **Step 1: Create the page**

Create `frontend/app/groups/page.tsx`:

```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket, type MatchView } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { GroupCard } from "@/components/lobby/GroupCard";
import { PaulFillAllButton } from "@/components/lobby/PaulFillAllButton";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";
import { deadlineShort } from "@/lib/tournament-format";

export default async function GroupsPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket] = await Promise.all([getMe(), getMyBracket()]);
  const t = await getTranslations("lobby");

  const groupLockedLabel = bracket.groupStageDeadline
    ? t("groupLockedPill", { when: deadlineShort(bracket.groupStageDeadline, me.timezone) })
    : "🔒";

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("groupsHeading")} meta={`${bracket.totalBets}/${bracket.totalMatches}`} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <section className="mx-3 mt-4">
          <div className="grid grid-cols-2 gap-2">
            {bracket.groups.map((g) => (
              <GroupCard
                key={g.code}
                letter={g.code}
                filled={g.filled}
                total={g.total}
                teams={teamPreview(g.matches)}
                locked={g.locked}
                lockedLabel={groupLockedLabel}
              />
            ))}
          </div>
        </section>
        <section className="mx-3 mt-4 flex flex-col gap-2 sm:flex-row">
          <div className="flex-1"><PaulFillAllButton /></div>
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

function teamPreview(matches: ReadonlyArray<MatchView>) {
  const seen = new Map<string, { code: string; flag: string | null }>();
  for (const m of matches) {
    if (m.team1Code && !seen.has(m.team1Code)) seen.set(m.team1Code, { code: m.team1Code, flag: m.team1Flag });
    if (m.team2Code && !seen.has(m.team2Code)) seen.set(m.team2Code, { code: m.team2Code, flag: m.team2Flag });
    if (seen.size === 4) break;
  }
  return Array.from(seen.values()).slice(0, 4);
}
```

- [ ] **Step 2: Verify `GroupCard`'s prop names match**

Run: `cd frontend && grep -nE "letter|filled|total|teams|locked|lockedLabel" components/lobby/GroupCard.tsx | head`
Expected: the `GroupCard` prop interface uses these names (it's the same call the current home makes). If `teamPreview`'s shape differs from what `GroupCard` expects for `teams`, copy the exact prop type from `GroupCard.tsx`.

- [ ] **Step 3: Typecheck**

Run: `cd frontend && pnpm typecheck`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/app/groups/page.tsx
git commit -m "feat(groups): /groups index page (group grid + Paul fill, moved off home)"
```

---

## Task 8: Rewrite the home page as the dashboard

**Files:**
- Modify: `frontend/app/home/page.tsx`
- Modify: `frontend/e2e/smoke.e2e.ts`

- [ ] **Step 1: Replace `app/home/page.tsx`**

Replace the whole file with:

```tsx
import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket } from "@/lib/api/bracket";
import { getRanking } from "@/lib/api/ranking";
import { getMatches } from "@/lib/api/matches";
import { getPublicSummary } from "@/lib/api/summary";
import { computeHomeState } from "@/lib/home-phase";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { FocusCard } from "@/components/lobby/FocusCard";
import { PhaseRail } from "@/components/lobby/PhaseRail";
import { StandingStrip } from "@/components/lobby/StandingStrip";
import { ResultsRecap } from "@/components/lobby/ResultsRecap";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";

export default async function HomePage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket, ranking, matches, summary] = await Promise.all([
    getMe(),
    getMyBracket(),
    getRanking(),
    getMatches(),
    getPublicSummary(),
  ]);

  const state = computeHomeState({
    bracket,
    ranking,
    matches,
    summary,
    nowMs: Date.parse(matches.serverTime),
  });

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <FocusCard focus={state.focus} timeZone={me.timezone} />
        <PhaseRail chips={state.chips} />
        <StandingStrip standing={state.standing} />
        <ResultsRecap recap={state.recap} timeZone={me.timezone} />
        <section className="mx-3 mt-4">
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}
```

- [ ] **Step 2: Typecheck + lint**

Run: `cd frontend && pnpm typecheck && pnpm lint`
Expected: typecheck clean; lint shows only the pre-existing warnings (custom-font + coverage files), 0 errors. If `getRanking` requires the user to be a pool member and can 404 for brand-new users, wrap it the way other pages do — but it is the same call `/ranking` and `/compare` make for an authed user, so it is safe here.

- [ ] **Step 3: Update the e2e smoke test**

Open `frontend/e2e/smoke.e2e.ts`. The current assertion checks the landing page render + axe scan. Find any assertion that targeted the old home group grid (e.g. text "Fase de grupos" as an `h2` on `/home`) and replace it with an assertion that `/home` renders the focus card headline. Add/adjust:

```ts
// On /home (authed path is mocked in this suite as today): the dashboard
// shows the adaptive focus card. Before kickoff that's the group-fill headline.
// (Match the es-CO copy: "Llena tus grupos".)
await expect(page.getByText("Llena tus grupos")).toBeVisible();
```

If the smoke test does not currently authenticate into `/home` (it may only cover the public landing), leave it unchanged and instead rely on the component unit tests — note that in the commit message.

- [ ] **Step 4: Run the unit + e2e suites**

Run: `cd frontend && pnpm vitest run`
Expected: all unit tests pass (including the new home-phase + component tests).

Run: `cd frontend && pnpm e2e` (if the environment has Playwright browsers installed; otherwise CI runs it).
Expected: smoke passes.

- [ ] **Step 5: Commit**

```bash
git add frontend/app/home/page.tsx frontend/e2e/smoke.e2e.ts
git commit -m "feat(home): rewrite home as adaptive phase dashboard"
```

---

## Task 9: Manual verification + cleanup

**Files:** none (verification only)

- [ ] **Step 1: Run the app and eyeball the home in the current (pre-kickoff) phase**

Use the project's run path (`/run` skill or `bin/dev-*.sh`). Confirm on `/home`:
- Focus card = "Llena tus grupos · {filled}/{total} · cierra en …" with a CTA to `/groups`.
- Phase rail shows Grupos ● + the knockout rounds 🔒.
- Tu puesto strip renders your rank (or "Aún sin puntos" pre-scoring) + "Ver tabla →" → `/ranking`.
- Recap shows the pot + countdown (pre-kickoff fallback).
- `/groups` shows the 12 group cards + "Paul llena todo" + invite, and tapping a group still opens `/group/{code}`.

- [ ] **Step 2: Confirm nothing else linked to the old home group grid**

Run: `cd frontend && grep -rn "GroupCard\|teamPreview" app/ components/ | grep -v "app/groups/"`
Expected: only `components/lobby/GroupCard.tsx` (the component itself) and the `/groups` page reference it. If anything else imported the old home grid, update it.

- [ ] **Step 3: Full local CI parity check**

Run: `cd frontend && pnpm typecheck && pnpm lint && pnpm vitest run`
Expected: all green (lint: 0 errors).

- [ ] **Step 4: Final commit (if any cleanup was needed)**

```bash
git add -A
git commit -m "chore(home): cleanup after dashboard rewrite"
```

---

## Notes for the implementer

- **No backend changes.** Every value comes from `getMyBracket`, `getRanking`, `getMatches`, `getPublicSummary`, `getMe`.
- **Two different `MatchView` types exist.** The bracket one (`@/lib/api/bracket`) has `betScoreT1`/`actualScoreT1`/`team1Code`; the matches one (`@/lib/api/matches`) has `score`/`yourPick`/`pointsEarned`/`team1`. The recap uses the **matches** one (it has `pointsEarned`); `teamPreview` on `/groups` uses the **bracket** one.
- **`now`** comes from `matches.serverTime` (server-authoritative), not the client clock.
- **i18n chip keys** are `home.chip{CODE}` (e.g. `chipR32`); add a key if a new round code ever appears.
- **Out of scope** (do not build here): live in-play scores (no data), per-round knockout multipliers (separate BACKLOG item), rank-delta arrows (ranking `delta` is null in v1).
```

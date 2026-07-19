# Final Results — Winners Banner + Prize-Rank Payout Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix a live bug where prize payouts are computed from the raw (bot-inclusive) leaderboard rank instead of a prize-eligible rank — wrong today on both `/ranking` (Pulpo Paul's row shows the real winner's gold medal + $320) and `/home`'s personal champion card — via one shared fix, then add a `WinnersBanner` naming the real winners (including the human 3-way... 2-way tie at 3rd) on `/home`.

**Architecture:** Frontend-only, `frontend/` package. `lib/ranking-payouts.ts` gains a shared `computePrizeRanks` primitive (bots excluded, ties grouped) that both the existing `/ranking` payout labels and a new `/home` winners derivation consume. No backend/API changes — everything derives from data already fetched (`ranking.entries` already carries `isBot` per entry; admins already excluded server-side).

**Tech Stack:** Next.js 16 (App Router, RSC), TypeScript, next-intl (en / es-CO), Vitest + Testing Library.

## Global Constraints

- No backend (Java) changes.
- Locale: add every new string to **both** `frontend/messages/en.json` and `frontend/messages/es-CO.json`, under the existing `"home"` namespace, flat key style (no nesting).
- `home-phase.ts` stays the only place with `/home`-specific derivation logic; `ranking-payouts.ts` stays the shared prize-rank source of truth used by both `/ranking` and `/home` — do not duplicate the rank-grouping logic in a second place.
- Currency formatting on the new banner: `` `$${(cents / 100).toFixed(0)}` `` — matches the existing inline pattern in `FocusCard.tsx`. (The existing `/ranking` labels already use `formatPot` from `lib/tournament-format.ts` — keep using that there, don't switch it.)
- Design doc: `docs/superpowers/specs/2026-07-19-final-results-winners-design.md`.

---

### Task 1: Fix `buildPayoutLabels` at its source (`lib/ranking-payouts.ts`) — the `/ranking` bug

**Files:**
- Modify: `frontend/lib/ranking-payouts.ts`
- Modify: `frontend/lib/ranking-payouts.test.ts`
- Modify: `frontend/app/ranking/page.tsx:84`

**Interfaces:**
- Consumes: `RankingEntry` (`{ rank, userId, displayName, points, delta, isYou, isBot }`, from `@/lib/api/ranking`), `PrizeSplitEntry` (`{ rank, percentage, payoutCents }`, from `@/lib/api/summary`).
- Produces (used by Task 2):
  - `export type PrizeRankGroup = { rank: number; entries: RankingEntry[] }`
  - `export function computePrizeRanks(entries: RankingEntry[], prizeRankCount: number): PrizeRankGroup[]`
- `buildPayoutLabels`'s return type is unchanged (`Map<number, string>`) but the **key changes from rank to `userId`** — this is a breaking contract change for its one caller, updated in this same task.

- [ ] **Step 1: Write the failing tests**

Replace the full contents of `frontend/lib/ranking-payouts.test.ts` with:

```ts
import { describe, expect, it } from "vitest";
import { buildPayoutLabels, computePrizeRanks } from "./ranking-payouts";
import type { RankingEntry } from "@/lib/api/ranking";
import type { PrizeSplitEntry } from "@/lib/api/summary";

function entry(overrides: Partial<RankingEntry> = {}): RankingEntry {
  return {
    rank: 1,
    userId: 1,
    displayName: "Jugador",
    points: 10,
    delta: null,
    isYou: false,
    isBot: false,
    ...overrides,
  };
}

const split: PrizeSplitEntry[] = [
  { rank: 1, percentage: 80, payoutCents: 20800 },
  { rank: 2, percentage: 15, payoutCents: 3900 },
  { rank: 3, percentage: 5, payoutCents: 1300 },
];

describe("buildPayoutLabels", () => {
  it("shows no labels when nobody has scored (all points 0)", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 0 }),
      entry({ userId: 2, rank: 1, points: 0 }),
      entry({ userId: 3, rank: 1, points: 0 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.size).toBe(0);
  });

  it("shows medal + amount for distinct top-3, keyed by userId", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 30 }),
      entry({ userId: 2, rank: 2, points: 20 }),
      entry({ userId: 3, rank: 3, points: 10 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.get(1)).toBe("🥇 $208");
    expect(labels.get(2)).toBe("🥈 $39");
    expect(labels.get(3)).toBe("🥉 $13");
  });

  it("shows the medal only (no amount) for a tied prize rank", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 30 }),
      entry({ userId: 2, rank: 1, points: 30 }),
      entry({ userId: 3, rank: 3, points: 10 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.get(1)).toBe("🥇");
    expect(labels.get(2)).toBe("🥇");
    // The untied bronze position still shows its amount.
    expect(labels.get(3)).toBe("🥉 $13");
  });

  it("ignores the bot when deciding whether standings are real", () => {
    const entries = [
      entry({ userId: 99, rank: 1, points: 50, isBot: true }),
      entry({ userId: 1, rank: 2, points: 0 }),
      entry({ userId: 2, rank: 2, points: 0 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.size).toBe(0);
  });

  it("attaches the gold label to the real #1 human, not a bot sitting at raw rank 1", () => {
    // Mirrors prod: Pulpo Paul tops the raw leaderboard but is never
    // prize-eligible — the human directly below him is the real #1.
    const entries = [
      entry({ userId: 99, rank: 1, displayName: "Pulpo Paul 🐙", points: 664, isBot: true }),
      entry({ userId: 5, rank: 2, displayName: "José Manuel", points: 627 }),
      entry({ userId: 6, rank: 3, displayName: "Arturo", points: 620 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.get(5)).toBe("🥇 $208");
    expect(labels.get(6)).toBe("🥈 $39");
    expect(labels.has(99)).toBe(false);
  });
});

describe("computePrizeRanks", () => {
  it("skips bots and assigns prize ranks based on position among humans only", () => {
    const entries = [
      entry({ userId: 99, rank: 1, points: 200, isBot: true }),
      entry({ userId: 1, rank: 2, points: 190 }),
      entry({ userId: 2, rank: 3, points: 180 }),
      entry({ userId: 3, rank: 4, points: 170 }),
    ];
    const groups = computePrizeRanks(entries, 3);
    expect(groups).toEqual([
      { rank: 1, entries: [entries[1]] },
      { rank: 2, entries: [entries[2]] },
      { rank: 3, entries: [entries[3]] },
    ]);
  });

  it("groups tied points under one rank and stops once the rank exceeds prizeRankCount", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 190 }),
      entry({ userId: 2, rank: 2, points: 180 }),
      entry({ userId: 3, rank: 3, points: 170 }),
      entry({ userId: 4, rank: 3, points: 170 }),
      entry({ userId: 5, rank: 5, points: 160 }),
    ];
    const groups = computePrizeRanks(entries, 3);
    expect(groups).toEqual([
      { rank: 1, entries: [entries[0]] },
      { rank: 2, entries: [entries[1]] },
      { rank: 3, entries: [entries[2], entries[3]] },
    ]);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && pnpm vitest run lib/ranking-payouts.test.ts`
Expected: FAIL — `computePrizeRanks` is not exported yet, and the "gold label to the real #1 human" test gets `undefined` for `labels.get(5)`.

- [ ] **Step 3: Implement `computePrizeRanks` and rewrite `buildPayoutLabels`**

Replace the full contents of `frontend/lib/ranking-payouts.ts` with:

```ts
import type { RankingEntry } from "@/lib/api/ranking";
import type { PrizeSplitEntry } from "@/lib/api/summary";
import { formatPot } from "@/lib/tournament-format";

const MEDALS: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

export type PrizeRankGroup = { rank: number; entries: RankingEntry[] };

/**
 * Groups prize-eligible entries (bots excluded) into competition-rank
 * positions 1..prizeRankCount. `entries` must already be sorted points DESC
 * (the ranking API guarantees this) — ties share a rank, same semantics as
 * SQL RANK(), recomputed over the bot-filtered subset so a bot occupying a
 * leaderboard slot never shifts a human's prize rank.
 */
export function computePrizeRanks(entries: RankingEntry[], prizeRankCount: number): PrizeRankGroup[] {
  const groups: PrizeRankGroup[] = [];
  let rank = 0;
  let lastPoints: number | null = null;
  let seen = 0;
  for (const e of entries) {
    if (e.isBot) continue;
    seen += 1;
    if (e.points !== lastPoints) {
      rank = seen;
      lastPoints = e.points;
    }
    if (rank > prizeRankCount) break;
    let group = groups.find((g) => g.rank === rank);
    if (!group) {
      group = { rank, entries: [] };
      groups.push(group);
    }
    group.entries.push(e);
  }
  return groups;
}

/**
 * Build "🥇 $24"-style labels keyed by userId for the prize positions (1–3),
 * applying two rules on top of the raw prize split:
 *
 *  - **No standings yet:** until at least one prize-eligible player has scored,
 *    return an empty map. Before the first result every player sits at rank 1
 *    with 0 points, so showing "🥇 $208" to all of them is meaningless.
 *  - **Tie at a prize rank:** show the medal only, no amount. The prize for a
 *    shared position is split between the tied players, so printing the full
 *    amount next to each would read as "everyone wins the whole thing".
 *
 * The bot (Pulpo Paul) is never prize-eligible, so it counts neither toward
 * "standings are real" nor toward a tie — and, via computePrizeRanks, never
 * occupies a prize position itself even when it tops the raw leaderboard.
 */
export function buildPayoutLabels(
  entries: RankingEntry[],
  prizeSplit: PrizeSplitEntry[],
  currency: string,
): Map<number, string> {
  const labels = new Map<number, string>();

  const standingsAreReal = entries.some((e) => !e.isBot && e.points > 0);
  if (!standingsAreReal) return labels;

  const prizeRanks = computePrizeRanks(entries, prizeSplit.length);
  for (const group of prizeRanks) {
    const split = prizeSplit[group.rank - 1];
    const medal = MEDALS[group.rank];
    if (!split || !medal) continue;
    const tied = group.entries.length > 1;
    const label = tied ? medal : `${medal} ${formatPot(split.payoutCents, currency)}`;
    for (const e of group.entries) labels.set(e.userId, label);
  }

  return labels;
}
```

- [ ] **Step 4: Fix the one caller — `frontend/app/ranking/page.tsx`**

Replace:

```tsx
                  payoutLabel={payoutByRank.get(e.rank)}
```

with:

```tsx
                  payoutLabel={payoutByRank.get(e.userId)}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && pnpm vitest run lib/ranking-payouts.test.ts`
Expected: PASS, all 7 tests.

- [ ] **Step 6: Typecheck**

Run: `cd frontend && pnpm typecheck`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
cd frontend && git add lib/ranking-payouts.ts lib/ranking-payouts.test.ts app/ranking/page.tsx
git commit -m "fix(ranking): key payout labels by userId so a bot at raw rank 1 doesn't steal the real winner's medal

buildPayoutLabels grouped/keyed by raw leaderboard rank, which includes
bots. With Pulpo Paul now topping the board, this put the gold medal +
full payout on his row (right next to his own FUERA DE PREMIO badge)
while the real #1 human showed silver money. Extracts computePrizeRanks
as the shared bot-excluding rank primitive, reused by the /home winners
banner in a later commit."
```

---

### Task 2: `lib/home-phase.ts` — reuse `computePrizeRanks`, add `winners`, fix the champion payout bug

**Files:**
- Modify: `frontend/lib/home-phase.ts`
- Modify: `frontend/lib/home-phase.test.ts`

**Interfaces:**
- Consumes: `computePrizeRanks` and `PrizeRankGroup` from `frontend/lib/ranking-payouts.ts` (Task 1) — import as `import { computePrizeRanks } from "./ranking-payouts";`.
- Produces (used by Task 3/4):
  - `export type PrizeWinner = { userId: number; displayName: string | null; points: number; isYou: boolean }`
  - `export type PrizeTopGroup = { rank: number; payoutCentsEach: number; winners: PrizeWinner[] }`
  - `export type Winners = { overall: { displayName: string | null; points: number; isBot: boolean }; prizeTop: PrizeTopGroup[] } | null`
  - `HomeState` gains `winners: Winners`.

- [ ] **Step 1: Write the failing tests**

Add to `frontend/lib/home-phase.test.ts`, right after the existing `"CHAMPION on finale day..."` test (after line 177):

```ts
it("CHAMPION payout uses prize-eligible rank, not raw rank, when a bot tops the leaderboard", () => {
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({
    entries: [
      { rank: 1, userId: 99, displayName: "Pulpo Paul 🐙", points: 200, delta: null, isYou: false, isBot: true },
      { rank: 2, userId: 1, displayName: "Tú", points: 190, delta: null, isYou: true, isBot: false },
    ],
  });
  const m = matches({ past: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, live: false, yourPick: null, pointsEarned: null, breakdown: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: Date.parse("2026-07-20T00:00:00Z") });
  expect(s.focus.kind).toBe("champion");
  // rank 2 raw would give 15% (3900); prize-eligible rank (bot excluded) is 1 -> 80% (20800)
  if (s.focus.kind === "champion") expect(s.focus.payoutCents).toBe(20800);
});

it("winners: overall leaderboard topper can be a bot; prize podium excludes bots and splits ties evenly", () => {
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({
    entries: [
      { rank: 1, userId: 99, displayName: "Pulpo Paul 🐙", points: 200, delta: null, isYou: false, isBot: true },
      { rank: 2, userId: 1, displayName: "José", points: 190, delta: null, isYou: false, isBot: false },
      { rank: 3, userId: 2, displayName: "Arturo", points: 180, delta: null, isYou: false, isBot: false },
      { rank: 4, userId: 3, displayName: "Yeison", points: 170, delta: null, isYou: false, isBot: false },
      { rank: 4, userId: 4, displayName: "Ricardo", points: 170, delta: null, isYou: false, isBot: false },
      { rank: 6, userId: 5, displayName: "Eduardo", points: 160, delta: null, isYou: false, isBot: false },
    ],
  });
  const m = matches({ past: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, live: false, yourPick: null, pointsEarned: null, breakdown: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({
    bracket: b,
    ranking: r,
    matches: m,
    summary: summary({ prizeSplit: [{ rank: 1, percentage: 80, payoutCents: 32000 }, { rank: 2, percentage: 15, payoutCents: 6000 }, { rank: 3, percentage: 5, payoutCents: 2000 }] }),
    nowMs: Date.parse("2026-07-20T00:00:00Z"),
  });
  expect(s.winners).not.toBeNull();
  if (s.winners) {
    expect(s.winners.overall).toEqual({ displayName: "Pulpo Paul 🐙", points: 200, isBot: true });
    expect(s.winners.prizeTop).toEqual([
      { rank: 1, payoutCentsEach: 32000, winners: [{ userId: 1, displayName: "José", points: 190, isYou: false }] },
      { rank: 2, payoutCentsEach: 6000, winners: [{ userId: 2, displayName: "Arturo", points: 180, isYou: false }] },
      {
        rank: 3,
        payoutCentsEach: 1000,
        winners: [
          { userId: 3, displayName: "Yeison", points: 170, isYou: false },
          { userId: 4, displayName: "Ricardo", points: 170, isYou: false },
        ],
      },
    ]);
  }
});

it("winners is null before the tournament is over", () => {
  const s = computeHomeState({ bracket: bracket(), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.winners).toBeNull();
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && pnpm vitest run lib/home-phase.test.ts`
Expected: FAIL — `s.winners` is `undefined`, and the bot-payout test gets `3900` instead of `20800`.

- [ ] **Step 3: Add the `Winners`/`PrizeTopGroup`/`PrizeWinner` types**

In `frontend/lib/home-phase.ts`, replace:

```ts
export type Standing = { rank: number; points: number; hasScored: boolean } | null;
```

with:

```ts
export type Standing = { rank: number; points: number; hasScored: boolean } | null;

export type PrizeWinner = { userId: number; displayName: string | null; points: number; isYou: boolean };
export type PrizeTopGroup = { rank: number; payoutCentsEach: number; winners: PrizeWinner[] };
export type Winners = {
  overall: { displayName: string | null; points: number; isBot: boolean };
  prizeTop: PrizeTopGroup[];
} | null;
```

- [ ] **Step 4: Import `computePrizeRanks` and add `winners` to `HomeState`**

Replace:

```ts
import type { BracketView } from "./api/bracket";
import type { RankingView } from "./api/ranking";
import type { MatchesView, MatchView } from "./api/matches";
import type { PublicSummary } from "./api/summary";
```

with:

```ts
import type { BracketView } from "./api/bracket";
import type { RankingView } from "./api/ranking";
import type { MatchesView, MatchView } from "./api/matches";
import type { PublicSummary } from "./api/summary";
import { computePrizeRanks } from "./ranking-payouts";
```

Then replace:

```ts
export type HomeState = { focus: FocusState; chips: PhaseChip[]; standing: Standing; recap: Recap };
```

with:

```ts
export type HomeState = { focus: FocusState; chips: PhaseChip[]; standing: Standing; recap: Recap; winners: Winners };
```

- [ ] **Step 5: Compute `winners` and fix the champion payout in `computeHomeState`**

Replace:

```ts
  const lastKnockout = bracket.knockouts.at(-1);
  const bracketComplete = lastKnockout != null && lastKnockout.locked;
  // Bucket membership is by kickoff DATE, not played status — on the finale day the
  // FINAL sits in `today` even after it's played. Decide "tournament over" by whether
  // every scheduled match is played, not by which buckets are empty.
  const allMatches = [...matches.past, ...matches.today, ...matches.upcoming];
  const allPlayed = bracketComplete && allMatches.length > 0 && allMatches.every((m) => m.played);

  // ── Focus (priority order) ────────────────────────────────────────────────
  let focus: FocusState;
  if (groupOpen) {
    focus = { kind: "fillGroup", filled: groupFilled, total: groupTotal, full: groupTotal > 0 && groupFilled >= groupTotal, deadline: bracket.groupStageDeadline, href: "/groups" };
  } else if (openKnockout) {
    focus = { kind: "fillKnockout", roundCode: openKnockout.code, roundName: openKnockout.name, filled: openKnockout.filled, total: openKnockout.total, full: openKnockout.total > 0 && openKnockout.filled >= openKnockout.total, deadline: openKnockout.deadline ?? null, href: `/knockout/${openKnockout.code}` };
  } else if (allPlayed) {
    const payoutCents = me && me.rank <= summary.prizeSplit.length ? summary.prizeSplit[me.rank - 1].payoutCents : null;
    focus = { kind: "champion", rank: me?.rank ?? null, points: me?.points ?? null, payoutCents };
  } else {
```

with:

```ts
  const lastKnockout = bracket.knockouts.at(-1);
  const bracketComplete = lastKnockout != null && lastKnockout.locked;
  // Bucket membership is by kickoff DATE, not played status — on the finale day the
  // FINAL sits in `today` even after it's played. Decide "tournament over" by whether
  // every scheduled match is played, not by which buckets are empty.
  const allMatches = [...matches.past, ...matches.today, ...matches.upcoming];
  const allPlayed = bracketComplete && allMatches.length > 0 && allMatches.every((m) => m.played);

  const winners: Winners = allPlayed
    ? {
        overall: {
          displayName: ranking.entries[0]?.displayName ?? null,
          points: ranking.entries[0]?.points ?? 0,
          isBot: ranking.entries[0]?.isBot ?? false,
        },
        prizeTop: computePrizeRanks(ranking.entries, summary.prizeSplit.length).map((g) => ({
          rank: g.rank,
          payoutCentsEach: Math.floor((summary.prizeSplit[g.rank - 1]?.payoutCents ?? 0) / g.entries.length),
          winners: g.entries.map((e) => ({ userId: e.userId, displayName: e.displayName, points: e.points, isYou: e.isYou })),
        })),
      }
    : null;

  // ── Focus (priority order) ────────────────────────────────────────────────
  let focus: FocusState;
  if (groupOpen) {
    focus = { kind: "fillGroup", filled: groupFilled, total: groupTotal, full: groupTotal > 0 && groupFilled >= groupTotal, deadline: bracket.groupStageDeadline, href: "/groups" };
  } else if (openKnockout) {
    focus = { kind: "fillKnockout", roundCode: openKnockout.code, roundName: openKnockout.name, filled: openKnockout.filled, total: openKnockout.total, full: openKnockout.total > 0 && openKnockout.filled >= openKnockout.total, deadline: openKnockout.deadline ?? null, href: `/knockout/${openKnockout.code}` };
  } else if (allPlayed) {
    const myPrizeGroup = winners?.prizeTop.find((g) => g.winners.some((w) => w.isYou));
    const payoutCents = myPrizeGroup ? myPrizeGroup.payoutCentsEach : null;
    focus = { kind: "champion", rank: me?.rank ?? null, points: me?.points ?? null, payoutCents };
  } else {
```

- [ ] **Step 6: Return `winners` from `computeHomeState`**

Replace the final line:

```ts
  return { focus, chips, standing, recap };
```

with:

```ts
  return { focus, chips, standing, recap, winners };
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd frontend && pnpm vitest run lib/home-phase.test.ts`
Expected: PASS, all tests including the pre-existing `"CHAMPION when every match is played, with payout for a prize rank"` (line 160) — that fixture has no bot, so prize rank equals raw rank and its expected `payoutCents: 3900` is unaffected.

- [ ] **Step 8: Typecheck**

Run: `cd frontend && pnpm typecheck`
Expected: no errors.

- [ ] **Step 9: Commit**

```bash
cd frontend && git add lib/home-phase.ts lib/home-phase.test.ts
git commit -m "fix(home): compute champion payout from prize-eligible rank, not raw leaderboard rank

Reuses the computePrizeRanks primitive from ranking-payouts.ts (same fix
as the /ranking page) instead of a second, duplicate implementation.
Also exposes the full prize-eligible podium (with ties split evenly) as
HomeState.winners, for the winners banner in the next commit."
```

---

### Task 3: `WinnersBanner` component + i18n strings

**Files:**
- Create: `frontend/components/lobby/WinnersBanner.tsx`
- Create: `frontend/components/lobby/WinnersBanner.test.tsx`
- Modify: `frontend/messages/en.json`
- Modify: `frontend/messages/es-CO.json`

**Interfaces:**
- Consumes: `Winners` type from `frontend/lib/home-phase.ts` (Task 2).
- Produces: `WinnersBanner({ winners }: { winners: Winners })` — a React component, renders `null` when `winners` is `null`. Used by Task 4.

- [ ] **Step 1: Add the i18n strings**

In `frontend/messages/es-CO.json`, inside the `"home"` object, add after the `"championSubPayout"` line (currently line 115):

```json
    "winnersOverallBotHeadline": "🐙 {name} se llevó el título",
    "winnersOverallBotSub": "{points} pts · les ganó a todos los panas 😅",
    "winnersOverallHumanHeadline": "🏆 {name} es el campeón",
    "winnersOverallHumanSub": "{points} pts en la tabla general",
    "winnersPodiumTitle": "Ganadores del premio",
    "winnersPodiumRowSingle": "{medal} {name} — {points} pts · {amount}",
    "winnersPodiumRowTied": "{medal} {names} — {points} pts · {amount} c/u",
```

In `frontend/messages/en.json`, inside the `"home"` object, add after the `"championSubPayout"` line (currently line 115):

```json
    "winnersOverallBotHeadline": "🐙 {name} took the crown",
    "winnersOverallBotSub": "{points} pts · beat every single pana 😅",
    "winnersOverallHumanHeadline": "🏆 {name} is the champion",
    "winnersOverallHumanSub": "{points} pts on the overall board",
    "winnersPodiumTitle": "Prize winners",
    "winnersPodiumRowSingle": "{medal} {name} — {points} pts · {amount}",
    "winnersPodiumRowTied": "{medal} {names} — {points} pts · {amount} each",
```

Keep both files valid JSON (comma after the inserted block, matching the existing trailing comma before the next key).

- [ ] **Step 2: Write the failing component test**

Create `frontend/components/lobby/WinnersBanner.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { WinnersBanner } from "./WinnersBanner";
import type { Winners } from "@/lib/home-phase";

const messages = {
  home: {
    winnersOverallBotHeadline: "🐙 {name} se llevó el título",
    winnersOverallBotSub: "{points} pts · les ganó a todos los panas 😅",
    winnersOverallHumanHeadline: "🏆 {name} es el campeón",
    winnersOverallHumanSub: "{points} pts en la tabla general",
    winnersPodiumTitle: "Ganadores del premio",
    winnersPodiumRowSingle: "{medal} {name} — {points} pts · {amount}",
    winnersPodiumRowTied: "{medal} {names} — {points} pts · {amount} c/u",
  },
};
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

const winners: Winners = {
  overall: { displayName: "Pulpo Paul 🐙", points: 664, isBot: true },
  prizeTop: [
    { rank: 1, payoutCentsEach: 32000, winners: [{ userId: 1, displayName: "José Manuel", points: 627, isYou: false }] },
    { rank: 2, payoutCentsEach: 6000, winners: [{ userId: 2, displayName: "Arturo", points: 620, isYou: false }] },
    {
      rank: 3,
      payoutCentsEach: 1000,
      winners: [
        { userId: 3, displayName: "Yeison", points: 613, isYou: false },
        { userId: 4, displayName: "Ricardo", points: 613, isYou: false },
      ],
    },
  ],
};

it("renders nothing when there are no winners yet", () => {
  const { container } = r(<WinnersBanner winners={null} />);
  expect(container).toBeEmptyDOMElement();
});

it("names the bot as the overall leaderboard topper", () => {
  r(<WinnersBanner winners={winners} />);
  expect(screen.getByText(/Pulpo Paul 🐙 se llevó el título/)).toBeInTheDocument();
});

it("uses the human-champion headline when the overall topper isn't a bot", () => {
  r(<WinnersBanner winners={{ ...winners, overall: { displayName: "María", points: 700, isBot: false } }} />);
  expect(screen.getByText(/María es el campeón/)).toBeInTheDocument();
});

it("renders the prize podium with a single winner per row for ranks 1 and 2", () => {
  r(<WinnersBanner winners={winners} />);
  expect(screen.getByText(/🥇 José Manuel — 627 pts · \$320/)).toBeInTheDocument();
  expect(screen.getByText(/🥈 Arturo — 620 pts · \$60/)).toBeInTheDocument();
});

it("renders both names and the per-person amount for a tied rank", () => {
  r(<WinnersBanner winners={winners} />);
  expect(screen.getByText(/🥉 Yeison & Ricardo — 613 pts · \$10 c\/u/)).toBeInTheDocument();
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd frontend && pnpm vitest run components/lobby/WinnersBanner.test.tsx`
Expected: FAIL — `Cannot find module './WinnersBanner'`.

- [ ] **Step 4: Implement `WinnersBanner`**

Create `frontend/components/lobby/WinnersBanner.tsx`:

```tsx
import { useTranslations } from "next-intl";
import type { Winners } from "@/lib/home-phase";

const MEDAL: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

export function WinnersBanner({ winners }: { winners: Winners }) {
  const t = useTranslations("home");
  if (!winners) return null;

  return (
    <section className="mx-3 mt-4 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4">
      <div className="chrome-label chrome-label-muted">{t("winnersPodiumTitle")}</div>
      <div className="mt-2 font-display text-base font-extrabold text-[var(--color-text-primary)]">
        {winners.overall.isBot
          ? t("winnersOverallBotHeadline", { name: winners.overall.displayName ?? "" })
          : t("winnersOverallHumanHeadline", { name: winners.overall.displayName ?? "" })}
      </div>
      <div className="chrome-label mt-1 text-[var(--color-text-secondary)]">
        {winners.overall.isBot
          ? t("winnersOverallBotSub", { points: winners.overall.points })
          : t("winnersOverallHumanSub", { points: winners.overall.points })}
      </div>
      <ul className="mt-3 space-y-1.5">
        {winners.prizeTop.map((g) => {
          const names = g.winners.map((w) => w.displayName ?? "?").join(" & ");
          const amount = `$${(g.payoutCentsEach / 100).toFixed(0)}`;
          return (
            <li key={`prize-${g.rank}`} className="font-display text-sm font-bold text-[var(--color-text-primary)]">
              {g.winners.length > 1
                ? t("winnersPodiumRowTied", { medal: MEDAL[g.rank], names, points: g.winners[0].points, amount })
                : t("winnersPodiumRowSingle", { medal: MEDAL[g.rank], name: names, points: g.winners[0].points, amount })}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd frontend && pnpm vitest run components/lobby/WinnersBanner.test.tsx`
Expected: PASS (all 5 tests).

- [ ] **Step 6: Typecheck and lint**

Run: `cd frontend && pnpm typecheck && pnpm lint`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
cd frontend && git add components/lobby/WinnersBanner.tsx components/lobby/WinnersBanner.test.tsx messages/en.json messages/es-CO.json
git commit -m "feat(home): add WinnersBanner naming the overall leaderboard topper and the real prize podium"
```

---

### Task 4: Wire `WinnersBanner` into the Home page

**Files:**
- Modify: `frontend/app/home/page.tsx`

**Interfaces:**
- Consumes: `WinnersBanner` (Task 3), `state.winners` (Task 2, already part of `computeHomeState`'s return value — `state` is already destructured on this page).

- [ ] **Step 1: Add the import and mount the component**

In `frontend/app/home/page.tsx`, replace:

```tsx
import { FocusCard } from "@/components/lobby/FocusCard";
import { PhaseRail } from "@/components/lobby/PhaseRail";
```

with:

```tsx
import { FocusCard } from "@/components/lobby/FocusCard";
import { WinnersBanner } from "@/components/lobby/WinnersBanner";
import { PhaseRail } from "@/components/lobby/PhaseRail";
```

Then replace:

```tsx
        <FocusCard focus={state.focus} timeZone={me.timezone} />
        <PhaseRail chips={state.chips} />
```

with:

```tsx
        <FocusCard focus={state.focus} timeZone={me.timezone} />
        <WinnersBanner winners={state.winners} />
        <PhaseRail chips={state.chips} />
```

- [ ] **Step 2: Run the full frontend test suite**

Run: `cd frontend && pnpm test`
Expected: all tests pass, including Tasks 1–3's new/updated tests.

- [ ] **Step 3: Typecheck and lint**

Run: `cd frontend && pnpm typecheck && pnpm lint`
Expected: no errors.

- [ ] **Step 4: Manual check against the real prod data**

Run the frontend dev server and eyeball `/ranking` (Pulpo Paul should now show his "FUERA DE PREMIO" badge with no medal, José Manuel's row should show 🥇, Arturo 🥈, and Yeison/Ricardo both show 🥉 with no amount) and `/home` (WinnersBanner should show the same podium, this time with per-person split amounts, plus the "Pulpo Paul se llevó el título" callout). If you're logged in as one of the top-3 finishers, confirm the personal champion card's payout now matches. This is the step from the design doc's "how to apply" — eyeball the real page before calling this done.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add app/home/page.tsx
git commit -m "feat(home): mount WinnersBanner on the dashboard once the tournament is over"
```

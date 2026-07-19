# Final Results — Winners Banner + Prize-Rank Payout Fix — Design

> **Status:** Approved design (brainstormed 2026-07-19). Next step: implementation plan.
> **Area:** `frontend` only (read-only against the existing `/api/ranking` and `/api/public/summary` responses — no backend changes).

## Context

The 2026 World Cup final was played today (2026-07-19); the pool's tournament is over
(`computeHomeState`'s `allPlayed` already flips true). Real final standings, pulled read-only
from prod:

- **Overall leaderboard** (includes bots): **Pulpo Paul 🐙 wins outright**, 664 pts — top of
  the whole board, but bots are never prize-eligible.
- **Prize-eligible standings** (humans only, `is_bot = false`; admins already excluded by the
  ranking query):
  1. José Manuel de Oliveira — 627 pts
  2. Arturo Hernandez — 620 pts
  3. **Tie**: Yeison Cardenas & Ricardo Rivera — 613 pts each

Pot is `panaCount(40) × entry_fee_cents(1000) = $400`, split 80/15/5 (`prize_split` table) →
$320 / $60 / $20, tied 3rd split evenly → $10 each.

## Problem

1. **No shared "who actually won" view.** The existing `"champion"` `FocusState` on `/home` is
   personal only — it tells *you* your own rank/points/payout, never names the actual winners.
2. **Payout bug.** `computeHomeState`'s champion branch looks up
   `summary.prizeSplit[me.rank - 1]` using `me.rank` from the raw leaderboard (`ranking.entries`),
   which **includes bots**. Since Paul now occupies rank 1, this miscomputes real payouts:
   - José Manuel (true rank 1, 80%) would see rank-2 math (15%).
   - Arturo (true rank 2, 15%) would see rank-3 math (5%).
   - Yeison/Ricardo (true tied rank 3, 5% split) would see **no payout at all** (raw rank 4 is
     past `prizeSplit.length`).

## Goal

- Fix the payout computation to use a **prize-eligible rank** (bots excluded, ties handled),
  split evenly across ties.
- Add a **`WinnersBanner`** shown to every player once the tournament is over: names the overall
  leaderboard topper (fun/bragging-rights framing) and the real prize-eligible podium, including
  the 3rd-place tie.

### Non-goals

- No backend changes. `ranking.entries` (from the already-fetched `getRanking()` call) already
  carries `isBot` per entry and already excludes admins server-side — everything needed is
  already on the wire. (The backend's `RankingService.getPrizeEligible()` is unused dead code;
  wiring it in would mean a new endpoint + an extra round trip for something derivable
  client-side. Leaving it untouched.)
- No changes to how ties are stored/settled financially (payment records) — this is a display
  concern only.
- No time-boxing / auto-hide of the banner. It stays up until the app itself is decommissioned
  (~end of July, tracked separately).

## Design

### 1. `lib/home-phase.ts` — derive prize-eligible standings once, reuse in two places

Add a pure helper, called only when `allPlayed`:

```ts
type PrizeWinner = { userId: number; displayName: string | null; points: number; isYou: boolean };
type PrizeRankGroup = { rank: number; payoutCentsEach: number; winners: PrizeWinner[] };

function computePrizeStandings(
  entries: RankingEntry[],       // already sorted points DESC, display_name ASC (server-side)
  prizeSplit: PublicSummary["prizeSplit"],
): PrizeRankGroup[]
```

Logic: filter `!isBot`, walk the already-sorted list assigning a fresh competition rank
(ties share a rank — same semantics as SQL `RANK()`, just recomputed over the bot-filtered
subset), stop once the rank exceeds `prizeSplit.length` (3). Group entries by rank. Payout per
group = `prizeSplit[rank-1].payoutCents / group.winners.length` (integer division; a stray cent
on an uneven split is fine for a friends pool).

### 2. `HomeState` gains `winners`

```ts
export type Winners = {
  overall: { displayName: string | null; points: number; isBot: boolean };
  prizeTop: PrizeRankGroup[];
} | null; // null unless allPlayed
```

`overall` = `ranking.entries[0]` (the actual #1, bot or not — that's the point). `prizeTop` =
`computePrizeStandings(...)`, always computed (not gated on `me`) since this is shown to
everyone, not just the viewer.

### 3. Fix the `"champion"` `FocusState`

Replace the raw-rank lookup with a search over the same `computePrizeStandings(...)` result:
find the group containing `me` (by `userId`); if found, `payoutCents = group.payoutCentsEach`;
if not found (not a top-3 prize finisher), `payoutCents = null` as today. `rank`/`points` on the
focus state stay the existing overall values (that's "your standing", separate from "did you
win money").

### 4. New `components/lobby/WinnersBanner.tsx`

Presentational only, props = `Winners` (non-null — parent conditions on it). Two parts, visually
distinct from the personal `FocusCard`:

- A one-line callout crowning the overall leaderboard topper — written to read as a fun jab when
  `isBot` is true ("🐙 Pulpo Paul se llevó el título — le ganó a los 40"), or a straight
  congratulations if a human ever tops the board outright in a future tournament.
- The real podium: one row per `PrizeRankGroup`, medal + name(s) + points + payout. Ties render
  both names on one row ("🥉 Yeison Cardenas & Ricardo Rivera — 613 pts · $10 c/u").

Mounted in `app/home/page.tsx` right below `<FocusCard />`, rendered only when
`state.winners != null`.

### 5. i18n

New keys in both `frontend/messages/en.json` and `es-CO.json` (the app is bilingual via
`next-intl`; es-CO is the primary/default locale). No copy is finalized here — write natural
strings for: overall-topper callout (bot and non-bot phrasing), podium row template, tied-row
template.

## Testing

Extend `lib/home-phase.test.ts` (existing pattern) with cases mirroring the real prod shape:
bot at rank 1, clean 2nd, and a 3-way not just 2-way — plus the regression case: a prize-eligible
user directly below a bot must NOT be pushed off the podium by the raw-rank-based
`prizeSplit.length` cutoff. Also assert the fixed `champion` payout for a synthetic "tied 3rd"
user splits evenly.

`FocusCard.test.tsx` and a new `WinnersBanner.test.tsx` cover rendering (ties render both names,
bot vs. human overall-topper copy).

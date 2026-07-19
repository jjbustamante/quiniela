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
2. **Payout bug, and it's not just `/home`.** Two independent places compute prize payouts from
   the leaderboard's **raw rank**, which includes bots — since Paul now occupies rank 1, both are
   wrong today, live, for the real winners:
   - `computeHomeState`'s champion branch (`lib/home-phase.ts`) looks up
     `summary.prizeSplit[me.rank - 1]` using `me.rank`. José Manuel (true rank 1, 80%) would see
     rank-2 math (15%); Arturo (true rank 2, 15%) would see rank-3 math (5%); Yeison/Ricardo (true
     tied rank 3) would see **no payout at all** (raw rank 4 is past `prizeSplit.length`).
   - `buildPayoutLabels` (`lib/ranking-payouts.ts`), which renders the "🥇 $320"-style badges on
     the **`/ranking` page today**, has the identical bug: it keys its output by raw rank and the
     caller (`app/ranking/page.tsx:84`) looks it up via `payoutByRank.get(e.rank)`. Right now in
     prod this puts **"🥇 $320" on Pulpo Paul's row** — right next to his own "FUERA DE PREMIO"
     (not prize-eligible) badge — while José Manuel's row shows silver money instead of gold, and
     the tie is mislabeled too. This is the more visible instance of the bug, since `/ranking` is
     the page people actually check, not a hypothetical.

Both bugs share one root cause (rank position ≠ prize position once a bot is on the board) and
should share one fix, not two independent patches.

## Goal

- Fix payout computation everywhere it happens to use a **prize-eligible rank** (bots excluded,
  ties handled) from a **single shared implementation** — both the existing `/ranking` badges and
  the `/home` champion card.
- Add a **`WinnersBanner`** shown to every player once the tournament is over: names the overall
  leaderboard topper (fun/bragging-rights framing) and the real prize-eligible podium, including
  the 3rd-place tie, with the payout split evenly and shown per person (a different, more
  detailed UX than `/ranking`'s compact "medal only on a tie" badge — see below).

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

### 1. `lib/ranking-payouts.ts` — one shared prize-rank primitive, and the `/ranking` fix

Add and export a pure grouping function — this becomes the single source of truth for "who is
prize-eligible rank N":

```ts
export type PrizeRankGroup = { rank: number; entries: RankingEntry[] };

export function computePrizeRanks(entries: RankingEntry[], prizeRankCount: number): PrizeRankGroup[]
```

Logic: filter `!isBot`, walk the already-sorted (`points DESC`, server-guaranteed) list assigning
a fresh competition rank — ties share a rank, same semantics as SQL `RANK()`, just recomputed
over the bot-filtered subset — and stop once the rank exceeds `prizeRankCount`.

Rewrite `buildPayoutLabels` to call `computePrizeRanks` instead of trusting each entry's raw
`rank`/grouping by it. This also fixes a second, existing bug in its output *contract*: the
current `Map<rank, label>` is looked up by the caller via `payoutByRank.get(e.rank)` (`app/
ranking/page.tsx:84`) — raw rank again, so a bot sitting at raw rank 1 causes the prize-rank-1
label to be attached to the bot's row instead of the real #1 human's. Change the map to be
**keyed by `userId`** instead of rank — correct regardless of how bots shift the raw numbering —
and update the one call site (`app/ranking/page.tsx:84`) from `payoutByRank.get(e.rank)` to
`payoutByRank.get(e.userId)`. Visual behavior on `/ranking` is otherwise unchanged (medal-only
badge on a tie, medal+amount when not tied) — only *which row* gets which label changes, and it's
now correct.

### 2. `lib/home-phase.ts` — reuse `computePrizeRanks`, add `winners`, fix the champion payout

`HomeState` gains a `winners` field:

```ts
export type PrizeWinner = { userId: number; displayName: string | null; points: number; isYou: boolean };
export type PrizeTopGroup = { rank: number; payoutCentsEach: number; winners: PrizeWinner[] };
export type Winners = {
  overall: { displayName: string | null; points: number; isBot: boolean };
  prizeTop: PrizeTopGroup[];
} | null; // null unless allPlayed
```

Computed only when `allPlayed`, by calling the shared `computePrizeRanks(ranking.entries,
summary.prizeSplit.length)` from `lib/ranking-payouts.ts` and mapping each group to a
`PrizeTopGroup` (`payoutCentsEach = Math.floor(prizeSplit[rank-1].payoutCents / group.entries.length)`).
`overall` = `ranking.entries[0]` (the actual #1, bot or not — that's the point), always computed,
not gated on `me`, since this is shown to everyone.

### 3. Fix the `"champion"` `FocusState`

Replace the raw-rank lookup with a search over `winners.prizeTop`: find the group containing `me`
(via `isYou`); if found, `payoutCents = group.payoutCentsEach`; if not found (not a top-3 prize
finisher), `payoutCents = null` as today. `rank`/`points` on the focus state stay the existing
overall values (that's "your standing", separate from "did you win money").

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

Extend `lib/ranking-payouts.test.ts` (existing pattern) with the regression case the current 4
tests don't cover: a bot at raw rank 1 with real points, humans below it — assert the label for
the true prize-rank-1 human is the gold amount (keyed by that human's `userId`), not attached to
the bot, and that `buildPayoutLabels`'s existing tie/no-scores-yet behavior is unchanged.

Extend `lib/home-phase.test.ts` (existing pattern) with cases mirroring the real prod shape:
bot at rank 1, clean 2nd, and a 3-way not just 2-way — plus the regression case: a prize-eligible
user directly below a bot must NOT be pushed off the podium by the raw-rank-based
`prizeSplit.length` cutoff. Also assert the fixed `champion` payout for a synthetic "tied 3rd"
user splits evenly.

A new `WinnersBanner.test.tsx` covers rendering (ties render both names, bot vs. human
overall-topper copy).

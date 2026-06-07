# Design — Group Compare results by stage (H2H + Group Consensus)

> The Compare page (Duelos) lists revealed matches in one flat list (Group
> Consensus) or one differ/agree-split table (H2H). Like the Matches page now
> does, group these by stage with the most-recent stage on top, in both modes.

**Date:** 2026-06-07
**Branch:** `feat/compare-by-stage`
**Status:** approved, ready for implementation plan

## Background / grounding

- `frontend/lib/matches-by-stage.ts` already has `groupMatchesByStage(matches:
  MatchView[], nowMs): { roundCode, matches }[]` — groups by `roundCode`, orders
  sections most-recent-*started* first, future stages last (5 unit tests).
- Both Compare types carry the fields the helper needs: `MatchConsensus` and
  `H2HMatch` (in `frontend/lib/api/compare.ts`) both have `roundCode: string`,
  `kickoffAt: string`, `revealed: boolean`.
- `GroupConsensus.tsx` and `H2HCompare.tsx` are **server** components
  (`async`, `getTranslations`). So a server-side `Date.now()` is safe for the
  ordering — no client hydration mismatch (unlike the Matches page, which is a
  client component and threads `serverTime`).
- Both components already filter to `m.revealed`. Revealed matches are played or
  past-deadline, so "most-recent stage first" reads naturally.
- Stage labels: the `home.chip{ROUNDCODE}` keys (same source the phase rail +
  Matches page use), so labels never drift.

## Shared helper (generalize)

Make `groupMatchesByStage` **generic** over `{ roundCode: string; kickoffAt:
string }`:
- `StageGroup<T> = { roundCode: string; matches: T[] }`
- `groupMatchesByStage<T extends { roundCode: string; kickoffAt: string }>(items:
  T[], nowMs: number): StageGroup<T>[]`
- Ordering/grouping logic is unchanged. The Matches page keeps calling it with
  `MatchView` (now `StageGroup<MatchView>`); Compare calls it with
  `MatchConsensus` / `H2HMatch`. The existing 5 tests still hold (genericity is
  compile-checked).

## Group Consensus view

Group the revealed matches into stage sections (most-recent first). Each section:
a header (`home.chip{roundCode}`) + the existing `ConsensusCard`s within
(chronological per the helper). No card change.

## H2H view (table)

Group the revealed matches into stage sections (most-recent first). Each stage
renders a stage-header row (a `<tr><td colSpan=4>` header, mirroring the current
"agree section" header style). **Within each stage, keep the current ordering:
differ rows first (highlighted), then agree rows (dimmed)** — disagreements still
surface at the top of each stage. The single global "agree section" header is
removed (per-stage grouping replaces it); the summary line at the top and the
table head are unchanged.

## i18n

Stage headers reuse `home.chip{ROUNDCODE}` — each Compare component adds a
`getTranslations("home")` (`tRound`) alongside its existing
`getTranslations("compare")`. No new message keys.

## Out of scope

- No backend change (no `serverTime` added to the Compare views — server-side
  `Date.now()` suffices for these server components).
- No change to `ConsensusCard`, the H2H `Row`, the reveal logic, the differ/agree
  classification, or the summary line.
- No change to the Matches page (it keeps using the now-generic helper).

## Testing

- **Unit (vitest):** the generic `groupMatchesByStage` is already pinned by its 5
  tests; add one test calling it with a minimal `{ roundCode, kickoffAt }` object
  literal (not `MatchView`) to lock the generic contract.
- **Component (RTL):**
  - `GroupConsensus`: renders a stage header per revealed stage, ordered
    most-recent-first; cards appear under the right header.
  - `H2HCompare`: renders a stage-header row per stage in most-recent-first
    order; within a stage, a differ row precedes an agree row.
  - Both reuse the `NextIntlClientProvider` test pattern (these are async server
    components — render via `await Component({...})` or the project's existing
    approach in `GroupConsensus.test.tsx`).

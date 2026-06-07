# Design — Collapsible stage groups (Matches + Compare)

> The Matches page now groups by stage; Compare (H2H + Group Consensus) should
> too. And the groups should be **collapsible** — with the most-recent stage open
> and older stages collapsed — so navigating long lists is easy. Apply one
> collapsible-section pattern across Matches and both Compare views.

**Date:** 2026-06-07
**Branch:** `feat/compare-by-stage`
**Status:** approved, ready for implementation plan

## Background / grounding

- `frontend/lib/matches-by-stage.ts` has `groupMatchesByStage(matches:
  MatchView[], nowMs): { roundCode, matches }[]` — groups by `roundCode`, orders
  sections most-recent-*started* first (5 unit tests). Used by the Matches page
  (`MatchTabs.tsx`, a client component, "Por fase" view).
- Both Compare types carry the needed fields: `MatchConsensus` and `H2HMatch`
  (`frontend/lib/api/compare.ts`) have `roundCode`, `kickoffAt`, `revealed`.
- `GroupConsensus.tsx` and `H2HCompare.tsx` are **server** components (`async`,
  `getTranslations`) → a server-side `Date.now()` is safe for ordering (no
  hydration concern). Both already filter to `m.revealed`.
- Stage labels: `home.chip{ROUNDCODE}` (same source as the phase rail / Matches).

## Shared helper (generalize)

Make `groupMatchesByStage` **generic** over `{ roundCode: string; kickoffAt:
string }`:
- `StageGroup<T> = { roundCode: string; matches: T[] }`
- `groupMatchesByStage<T extends { roundCode: string; kickoffAt: string }>(items:
  T[], nowMs: number): StageGroup<T>[]`
- Logic unchanged; the existing 5 tests still hold. Matches uses
  `StageGroup<MatchView>`; Compare uses `StageGroup<MatchConsensus>` /
  `StageGroup<H2HMatch>`.

## Shared collapsible section component

New `frontend/components/shared/StageSection.tsx` — a plain presentational
component using **native `<details>`/`<summary>`** (zero JS; works in both server
and client trees, no `"use client"`):

```
StageSection({ header, count, defaultOpen, children })
  <details open={defaultOpen}>
    <summary> {header}  <span>{count}</span> </summary>
    <div> {children} </div>
  </details>
```

- The `<summary>` shows the stage label + the match count, styled as the section
  header (custom chevron via CSS; `list-none` to drop the default marker).
- `defaultOpen` is set to `true` for the **first** group only (the helper returns
  most-recent-first, so index 0 = most recent). All other groups start collapsed.
- Reused by all three stage views below so collapse/spacing/markers stay uniform.

## Matches "Por fase" view (update the shipped view)

In `MatchTabs.tsx`, wrap each stage group's `MatchListItem`s in a `StageSection`
(`header = home.chip{roundCode}`, `count = g.matches.length`,
`defaultOpen = index === 0`). The date view ("Por fecha") is unchanged.

## Group Consensus view

Wrap each revealed-stage's `ConsensusCard`s in a `StageSection` (most-recent
first, first open). No card change.

## H2H view (table)

Render a single column-header row at the top (You · Rival · Real), then one
`StageSection` per stage (most-recent first, first open). Each section's body is
a small `<table className="table-fixed w-full">` of that stage's rows with the
**same fixed column widths** as the header so columns align across sections.
**Within each stage keep differ rows first (highlighted), then agree rows
(dimmed).** The summary line at the top is unchanged; the single global "agree
section" header is removed (per-stage grouping replaces it).

## i18n

Stage headers reuse `home.chip{ROUNDCODE}` — each Compare component adds a
`getTranslations("home")` (`tRound`). No new message keys (the count is a number).

## Out of scope

- No backend change (server-side `Date.now()` suffices for the Compare server
  components; no `serverTime` added to Compare views).
- No change to `ConsensusCard`, the H2H `Row` cell rendering, reveal logic, the
  differ/agree classification, or the summary line.
- No persistence of which sections the user expands (native `<details>` state is
  per-render; YAGNI).

## Testing

- **Unit (vitest):** `groupMatchesByStage` keeps its 5 tests; add one calling it
  with a minimal `{ roundCode, kickoffAt }` literal (not `MatchView`) to pin the
  generic contract.
- **Component (RTL):**
  - `StageSection`: renders the header + count; `defaultOpen` controls the
    `<details open>` attribute; collapsed body still in the DOM (native details).
  - `MatchTabs` (stage mode): first stage section `open`, others not; headers in
    most-recent-first order.
  - `GroupConsensus`: a `StageSection` per revealed stage, first open, ordered
    most-recent-first.
  - `H2HCompare`: a `StageSection` per stage, first open, ordered most-recent
    first; within a stage a differ row precedes an agree row.
  - Async server components rendered via the existing approach in
    `GroupConsensus.test.tsx`; `NextIntlClientProvider` for messages.

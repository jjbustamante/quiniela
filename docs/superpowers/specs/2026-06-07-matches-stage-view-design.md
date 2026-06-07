# Design — Matches page "Por fecha / Por fase" toggle

> The Matches (Partidos) page lists matches only in date buckets (Past · Today ·
> Upcoming). That's good for live play but hard to scan when browsing or
> simulating the bracket — there's no way to jump to a stage. Add a view toggle
> that also groups matches by stage, most-recent stage first.

**Date:** 2026-06-07
**Branch:** `feat/matches-stage-view`
**Status:** approved, ready for implementation plan

## Background / grounding

- `/matches` (`frontend/app/matches/page.tsx`) fetches `getMatches()` →
  `MatchesView { serverTime, past[], today[], upcoming[] }` (date buckets from
  `MatchesService`).
- `MatchTabs.tsx` (client) renders the three date tabs and a `MatchListItem`
  per match. Each `MatchView` already carries `roundCode` (e.g. `GROUP`, `R32`,
  `FINAL`), `groupCode`, `kickoffAt`, `played`, `score`, `pointsEarned`.
- Stage labels are localized via the `home.chip*` keys (`chipGROUP`, `chipR32`,
  …) — the same source the home phase rail uses, so labels never drift.
- **Frontend-only:** the page already has every match (past + today + upcoming)
  with its `roundCode` + `kickoffAt`, so it can regroup by stage with no backend
  change.

## View toggle

A top-level toggle on `/matches`, above the current content:
- **Por fecha** (default — unchanged behavior): the existing Past · Today ·
  Upcoming date tabs (`MatchTabs`). Best for live play.
- **Por fase** (new): all matches grouped into stage sections.

Default is **date** so live play is unchanged; stage is opt-in. The toggle is
local component state (resets to date on a fresh visit) — no URL param (YAGNI;
revisit if shareable links are wanted).

## Stage view

One section per stage that has matches, each with a header (the localized
`home.chip*` label) and the stage's `MatchListItem` rows.

**Section ordering — most-recent activity first:**
- For each stage, compute its "latest started kickoff" = the max `kickoffAt`
  among its matches with `kickoffAt <= now` (using `MatchesView.serverTime` as
  `now`, same authoritative clock `MatchTabs` already uses). Null if no match has
  started.
- Stages with a started match sort first, by latest-started-kickoff **descending**
  (so after the group phase, 16vos sits above Grupos).
- Stages with no started match (fully future) sort after, by earliest `kickoffAt`
  **ascending** (soonest upcoming next).

**Within a section:** matches sorted by `kickoffAt` **ascending** (chronological).

**Rows:** reuse `MatchListItem`. `showResult = match.played` (show the result for
played matches, the pick + points like the past/today tabs do). The per-row
`roundLabel`/`groupLabel` labels stay as today (harmless duplication with the
section header; the group label still distinguishes which group within GRUPOS).

## Components and data flow

- **`lib/matches-by-stage.ts` (new, pure, unit-tested):**
  `groupMatchesByStage(matches: MatchView[], nowMs: number): { roundCode: string; matches: MatchView[] }[]`
  — merges the list, groups by `roundCode`, applies the section-ordering rule
  above, sorts each section's matches by kickoff. This is the testable core; it
  holds the ordering logic out of JSX.
- **Matches client component** (extend `MatchTabs`, or a thin `MatchesBrowser`
  wrapper around it): owns a `view: "date" | "stage"` state + the toggle UI.
  `date` → the existing date tabs; `stage` → maps `groupMatchesByStage(all, now)`
  to a `<section>` per stage with a header + `MatchListItem` rows. `all` =
  `[...past, ...today, ...upcoming]`.
- **i18n:** add `matches.viewByDate` ("Por fecha" / "By date") and
  `matches.viewByStage` ("Por fase" / "By stage"). Section headers reuse
  `home.chip{ROUNDCODE}`. es-CO + en parallel.

## Out of scope

- No sub-grouping of the GRUPOS section by group letter (the per-row group label
  already shows it). YAGNI.
- No backend change, no new endpoint, no URL-param persistence of the toggle.
- No change to the date-tabs view or to `MatchesService` bucketing.

## Testing

- **Unit (vitest):** `groupMatchesByStage` — groups by `roundCode`; section order
  is recent-started-first then future-by-soonest; within-section chronological; a
  stage with no started matches lands after started stages; empty input → `[]`.
- **Component (RTL):** the matches client renders the date tabs by default and
  switches to stage sections (with stage headers) when the toggle is set to
  stage; reuses the `NextIntlClientProvider` test pattern.
- Existing `MatchListItem` / `MatchTabs` behavior unchanged (date view untouched).

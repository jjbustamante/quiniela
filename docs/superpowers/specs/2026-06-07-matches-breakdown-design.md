# Design — Per-match points breakdown on Matches + scorecard player header

> Two scoring-display polish items: (A) on the Matches (Partidos) list, tap the
> gold `+N PTS` badge to reveal the point breakdown (`resultado +3 · visitante
> +2 · ×2`) for that match — the same components the scorecard shows. (B) On the
> player scorecard, add a big "whose points" header at the top (the TopBar name
> is too small on mobile).

**Date:** 2026-06-07
**Branch:** `feat/matches-breakdown`
**Status:** approved, ready for implementation plan

## Background / grounding

- `ScoreBreakdown.of(...)` (backend `io.quiniela.api.scoring`) returns the point
  components and is **pinned to the live DB `score_match_for_bet`** (the scorecard
  uses it). Reuse it here.
- `MatchesService.MatchRow` carries only `pointsEarned` (a total); its SQL query
  computes that via `score_match_for_bet` and already has the inputs in the call
  (`b.score_t1/t2`, `m.score_t1/t2`, `b.predicted_winner_id`,
  `m.advanced_team_id`, `r.points_multiplier`, `r.code`) — but only the total is
  selected out.
- `MatchListItem.tsx` renders the gold `+N PTS` badge (only when
  `match.pointsEarned != null`). It's presentational; `MatchTabs` (its client
  parent) builds its label props.
- The scorecard's `MatchScoreRow` already builds a breakdown line inline (outcome
  / team1 / team2 / diff / ×mult) — extract a shared helper so the two can't drift.
- The scorecard page (`app/ranking/[userId]/page.tsx`) has the "← Tabla" back
  link and shows the name in the `TopBar` title (too small on mobile).
- Matches points are the **caller's own** picks only — no privacy concern.

## (A) Matches list — tappable per-match breakdown

**Backend** — `MatchesService.MatchRow` gains `ScoreBreakdown breakdown` (nullable;
null when the match is unplayed or the caller has no bet). Add
`b.predicted_winner_id`, `m.advanced_team_id`, `r.points_multiplier`, and the
already-present `m.score_t1/t2`, `b.score_t1/t2`, `r.code` to the row mapper's
reach, and compute `ScoreBreakdown.of(knockout, betT1, betT2, actualT1, actualT2,
predictedWinnerId, advancedTeamId, multiplier)` per played, bet-on row.
`MatchesView` JSON then carries `breakdown` per match.

**Frontend**
- `MatchView` (`lib/api/matches.ts`) gains `breakdown: ScoreBreakdown | null`
  (type imported from `lib/api/scorecard.ts`).
- A shared pure helper `lib/breakdown-format.ts`:
  `breakdownParts(b: ScoreBreakdown, labels): string[]` — the non-zero
  components as `["resultado +3", "visitante +2"]`, plus `×{mult}` when > 1.
  Unit-tested. The scorecard's `MatchScoreRow` is refactored to use it.
- `MatchListItem` becomes a client component (`"use client"`) with a
  `showBreakdown` toggle. The `+N PTS` badge becomes a `<button>` that toggles it;
  when open, a breakdown line renders under the pick row
  (`breakdownParts(...).join(" · ")`). Tap again to hide. Matches without points
  render no badge → no toggle (unchanged).
- `MatchTabs` passes the breakdown labels (from the existing `scorecard` i18n
  namespace: `bdOutcome` / `bdTeam1` / `bdTeam2` / `bdDiff` / `multiplier` /
  `pts`) down via the `labels` prop, plus a `toggleBreakdown` aria-label.

## (B) Scorecard — big "whose points" header

On `app/ranking/[userId]/page.tsx`, after the "← Tabla" back link, add a prominent
heading: **`{displayName} · {totalPoints} pts`** in large display type (the
`scorecard.totalPoints` label already exists). The `TopBar` is unchanged; this
body header is the intuitive, mobile-legible one. Empty-state (no points) still
shows `noPoints` below it.

## Out of scope

- No change to `MatchesService`'s date bucketing, the trigger, or `ScoreBreakdown`
  itself.
- No breakdown for other players on the Matches list (it only shows the caller's
  own picks anyway).
- No hover behaviour (touch-first; tap toggles — works on desktop click too).

## Testing

- **Backend:** extend the matches IT (`MatchesControllerIT`) — a played, bet-on
  match returns a `breakdown` with the right components + total; an unplayed match
  has `breakdown: null`.
- **Frontend unit:** `breakdownParts` (non-zero components only; `×mult` when > 1;
  empty for a zero/all-zero breakdown); `MatchScoreRow` still green after the
  refactor.
- **Frontend component:** `MatchListItem` — badge present when points exist; tap
  → breakdown line appears; tap again → hides; no badge/toggle when
  `pointsEarned`/`breakdown` is null.
- **Frontend:** the scorecard page renders the `{name} · {total} pts` header.

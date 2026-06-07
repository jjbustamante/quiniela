# Design — Player scorecard (points by stage + per-match breakdown)

> From the ranking table, tap any player to see how their points add up: a
> per-stage total (Grupos 100, 16vos 50, …) and, per played match, the point
> breakdown (3 resultado + 2 local + 2 visitante ×mult = total). Unifies backlog
> #4 (points by stage) and #5 (per-match provenance) into one scorecard.

**Date:** 2026-06-07
**Branch:** `feat/player-scorecard`
**Status:** approved, ready for implementation plan

## Background / grounding

- `RankingService` returns only per-user **totals** (`q.points`, trigger-
  maintained). No per-stage sums, no point components.
- No backend computes scoring **components** (outcome / team-exact / goal-diff):
  the DB `score_match_for_bet` (V016/V019) and the Java mirror
  `CompareService.scoreMatchForBet` both return only the total.
- `MatchesService` computes a per-match `pointsEarned` total (caller's own picks)
  in SQL via `score_match_for_bet`.
- Privacy: points only exist for **played** matches, and played matches are
  already revealed (reveal-on-played). So a player's per-stage totals + per-match
  breakdown for played matches leak nothing not already public → **any player's
  scorecard is safe**, played matches only.
- Reuse: the collapsible `StageSection` (`@/components/shared/StageSection`) and
  the generic `groupMatchesByStage` helper from the recent work. Stage labels:
  `home.chip{ROUNDCODE}`.

## Scope decisions (approved)

- **Any player** — tap any ranking row → that player's scorecard.
- **Played matches only** — unplayed/future (no points) are not shown.
- **Dedicated route** `/ranking/[userId]` with a "← Tabla" back link (+ browser
  back), so it's easy to return to the table.

## Phase 1 — Backend: `ScoreBreakdown` + scorecard endpoint

### `ScoreBreakdown` (pure, pinned to the DB)

New pure helper (package `io.quiniela.api.scoring`) computing the additive point
components for one match, matching the DB function exactly:

- `record ScoreBreakdown(int outcome, int team1Exact, int team2Exact, int goalDiff, int multiplier, int total)`
  where `total = (outcome + team1Exact + team2Exact + goalDiff) * multiplier`.
- `ScoreBreakdown of(boolean knockout, int betT1, int betT2, Integer actualT1,
  Integer actualT2, Long predictedWinnerId, Long advancedTeamId, int multiplier)`
  reproducing V016/V019:
  - outcome: 3 when bet winner == actual winner; the knockout-regulation-draw
    refinement (both draws + predicted/advanced ids present → 3 if
    `predictedWinnerId == advancedTeamId` else 0); 0 otherwise.
  - team1Exact: 2 when `betT1 == actualT1`; team2Exact: 2 when `betT2 == actualT2`.
  - goalDiff: 1 when not exact and `(betT1-betT2) == (actualT1-actualT2)`.
  - unplayed (null actuals) → all-zero breakdown.
- **Pinned:** a test feeds identical inputs to `ScoreBreakdown.of(...).total()` and
  the live DB `score_match_for_bet(...)` across every 0–5 scoreline and a few
  multipliers (group + knockout), asserting they agree — so the breakdown always
  sums to the real points and can't drift (same guarantee as
  `ScoringDivergenceTest`).

### Scorecard service + endpoint

- `GET /api/ranking/{userId}/scorecard` (authenticated; any pool member's id).
- Service: load the target user's display name + total; load their bets joined to
  **played** matches → round (code, name, `points_multiplier`, sequence) + actual
  scores + `advanced_team_id`; compute a `ScoreBreakdown` per match; group by round
  (ordered by round sequence), sum each stage's totals.
- Response shape:
  ```
  ScorecardView(
    userId, displayName, totalPoints,
    stages: [ StageScore(roundCode, roundName, points, matches: [
      MatchScore(matchId, team1{code,flag,name}, team2{...}, kickoffAt,
                 betScoreT1, betScoreT2, actualScoreT1, actualScoreT2,
                 breakdown: ScoreBreakdown) ]) ]
  )
  ```
- Only stages with ≥1 scored (played, bet-on) match appear. 404 if the user has no
  quiniela in the pool.

## Phase 2 — Frontend: scorecard page

- `frontend/lib/api/scorecard.ts` — `getScorecard(userId)` + types.
- `frontend/app/ranking/[userId]/page.tsx` — server component: auth-gate, fetch
  the scorecard, render header (name + total) + a `StageSection` per stage
  (most-recent first, first open; **stage points total shown in the header**
  alongside the count or in place of it), and per-match breakdown rows inside.
- `frontend/components/ranking/MatchScoreRow.tsx` — one played match: teams, the
  player's pick, the actual result, and the breakdown line
  (`{outcome} resultado · {team1} local · {team2} visitante · ×{mult} = {total}`,
  bilingual; only non-zero components emphasized). Pure/presentational (label
  props), unit-tested like `RankingRow`.
- **Make ranking rows navigate:** wrap each `RankingRow` in a `Link` to
  `/ranking/{userId}` (the row stays visually identical; just becomes tappable).
- i18n: `scorecard` namespace (title, back-to-table, the breakdown labels,
  "no points yet"); es-CO + en.

## Out of scope

- No consolidation of the three scoring implementations (DB, CompareService,
  ScoreBreakdown). `ScoreBreakdown` is pinned to the DB so it's safe; log the
  consolidation in the tech-debt backlog item rather than refactoring now.
- No change to the ranking totals, the trigger, or `MatchesService`.
- No scorecard for unplayed/future matches; no caching.

## Testing

- **Backend unit:** `ScoreBreakdown.of` component values for representative cases
  (exact, winner+diff, draw, knockout ×N, knockout-draw via advanced team,
  unplayed → zeros).
- **Backend IT (Testcontainers):** the pinning test (breakdown total == DB
  function across scorelines/multipliers); a scorecard IT (a user with bets on
  played group + knockout matches → correct per-stage totals + one known
  breakdown; auth required; 404 for a non-member).
- **Frontend:** `MatchScoreRow` renders the breakdown + pick + result; the
  scorecard page renders stage sections with totals and the back link; ranking
  rows link to `/ranking/{userId}`.

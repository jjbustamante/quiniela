# Design — Configurable per-round knockout multipliers

> Today every knockout round scores a flat `×2`. Admins should set a per-round
> multiplier (e.g. R32 ×2, R16 ×3, QF ×4, SF/Final higher) so later rounds are
> worth more. The literal `×2` lives in the DB scoring function and its Java
> mirror; both must read the configured value.

**Date:** 2026-06-06
**Branch:** `feat/knockout-multipliers`
**Status:** approved, ready for implementation plan

## Background / grounding

- Knockout-ness is derived from the `round` table: `r.code <> 'GROUP'`. Round
  codes are `GROUP` plus knockout codes (R*, QF, SF, THIRD, FINAL). The
  `update_players_score` trigger already has `NEW.round_id`, so it can look up a
  per-round value.
- Scoring lives in **two** implementations, now aligned and guarded:
  - DB: `score_match_for_bet(...)` (V016) — additive (outcome 3 + each-team-exact
    2 + goal-diff 1), then `IF is_knockout THEN total * 2`.
  - Java: `CompareService.scoreMatchForBet(...)` — same additive model, `total * 2`
    for knockout (for the Duelos/H2H preview).
  - `ScoringDivergenceTest` (added in `32abb74`) compares the two across every
    0–5 scoreline (group + knockout). This is the safety net that ensures both
    read the multiplier consistently.
- Config convention in this codebase = a column on the relevant entity (V018
  added `house_cut_percentage` to `pool`). We follow that, not a new table.
- `AdminGuard` (`io.quiniela.api.user.AdminGuard`, added in `32abb74`) is the
  shared admin check; new admin endpoints inject it.

## Storage

Add `points_multiplier INT NOT NULL DEFAULT 1` to the **`round`** table.
- Seed (in the migration): `GROUP = 1`; every knockout round = `2` (preserves
  today's behavior exactly).
- A `CHECK (points_multiplier >= 1)` guard (a 0 or negative multiplier would
  zero/invert scoring).
- Per-tournament safe (`round` rows belong to a tournament).
- Migration version **V019** — current head is `V018__pool_house_cut.sql`.
  Note: the deferred `feat/bilingual-paul` branch also reserved V019; whichever
  merges first keeps it, the other bumps. Confirm the next free number at
  implementation time.

## DB scoring

- `score_match_for_bet` gains a trailing `multiplier INT DEFAULT 2` parameter
  and returns `total * multiplier` instead of the literal `IF is_knockout THEN
  total * 2 ELSE total`. The `DEFAULT 2` keeps any incidental existing callers
  working; the trigger always passes the real value.
- `is_knockout` stays — the draw / predicted-winner outcome branch still needs
  it.
- The `update_players_score` trigger looks up `r.points_multiplier` for
  `NEW.round_id` (alongside the existing `is_knockout` lookup) and passes it.
  GROUP rounds pass `1` → `total * 1` = unchanged group scoring.

## Java scoring

- `CompareService.scoreMatchForBet` takes the multiplier (instead of the
  `knockout` boolean's implicit `×2`) and returns `total * multiplier`. The
  `is_knockout` boolean is still needed for the additive draw logic, so the
  signature carries both (knockout flag + multiplier), or resolves the multiplier
  from the round.
- The match projection CompareService already runs (JdbcTemplate) joins
  `round.points_multiplier` so the value is available per match without an extra
  round trip.
- **Third call site (found in Plan 1 final review):** `MatchesService` computes
  the per-match `points_earned` shown on the Matches page via the same SQL
  `score_match_for_bet`. It must also thread `r.points_multiplier`, or the
  Matches page would show ×2 per-match points while the leaderboard/Duelos use
  the configured value once a multiplier is changed. Fixed in Plan 1.
- `ScoringDivergenceTest` is extended to drive matching multipliers through both
  the Java function and the DB function so they can never drift.

## Admin

- A new `AdminGuard`-protected service + endpoint (mirroring
  `AdminPoolConfigService` / the money-config flow) to list knockout rounds with
  their multipliers and update them. Writes `round.points_multiplier`.
- Frontend: a small panel alongside the existing money-config panel
  (`/admin/config`) — one editable number per knockout round.

## Scoring explainer page

- `frontend/app/scoring/page.tsx` reads the per-round multipliers (via a public
  read endpoint or the existing public summary) and explains them, so the page
  never drifts from the actual configured values. Bilingual copy
  (`messages/es-CO.json` + `messages/en.json`; UI stays Spanish).

## Behavioral constraints / non-goals

- **No retroactive rescore on config change.** The trigger recomputes points on
  match-result updates, not on a multiplier edit. A changed multiplier only
  affects matches scored after the change. Per
  `feedback_quiniela_no_engagement_gated_mechanics`, multipliers must be **locked
  before the first knockout match**; the admin UI states this explicitly. Auto-
  rescore is intentionally out of scope (rescoring mid-tournament is exactly what
  the policy forbids).
- **Group multiplier** stays `1` (configurable in the schema, but the UI focuses
  on knockout rounds; group is shown read-only or omitted).
- Not kickoff-blocking (no knockouts until after the group stage), but should be
  in place and locked before knockouts begin.

## Testing

- **`V019MigrationTest`** — `round.points_multiplier` exists; GROUP seeded `1`,
  knockout rounds seeded `2`.
- **`ScoringDivergenceTest`** (extend) — Java `scoreMatchForBet` and the DB
  `score_match_for_bet` agree across scorelines AND multipliers (e.g. 1, 2, 3).
- **Scoring IT** — a knockout match with a non-default multiplier (e.g. 3) flows
  through the trigger to the correct stored `quiniela.points`.
- **Admin service IT** — `AdminGuard` enforced; update changes
  `round.points_multiplier`; non-admin rejected.
- **Frontend** — admin panel renders the editable per-round inputs; scoring page
  renders the configured multipliers; `pnpm typecheck` + lint green.

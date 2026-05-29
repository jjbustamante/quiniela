# Admin Test Mode — Design

> Lets the admin run realistic pre-launch test rounds with friends on the
> single production environment, then flip cleanly to real play. Adds a
> persisted test-mode flag, a "not real data" banner, and an admin test panel
> with clean / set-deadlines / simulate tools.

## Problem & context

There is one environment (prod). Two weeks before kickoff (2026-06-11) we want
friends to exercise the real app — bracket fill, scoring, ranking, payments,
partidos — without that data becoming the real pool. Needs:

1. A visible signal to players that data is not real.
2. An admin reset to wipe test play between rounds.
3. A way to exercise locked-state + knockout rounds despite real deadlines being
   in the future.
4. A Paul-like admin "simulate results" tool to generate scores and advance the
   bracket so later phases can be tested.

### Current state (verified)
- `tournament` table (V001) has `status`, `group_stage_deadline`,
  `knockout_deadline` (TIMESTAMPTZ). Lock logic reads these (`LockClock`).
- Fixtures (teams + matches) are loaded from football-data.org by
  `FootballDataLoader` at startup — **must not be deleted** by clean.
- `match` has `winner_id`, `match_parent_1_id`, `match_parent_2_id` — the
  knockout bracket tree, so advancement is computable.
- Scoring is a PL/pgSQL trigger fired on `match.score_t1/score_t2` UPDATE
  (V005/V010) — writing simulated scores recomputes `quiniela.points` for free.
- `Round` has `code` + `sequence` (1=GROUP … 7=FINAL);
  `RoundRepository.findByTournamentIdOrderBySequenceAsc`.
- Admin endpoints live under `/api/admin`; admin gate is
  `AdminResultsService.requireAdmin` (ResponseStatusException, role check).
- Next migration: **V012**.

## Decisions (locked in brainstorming)

1. **Test mode = persisted flag** `tournament.test_mode BOOLEAN NOT NULL
   DEFAULT true`. Admin toggles it; flipping to `false` is the "go live" action.
   Gates the banner (everyone) and the admin test tools (server-enforced).
2. **Clean wipes:** bets, match results (score/winner/played reset), quiniela
   points, AND payment paid/settled flags. **Keeps:** users, pool memberships,
   football-data fixtures (teams/matches/schedule).
3. **Simulate:** both per-round (current round) and simulate-all (current round
   → Final).
4. **Dates:** admin-editable deadline timestamps (no fake clock). Set group
   deadline to the past to test locked state + unlock knockouts; reset to real
   dates when done. (Separate, pre-existing timezone display bug is handled in
   its own piece, shipped before this — out of scope here.)
5. **Knockout draws:** allowed; the simulator assigns a random `winner_id` on a
   drawn knockout score. Group draws stay as draws.
6. Admin test panel is its own page `/admin/test`.

## Architecture

### Data — migration `V012__test_mode.sql`
```sql
ALTER TABLE tournament ADD COLUMN test_mode BOOLEAN NOT NULL DEFAULT true;
```
Add `testMode` to the `Tournament` JPA entity (boolean, `@Column(name =
"test_mode")`) with getter + setter.

### Banner — `/api/public/summary`
- Extend `PublicSummaryController.TournamentSummary` (or `SummaryResponse`) with
  `boolean testMode`, read from the tournament row. (Put it on
  `SummaryResponse` top-level for simplicity: `record SummaryResponse(...,
  boolean testMode)`.)
- Frontend: extend the `PublicSummary` type + FALLBACK (`testMode: false`).
- **`TestModeBanner`** — a thin async server component that calls
  `getPublicSummaryOrFallback()` itself and renders a fixed bar only when
  `testMode` is true (renders `null` otherwise, including when the summary is
  unreachable since FALLBACK has `testMode: false`). Placed once in
  `app/layout.tsx` immediately above `{children}` so it's global — no per-page
  wiring. `layout.tsx` is already an async server component, so the extra fetch
  is fine (and `getPublicSummaryOrFallback` never throws).
- Copy (i18n `testMode` namespace): es "MODO PRUEBA · los datos no son reales",
  en "TEST MODE · data is not real".

### Admin test API — `/api/admin/test/*` (`AdminTestController` + `AdminTestService`)
All methods call `requireAdmin(callerId)` (copy the established pattern) AND
`requireTestModeEnabled()` — the latter loads the tournament and throws
`ResponseStatusException(HttpStatus.CONFLICT, "Test mode is off")` if
`test_mode = false`, so these tools cannot touch real data post-launch. The
toggle endpoint itself only needs `requireAdmin` (you must be able to turn it
back on).

Endpoints (tournament id = 1, pool id = 1):

- `GET /api/admin/test/state` → `{ testMode, groupStageDeadline,
  knockoutDeadline, currentRoundCode | null, roundsRemaining }` — drives the
  panel UI. `currentRoundCode` = lowest-sequence round that still has unplayed
  matches; null if all played. Admin-only (no test-mode gate on read).
- `PUT /api/admin/test/mode` body `{ enabled: boolean }` → sets
  `tournament.test_mode`. Admin-only. Returns `{ testMode }`.
- `POST /api/admin/test/clean` → test-mode-gated. In ONE transaction:
  `DELETE FROM bet`;
  `UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false
   WHERE tournament_id=1`;
  `UPDATE quiniela SET points=0 WHERE pool_id=1`;
  `UPDATE payment SET paid=false, paid_at=NULL, marked_paid_by=NULL,
   settled=false, settled_at=NULL, marked_settled_by=NULL WHERE pool_id=1`.
  Does NOT touch users, pool_membership, team, round. Returns
  `{ betsDeleted, matchesReset }` counts.
- `PUT /api/admin/test/deadlines` body `{ groupStageDeadline, knockoutDeadline }`
  (ISO-8601 strings, either nullable) → test-mode-gated; updates the tournament
  deadline columns. Returns the new values.
- `POST /api/admin/test/simulate/round` → test-mode-gated. Simulates the current
  round (see engine). Returns `{ roundCode, matchesPlayed, advancedToRoundCode |
  null }`.
- `POST /api/admin/test/simulate/all` → test-mode-gated. Loops round simulation
  from the current round through the Final. Returns `{ roundsSimulated,
  totalMatchesPlayed }`.

### Simulation engine (in `AdminTestService`)
Random score per side: 0–4, low-weighted (e.g. pick from a small distribution
favoring 0–2). Determinism not required, but to keep tests stable the engine
takes scores from an injectable source — simplest: a private `int randomGoals()`
using `java.util.concurrent.ThreadLocalRandom`; tests assert structural
outcomes (all played, winners set, children populated) not exact scores.

Per-round algorithm (`simulateRound`):
1. Resolve current round = lowest `sequence` round with ≥1 unplayed match
   (`played=false`) for tournament 1. If none, no-op (return roundCode null).
2. For each unplayed match in that round with both teams present
   (`team_1_id` and `team_2_id` not null):
   - generate `s1, s2`.
   - knockout (round.code != 'GROUP'): if `s1==s2`, keep the drawn score but
     set `winner_id` = randomly one of the two teams; else winner = higher.
     group: `winner_id` = higher score or null on draw (matches
     `AdminResultsService.winnerOf` semantics).
   - set `score_t1=s1, score_t2=s2, played=true, winner_id=...`, save (fires the
     scoring trigger → points recompute).
3. If the round is a knockout round, advance: for every match whose
   `match_parent_1_id`/`match_parent_2_id` point at matches in the just-played
   round, set its `team_1_id` = parent1.winner_id, `team_2_id` =
   parent2.winner_id (only when both parents now have winners). This fills the
   next round's fixtures so it becomes simulatable.
4. Return the round code + the round advanced into (next sequence) if any.

`simulateAll`: call `simulateRound` in a loop until it reports no current round
(all matches played), capped at the 7 rounds to avoid any infinite loop.

Matches with null teams (knockout slots not yet resolved) are skipped — they get
populated by advancement from their parents on the next round's simulation.

### Frontend — `/admin/test` page (admin-only)
- Server component, admin gate mirroring `app/admin/payments/page.tsx`
  (`getMe()`, redirect non-admins). Also: if `test_mode` is off, still render the
  page but show only the "test mode is OFF" state + the enable toggle (so admin
  can turn it back on); hide clean/simulate/deadline tools when off.
- Fetches `GET /api/admin/test/state`.
- Sections:
  - **Mode toggle** — switch bound to `test_mode`; turning OFF shows a confirm
    ("Go live? Test tools will be disabled and the banner removed.").
  - **Deadlines** — two datetime inputs prefilled from state; Save → PUT
    deadlines. Helper text: "Set group deadline in the past to test locked
    brackets + unlock knockouts."
  - **Simulate** — shows `currentRoundCode`; "Simular ronda actual" button →
    POST simulate/round; "Simular todo" → POST simulate/all. After each, refetch
    state.
  - **Clean** — "Limpiar datos de prueba" button with a confirm; POST clean,
    then refetch.
- Server actions for each mutation (`revalidatePath("/admin/test")`).
- Nav drawer: add an admin-only "Modo prueba" → `/admin/test` item, shown
  whenever `role === "ADMIN"` (NOT gated on testMode — the admin must always be
  able to reach the page to toggle the mode back on; the page self-handles the
  off state).
- i18n: `test` namespace (title, modeOn/Off, enable/disable, confirmGoLive,
  deadlinesTitle, deadlinesHelp, save, simulateRound, simulateAll,
  currentRound, allPlayed, clean, confirmClean, cleaned) + the `testMode` banner
  key. Spanish source, English mirror, parity.

## Authorization summary

| Endpoint | Gate |
|---|---|
| `GET /api/admin/test/state` | admin |
| `PUT /api/admin/test/mode` | admin |
| `POST /api/admin/test/clean` | admin + test-mode-on |
| `PUT /api/admin/test/deadlines` | admin + test-mode-on |
| `POST /api/admin/test/simulate/round` | admin + test-mode-on |
| `POST /api/admin/test/simulate/all` | admin + test-mode-on |

Non-admin → 403. Test-mode-gated endpoint while mode off → 409.
`SecurityConfig` already requires auth on `/api/admin/**`.

## Testing

Backend ITs (`AdminTestControllerIT`):
- `clean` deletes bets, resets matches (score/winner/played), zeroes points,
  resets payments; leaves team/round/users/pool_membership intact.
- `simulate/round` plays only the current (lowest-sequence-unplayed) round, sets
  played+winner, and fires the trigger (points change for a bet on a played
  match).
- knockout advancement: after simulating a knockout round, child matches whose
  parents are in that round get `team_1_id`/`team_2_id` populated from parent
  winners.
- `simulate/all` reaches a state where every match with resolvable teams is
  played.
- authz: every endpoint 403 for non-admin; clean/deadlines/simulate 409 when
  `test_mode=false`; `mode` toggle works for admin regardless.
- `PublicSummaryControllerIT`: response includes `testMode`.

Frontend: typecheck + lint + build; banner renders when `testMode` true.

## Out of scope
Timezone display fix (separate piece, ships first); a simulated/fake clock;
penalty-shootout score modeling; manual per-match winner override in the
simulator; deleting users/memberships/fixtures on clean; multi-tournament (all
ops hardcode tournament/pool id = 1, matching the rest of the app).

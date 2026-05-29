# Knockout Bracket Seeding (test simulator) — Design

> Makes the test-mode result simulator able to drive the knockout phase, not
> just the group stage. Adds a one-time "seed the bracket from group standings +
> wire the parent-pointer tree" step, after which the existing
> `advanceFromRound` logic carries winners all the way to the Final. Test-mode
> only — production fills the bracket from the football-data.org provider.

## Why this exists (context)

In **production**, the knockout bracket fills itself: `FootballDataLoader`
re-fetches from football-data.org, and once FIFA plays the groups the provider
returns knockout matches with real teams already assigned. We never compute
standings or wire a bracket — that's why `match.match_parent_1_id` /
`match_parent_2_id` (columns added in V004) are **never populated** by the loader
or any migration, and group-qualification logic was correctly never built.

The **test simulator** exists only because we're testing ~2 weeks before kickoff
with no real results. football-data.org returns knockout matches with null/TBD
teams (the real groups haven't happened), so to exercise the knockout
UI/scoring/ranking now, the simulator must stand in for what the provider will
eventually hand us: invent group results, derive standings, fill the 32 R32
slots, and wire the bracket so winners advance.

**Decision (locked):** keep the simulator and the provider as two separate,
focused things — do NOT introduce a shared `ResultsSource` interface. The
provider "pulls external truth" (idempotent, stateless) and the simulator
"invents + steps forward" (stateful, round-by-round, computes standings + wires
the tree). They share almost no behavior; an interface over them would be
indirection without payoff. (Separately tracked, NOT part of this work: audit
`FootballDataLoader` re-sync idempotency before go-live — does it UPDATE played
matches as real results arrive, or only insert once?)

## Current state (verified)

- `match` has `match_parent_1_id` / `match_parent_2_id` (V004) — **always null**
  in prod (loader inserts without them; no migration sets them).
- Plan 7 simulator (`AdminTestService`): `simulateRound`/`simulateAll` →
  `simulateCurrentRound()` plays the lowest-sequence round with unplayed
  matches; for knockout rounds it calls `advanceFromRound(roundId)` which fills
  a child match's teams from its parents' winners — **but only when parents are
  wired**, which they aren't. So today `simulateAll` plays the 72 group matches
  and stops (R32 has null teams + null parents).
- `match` columns the seeder needs: `group_code` (group matches), `team_1_id`,
  `team_2_id`, `score_t1/score_t2`, `winner_id`, `played`, `round_id`,
  `match_parent_1/2_id`, `kickoff_at`. `Match` entity has getters for all + setters
  for score/winner/played; team + parent writes go via `jdbc` (no entity setters
  for those — matches the existing `advanceFromRound` which uses `jdbc.update`).
- Rounds (V006 seed): GROUP(seq1), R32(2), R16(3), QF(4), SF(5), THIRD_PLACE(6),
  FINAL(7). Counts: 72 group, 16 R32, 8 R16, 4 QF, 2 SF, 1 THIRD_PLACE, 1 FINAL.
- `AdminTestService` already has `MatchRepository matches`, `RoundRepository
  rounds`, `JdbcTemplate jdbc`, `TOURNAMENT_ID=1L`,
  `matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc`.

## Design

All changes are in `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java`
(+ its IT). No frontend, no migration. Test-mode + admin gating already wraps the
public simulate methods.

### 1. Hook the seeder into the group-round simulation

In `simulateCurrentRound()`, after the loop that plays the matches and sets
`advancedTo`, add: if the round just played is `GROUP`, call
`seedKnockoutBracket()`. (Group is not a knockout round, so the existing
`advanceFromRound` branch doesn't run for it — the seeder replaces that step for
the GROUP→R32 transition.)

`seedKnockoutBracket()` is idempotent: it first checks whether any R32 match
already has a non-null `team_1_id`; if so it returns immediately (already
seeded — e.g. a second `simulate/round` call).

### 2. Compute group standings

A private `groupStandings()` that, over the played group matches (`round.code =
GROUP`, `played = true`), builds a per-team record: points (3 win / 1 draw / 0
loss), goals for, goals against. Rank within each group by:
1. points DESC
2. goal difference (GF − GA) DESC
3. goals for DESC
4. team id ASC (final deterministic tiebreak)

Produces, per group code (A–L), an ordered list of its 4 teams. A team's
`group_code` comes from the group match rows (each group match carries
`group_code`; team membership is derivable as the distinct team ids appearing in
that group's matches).

### 3. Select the 32 qualifiers

- 1st + 2nd of each of the 12 groups → 24 teams.
- The 8 best 3rd-placed: take each group's 3rd-ranked team, rank those 12 by the
  same rule (points → GD → GF → team id), take the top 8.
- Total = 32. (Deterministic; no unbroken ties because team id is the final key.)

### 4. Fill the R32 slots

Order the 16 R32 matches by `kickoff_at` (stable). Order the 32 qualifiers into a
seeding list — simple deterministic pairing: interleave so stronger-ranked teams
meet lower-ranked ones (e.g. seed list = [all 12 group winners by overall rank,
then 12 runners-up, then 8 third-placed], paired slot i = list[i] vs
list[31−i]). This is a **valid test bracket, NOT FIFA's official slotting
matrix** — purpose is exercising the app, not predicting the real draw. Write
each pair into the R32 match's `team_1_id`/`team_2_id` via `jdbc.update`.

### 5. Wire the parent-pointer tree

For the rounds above R32, set `match_parent_1_id`/`match_parent_2_id` so the
existing `advanceFromRound` can walk them. For each round R16/QF/SF, order both
the child round's matches and the parent round's matches by `kickoff_at`; child
*i* gets parents `2i` and `2i+1` from the parent round. Final: parents = the two
SF matches. Third-place: parents = the two SF matches as well (but it needs the
SF **losers** — see §6). All via `jdbc.update`.

Counts line up: 16 R32 → 8 R16 (each 2 parents) → 4 QF → 2 SF → 1 Final; +1
Third-place from the 2 SFs.

### 6. Advancement: winners (existing) + the third-place loser case

The existing `advanceFromRound(playedRoundId)` already fills children from parent
**winners** — that correctly handles R32→R16→QF→SF→Final once parents are wired.
The ONE case it doesn't cover: the third-place match needs the SF **losers**.

Add handling so that when the SF round is simulated, the third-place match's two
team slots are filled from the two SF losers (loser = the SF match's non-winner
team). Implement as a small explicit step inside the SF-advancement path (after
SF matches are played, compute each SF's loser = the team that isn't
`winner_id`, and write them into the third-place match). Keep it narrow — only
the third-place match, identified as the round-6 match.

### 7. Extend `clean` to reset the bracket wiring

`clean` currently nulls `score/winner/played` for all matches + deletes
bets/points/payments. Extend it to also reset the **simulated bracket** so a
clean→re-simulate cycle starts fresh.

**Rule:** a knockout match = any match whose round is not GROUP. The simulator
writes simulated team slots (R32) and parent pointers (R16+) only onto knockout
matches; group matches' teams come from the fixture loader and must be kept. So,
in one statement, reset teams + parents for every non-GROUP match:
```sql
UPDATE match SET team_1_id=NULL, team_2_id=NULL,
                 match_parent_1_id=NULL, match_parent_2_id=NULL
WHERE tournament_id = 1
  AND round_id <> (SELECT id FROM round WHERE tournament_id = 1 AND code = 'GROUP')
```
This single rule covers everything: R32 (teams cleared, no parents anyway),
R16/QF/SF/Final/Third-place (teams + parents cleared). It's safe pre-seeding
(those columns are already null → no-op) and correct post-seeding. The existing
`score/winner/played` reset still applies to all matches including group.

### Files

- Modify: `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java` —
  add `seedKnockoutBracket`, `groupStandings` (+ a small standings record),
  third-place-loser handling, the `simulateCurrentRound` GROUP hook, and the
  `clean` extension.
- Modify: `backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java`
  — new cases.

## Testing

Backend ITs (extend `AdminTestControllerIT`):
- **Seeds R32 on group completion:** clean → `simulate/round` (plays GROUP) →
  exactly 32 R32 team slots populated (all 16 R32 matches have both teams).
- **Wires the tree:** after the above, every R16/QF/SF/Final/Third-place match
  has both `match_parent_*` set.
- **Standings tiebreak:** construct a group (via direct match-score updates)
  where two teams tie on points but differ on goal difference; assert the
  higher-GD team ranks above (qualifies). (Set scores with `jdbc`, run the
  standings step via a `simulate/round`, check which team landed in R32.)
- **simulate/all reaches a champion:** clean → `simulate/all` → the Final match
  is `played=true` with a non-null `winner_id`; every R32 match played.
- **Third-place gets SF losers:** after `simulate/all`, the third-place match's
  two teams are the two SF losers (each is its SF's non-winner).
- **clean resets the bracket:** simulate/all → clean → all knockout matches have
  null teams + null parents again; group matches still have their teams.

No frontend tests — `/admin/test`, the bracket pages, and `/matches` already
render whatever's in the DB.

## Out of scope
FIFA's official 3rd-place matrix + exact crossing pattern (this is a valid
test bracket, not the real draw); any `ResultsSource` interface; frontend
changes; the `FootballDataLoader` re-sync idempotency audit (separately tracked,
pre-launch). This logic is test-mode + admin gated and 409-locked once test mode
is off, so it can never run in production.

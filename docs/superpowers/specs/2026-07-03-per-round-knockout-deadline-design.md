# Per-Round Knockout Deadline — Design

> Replaces the single, shared `tournament.knockout_deadline` (locked once, before
> R32 kicked off) with a deadline derived per knockout round, so each round gets
> its own small prediction window: opens once its bracket is defined (any match
> in the round has a team assigned), closes at that round's own first kickoff.

## Why this exists (context)

`tournament.knockout_deadline` was set once, before R32 started
(2026-06-29 17:00 UTC), and is shared by every knockout round (R32, R16, QF,
SF, Final, Third-place) in both `BracketService.saveBet` (bet lock) and
`LockClock.isMatchRevealable` (reveal-to-other-players in Compare/Duelos). With
R32 now finishing (2026-07-03) and R16 kicking off tomorrow
(2026-07-04 17:00 UTC), this single stale deadline incorrectly shows R16 (and
every later round) as locked, with no production-safe way to move it — the
only code that writes `knockout_deadline` is `AdminTestService.setDeadlines()`,
which is test-mode-gated and previously clobbered this same value by accident
(2026-06-07 incident, see BACKLOG.md).

This is not a one-off data fix: the intended design (confirmed 2026-07-03) is
that each knockout round gets its own fill-once-before-kickoff window — the
same anti-front-runner principle behind
[[feedback_quiniela_no_engagement_gated_mechanics]] ("no mid-tournament
re-predictions"), just scoped per round instead of to the whole knockout
stage. A code change is required, not a database write.

## Design

All matches (including future rounds like R16/QF before their teams are known)
already carry a real `kickoff_at` from the football-data.org fixture calendar,
loaded well ahead of time. So each round's window can be derived purely from
its own matches — no new schema, no new admin tool, no risk of a repeat
clobber incident:

- **Round opens** once at least one match in the round has a team assigned
  (`team_1_id IS NOT NULL OR team_2_id IS NOT NULL`) — this is the existing
  "any team known" signal `BracketService.getMyBracket()` already computes for
  the `unlocked` flag (`roundUnlocked`), just newly also enforced on the save
  path.
- **Round closes** at `MIN(kickoff_at)` across the round's own matches — this
  replaces `tournament.knockout_deadline` everywhere it's read for knockout
  rounds.
- The existing per-match guard (`now.isAfter(match.getKickoffAt())`, "Este
  partido ya comenzó") is unchanged — it still blocks editing a bet for one
  specific match that has already kicked off, independent of the round-level
  window.
- GROUP stage is untouched — still gated by `tournament.group_stage_deadline`.

### Backend changes

1. **`BracketService.saveBet`**: for non-GROUP rounds, replace the
   `t.knockoutDeadline()` comparison with: fetch the round's own matches
   (`matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc`), reject if no
   match has a team assigned yet ("round not open"), reject if `now` is at or
   past the round's own first kickoff ("round locked"). The per-match kickoff
   guard stays as-is, checked after.
2. **`BracketService.getMyBracket`**: `KnockoutRoundView.locked` switches from
   the single `knockoutLocked` (tournament-wide) to the same per-round
   first-kickoff comparison. Add a new `deadline` field (nullable ISO string =
   the round's own first kickoff) to `KnockoutRoundView` so the frontend can
   display the correct close time instead of the stale global one.
3. **`CompareService`** (`getGroupConsensus`, `getMatchPicks`, `getH2H`): the
   `revealed = played || isMatchRevealable(...)` check currently uses the
   single shared knockout deadline too — meaning right now, any not-yet-played
   knockout match is already "revealed" to opponents in Compare/Duelos
   (harmless today only because saveBet also blocks new bets under the same
   stale deadline; would leak live picks the moment saveBet is fixed without
   also fixing this). Replace with a per-round-code first-kickoff map derived
   once from the already-fetched `fetchMatchMeta()` list (group by
   `roundCode`, take `min(kickoffAt)` — no extra query), and reveal a knockout
   match once `now` is past its round's own first kickoff. `LockClock`'s
   `isKnockoutRevealable`/`isMatchRevealable` helpers become unused for
   knockout rounds and can be removed or left GROUP-only; `knockout_deadline`
   stops being read anywhere in application logic (column left in place,
   unused — no migration needed).

### Frontend changes

- `KnockoutRoundView` type (`frontend/lib/api/bracket.ts`) gains `deadline:
  string | null`.
- `frontend/app/knockout/[roundId]/page.tsx`: locked-badge display switches
  from `bracket.knockoutDeadline` to `round.deadline`.
- `frontend/lib/home-phase.ts`: the `fillKnockout` focus card's `deadline`
  switches from `bracket.knockoutDeadline` to `openKnockout.deadline`.

## Out of scope

- Any admin UI to override a round's deadline — the derived (matches-based)
  window is the whole point of avoiding a repeat of the manual-deadline
  clobber incident.
- Dropping or migrating the now-unused `tournament.knockout_deadline` column —
  left in place, harmless, can be cleaned up later.
- Any change to GROUP-stage locking.
- Any change to scoring/points logic.

## Testing

Extend backend integration tests (`BracketServiceIT`/`BracketControllerIT`,
whichever currently cover `saveBet`/`getMyBracket`) with:
- A round with no team assigned yet: `saveBet` rejected as not-yet-open.
- A round with a team assigned, now before its first kickoff: `saveBet`
  succeeds.
- A round with a team assigned, now after its first kickoff (but the specific
  match not yet kicked off): `saveBet` rejected as locked, distinct from the
  existing per-match "ya comenzó" case.
- `getMyBracket`'s `KnockoutRoundView.locked`/`deadline` reflect the round's
  own first kickoff, not the tournament-wide value.

Extend `CompareService` tests (if present) or add coverage confirming a
knockout match is not `revealed` before its round's first kickoff even when
bets exist, and is revealed after.

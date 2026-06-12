# Frozen per-match points + per-match kickoff lock — Design

**Date:** 2026-06-12
**Status:** Approved (design), pending build
**Author:** Juan + Claude

## Problem

During the group stage, betting was reopened (to let players who'd missed the
deadline participate) and the `group_stage_deadline` was extended to
`2026-06-12 02:00Z`. That deadline is **later than early matches' kickoffs**
(game 1 = match 537327 kicked off 2026-06-11 19:00Z and was scored 21:04Z;
game 2 = 537328 kicked off 02:00Z). With a single group-wide lock (not per-match),
players were able to **create or edit bets on matches that had already kicked off
or finished**.

Two symptoms resulted:

1. **The per-player scorecard (`/ranking/{id}`) shows points that don't match the
   leaderboard.** The leaderboard total (`quiniela.points`) is maintained
   incrementally by the match-scoring trigger and only credits bets that existed
   when the match was scored — so it is **correct** (game 1 is frozen at the
   legitimate 21:04Z scoring). But `ScorecardService` recomputes each match
   *live* from the player's *current* bet, so for players who created/edited a
   game-1 bet after game 1 was played, it shows phantom points. Example: Daneff's
   scorecard shows 5 + 7 = 12, but his real total is 7.

2. **Latent risk:** those post-kickoff bets are armed. If a played match is ever
   re-scored (manual correction), the trigger would recompute against current
   bets and credit them — corrupting the leaderboard for real.

The **leaderboard totals are correct and must not change.** Verified: for all 43
players, `quiniela.points` equals a correct frozen scoring (game 1 at 21:04Z +
game 2 at finalization). Only **3 players** have a stored total that differs from
a naive live recompute:

| Player (uid) | Stored | Live recompute g1 | Frozen g1 (correct) | Case |
|---|---|---|---|---|
| Daneff Dávila (19) | 7 | 5 | **0** | bet created after game 1 ended |
| Juan M. Viloria (42) | 7 | 7 | **0** | bet created after game 1 ended |
| Artistic Contracting (36) | 9 | 7 | **4** | edited prediction after game 1 ended |

Everyone else (40, incl. Gabriela who edited a bet but whose score is unchanged)
is already consistent.

## Goals

1. Make the scorecard show the **real, frozen** points per match, so it always
   matches the leaderboard — with a **polite** message explaining any difference.
2. **Prevent recurrence**: lock each bet at its own match's kickoff.
3. **De-arm the landmine** so a future re-score can't credit post-kickoff bets.
4. Leave `quiniela.points` (the authoritative leaderboard) untouched and correct.

## Non-goals

- Changing the leaderboard totals or the scoring formula.
- Recovering the *original* prediction text of an edited bet (it's overwritten
  and unrecoverable — we keep the original *points*, not the original pick).

## Design

### 1. New column `bet.points` — frozen per-match score

`ALTER TABLE bet ADD COLUMN points INT NOT NULL DEFAULT 0;`

This stores the points a bet **actually scored**, frozen at scoring time. It is
the new source of truth for *display*; `quiniela.points` remains the leaderboard
total. (`bet` PK is `(quiniela_id, match_id)`, so one row per player per match.)

### 2. Scoring trigger writes `bet.points`, and only scores pre-kickoff bets

Update `update_players_score()` (the `BEFORE UPDATE OF score_t1, score_t2,
advanced_team_id ON match` trigger):

- Restrict the bet loop to bets placed/edited **before the match kicked off**:
  `WHERE b.match_id = NEW.id AND b.created_at <= NEW.kickoff_at`.
  (Use `created_at`; see "Edited bets" below for why not `updated_at`.)
- For each such bet, after computing `new_points`, also persist it:
  `UPDATE bet SET points = new_points WHERE quiniela_id = bet_row.quiniela_id AND match_id = NEW.id`.

Effects:
- Going forward (with the kickoff lock in §5, no post-kickoff bets can exist), every
  bet is scored normally and its `bet.points` is frozen at scoring.
- The `created_at <= kickoff` filter **de-arms the landmine**: a re-score of an
  already-played match will *not* credit bets created after kickoff (Daneff,
  Juan M. Viloria), so the leaderboard can't be corrupted by them.
- This change only affects *future* match-score updates; it does **not**
  retroactively alter existing `quiniela.points`.

**Edited bets (Artistic):** a bet created before kickoff but *edited* after
(Artistic) is still in the loop (filter is on `created_at`). On a re-score it
would be scored with its *current* (edited) value — which we cannot prevent
without the lost original. This is acceptable because (a) the kickoff lock (§5)
stops new post-kickoff edits, and (b) we will **not** re-score game 1. We keep
`created_at` (not `greatest(created_at, updated_at)`) in the filter so that a
legitimate pre-kickoff bettor who edits a *later* match isn't wrongly dropped.

### 3. One-time backfill (in the V021 migration), verified

After adding the column and updating the trigger function:

```sql
-- (a) Recompute every bet on an already-played match (correct for the 40 clean).
UPDATE bet b
SET points = score_match_for_bet(
      (r.code <> 'GROUP'), b.score_t1, b.score_t2,
      m.score_t1, m.score_t2, b.predicted_winner_id, m.advanced_team_id,
      r.points_multiplier)
FROM match m JOIN round r ON r.id = m.round_id
WHERE b.match_id = m.id AND m.played;

-- (b) Bets that did NOT exist when their match was scored → 0 (created after
--     the match's scoring time, i.e. match.updated_at). Covers Daneff & Juan M.
UPDATE bet b SET points = 0
FROM match m
WHERE b.match_id = m.id AND m.played AND b.created_at > m.updated_at;

-- (c) Bets that existed at scoring but were EDITED afterward → restore the
--     frozen original = quiniela.points minus the player's other (already
--     correct) bet points. Covers Artistic. Runs after (a)+(b).
UPDATE bet b SET points = q.points - COALESCE((
        SELECT SUM(b2.points) FROM bet b2
        WHERE b2.quiniela_id = b.quiniela_id AND b2.match_id <> b.match_id), 0)
FROM match m, quiniela q
WHERE b.match_id = m.id AND m.played AND q.id = b.quiniela_id
  AND b.created_at <= m.updated_at AND b.updated_at > m.updated_at;
```

**In-migration assertion (fail the migration if violated):** for every quiniela,
`SUM(bet.points)` over played matches must equal `quiniela.points`.

```sql
DO $$
DECLARE bad INT;
BEGIN
  SELECT count(*) INTO bad FROM (
    SELECT q.id, q.points,
           COALESCE(SUM(b.points) FILTER (WHERE m.played), 0) AS sum_bet
    FROM quiniela q
    LEFT JOIN bet b ON b.quiniela_id = q.id
    LEFT JOIN match m ON m.id = b.match_id
    GROUP BY q.id, q.points
  ) t WHERE t.points <> t.sum_bet;
  IF bad > 0 THEN
    RAISE EXCEPTION 'bet.points backfill mismatch for % quiniela(s)', bad;
  END IF;
END $$;
```

Expected outcome (dry-run verified read-only against prod): Daneff g1→0,
Juan M. Viloria g1→0, Artistic g1→4; all 43 reconcile.

> The migration is safe on fresh/test DBs: with no played matches, (a)–(c) are
> no-ops and the assertion passes trivially (every quiniela 0 = 0).

### 4. `ScorecardService` — read frozen points + tamper message

Currently it recomputes each match live via `ScoreBreakdown.of(currentBet)`.
Change to:

- Use **`bet.points`** as the displayed/summed points per match (so the breakdown
  subtotal equals the header `quiniela.points`).
- Still compute the live recompute of the *current* prediction. When it **differs**
  from `bet.points`, the bet was placed/edited after the match — set a
  `note` on that match row:
  - if `bet.created_at > match.kickoff_at` → **placed-after** note (points are 0).
  - else → **edited-after** note (original points kept).
- No note when they're equal (Gabriela, everyone clean).

The `ScorecardView`/`MatchScoreView` gains an optional `note` field
(an enum/string key: `PLACED_AFTER_KICKOFF` | `EDITED_AFTER_KICKOFF`), so the
frontend can localize.

### 5. Per-match kickoff lock in `BracketService.saveBet`

Keep the existing round-deadline check **and** add a per-match kickoff check:

```java
// after resolving the match `m`:
if (now.isAfter(m.kickoffAt()))
    throw new BracketLockedException("Este partido ya comenzó");
```

So a bet is rejected if its match has kicked off **or** the round deadline passed.
This is the actual missing rule and prevents the whole class of problem on game 3+.

### 6. Frontend — render the note

`frontend/components/ranking/MatchScoreRow.tsx` shows the per-match points; add a
small, non-alarming note line when the API returns a `note`. Localized via
`frontend/i18n` (ES + EN). Polite, factual wording (no "trampa"/"cheat"):

| key | ES | EN |
|---|---|---|
| `PLACED_AFTER_KICKOFF` | "Registraste esta predicción después de que terminó el partido, por eso no suma puntos." | "This prediction was entered after the match ended, so it doesn't add points." |
| `EDITED_AFTER_KICKOFF` | "Editaste esta predicción después de que terminó el partido; se conservó tu puntaje original." | "This prediction was edited after the match ended; your original score was kept." |

## Affected files

- **Create:** `backend/.../db/migration/V021__bet_points_frozen.sql` (column +
  trigger update + backfill + assertion).
- **Modify:** `BracketService.saveBet` (kickoff lock); `ScorecardService` (read
  `bet.points`, set notes); `ScorecardView`/`MatchScoreView` record (+`note`);
  `Bet` entity (+`points`, read-only); `MatchScoreRow.tsx` + scorecard API
  type + i18n (ES/EN note strings).
- **Tests:** migration/trigger test (freeze + de-arm on re-score); backfill
  assertion; `saveBet` kickoff-lock test; `ScorecardService` note + frozen-points
  test; frontend `MatchScoreRow` note rendering.

## Testing

- **Migration/DB (Testcontainers):** seed a played match + bets (one pre-kickoff,
  one created-after, one edited-after); apply V021; assert `bet.points` (frozen,
  0, original) and the `SUM = quiniela.points` invariant. Then **re-score** the
  match and assert post-kickoff bets are NOT credited (landmine de-armed) and
  pre-kickoff bets update.
- **`BracketService.saveBet`:** rejects a bet whose match already kicked off
  (`BracketLockedException`); still allows a future match before its kickoff;
  still rejects after the round deadline.
- **`ScorecardService`:** clean bet → points = `bet.points`, no note; created-after
  → 0 + `PLACED_AFTER_KICKOFF`; edited-after → original + `EDITED_AFTER_KICKOFF`;
  breakdown subtotal equals header total.
- **Frontend `MatchScoreRow`:** renders each note (ES/EN) when present, nothing
  when absent.

## Rollout

- Build on a branch, full TDD, then merge to `master` (push deploys backend via
  CI). The V021 migration runs via Flyway on deploy; the in-migration assertion
  is the safety net. No manual prod SQL needed — the backfill *is* the migration.
- `quiniela.points` is never written by this change, so the leaderboard/prizes
  are unaffected.

## Out of scope

- Eliminating the incremental-trigger model entirely (deriving `quiniela.points`
  as `SUM(bet.points)`), a larger refactor — deferred.
- Recovering original prediction text of edited bets.

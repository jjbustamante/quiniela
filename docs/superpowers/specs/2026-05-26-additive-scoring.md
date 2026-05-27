# Spec — 4-Component Additive Scoring

> **Status:** Approved by Juan + friend-group review pending. Replaces the discarded `team-score-bonus` proposal.
> **Author / decision-maker:** Juan.

## Goal

Replace the existing V005 `score_match_for_bet` ladder (5/3/2/2/0 + ×2 knockout) with a **4-component additive shape** that gives partial credit more granularly and spreads points more evenly, while still rewarding the hardest predictions the most.

Per match, four independent components contribute points:

| Componente | Group | Knockout (×2) |
|---|---|---|
| **Ganador / Empate** — predicción correcta del *bucket* de resultado (gana T1, empate, o gana T2) | 3 | 6 |
| **Marcador del Equipo 1** — predicción exacta de los goles del equipo 1 | 2 | 4 |
| **Marcador del Equipo 2** — predicción exacta de los goles del equipo 2 | 2 | 4 |
| **Diferencia de goles** — `bet_t1 - bet_t2 == actual_t1 - actual_t2` (con signo), **solo si NO es exacto** | 1 | 2 |
| **Máximo por partido** | **7** | **14** |

Notes:
- The diff component is **suppressed when the prediction is exact**, because exact already implies outcome + T1 + T2 are all correct; awarding diff on top would feel like double credit.
- The diff is **signed**, so it cannot pay out on the wrong winner (different sign on diff = different winner).
- Mathematically, the components are not all independent: if T1 AND T2 are both correct, the prediction is exact and the diff is correct. So in practice you'll see only certain combinations occur — see worked examples below.

## Worked examples (fase de grupos)

| Apuesta | Real | Ganador (3) | T1 (2) | T2 (2) | Diff (1, no exact) | **Total** |
|---|---|:-:|:-:|:-:|:-:|:-:|
| 2-1 | 2-1 (exacto) | 3 | 2 | 2 | suppressed | **7** |
| 2-1 | 3-2 (ganador + diff) | 3 | 0 | 0 | 1 | **4** |
| 2-1 | 2-0 (ganador + T1 exacto) | 3 | 2 | 0 | 0 | **5** |
| 2-1 | 5-0 (solo ganador) | 3 | 0 | 0 | 0 | **3** |
| 1-1 | 0-0 (empate + diff) | 3 | 0 | 0 | 1 | **4** |
| 1-1 | 1-1 (empate exacto) | 3 | 2 | 2 | suppressed | **7** |
| 1-1 | 1-3 (empate fallido, T1 exacto) | 0 | 2 | 0 | 0 | **2** |
| 2-1 | 0-1 (falló ganador, T2 exacto) | 0 | 0 | 2 | 0 | **2** |
| 2-1 | 3-4 (fallo total) | 0 | 0 | 0 | 0 | **0** |

## Worked examples (eliminatorias, todo ×2)

| Apuesta | Real | **Total** |
|---|---|:-:|
| 2-1 | 2-1 (exacto) | 6 + 4 + 4 + 0 = **14** |
| 2-1 | 3-2 (ganador + diff) | 6 + 0 + 0 + 2 = **8** |
| 2-1 | 2-0 (ganador + T1) | 6 + 4 + 0 + 0 = **10** |
| 2-1 | 5-0 (solo ganador) | 6 + 0 + 0 + 0 = **6** |
| 2-1 | 0-1 (falló ganador, T2) | 0 + 0 + 4 + 0 = **4** |
| 2-1 | 3-4 (fallo total) | 0 + 0 + 0 + 0 = **0** |

## Total tournament cap

- Group stage: 72 × 7 = 504
- Knockouts: 32 × 14 = 448
- **Theoretical max: 952** (coincidentally same as the old 5/3/2/2/0 + bonus proposal; differently distributed)

## Why this shape

- **Smoother gradient.** Old ladder went 5 → 3 → 2 → 0 in steep steps. The new shape produces 7 / 5 / 4 / 3 / 2 / 0 — finer-grained, more variance.
- **No nested ladder.** The function body is purely additive: four independent checks → sum. Easier to read, easier to explain to the panas in WhatsApp.
- **Empate guessing rewarded more.** A predicted draw that lands on a different score (1-1 vs 0-0) now earns 4 (was 2): outcome + diff. Reflects that empate predictions are inherently harder than picking the favorite.
- **Wrong-winner-with-one-team-correct earns more.** 2 pts instead of 1 — partial credit feels less like a token consolation.
- **Front-runner softened.** With more partial-credit predictions across the bracket, the gap between casual and sharp players is smaller. A casual player who got 50 outcomes right but few exact scores can still post 150+ points.

## Scope

### In scope

- New Flyway migration `V010__additive_scoring.sql` doing `CREATE OR REPLACE FUNCTION score_match_for_bet(...)` with the new body + one-time recompute of `quinielas.points`.
- Rewrite `ScoringTriggerIT` test cases to match the new buckets (existing 5/3/2/2/0 numbers no longer apply).
- Update friend-group rules + any in-app rules documentation to reflect the new shape.

### Out of scope

- No other scoring change (no underdog bonus, no champion bonus, no prize-split change).
- No new player workflow. Bracket stays fill-once.
- The `update_players_score()` trigger is unchanged — it just calls `score_match_for_bet`.

## Function pseudocode

```
score_match_for_bet(is_knockout, bet_t1, bet_t2, actual_t1, actual_t2) -> int:
    if actual_t1 is NULL or actual_t2 is NULL:
        return 0

    is_exact = (bet_t1 == actual_t1 and bet_t2 == actual_t2)
    bet_winner    = sign(bet_t1 - bet_t2)       # +1, 0, or -1
    actual_winner = sign(actual_t1 - actual_t2)

    outcome = 3 if bet_winner == actual_winner else 0
    t1      = 2 if bet_t1 == actual_t1 else 0
    t2      = 2 if bet_t2 == actual_t2 else 0
    diff    = 1 if not is_exact and (bet_t1 - bet_t2) == (actual_t1 - actual_t2) else 0

    total = outcome + t1 + t2 + diff
    if is_knockout:
        return total * 2
    return total
```

## Backfill

After replacing the function, the migration recomputes `quinielas.points` for all quinielas via a single UPDATE that sums `score_match_for_bet(...)` over each player's bets joined with played matches. The recompute is idempotent — running the migration twice produces the same result.

To avoid bumping `updated_at` on quinielas whose new total equals their old total, the recompute UPDATE is restricted with a `WHERE` clause that requires at least one played-match bet (see plan).

## Tests

`ScoringTriggerIT` test cases (rewritten):

**Existing (re-asserted with new numbers):**
- `exactScoreAwardsSeven`: bet 2-1, actual 2-1 → 7 (was 5)
- `winnerAndGoalDifferenceAwardsFour`: bet 2-1, actual 3-2 → 4 (was 3)
- `winnerOnlyNoTeamScoreAwardsThree`: bet 2-1, actual 5-0 → 3 (was 2)
- `correctDrawWithDifferentExactAwardsFour`: bet 1-1, actual 0-0 → 4 (was 2)
- `wrongWinnerNoPartialCreditAwardsZero`: bet 2-1, actual 3-4 → 0 (unchanged)

**New paths exercised by the additive shape:**
- `winnerWithOneTeamExactAwardsFive`: bet 2-1, actual 2-0 → 5 (3+2)
- `wrongWinnerWithOneTeamExactAwardsTwo`: bet 2-1, actual 0-1 → 2 (T2 only)
- `predictedDrawActualNotDrawWithOneTeamExactAwardsTwo`: bet 1-1, actual 1-3 → 2 (T1 only)
- `exactScoreSuppressesDiffBonusAwardsSeven`: bet 2-1, actual 2-1 → 7 (NOT 8 — diff is suppressed)

**Knockouts (round_id = 2, match ids 73-74 in test fixtures):**
- `exactScoreKnockoutAwardsFourteen`: bet 2-1, actual 2-1 → 14
- `winnerWithOneTeamExactKnockoutAwardsTen`: bet 2-1, actual 2-0 → 10
- `wrongWinnerWithOneTeamExactKnockoutAwardsFour`: bet 2-1, actual 0-1 → 4

**Trigger semantics (preserved from V005, verified via the AdminResultsControllerIT):**
- Corrections produce the right delta (old contribution subtracted, new contribution added).
- The trigger only fires on UPDATE OF score_t1, score_t2 — no spurious recomputes on other column changes.

## Migration timing & risk

- The migration replaces the function and recomputes the cache. Both run in the migration transaction. Atomic — if either fails, the schema rolls back.
- Production already has the admin entering match results live. The migration must run during a brief deploy window, but since the function replacement is idempotent and the recompute is a single UPDATE, it's safe to run during a routine deploy (Cloud Run rolls the backend while Cloud SQL is up).
- No data loss possible. `quinielas.points` is a cache; bet rows and match rows are unchanged.

## Open questions

None. The user has approved the shape, the values, and the diff-suppression rule.

## Acceptance

- [ ] `V010__additive_scoring.sql` replaces `score_match_for_bet` and recomputes `quinielas.points`.
- [ ] `ScoringTriggerIT` test counts: rewrite the 5 existing cases + add 7 new = **12 total**, all passing.
- [ ] `AdminResultsControllerIT` still passes (the trigger contract is unchanged).
- [ ] Full backend `./mvnw verify` is BUILD SUCCESS.
- [ ] Friend-group WhatsApp message updated with the new shape (separate communication step, not in this spec's CI).

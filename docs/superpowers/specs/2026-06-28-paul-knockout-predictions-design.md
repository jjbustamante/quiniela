# Paul Knockout Predictions (v1.1) — Design

**Date:** 2026-06-28
**Status:** Approved
**Author:** Juan + Claude

## Problem

Paul (the "Pulpo Paul" AI oracle) predicts only **group-stage** matches. The entire
pipeline is hardcoded to the `GROUP` round:

- `PaulPredictionService.generateAllGroup()` — generates CANDIDATE predictions only for
  group matches (looks up the `"GROUP"` round, sends `"GROUP"` in the prompt).
- `PaulEnsembleService.synthesizeAllGroup()` — synthesizes OFFICIAL picks only for group
  matches (same `"GROUP"` lookup).
- `PaulService.fillAllForUser()` — fills only group matches into a user's bracket
  (comment: *"v1.1 fills knockouts after group stage closes"*).
- `PaulService.reveal()` — snapshots OFFICIAL predictions, of which only group officials
  exist, so Paul never bets knockout.

The group stage is over and the Round of 32 (R32) is set, but no code path asks the
models for knockout predictions. This is deferred functionality ("v1.1"), not a
regression — the knockout *storage and scoring* side is already complete.

## What already works (do not rebuild)

- **`bet.predicted_winner_id`** (V016) + **`match.advanced_team_id`** (V017): the scoring
  trigger `update_players_score()` awards the knockout outcome bonus by comparing a bet's
  `predicted_winner_id` against the match's `advanced_team_id`, and applies the ×2 knockout
  multiplier. Group / decisive-knockout bets leave `predicted_winner_id` NULL.
- **`BracketService.saveBet`**: validates and persists `predictedWinnerId`, but only for a
  knockout match whose predicted score is a draw; it clears it otherwise. It enforces the
  deadline lock and the per-match kickoff lock in one place.
- **Progressive bettability** (`BracketService.getMyBracket`): a knockout round is bettable
  once the group stage closed and its matches have teams assigned. Teams fill in as prior
  rounds resolve (R32 now; R16 after R32 plays; etc.).
- **Reveal visibility gate**: `CompareService` exposes another player's pick for a match
  only when `LockClock.isMatchRevealable(now, deadlines, roundCode)` is true (knockout: the
  match is played or the knockout deadline has passed). So Paul's picks are hidden from
  other players until the round locks — no separate reveal-gating code is needed.

## Goals

1. Paul generates CANDIDATE + OFFICIAL predictions for knockout matches that are currently
   resolved (R32 now, then R16/QF/SF/FINAL as each round resolves).
2. Paul can predict a regulation **draw** and name the **advancing** team (scored via the
   existing `predicted_winner_id` path).
3. Both consumers work for knockout: per-match "ask Paul" suggestions **and** "Paul fill
   all" for the currently-open round.
4. Paul's own bracket (`reveal()`) includes his knockout picks, hidden from other players
   until each round locks (via the existing read-layer gate).
5. Triggering stays a **stage-aware manual admin job** — the existing `generate` /
   `synthesize` jobs predict whatever is currently open. No auto-trigger, no new endpoints.

## Non-goals (YAGNI)

- No auto-trigger from the post-match tail-refresh pipeline.
- No new admin endpoints or buttons (reuse `generate` / `synthesize` / `reveal`).
- No new knockout-deadline column; reuse the existing single `tournament.knockout_deadline`
  + per-match kickoff lock.
- No whole-bracket placeholder reasoning ("Group A winner vs B runner-up"). Paul predicts
  only currently-resolved matchups, one round at a time.

## Approach: generalize generation to "open matches" (round-agnostic)

Replace the `GROUP`-hardcoded match selection with a single rule used everywhere:

> **Open match** = a match whose **both** `team1_id` and `team2_id` are assigned **and**
> whose kickoff is still in the future (`now < kickoff_at`).

This rule naturally selects only the current open round: group matches are in the past
(filtered out); R16+ matches have NULL teams (filtered out) until they resolve. One code
path serves the group stage (historically) and each knockout round progressively.

## Design by component

### 1. Data model — migration `V023` + entity

- **`V023__paul_prediction_predicted_winner.sql`**:
  `ALTER TABLE paul_prediction ADD COLUMN predicted_winner_id BIGINT REFERENCES team(id);`
  Nullable. NULL for group predictions and for decisive knockout scores; set only when Paul
  predicts a regulation draw. (Next free version: V020 is the last applied main migration;
  V021 is test-only; V022 exists — **the next number is V023.**)
- **`PaulPrediction` entity**: add `predictedWinnerId` field + getter; append one trailing
  constructor param. Mirrors `bet.predicted_winner_id`.

### 2. LLM result type & oracles

- **`PaulPredictionResult`** gains `String advancing` — enum-valued: `"LOCAL"` |
  `"VISITANTE"` | `null`. A side indicator (not a team code/id) so the model can neither
  hallucinate a non-participating team nor guess DB ids. Mapping: `LOCAL → team1Id`,
  `VISITANTE → team2Id`.
- **Each oracle** (`VertexPaulOracle`, `GeminiPaulOracle`, `OpenAiCompatVertexOracle`) adds
  the optional `advancing` enum to its structured-output schema and parses it. This is the
  one unavoidably repetitive change (one field per oracle).
- **`FakePaulOracleConfig`** (test double) returns a configurable `advancing`.

### 3. Prompt — `MatchContextBuilder`

`userPrompt` becomes round-aware (it already takes `stageCode`; today only `"GROUP"` is
passed):
- **Group:** unchanged.
- **Knockout:** pass the round's display name as the stage, omit the group code, and append:
  *"Es eliminación directa: no hay empates en el global. Si predices empate en tiempo
  reglamentario, indica en `advancing` qué equipo avanza (LOCAL o VISITANTE) por penales."*
- System prompt unchanged.

### 4. Generation — `PaulPredictionService` + `PaulEnsembleService`

- **Shared open-match selection helper** implementing the rule above, ordered by kickoff.
- **`PaulPredictionService.generateAllGroup` → `generateOpen(progress)`**: iterate open
  matches × roster; build the round-appropriate prompt; persist a candidate. For a
  **predicted draw**, set `predictedWinnerId = map(advancing)`; for a decisive score, leave
  it NULL. If `advancing` is missing/invalid on a predicted draw, fall back to the
  **higher-FIFA-ranked team** (deterministic). The LLM-failure stub path does the same.
- **`PaulEnsembleService.synthesizeAllGroup` → `synthesizeOpen(progress)`**: same selection;
  `candidatePrompt` also lists each candidate's advancing pick; the judge returns an
  official score **and** advancing; the OFFICIAL row stores `predicted_winner_id` (draw-only
  rule). Fallback (judge fails) keeps the first candidate's score **and** its advancing pick.
- **`PaulJobService` / `PaulAdminService`**: the existing `generate` and `synthesize` jobs
  call the `…Open` variants. No new endpoints, no new buttons.

### 5. Consumers — `PaulService`

- **`suggestForMatch`**: `Suggestion` record gains `predictedWinnerId`, read from the chosen
  cached candidate. Already match-agnostic — works for knockout once candidates exist.
- **`fillAllForUser`**: drop the `GROUP` lookup; fill **all open matches the user hasn't bet
  yet** (= the currently-open round) via
  `bracket.saveBet(matchId, scoreT1, scoreT2, predictedWinnerId)`. `bracket.saveBet` already
  enforces the lock and re-applies the draw-only winner rule, so no parallel validation here.
- **`reveal`**: extend the `Bet` it creates to set `predictedWinnerId` from the OFFICIAL
  prediction. Knockout officials are picked up automatically once they exist.

### 6. Reveal / visibility — no new code

`reveal()` stays un-gated; the read-layer gate (`CompareService` + `LockClock.isMatchRevealable`)
already hides Paul's knockout picks from other players until each match is played or the
knockout deadline passes.

**Ops caveat (not code):** verify the production `tournament.knockout_deadline` value. If it
is NULL, knockout picks reveal per-match on `played` (the safe behavior). If it is a single
early timestamp, all knockout picks reveal once it passes — confirm that matches intent.

### 7. Frontend — additive

- `frontend/lib/api/paul.ts`: `Suggestion` type gains `predictedWinnerId`.
- Per-match "ask Paul" apply: when the suggested knockout score is a draw, also set the
  advancing-team selector.
- "Paul fill all" button: unchanged call; re-fetch the bracket afterward (knockout bets now
  return filled). No new components.

## Testing

- `MatchContextBuilderTest`: knockout prompt variant (no group code, advancing instruction).
- `PaulPredictionServiceIT`: knockout candidate generation; draw → `predicted_winner_id`
  set; decisive → NULL; invalid/missing advancing on a draw → higher-ranked fallback.
- `PaulEnsembleServiceIT`: knockout official synthesis aggregates advancing; fallback keeps
  the candidate's advancing.
- `PaulServiceCachedIT` / `PaulRevealIT`: `suggestForMatch` returns `predictedWinnerId`;
  `fillAllForUser` fills the open knockout round with `predicted_winner_id` on draws and
  respects locks; `reveal` snapshots knockout officials with `predicted_winner_id`.
- `PaulPredictionRepositoryIT` / `PaulSchemaIT`: the new column round-trips.
- Test fixtures already reanchor fixture dates (`V021`), so "open match" date filtering is
  testable.

## Risks & edge cases

- **Repetitive oracle change**: the `advancing` field must be added to all three oracle
  schemas; missing one silently drops the winner for that model (fallback to higher-ranked
  covers correctness but loses fidelity). Covered by per-oracle handling + the higher-ranked
  fallback.
- **Knockout deadline config** (ops caveat above) governs reveal timing; verify before
  running the first knockout reveal.
- **Partially-resolved round**: if only some matches in a round have teams, only those are
  predicted (the open-match rule is per match, not per round). Acceptable and self-healing
  on the next generate run.

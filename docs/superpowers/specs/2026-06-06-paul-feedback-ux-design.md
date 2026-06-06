# Design — Paul feedback UX

> Backlog item #2: "No UI feedback while Paul runs." When a user asks Paul to
> fill all their picks, or asks Paul on a single match, the UI gives no
> indication of what happened or what Paul decided. The reasoning Paul already
> produces is fetched and silently discarded.

**Date:** 2026-06-06
**Branch:** `fix/paul-feedback`
**Status:** approved, ready for implementation plan

## Problem

Two user-facing Paul surfaces give no feedback:

- **Single match** — `acceptPaulSuggestionAction` (group + knockout `actions.ts`)
  calls `suggestForMatch`, gets `{ scoreT1, scoreT2, reasoning }`, immediately
  saves the score and revalidates. The reasoning is **thrown away**; the tap
  silently fills a score.
- **Fill all** — `paulFillAllAction` calls `/api/paul/fill`, gets `{ created }`.
  `PaulFillAllButton` captures `created` but only ever renders the locked-round
  notice — the count is never shown.

## Key finding: this is frontend-only

The backend already returns everything we need. No backend, DB, or new-endpoint
changes:

- `POST /api/paul/suggest` → `Suggestion(scoreT1, scoreT2, reasoning)`.
  `reasoning` is the stored Paul prediction's reasoning (`pick.getReasoning()`),
  so there is **no live LLM call on tap**; falls back to a stub string when no
  prediction exists for the match.
- `POST /api/paul/fill` → `FillResult(created)`. `fillAllForUser` **skips
  matches the user already bet** — it never overwrites — so `created` counts
  only newly filled blanks. `created == 0` therefore means "everything was
  already filled".

Consequence for the "kept the same result" funny message:

- **Single match overwrites**, so "Paul kept your pick" = Paul's suggested score
  equals the score the user already had on that row.
- **Fill-all never overwrites**, so its funny case is `created == 0`.

## Behavior

### Single match ("Ask Paul" on a row)

- Per-row spinner while the action is pending.
- Auto-saves Paul's pick (unchanged from today).
- `acceptPaulSuggestionAction` returns a structured result instead of `void`:
  `{ scoreT1, scoreT2, reasoning, locked?, error? }`.
- Client compares Paul's score to the row's prior score:
  - **Different** → inline panel under the row: "🐙 Paul dice: 2-1" + reasoning.
  - **Same** (Paul agreed with the existing pick) → inline panel with a funny
    "de acuerdo contigo" header + reasoning.
- Panel is dismissible. A shared `PaulReasoningPanel` presentational component
  is used by both the group row and the knockout row.

### Fill all

- Spinner on the button (already present via `useTransition`).
- Inline summary message below the button (the slot already exists):
  - `created > 0` → "🐙 Paul rellenó N pronósticos. ¡Suerte!"
  - `created == 0` → funny "ya tenías todo listo" line.
  - locked → existing `fillLocked` message.

### Funny messages

A small rotating set (2–3 Spanish lines per case), picked at random
(`Math.random` is fine in app code). `en.json` kept in parallel. UI copy stays
Spanish; English is the secondary locale.

## Components and data flow

- **`lib/paul-feedback.ts` (new, pure, unit-tested)** — the testable core. Given
  the suggestion + prior score (single match) or `created` (fill-all), returns a
  feedback descriptor:
  `{ kind: "changed" | "kept" | "filled" | "nothing" | "locked" | "error",
     score?, reasoning?, count?, messageKey }`.
  Message-key selection and the changed/kept decision live here, not in JSX.
- **`acceptPaulSuggestionAction`** (group + knockout `actions.ts`) — return the
  suggestion result (scores + reasoning + locked/error flags) instead of `void`;
  still saves and revalidates. Locked race keeps using `ignoreLockedRace`, but
  reports `locked: true` back instead of swallowing silently.
- **Drill-in clients** (`GroupDrillIn`, `KnockoutDrillIn`) — own a
  `Map<matchId, feedback>` of results plus per-row pending state; call the
  action, store the result, pass it down to `MatchRow`.
- **`MatchRow`** (+ the knockout row equivalent) — render the optional
  `PaulReasoningPanel`, a per-row spinner, and a dismiss control.
- **`PaulFillAllButton`** — render the count / zero-case message (today it only
  renders the locked case).
- **i18n** — new keys in `messages/es-CO.json` + `messages/en.json`.

## Error handling

- **Locked (423)** — single match shows an inline "ronda cerrada" note; fill-all
  shows the existing `fillLocked`.
- **Suggest/save failure** — the action returns `{ error: true }`; the client
  shows an inline "Paul no pudo decidir, intenta de nuevo." No more silent
  swallow.
- **Empty/stub reasoning** — render the pick without a reasoning paragraph, so
  the panel never shows an empty body.

## Testing

- **Unit (vitest)** — `paul-feedback.ts`: changed vs kept vs filled vs nothing
  vs locked vs error; assert the chosen `kind` and that the message key belongs
  to the expected set (random picker asserted by set membership).
- **Component (RTL)** — `MatchRow` shows the reasoning panel when feedback is
  present and a spinner when pending; `PaulFillAllButton` renders the count
  message and the zero-case funny message.
- Follows the existing `RankingRow.test.tsx` conventions.

## Out of scope

- Per-match reasoning breakdown for fill-all (would need `/api/paul/fill` to
  return per-match details). Fill-all stays a summary count.
- Any change to the admin batch-generation job (`PaulJobService` / progress) —
  that is a separate surface from the user-facing fill/suggest flow.
- Changes to how/when Paul predictions are generated or their reasoning content.

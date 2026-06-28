# Multi-Oracle AI Avatars (Otto & Chitara) — Design

**Date:** 2026-06-28
**Status:** Approved
**Author:** Juan + Claude

## Problem / story

Two registered players never submitted any predictions and are dead weight in the
pool: **Miguel Angel Tona Gené** (user 13, captain) and **Daniel** (user 24, player) —
both have a quiniela but **0 bets / 0 points**, and the group stage is locked so they
can't catch up.

Paul (the AI predictor bot) has been a hit. The plan: Paul "calls his friends" to take
those two spots — two **new AI avatars** that behave like Paul but think with *different*
LLM models so their picks genuinely differ:

- **Otto la Nutria 🦦** — backed by **DeepSeek** (`deepseek-ai/deepseek-v3.1`)
- **Chitara la Leoparda 🐆** — backed by **Llama** (`meta/llama-4-maverick`)

They compete **for fun only — never for money** (`is_bot=true`, like Paul). They play the
**knockout stage only** (group is over; they start fresh at the knockout).

## Current architecture (single-bot) — what we're generalizing

- One bot user, `paul-bot-oracle`, hardcoded in `PaulService.reveal()` and seeded in `V015`.
- `paul_prediction` is keyed `UNIQUE(match_id, model, kind)` — **no notion of which bot owns a prediction**. The OFFICIAL pick is `model='ensemble'`, one per match.
- `generateOpen` (candidates per roster model) → `synthesizeOpen` (judge → one `ensemble` OFFICIAL) → `reveal` (snapshot OFFICIALs into Paul's quiniela).
- The roster is a single global list (`app.paul.models`, 5 models in prod).
- The leaderboard (`CompareService`/`RankingView`) and prize pot (`AdminPaymentService`) already treat **any `is_bot` user** correctly: bots rank "por la gloria" but are excluded from money.

## Goals

1. Two new AI competitors, Otto & Chitara, each with a **single distinct model**, playing the **knockout stage**.
2. Generalize the single-bot pipeline to a **config-driven oracle registry** — Paul becomes one oracle among several; **Paul's data and behavior are unchanged**.
3. Both new avatars are **not prize-eligible** (`is_bot=true`).
4. **Delete** Miguel & Daniel so the avatars literally take their two spots.
5. Replace the leaderboard banner copy with Paul's announcement of the two friends.

## Non-goals (YAGNI)

- No per-avatar ensemble/judge (single model each).
- No group-stage backfill / re-scoring (knockout only).
- No new LLM vendor integration (DeepSeek/Llama route through the existing `OpenAiCompatVertexOracle`).
- No new admin endpoints/buttons (the existing jobs loop all oracles).
- Banner **dismissibility is deferred** — copy swap only for now.
- No per-oracle admin UI selection (one click runs all oracles).

## Approach A: oracle registry + `oracle` column

### 1. Data model

- **`V024__paul_prediction_oracle.sql`**:
  ```sql
  ALTER TABLE paul_prediction ADD COLUMN oracle VARCHAR(32) NOT NULL DEFAULT 'paul';
  ALTER TABLE paul_prediction DROP CONSTRAINT paul_prediction_match_id_model_kind_key,
      ADD CONSTRAINT paul_prediction_oracle_match_model_kind_key
      UNIQUE (oracle, match_id, model, kind);
  ```
  Existing rows backfill to `'paul'` via the column default — **all of Paul's current group
  officials/candidates and the R32 candidates already generated become `oracle='paul'`**.
- **`V025__seed_avatar_bots.sql`**: insert two bot users (same shape as Paul's V015 seed):
  - `otto-bot-oracle` / "Otto la Nutria 🦦" / `otto@laquinieladelospanas.com`
  - `chitara-bot-oracle` / "Chitara la Leoparda 🐆" / `chitara@laquinieladelospanas.com`
  - both `role='player'`, `is_bot=TRUE`, `ON CONFLICT (google_sub) DO NOTHING`.
- **`PaulPrediction` entity**: add `oracle` field (+ trailing constructor arg + getter).

### 2. Oracle registry (config-driven)

A list of oracle definitions; each: `{ key, googleSub, displayName, models[], ensembleModel? }`.
- `paul`: googleSub `paul-bot-oracle`, the 5 current models, `ensembleModel` = today's value.
  Built from the existing `app.paul` settings so current env vars keep working — **Paul unchanged**.
- `otto`: googleSub `otto-bot-oracle`, models `["deepseek:deepseek-ai/deepseek-v3.1"]`, `ensembleModel = null`.
- `chitara`: googleSub `chitara-bot-oracle`, models `["meta:meta/llama-4-maverick…"]`, `ensembleModel = null`.

Final model id strings are **verified by a one-shot test call** before relying on them; if
DeepSeek or Llama isn't enabled in the Vertex project, fall back to Mistral (`mistralai/…`).
`projectId`/`location`/provider routing stay shared infra (unchanged).

### 3. Pipeline generalization (Paul behavior preserved)

`generateOpen` / `synthesizeOpen` / `reveal` become **oracle-parameterized**:

- **generate(oracle)** — open matches × `oracle.models` → candidates tagged with `oracle`,
  routed by the existing `RoutingPaulOracle` (DeepSeek/Llama → `OpenAiCompatVertexOracle`).
- **synthesize(oracle)** —
  - ensemble oracle (Paul, `ensembleModel != null`): judge pass exactly as today.
  - single-model oracle (`ensembleModel == null`): **promote its lone candidate to the
    OFFICIAL** (write `kind=OFFICIAL`, `model='ensemble'`, copying score + `predicted_winner_id`).
    No LLM judge call.
- **reveal(oracle)** — snapshot that oracle's `OFFICIAL` rows → that oracle's quiniela
  (looked up by `oracle.googleSub`), setting `predicted_winner_id` (knockout draws).
- `PaulPredictionRepository` queries gain an `oracle` filter
  (`findByOracleAndKind`, `findByOracleAndMatchIdAndKind`, `findByOracleAndMatchIdAndModelAndKind`).

Idempotency is per `(oracle, match, model, kind)`, so re-running any job is safe and never
disturbs another oracle's picks.

### 4. Admin jobs / UI

`PaulJobService.startGenerate` / `startSynthesize` and `PaulAdminService.reveal` **loop over
all enabled oracles**. The admin panel (already built) stays three buttons — one click runs
Paul + Otto + Chitara. Job progress `total` sums across oracles.

### 5. Delete Miguel & Daniel (reviewed one-off SQL)

Run via the Cloud SQL proxy against prod (not a Flyway migration — deleting specific real
people must not live in version-controlled migrations applied to every environment). Blast
radius verified clean: no invitees reference them, 0 payments, 0 bets, captain is a role not
an FK. Script:
```sql
BEGIN;
DELETE FROM bet WHERE quiniela_id IN (SELECT id FROM quiniela WHERE user_id IN (13,24));
DELETE FROM quiniela        WHERE user_id IN (13,24);
DELETE FROM pool_membership WHERE user_id IN (13,24);
DELETE FROM users           WHERE id      IN (13,24);
-- verify counts, then COMMIT;  (run as BEGIN … ROLLBACK first to dry-run)
COMMIT;
```

### 6. Frontend banner

Replace the hardcoded string in `frontend/app/ranking/page.tsx` with an i18n key
(`ranking.botsAnnouncement`) in `es-CO.json` + `en.json`. Trigger stays `entries.some(isBot)`.

Spanish copy:
> 🐙 Paul notó que dos competidores se quedaron en blanco y llamó a sus amigos **Otto la Nutria 🦦** y **Chitara la Leoparda 🐆** para tomar sus dos puestos.

English copy:
> 🐙 Paul noticed two players never showed up, so he called his friends **Otto the Otter 🦦** and **Chitara the Leopard 🐆** to take their two spots.

Dismissibility is a later iteration.

## What happens to Paul's existing estimation

Nothing is lost. `V024`'s `DEFAULT 'paul'` backfills every existing `paul_prediction` row to
`oracle='paul'`: his 72 group OFFICIALs, all group candidates, and the R32 candidates already
generated. His reveal/bets/points are untouched; the generalized `reveal('paul')` and
`generate('paul')`/`synthesize('paul')` reproduce today's behavior exactly. The first post-deploy
run will (idempotently) re-tag/refresh Paul's R32 candidates and add Otto's & Chitara's.

## Money / fairness

Otto & Chitara are `is_bot=true`, so `AdminPaymentService`'s pot query
(`WHERE u.role <> 'admin' AND u.is_bot = false`) excludes them automatically — they show on
the leaderboard with the "fuera de premio" treatment, like Paul. No prize-split changes needed.

## Testing

- `PaulPredictionRepositoryIT`: `oracle` column round-trips; oracle-filtered queries return only that oracle's rows.
- `PaulPredictionServiceIT`: `generate(otto)` writes candidates tagged `oracle='otto'` for the single model; a second oracle's run doesn't touch Paul's rows.
- `PaulEnsembleServiceIT`: `synthesize` for a single-model oracle promotes the lone candidate to OFFICIAL (no judge) with score + winner; ensemble oracle path unchanged.
- `PaulServiceIT` / reveal: `reveal(otto)` snapshots only Otto's officials into Otto's quiniela.
- Job loop: starting a job runs every enabled oracle (assert candidates exist for each).
- Frontend: banner renders the new copy when a bot is present.
- The Miguel/Daniel deletion is the reviewed SQL (manual; dry-run with ROLLBACK first).

## Rollout order

1. Ship code (generalization + `V024`/`V025` + banner) → deploy.
2. Verify DeepSeek & Llama are enabled in Vertex Model Garden (one-shot test call); swap to Mistral fallback if not.
3. Run the delete script (dry-run with ROLLBACK, then COMMIT) — Miguel & Daniel gone.
4. Run **Generate → Synthesize → Reveal** (now covers all oracles) for **R32 before its matches kick off** (knockout deadline 2026-06-28 19:00 UTC; reveal bypasses the lock but do it before kickoffs for fairness).

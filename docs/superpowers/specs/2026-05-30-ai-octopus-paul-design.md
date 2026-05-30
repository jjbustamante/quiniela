# AI-Powered Octopus Paul — Design

**Date:** 2026-05-30
**Status:** Approved (brainstorming) — ready for implementation plan
**Scope:** v1 = group-stage predictions. Knockouts are an explicit phase 2 on the same engine.

## Problem

"Octopus Paul" is the quiniela's automated predictor. Today he is dumb: `PaulService.suggestForMatch` returns a deterministic seed-based scoreline (`scoreT1 = (matchId*17+11) % 4`, etc.) — the same prediction for every match, with no reasoning. We want Paul to make genuinely good predictions using an LLM (Google Gemini via Spring AI), and to play a fun narrative role in the pool.

## Goals

- Replace the seed formula with real AI predictions driven by injected match context.
- Give Paul a two-phase narrative: a **helper** before group lock, then a **surprise competitor** afterward.
- Experiment with Spring AI and multiple models, cheaply (Google AI Studio free tier; GCP $300 credits reserved for hosting and later Vertex/Claude experiments).
- Keep the design extensible: more models/providers, knockout predictions, and multi-language reasoning are additive, not rewrites.

## Non-goals (v1, YAGNI)

- Knockout-stage predictions (phase 2 — same engine, built during the group stage once bracket teams resolve).
- Multi-vendor providers wired in production (architected for it; not shipped in v1).
- Re-filling or retroactively changing bets a user already placed.
- Search-grounding / live news at prediction time.
- An English (or any non-Spanish) UI. v1 generates Spanish reasoning; the schema is language-tagged so translation can be added later without a migration headache.
- **Pot-payout math** (aggregating entry fees and applying the 80/15/5 `prize_split`). This does not exist today and is not built here. v1 only establishes *eligibility* — a prize-eligible ranking view — so whenever payout is built, Paul and the admin are already correctly excluded.

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Paul's role | Dual-phase: **helper** before group lock → **surprise competitor** at lock |
| Prediction inputs | LLM + injected stats (team identity, group, FIFA ranking, standings/results when mid-tournament) |
| Provider | Google AI Studio Gemini (API key, free tier). Provider-agnostic via Spring AI |
| v1 scope | Group stage only, **plus** a team-strength column |
| Engine architecture | Approach A: canonical cached predictions + copy-on-fill |
| Models | Configurable roster; v1 = best available Gemini variants. Claude/GPT addable later |
| `suggest`/`fill` behavior | Return a **random** cached candidate prediction (gives Paul personality; zero per-request LLM cost) |
| Paul's official bet | **LLM ensemble judge**: a final call synthesizes the candidate predictions into Paul's official pick |
| Reasoning language | Stored Spanish + tagged `reasoning_lang`; translation is a future lazy-cached LLM step |
| Pot eligibility model | Identity-based: leaderboard excludes `ADMIN` role; prize ranking also excludes `is_bot`. No per-bracket flag |
| Admin on leaderboard | Hidden entirely — admin is management-only; Juan plays via his Captain account |
| Paul on leaderboard | Shown as an **exhibition** competitor (badge), never prize-eligible |

## Lifecycle

1. **Helper phase** (before group-stage lock, 2026-06-11):
   - Paul holds cached opinions per match in `paul_prediction`.
   - `POST /api/paul/suggest` and `POST /api/paul/fill` return/copy a **random candidate** prediction instantly — no LLM call in the request path.
   - Admin can re-generate predictions anytime ("Paul cambió de opinión 🐙"). Because `fill` only fills *empty* bets, re-generation never churns bets users already have.
2. **Reveal** (at group lock):
   - Admin triggers the surprise. Paul **synthesizes** his official bets from his own prior candidate predictions (ensemble judge).
   - A Paul bot quiniela is created and his `OFFICIAL` predictions are snapshotted into his bets.
   - Paul now appears on the leaderboard as a competitor.

## Data model

Two Flyway migrations (plain SQL, under `backend/src/main/resources/db/migration/`).

### `V014__team_strength.sql`
```sql
ALTER TABLE team ADD COLUMN fifa_ranking INT;   -- nullable
-- seed known WC2026 teams by code (UPDATE ... WHERE code = ...)
```
Nullable so playoff-pending slots stay null. The context builder degrades gracefully when ranking is absent.

### `V015__paul_predictions.sql`
```sql
-- Identity-based pot eligibility (see "Pool eligibility" section).
ALTER TABLE users ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE paul_prediction (
    id             BIGSERIAL PRIMARY KEY,
    match_id       BIGINT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    provider       VARCHAR(32)  NOT NULL,                 -- 'google'
    model          VARCHAR(64)  NOT NULL,                 -- 'gemini-2.5-pro'
    kind           VARCHAR(16)  NOT NULL DEFAULT 'CANDIDATE', -- CANDIDATE | OFFICIAL
    score_t1       INT NOT NULL,
    score_t2       INT NOT NULL,
    confidence     NUMERIC(3,2),                          -- 0.00–1.00 self-reported
    reasoning      TEXT,                                  -- generated text
    reasoning_lang VARCHAR(8) NOT NULL DEFAULT 'es',      -- language of `reasoning`
    source         VARCHAR(16) NOT NULL DEFAULT 'AI',     -- AI | FALLBACK
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (score_t1 >= 0 AND score_t2 >= 0),
    UNIQUE (match_id, model, kind)
);
CREATE INDEX idx_paul_prediction_match ON paul_prediction(match_id);

-- Paul bot user. NO quiniela yet — absence of a quiniela means "not competing".
-- is_bot = true keeps him out of the prize ranking even after he reveals.
INSERT INTO users (google_sub, email, display_name, role, is_bot)
VALUES ('paul-bot-oracle', 'paul@laquinieladelospanas.com', 'Pulpo Paul 🐙', 'player', TRUE)
ON CONFLICT (google_sub) DO NOTHING;
```
- Multiple `CANDIDATE` rows per match (one per model). One `OFFICIAL` row per match (the ensemble result, `model = 'ensemble'`).
- The Paul bot user is seeded so it has a stable id, but its quiniela is created only at reveal, so Paul cannot leak onto the leaderboard early. He is role `player` (so he shows on the leaderboard once revealed) but `is_bot = true` (so he is never prize-eligible).

### Future (not in v1): translation cache
When the app gains i18n, add:
```sql
CREATE TABLE paul_prediction_translation (
    prediction_id BIGINT NOT NULL REFERENCES paul_prediction(id) ON DELETE CASCADE,
    lang          VARCHAR(8) NOT NULL,
    reasoning     TEXT NOT NULL,
    translated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (prediction_id, lang)
);
```
Reader path: want `en`? look up cache → miss → LLM-translate from the `reasoning_lang` original → store → return. Additive; no change to v1 tables.

## Pool eligibility (who counts for the pot)

The system currently has **no** prize logic and **no** eligibility concept: `RankingRepository.findRankedByPoolId` returns every quiniela in the pool joined to users, with no filter, and a quiniela is auto-created the moment anyone bets or runs Paul's fill (`BracketService:178`, `PaulService:58`). So today the leaderboard *is* the de-facto prize ranking, and both Paul and the admin would appear in it. `prize_split` (80/15/5) is seeded but read by nobody.

We formalize two distinct concepts, **identity-based** (no per-bracket flag):

| Actor | On competitive leaderboard? | Prize-eligible? |
|---|---|---|
| Players + Juan's **Captain** account | ✅ shown | ✅ eligible |
| **Pulpo Paul** (bot) | ✅ shown (exhibition) | ❌ never |
| **Admin** account (management) | ❌ hidden | ❌ never |

Rules:
- **Leaderboard (display):** `WHERE u.role <> 'ADMIN'`. Real players + captains + Paul show; the admin never does (even if testing auto-creates a quiniela for it).
- **Prize-eligible ranking:** the leaderboard filter **AND** `u.is_bot = false`. Excludes Paul; admin already excluded by role.
- Only **`ADMIN` role** and **bots** are excluded. **Captains are full players** (eligible) — the bootstrap (`AdminBootstrapService`) promotes only the management email to `ADMIN`; Juan's personal account stays `captain` and competes normally.
- Pot-payout math itself stays out of scope (see Non-goals); this just guarantees the eligible-ranking input is correct.

### Ranking changes
- `RankingRepository.findRankedByPoolId` → add `AND u.role <> 'ADMIN'` to the JPQL.
- Add a prize-eligible query/method (e.g. `findPrizeEligibleByPoolId`) that additionally filters `u.isBot = false`. Used by future payout; can also back an admin "premios" preview.
- `RankingRow` gains an `isBot` (or `exhibition`) boolean so the frontend can badge Paul.

## Backend components (`io.quiniela.api.paul`)

- **`PaulModelRoster`** — config-driven list of `(provider, model)` candidates resolved to Spring AI `ChatModel`s. v1 = best available Gemini variants (e.g. `gemini-2.5-pro`, `gemini-2.5-flash`). Adding Claude/GPT later = add a roster entry + starter dependency + key; no service rewrite.
- **`MatchContextBuilder`** — *pure* function `(match, team1, team2, fifaRanking, standings?) → context`. The "injected stats" seam. No LLM; fully unit-tested. Handles null ranking / missing standings.
- **`PaulPredictionService`** — per match, loops the roster, calls Gemini via `ChatClient` with **structured output** (`.entity(PaulPredictionResult.class)`), persists one `CANDIDATE` row per model. **Deterministic fallback** to the legacy seed formula (`source = FALLBACK`) when a model call fails, so generation never hard-fails.
- **`PaulEnsembleService`** — synthesis: loads a match's `CANDIDATE` rows, builds an **ensemble-judge** prompt (here are models A/B/C's scores + reasoning + the match context), one final LLM call → `OFFICIAL` pick + Spanish reasoning, persisted with `kind = OFFICIAL`.
- **`PaulService`** (refactor existing):
  - `suggestForMatch(matchId)` → random `CANDIDATE` (or `OFFICIAL` once revealed).
  - `fillAllForUser(userId)` → copies a random candidate into each *empty* group bet. Preserves current skip-already-bet behavior; no LLM call.
  - `reveal()` → idempotently create Paul's quiniela and snapshot `OFFICIAL` rows into his bets.
- **Controllers**: keep `POST /api/paul/suggest`, `POST /api/paul/fill`. Add admin-guarded `POST /api/admin/paul/generate?force=`, `POST /api/admin/paul/synthesize`, `POST /api/admin/paul/reveal`.

## Spring AI wiring

- Dependency: `spring-ai-starter-model-google-genai` (Google AI Studio / Gemini Developer API via API key) + Spring AI BOM. **All versions externalized to `<properties>`** per project convention.
- Config: `GEMINI_API_KEY` from Secret Manager on Cloud Run; structured output via a Java record schema.
- Stateless one-shot structured-output calls — **no ChatMemory / advisors needed**. The ensemble judge passes candidates inside the prompt, so no conversational state.
- Provider-agnostic: the roster maps a logical model to a `ChatModel`/`ChatClient`; multi-vendor later is config + dependency, not a rewrite.

## Prompt

- **System:** Paul, an octopus oracle. Respond ONLY as the structured JSON schema. Reasoning in **Spanish**, fun and in-character, 1–2 sentences.
- **Context (user):** tournament, stage = GROUP, group letter, both teams (name, code, FIFA ranking when present), and — mid-tournament — results so far / standings. Ask for predicted scoreline + confidence + reasoning.
- **Output record:** `PaulPredictionResult(int scoreT1, int scoreT2, double confidence, String reasoning)`.

## Cost

- Group stage ≈ 72 matches × ~3 models ≈ 216 candidate calls + 72 ensemble calls.
- **Admin-triggered**, throttled to free-tier RPM; never in the user hot path.
- Negligible token cost on Gemini Flash/Pro free tier; the $300 GCP credits remain for Cloud Run / Cloud SQL hosting and later Vertex/Claude experiments.

## Frontend (minimal v1)

- Surface Paul's Spanish reasoning on suggest/fill (the `PaulMascot` component already exists).
- After reveal: Paul appears on the ranking with an octopus avatar + a "¡Pulpo Paul decidió jugar! 🐙" banner. His row carries an **exhibition badge** (e.g. "fuera de premio") driven by the `isBot` field on `RankingRow`, so users see he competes for glory but not money. The admin account never appears (filtered server-side by role).

## Testing (TDD)

- **Unit:** `MatchContextBuilder` (null ranking, missing standings), random-pick logic, fallback path, ensemble prompt builder.
- **Integration:** mock the `ChatModel`/`ChatClient` bean → deterministic responses. Assert `paul_prediction` rows are written; assert `fill` copies candidates and skips already-bet matches; assert `reveal` is idempotent and snapshots only `OFFICIAL` rows. Reuse existing `PaulControllerIT` patterns.
- **Eligibility:** assert the leaderboard hides `ADMIN`-role accounts; assert the prize-eligible query also excludes `is_bot` (Paul); assert a revealed Paul **does** appear on the display leaderboard but **not** in the prize-eligible list; assert a `captain` account is both shown and prize-eligible.

## Open items for the implementation plan

- Confirm the exact `spring-ai-starter-model-google-genai` artifact + Spring AI BOM version compatible with Spring Boot 4 / Java 25.
- Confirm the precise Gemini model identifiers available on the chosen tier; finalize the v1 roster (2–3 models).
- Source FIFA ranking values to seed in `V014` (by team `code`).
- Decide admin auth surface for the new `/api/admin/paul/*` endpoints (reuse existing admin guard).

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A monorepo for a Spanish-language sports betting pool ("quiniela") web app, being rewritten for the **2026 FIFA World Cup** (kickoff 2026-06-11). The original 2014 Rails 3.2 implementation lives under `/legacy/` and is kept as a **reference spec only** — read it for the business rules, do not extend it.

The new stack is **Spring Boot 4 + Java 25 (backend)** and **Next.js 16 + TypeScript (frontend)** as two independent apps in this same repo, deployed to **GCP Cloud Run** via Cloud Native Buildpacks, backed by **Cloud SQL Postgres**. Infrastructure is provisioned by **OpenTofu** in `iac/`. The schema is designed to be **multi-tournament from day one** (UEFA Champions League etc. planned for v2 after the 2026 tournament) — see "Domain model" below.

## Repo layout

```
/
├── backend/           Spring Boot 4 + Java 25 + Maven
├── frontend/          Next.js 16 + TS + Tailwind
├── iac/               OpenTofu — provisions GCP resources for the planned
│                      Cloud Run + Cloud SQL deploy (state in a GCS bucket)
├── legacy/            Rails 3.2 app (2014) — spec only   ← see legacy/CLAUDE.md
├── bin/               deploy-backend.sh, deploy-frontend.sh
│                      (Cloud Run deploy via pack build → Artifact Registry)
├── docs/              Architecture + deploy notes        (added as needed)
└── .github/workflows/ CI for backend + frontend
```

Each subapp will have its own `CLAUDE.md` once scaffolded; this top-level file stays focused on cross-cutting concerns and pointers.

## Deploy target

**Two GCP Cloud Run services**, both built locally with Cloud Native Buildpacks and pushed to Artifact Registry. The whole GCP infra (Cloud SQL, Artifact Registry, Secret Manager, IAM, Cloud Run services) is provisioned by **OpenTofu** in `iac/`.

| Service | Cloud Run | What it runs |
|---|---|---|
| `quiniela-api` | runtime SA: `quiniela-api-runtime` | Spring Boot fat JAR, connects to Cloud SQL via Auth Proxy sidecar |
| `quiniela-web` | runtime SA: `quiniela-web-runtime` | Next.js (`next start`) |

Single shared **Cloud SQL Postgres** instance (`quiniela-db`, db-f1-micro Enterprise edition) for the backend. Secrets (DB password, NextAuth secret, Google OAuth client secret) live in Secret Manager and are mounted into the Cloud Run services as env vars.

Deploy:

```bash
bin/deploy-backend.sh    # pack build → docker push to Artifact Registry → gcloud run deploy
bin/deploy-frontend.sh
```

Local CNB build (same image the deploy script pushes — useful for testing without releasing):

```bash
pack build quiniela-api --builder heroku/builder:26 --path backend/
pack build quiniela-web --builder heroku/builder:26 --path frontend/
```

We still use the `heroku/builder:26` image to *build* — it's a high-quality CNB builder and works fine on Cloud Run (Cloud Run is a generic container runtime, doesn't care which CNB builder produced the image). The Heroku platform itself is no longer in the picture.

### CNB on Cloud Run notes

- **No wrapper Dockerfile needed** (unlike Heroku Cedar container, which had ENTRYPOINT-vs-CMD config-var injection quirks). Cloud Run injects env vars into ENTRYPOINT cleanly, so `CNB_PLATFORM_API=0.15` set on the service via Tofu reaches the launcher.
- **JVM version pinning:** the `heroku/jvm` CNB buildpack reads `backend/system.properties` (`java.runtime.version=25`), NOT `<java.version>` from `pom.xml`. Default falls back to the latest LTS — happens to be 25 today, but pin it explicitly.
- **Cloud SQL connection:** the api service mounts the Cloud SQL Auth Proxy as a sidecar (declared in `iac/cloud_run.tf` via `volumes.cloud_sql_instance`). Spring Boot's `cloudrun` profile (`backend/src/main/resources/application-cloudrun.yml`) reaches it through the `com.google.cloud.sql:postgres-socket-factory` JDBC SocketFactory.

## Auth

**Google OIDC only.** Pattern: Next.js + Auth.js handles Google sign-in → posts Google's signed ID token to Spring `/auth/google` → Spring verifies it against Google's JWKS, upserts a `User` row keyed by `google_sub`, returns its own session/JWT. No password storage, no email verification, no reset flows. Admin escalation via `ADMIN_EMAILS` env var on the backend.

## Domain model (high level)

Seven tables, all carrying `tournament_id` where applicable (multi-tournament-ready):

- **`tournament`** — FIFA 2026, future UCL, etc. Holds dates, deadlines, status.
- **`stage`** — per-tournament; e.g., for 2026: GROUP, R32, R16, QF, SF, THIRD_PLACE, FINAL.
- **`team`** — per-tournament; name, code, group letter (A–L for 2026).
- **`match`** — fixtures with kickoff, venue, both teams, scores, winner, and `parent_match_1_id`/`parent_match_2_id` for knockout bracket structure.
- **`quiniela`** — a user's bracket for one tournament. Caches `points` (kept correct by DB trigger).
- **`bet`** — a prediction per match: `score_t1`, `score_t2`, `winner_id` (the last covers "what if it's a draw in extra time" for knockouts).
- **`user`** — thin: `google_sub`, email, display name, avatar, `is_admin`. Spans tournaments.

This is a deliberate simplification from the 2014 schema — see `legacy/CLAUDE.md` for what was dropped (`winner_name`, `editable`, `bets.team1_id`/`team2_id`, the `rounds` table, half the User columns) and why.

## Scoring lives in the database

The legacy app used a PL/pgSQL `BEFORE UPDATE` trigger on `matches` to recompute `quinielas.points` whenever a match result changed. **We keep this pattern** — authoritative scoring in the DB is robust and avoids race conditions — but **rewrite the rules** for the new 48-team bracket shape (R32 is new; group-stage and knockout rules need to branch on `stage`, not `round_id`).

See `legacy/db/scripts/update_players_score_trigger.sql` for the original — read it as a spec for the *kind* of scoring rules, not as code to port.

## Tournament data

Read-only sync from **football-data.org** (free tier, 10 req/min). Covers World Cup + UCL + top 5 European leagues — enough for 2026 and the planned UCL expansion. Used for: initial fixture seeding (104 matches with dates/venues), live scores during matches, and knockout team assignment after group stage. Admin can manually override anything. **Does not** cover CONMEBOL competitions (Copa Libertadores) — would need API-Football (api-sports.io) or another provider if those become target tournaments.

## Conventions

- **UI copy stays Spanish.** The audience hasn't changed.
- **Spring Boot 4 idioms only.** No `WebSecurityConfigurerAdapter` etc. — that's Spring Boot 2/3 era. Stack Overflow answers older than ~Nov 2025 are likely stale.
- **Flyway, not Liquibase.** Plain SQL migrations under `backend/src/main/resources/db/migration/`.
- **No premature multi-tournament generalization in the scoring logic.** Schema is multi-tournament; the trigger and UI ship World-Cup-only for June 11. Generalize when the second tournament has actual requirements.

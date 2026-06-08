# Quiniela

A Spanish-language sports betting pool ("quiniela") for the **2026 FIFA World Cup**. Friends predict scores for every match, earn points as real results come in, and compete on a leaderboard.

This is a 2026 rewrite of a 2014 Rails app (kept under `legacy/` as a reference spec). The schema is multi-tournament from day one — UEFA Champions League and others are planned after the World Cup.

## Stack

| Part | Tech |
|---|---|
| Backend | Spring Boot 4 · Java 25 · Maven |
| Frontend | Next.js 16 · TypeScript · Tailwind |
| Database | PostgreSQL (Cloud SQL in prod) |
| Auth | Google sign-in (OIDC) only — no passwords |
| Infra | GCP Cloud Run + Cloud SQL, built with Cloud Native Buildpacks, provisioned by OpenTofu |
| Match data | [football-data.org](https://football-data.org) (fixtures + live scores) |

## Repo layout

```
backend/    Spring Boot API
frontend/   Next.js web app
iac/         OpenTofu — provisions all GCP resources
bin/         dev + deploy helper scripts
legacy/      Rails 3.2 app (2014) — reference only, do not extend
docs/        Architecture + deploy notes
```

## Local development

**Prerequisites:** Docker, Java 25, `pnpm` (`corepack enable && corepack prepare pnpm@10.27.0 --activate`).

Start the whole stack — Postgres, backend, and frontend — in one terminal:

```bash
bin/dev.sh
```

- Frontend: http://localhost:3000
- Backend health: http://localhost:8080/actuator/health

`Ctrl+C` stops the apps; Postgres keeps running so data persists. Other commands:

```bash
bin/dev.sh --stop     # halt Postgres
bin/dev.sh --reset    # halt Postgres + wipe its data volume
```

To run only the backend (secrets sourced from `backend/.env`):

```bash
cp backend/.env.example backend/.env   # first time — fill in NEXTAUTH_SECRET
bin/dev-backend.sh
```

## How it works

- **Predictions** — each user fills a bracket (`quiniela`) with a score prediction (`bet`) per match.
- **Scoring** — computed authoritatively in the database via a PostgreSQL trigger that recomputes points whenever a match result changes. Group-stage and knockout rounds use different rules.
- **Tournament data** — fixtures, live scores, and knockout team assignments sync read-only from football-data.org. Admins can manually override anything.

## Deployment

Push to `master` triggers CI (`.github/workflows/`): build → Trivy scan → push image to Artifact Registry → deploy to Cloud Run. PRs build and scan only.

Manual deploy mirrors the same pipeline:

```bash
bin/deploy-backend.sh
bin/deploy-frontend.sh
```

Both read `tofu output` for project/region/service names, so `iac/` must be applied first.

## Conventions

- UI copy stays **Spanish** — the audience hasn't changed.
- **Spring Boot 4 idioms only** (no Boot 2/3-era patterns).
- DB migrations: **Flyway**, plain SQL under `backend/src/main/resources/db/migration/`.

See `CLAUDE.md` for the full architecture and design rationale.

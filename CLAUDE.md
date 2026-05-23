# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A monorepo for a Spanish-language sports betting pool ("quiniela") web app, being rewritten for the **2026 FIFA World Cup** (kickoff 2026-06-11). The original 2014 Rails 3.2 implementation lives under `/legacy/` and is kept as a **reference spec only** — read it for the business rules, do not extend it.

The new stack is **Spring Boot 4 + Java 25 (backend)** and **Next.js 16 + TypeScript (frontend)** as two independent apps in this same repo, deployed to two Heroku apps via Cloud Native Buildpacks. The schema is designed to be **multi-tournament from day one** (UEFA Champions League etc. planned for v2 after the 2026 tournament) — see "Domain model" below.

## Repo layout

```
/
├── backend/           Spring Boot 4 + Java 25 + Maven
├── frontend/          Next.js 16 + TS + Tailwind
├── iac/               OpenTofu — provisions GCP resources for the planned
│                      Cloud Run + Cloud SQL deploy (state in a GCS bucket)
├── legacy/            Rails 3.2 app (2014) — spec only   ← see legacy/CLAUDE.md
├── bin/               deploy-backend.sh, deploy-frontend.sh (current: Heroku)
├── docs/              Architecture + deploy notes        (added as needed)
└── .github/workflows/ CI for backend + frontend
```

Each subapp will have its own `CLAUDE.md` once scaffolded; this top-level file stays focused on cross-cutting concerns and pointers.

## Deploy target

**Two Heroku apps deployed via Cloud Native Buildpacks + Heroku's container registry.** This is the only path that combines a monorepo with CNB — Heroku Fir (the native-CNB platform) has no monorepo support yet, and the classic `heroku-buildpack-monorepo` is not CNB-compatible. So we build OCI images locally with `pack` and push them to Heroku.

| App | CNB buildpacks | What it runs |
|---|---|---|
| `quiniela-panas-api` | `heroku/jvm` + `heroku/maven` + `heroku/procfile` | Spring Boot fat JAR |
| `quiniela-panas-web` | `heroku/nodejs-*` + `heroku/procfile` | Next.js (`next start`) |

Each app is on the **`container` Heroku stack** (`heroku stack:set container`) so it accepts pre-built images instead of running its own buildpack build. A single **Heroku Postgres `essential-0`** add-on (the current entry tier; `mini` was retired) is provisioned on the backend app.

Deploy is wrapped in two scripts at `bin/`:

```bash
bin/deploy-backend.sh    # pack build → docker push → heroku container:release
bin/deploy-frontend.sh
```

Local CNB build (same image you'd push — useful for testing without releasing):

```bash
pack build quiniela-panas-api --builder heroku/builder:26 --path backend/
pack build quiniela-panas-web --builder heroku/builder:26 --path frontend/
```

### Two Heroku gotchas the deploy scripts handle

- **CNB launcher needs `CNB_PLATFORM_API` baked into the image** — Heroku's container runtime injects config vars into `CMD` (shell-wrapped) but NOT into `ENTRYPOINT` (exec'd raw). The launcher is the natural ENTRYPOINT, and it errors with status 11 if `CNB_PLATFORM_API` is unset. The deploy scripts (`bin/deploy-*.sh`) handle this with a tiny wrapper Dockerfile that adds `ENV CNB_PLATFORM_API=0.15` and moves the launcher from ENTRYPOINT to CMD. Side effect: future Heroku config vars (`DATABASE_URL`, `ADMIN_EMAILS`, etc.) now reach the Java/Node process too. Heroku Fir would handle all of this natively, but Fir has no monorepo support — see the section above.
- **`JDBC_DATABASE_URL` is not auto-set on container deploys** — the classic Heroku Java buildpack used to derive it from `DATABASE_URL` via a runtime profile.d script. Container deploys skip buildpack runtime, so only `DATABASE_URL` is set. Our Spring app currently reads `JDBC_DATABASE_URL`; we'll either set it manually or refactor the Spring config to parse `DATABASE_URL` directly. (Not solved yet — next thing on the list.)

**JVM version detection:** the `heroku/jvm` CNB buildpack reads `backend/system.properties` (`java.runtime.version=25`), NOT `<java.version>` from `pom.xml`. Without `system.properties` it defaults to the latest LTS — happens to be 25 today, but pin it explicitly so a future buildpack release can't surprise us.

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

# CLAUDE.md (backend/)

Spring Boot 4 + Java 25 backend for the Quiniela 2026 app. Deployed to Heroku app `quiniela-panas-api` via Cloud Native Buildpacks. See repo root `CLAUDE.md` for the overall architecture and deploy story.

All paths and commands below are relative to this `backend/` directory.

## Prerequisites

- **Java 25** (LTS, GA Sept 2025). With SDKMAN: `sdk install java 25-tem && sdk env`. The `.sdkmanrc` in this directory will auto-switch when you `cd` in if SDKMAN auto-env is on.
- **Maven** — use the bundled wrapper (`./mvnw`); don't rely on a system install
- **Docker** — only for the local Postgres in `docker-compose.yml`
- **pack** (optional) — only if you want to build the CNB image locally

## Commands

```bash
# Local Postgres
docker compose up -d                    # start
docker compose down                     # stop (keeps data)
docker compose down -v                  # stop and wipe data

# Build & test
./mvnw verify                           # compile + run tests
./mvnw spring-boot:run                  # run the API on :8080
./mvnw package -DskipTests              # produce target/quiniela-api.jar

# Smoke-check the running API
curl http://localhost:8080/actuator/health

# CNB build (matches what Heroku does on push)
pack build quiniela-panas-api --builder heroku/builder:26 --path .

# Database connection (local dev defaults)
psql -h localhost -U quiniela -d quiniela    # password: dev
```

## Configuration

`src/main/resources/application.yml` is the single source. Environment-overridable via:

| Env var | Default | Purpose |
|---|---|---|
| `JDBC_DATABASE_URL` | `jdbc:postgresql://localhost:5432/quiniela` | DB URL. **Heroku sets this automatically** when you provision a Postgres add-on. |
| `JDBC_DATABASE_USERNAME` / `_PASSWORD` | `quiniela` / `dev` | Local dev. Heroku encodes these into the URL. |
| `PORT` | `8080` | Heroku sets this. Spring binds to it. |

Note the use of `JDBC_DATABASE_URL` (not `DATABASE_URL`). Heroku Postgres provides both: `DATABASE_URL` is in `postgres://...` form (no JDBC prefix), while the Java buildpack also exposes `JDBC_DATABASE_URL` in the canonical JDBC form. Always use the JDBC variant from Spring.

## Architecture (so far)

PR 2 only scaffolds the skeleton — no domain code yet. Layout the rest will follow:

```
src/main/java/io/quiniela/api/
├── QuinielaApiApplication.java
├── tournament/        Tournament entity, repo, service, controller   (next PR)
├── stage/             Stage entity + queries
├── team/              ...
├── match/             ...
├── quiniela/          ...
├── bet/               ...
├── user/              User entity + Google OIDC verification
├── auth/              Google ID token validation, session/JWT issuance
├── scoring/           Hooks to invoke / verify the PL/pgSQL scoring trigger
└── config/            Spring Security, web MVC, Jackson, etc.
```

Package-by-feature, not package-by-layer (no `controllers/`, `services/`, `repositories/` mega-packages). Keeps each feature self-contained.

## Database

**Flyway** owns the schema. Migrations live in `src/main/resources/db/migration/` and run on app startup. `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never modifies the schema; it just verifies entities match.

Current migrations:
- `V001__init_tournament.sql` — `tournament` + `stage` tables. Both have `tournament_id` foreign keys flowing through the rest of the schema (multi-tournament from day one — see repo root CLAUDE.md).

Scoring trigger (`update_players_score`) will arrive in a later migration once `match`, `bet`, `quiniela` exist. Read `legacy/db/scripts/update_players_score_trigger.sql` as the spec for the *kind* of rules, not as code to port — the bracket shape changes for 2026.

## Tests

- `QuinielaApiApplicationTests` — context-loads smoke test using **H2 in PostgreSQL-compatible mode**, with Flyway disabled. This is fast (no Docker) and only verifies Spring wiring.
- **Integration tests** (Testcontainers against real Postgres) will arrive when there are repositories/services to exercise. Don't run real DB tests against H2 — its PostgreSQL emulation drifts on anything non-trivial (and is useless for the PL/pgSQL trigger).

## Heroku deploy

**Container deploy via Cloud Native Buildpacks.** We build the OCI image locally with `pack` and push it to Heroku's container registry. This is the only path that supports our monorepo while staying on CNB — Heroku Fir has no monorepo support, and the classic `heroku-buildpack-monorepo` doesn't run on CNB stacks.

One-time setup (run by hand, with the `quiniela.panas.svp@gmail.com` Heroku account):

```bash
heroku create quiniela-panas-api --stack container      # container-deploy from the start
heroku addons:create heroku-postgresql:essential-0 --app quiniela-panas-api
# Heroku auto-sets DATABASE_URL / JDBC_DATABASE_URL config vars from the add-on
```

The Heroku platform stack is set to `container` directly — no `heroku-24` / `heroku-26` choice to make, because the OCI image we push carries its own run-image base (Ubuntu 26.04 LTS from `heroku/heroku:26`, baked in by `heroku/builder:26`).

Per-release:

```bash
bin/deploy-backend.sh    # from repo root: pack build → docker push → heroku container:release
```

The script wraps:

1. `pack build quiniela-panas-api --builder heroku/builder:26 --path backend/`
2. `docker tag quiniela-panas-api registry.heroku.com/quiniela-panas-api/web`
3. `heroku container:login`
4. `docker push registry.heroku.com/quiniela-panas-api/web`
5. `heroku container:release web --app quiniela-panas-api`

### How the image picks Java 25

The `heroku/jvm` CNB buildpack reads `system.properties` (key `java.runtime.version`), **NOT** `pom.xml`. We have `backend/system.properties` containing `java.runtime.version=25`. Without it the buildpack falls back to the latest LTS default (currently 25, but pinning protects us from surprises). Supported majors: 8, 11, 17, 21, 25.

Confirmed empirically — buildpack log shows: *"Using version string provided in `system.properties`. Selected major version `25` resolves to `25.0.3`."*

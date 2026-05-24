# CLAUDE.md (backend/)

Spring Boot 4 + Java 25 backend for the Quiniela 2026 app. Deployed to GCP Cloud Run service `quiniela-api` via Cloud Native Buildpacks (built locally with `pack`, pushed to Artifact Registry). See repo root `CLAUDE.md` for the overall architecture and deploy story.

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

# CNB build (same image bin/deploy-backend-gcp.sh pushes to Artifact Registry)
pack build quiniela-api --builder heroku/builder:26 --path .

# Database connection (local dev defaults)
psql -h localhost -U quiniela -d quiniela    # password: dev
```

## Configuration

`src/main/resources/application.yml` is the default — used for local dev. When `SPRING_PROFILES_ACTIVE=cloudrun` is set (Cloud Run does this via the IaC-managed env), `application-cloudrun.yml` overlays it — switching the datasource to the Cloud SQL Auth Proxy via the `com.google.cloud.sql:postgres-socket-factory` JDBC SocketFactory. No code change in the rest of the app.

Environment-overridable values:

| Env var | Default | Source |
|---|---|---|
| `JDBC_DATABASE_URL` | `jdbc:postgresql://localhost:5432/quiniela` | Local dev override. Not used in Cloud Run (the `cloudrun` profile takes over completely). |
| `JDBC_DATABASE_USERNAME` / `_PASSWORD` | `quiniela` / `dev` | Local dev override. |
| `PORT` | `8080` | Cloud Run sets this. Spring binds to it. |
| `DB_NAME` / `DB_USER` / `CLOUDSQL_CONNECTION_NAME` | — | Set by Cloud Run (from IaC). Used only by `application-cloudrun.yml`. |
| `DATABASE_PASSWORD` / `NEXTAUTH_SECRET` | — | Mounted from Secret Manager onto the Cloud Run service. |

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

## GCP deploy

**Cloud Run service `quiniela-api`**, built locally with CNB and pushed to Artifact Registry. All infra (Cloud Run service definition, Cloud SQL instance, Secret Manager bindings, runtime service account) is provisioned by OpenTofu in `iac/` — see `iac/README.md`.

Per-release:

```bash
bin/deploy-backend-gcp.sh   # from repo root: pack build → push to AR → gcloud run deploy
```

The script reads `tofu output` for project ID, region, registry URL, and service name — single source of truth. Image tags include the git SHA (+ `-dirty` if working tree is unclean) so every release is traceable and rollback-friendly.

### How the image picks Java 25

The `heroku/jvm` CNB buildpack reads `system.properties` (key `java.runtime.version`), **NOT** `pom.xml`. We have `backend/system.properties` containing `java.runtime.version=25`. Without it the buildpack falls back to the latest LTS default (currently 25, but pinning protects us from surprises). Supported majors: 8, 11, 17, 21, 25.

Confirmed empirically — buildpack log shows: *"Using version string provided in `system.properties`. Selected major version `25` resolves to `25.0.3`."*

### How Spring connects to Cloud SQL

`application-cloudrun.yml` (active when `SPRING_PROFILES_ACTIVE=cloudrun`, which the IaC sets on the Cloud Run service) overlays the default datasource with:
- `url: jdbc:postgresql:///${DB_NAME}` (no host — uses Unix socket)
- Hikari data-source-properties pointing at `com.google.cloud.sql.postgres.SocketFactory`
- `cloudSqlInstance: ${CLOUDSQL_CONNECTION_NAME}` (resolves to the project:region:instance string)

The Cloud SQL Auth Proxy runs as a Cloud Run sidecar (declared by `volumes.cloud_sql_instance` in `iac/cloud_run.tf`), creating the socket at `/cloudsql/<connection-name>`. The runtime SA `quiniela-api-runtime` has `roles/cloudsql.client` for IAM-authenticated connections. The Postgres user/password (mounted from Secret Manager) is used for in-DB authorization.

# Contributing

Thanks for helping out! This is a small World Cup quiniela app — contributions of any size are welcome. Start with [`README.md`](README.md) to get the stack running locally, then read this for the workflow.

## Getting set up

```bash
bin/dev.sh
```

That starts Postgres, the backend, and the frontend together. Prerequisites (Docker, Java 25, `pnpm`) are listed in the README.

## Workflow

We use a simple branch-and-PR flow off `master`:

1. **Branch** off `master` — name it `feat/...`, `fix/...`, or `docs/...`.
2. **Make your change**, with tests where it makes sense.
3. **Run the checks locally** (see below) before pushing — CI runs the same ones.
4. **Open a PR** against `master`. PRs build and scan but don't deploy.
5. Once merged to `master`, CI deploys automatically to Cloud Run.

Keep PRs focused and reasonably small — they're easier to review and revert.

## Running the checks

Run these before you push; they mirror what CI enforces.

**Backend** (from `backend/`):

```bash
./mvnw spotless:check          # formatting — run ./mvnw spotless:apply to fix
./mvnw test                    # unit tests
./mvnw verify -DskipUnitTests=true   # integration tests
```

**Frontend** (from `frontend/`):

```bash
pnpm lint
pnpm typecheck
pnpm test                      # Vitest
pnpm e2e                       # Playwright (optional locally)
```

CI also runs OWASP Dependency-Check and Trivy image scans. Dependency bumps come in via Dependabot and auto-merge when green.

## Conventions

- **UI copy stays Spanish** — the audience is Spanish-speaking.
- **Backend:** Spring Boot 4 idioms only (no Boot 2/3-era patterns). Code is formatted by Spotless.
- **Database:** schema changes go in as **Flyway** migrations — plain SQL under `backend/src/main/resources/db/migration/`. Never edit an applied migration; add a new one.
- **Scoring** lives in the database (a Postgres trigger), not in app code — see `CLAUDE.md`.
- **Don't touch `legacy/`** — it's the 2014 Rails app, kept only as a reference spec.
- **Commits:** short, imperative subject lines; prefix with `feat:`, `fix:`, `docs:`, etc. when it helps.

## Commit messages & PRs

- Explain the *why*, not just the *what*.
- Reference related issues where relevant.
- Make sure the local checks above pass.

## Questions

Open an issue or start a discussion on the PR. For the bigger picture of how things fit together, `CLAUDE.md` is the architecture source of truth.

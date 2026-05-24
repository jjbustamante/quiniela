@AGENTS.md

# CLAUDE.md (frontend/)

Next.js 16 + TypeScript + Tailwind 4 + App Router frontend for the Quiniela 2026 app. Deployed to GCP Cloud Run service `quiniela-web` via Cloud Native Buildpacks (built locally with `pack`, pushed to Artifact Registry). See repo root `CLAUDE.md` for the overall architecture.

> The `@AGENTS.md` import above pulls in the Next.js team's per-version agent guidance, which warns that **APIs and conventions in Next.js 16 differ from older training data**. Before writing anything non-trivial, check `node_modules/next/dist/docs/` for the relevant guide.

All paths and commands below are relative to this `frontend/` directory.

## Prerequisites

- **Node.js 24** (active LTS). Use `nvm use` (it reads `.nvmrc`).
- **pnpm 9+** via Corepack: `corepack enable && corepack prepare pnpm@9.15.0 --activate`. The `packageManager` field in `package.json` pins the exact version Corepack will fetch.
- **pack** (optional) — for local CNB build testing.

## Commands

```bash
# Install
pnpm install

# Dev (Turbopack on by default in Next 16)
pnpm dev                                 # http://localhost:3000

# Build & serve production
pnpm build
pnpm start                               # honors $PORT env var (default 3000)

# Lint + typecheck
pnpm lint
pnpm typecheck

# Local CNB build (same image bin/deploy-frontend.sh pushes to Artifact Registry)
pack build quiniela-web --builder heroku/builder:26 --path .
```

## Configuration

`.env.local` (gitignored) holds local secrets. See `.env.local.example` for the template.

| Env var | Default | Where set | Purpose |
|---|---|---|---|
| `API_URL` | `http://localhost:8080` | `.env.local` (local) / Cloud Run env (prod, set by IaC) | Backend base URL for server-side fetches |
| `PORT` | `3000` | Cloud Run sets it | Next.js auto-reads via `next start` |
| `NEXT_PUBLIC_*` | — | — | Anything exposed to the browser must be prefixed `NEXT_PUBLIC_` |

`API_URL` is **server-only** — only Server Components / Route Handlers can read it. The browser must not know about the backend URL except via Next.js API routes we proxy. If we ever need direct browser→backend calls, add `NEXT_PUBLIC_API_URL` (different variable).

## Architecture (so far)

PR 3 only scaffolds the skeleton — a single landing page that fetches the backend's `/actuator/health` as a Server Component. Layout to come:

```
app/
├── layout.tsx                  Root layout (lang="es", Geist fonts, Tailwind globals)
├── page.tsx                    Landing — health check (current)
├── globals.css                 Tailwind 4 entry
├── (auth)/                     Sign-in routes (NextAuth.js, PR 4)
├── quiniela/                   My-quiniela views
├── partidos/                   Matches list + results
├── ranking/                    Leaderboard
└── api/                        Server-only routes (auth callback, BFF proxies)
components/
├── ui/                         shadcn primitives (PR 4+)
└── ...                         Feature components
lib/
├── api.ts                      Typed client for the Spring backend
└── auth.ts                     NextAuth.js configuration
```

**Server Components by default.** Use `"use client"` only where you need state, event handlers, or browser-only APIs (forms, charts, draggable brackets). The health check on the landing page is a Server Component — note there's no `"use client"` and no `useEffect`; the `fetch` runs on the server during the request.

## UI conventions

- **Spanish copy** (audience hasn't changed from the legacy app)
- **Tailwind 4** with the new CSS-first config — see `app/globals.css` for the `@theme` declarations rather than a `tailwind.config.ts` file
- **Geist** sans + mono (default fonts from `create-next-app`, kept)
- **shadcn/ui** will be added in PR 4 for form/dialog/button primitives — don't reach for a heavier component library

## GCP deploy

**Cloud Run service `quiniela-web`**, built locally with CNB and pushed to Artifact Registry. The service definition (env vars, runtime SA, secret mounts, scaling) lives in `iac/cloud_run.tf`. See `iac/README.md` for setup.

Per-release:

```bash
bin/deploy-frontend.sh   # from repo root: pack build → push to AR → gcloud run deploy
```

The script reads `tofu output` for project ID, region, registry URL, and service name. Image tags include the git SHA (+ `-dirty` if unclean working tree) so every release is traceable.

### How the image works

The `heroku/nodejs-*` CNB buildpacks (same builder image we use even though we no longer deploy to Heroku) detect a Next.js app by the presence of `package.json` + a lockfile (`pnpm-lock.yaml`). They:

1. Read `engines.node` (`"24.x"` → Node 24 LTS)
2. Read `packageManager` (`pnpm@9.15.0` → activates pnpm via Corepack)
3. Run `pnpm install --frozen-lockfile` (the `heroku/nodejs-pnpm-install` buildpack handles this)
4. Run the `build` script (`next build`)
5. Set the launch process to the `web` line in `Procfile` (`node node_modules/.bin/next start` — calls Next.js directly without depending on a package manager at runtime)

No Next.js-specific buildpack adapter exists, so no standalone-output magic is needed — `next start` works out of the box.

## Tests

**Unit + component (Vitest + React Testing Library + MSW v2):**

```bash
pnpm test                 # one-shot run
pnpm test:watch           # watch mode
pnpm test:coverage        # with coverage (v8 provider)
```

Config: `vitest.config.ts` (jsdom env, globals, alias `@` → project root). Setup: `vitest.setup.ts` wires `jest-dom` matchers + MSW lifecycle. API mocks live in `mocks/handlers.ts`. `onUnhandledRequest: 'error'` is on — every fetched URL needs an explicit handler.

Co-locate `*.test.tsx` next to the component under test. For server logic that touches the backend, prefer an integration test on the backend over duplicating it here.

**E2E + a11y (Playwright + axe-core):**

```bash
pnpm e2e                  # build + start production server + run tests
pnpm exec playwright test --ui   # interactive mode (local)
```

Config: `playwright.config.ts`. Tests live in `e2e/*.e2e.ts`. **Tests run against the production server (`next start`), never `next dev`** — dev-mode HMR and dev-only overlays produce false signals.

The current smoke test (`e2e/smoke.e2e.ts`) checks the landing page renders + has zero WCAG 2 AA violations. Add a spec per route group as pages land.

## CI/CD

This frontend applies the personal CI/CD plugin at `brain/plugins/tech/nextjs-cicd/`.

**Wave 1 applied 2026-05-24** (platform-independent):
- Vitest + RTL + MSW v2 wired into `package.json` scripts + `vitest.config.ts` + `vitest.setup.ts` + `mocks/`
- Playwright + `@axe-core/playwright` wired into `playwright.config.ts` + `e2e/`
- `.github/workflows/frontend-ci.yml`: path-filtered to `frontend/**`; jobs = lint+typecheck → unit-tests + e2e-tests + npm-audit (parallel) → build-and-scan-image (`pack build` local + Trivy SARIF). No image push yet.
- Dependabot npm `/frontend` entry already wired into `.github/dependabot.yml`
- Dependabot auto-merge workflow already wired (shared with backend)

**Deviations from plugin defaults** (all intentional):
| Plugin default | Quiniela frontend | Why |
|----------------|-------------------|-----|
| Node 22 LTS | Node 24 LTS | Newer active LTS; pinned via `engines.node: 24.x` + `.nvmrc` |
| `output: 'standalone'` | bare `next.config.ts` | Trust `next start` for CNB build (smaller-image standalone optimization deferred) |
| `Procfile: node .next/standalone/server.js` | `Procfile: node node_modules/.bin/next start` | Non-standalone variant of the no-package-manager-at-runtime pattern |

**Wave 2 deferred** until `quiniela.dpdns.org` is responding: Artifact Registry push (via OIDC federation) + `gcloud run deploy` step. See `brain/plugins/tech/nextjs-cicd/deployment/gcp-cloud-run.md` for the canonical recipe + the three Cloud Run gotchas (`HOSTNAME=0.0.0.0`, `PORT`, `next/image` memory).

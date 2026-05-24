@AGENTS.md

# CLAUDE.md (frontend/)

Next.js 16 + TypeScript + Tailwind 4 + App Router frontend for the Quiniela 2026 app. Deployed to GCP Cloud Run service `quiniela-web` via Cloud Native Buildpacks (built locally with `pack`, pushed to Artifact Registry). See repo root `CLAUDE.md` for the overall architecture.

> The `@AGENTS.md` import above pulls in the Next.js team's per-version agent guidance, which warns that **APIs and conventions in Next.js 16 differ from older training data**. Before writing anything non-trivial, check `node_modules/next/dist/docs/` for the relevant guide.

All paths and commands below are relative to this `frontend/` directory.

## Prerequisites

- **Node.js 24** (active LTS). Use `nvm use` (it reads `.nvmrc`).
- **npm 11+** (ships with Node 24).
- **pack** (optional) — for local CNB build testing.

## Commands

```bash
# Install
npm install

# Dev (Turbopack on by default in Next 16)
npm run dev                              # http://localhost:3000

# Build & serve production
npm run build
npm start                                # honors $PORT env var (default 3000)

# Lint
npm run lint

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

The `heroku/nodejs-*` CNB buildpacks (same builder image we use even though we no longer deploy to Heroku) detect a Next.js app by the presence of `package.json` + a lockfile (`package-lock.json` here). They:

1. Read `engines.node` (`"24.x"` → Node 24 LTS)
2. Run `npm ci`
3. Run the `build` script (`next build`)
4. Set the launch process to the `web` line in `Procfile` (`npm start` → `next start`, which auto-reads `PORT` via Next.js 16's commander integration)

No Next.js-specific buildpack adapter exists, so no standalone-output magic is needed — `next start` works out of the box.

## Tests

None yet — Vitest + React Testing Library will land alongside the first non-trivial component (likely the bet entry form in PR 5 or 6). For server logic, prefer integration tests on the backend over duplicating the test in the frontend.

@AGENTS.md

# CLAUDE.md (frontend/)

Next.js 16 + TypeScript + Tailwind 4 + App Router frontend for the Quiniela 2026 app. Deployed to Heroku app `quiniela-web` via Cloud Native Buildpacks. See repo root `CLAUDE.md` for the overall architecture.

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

# Local CNB build (matches what Heroku does on push)
pack build quiniela-web --builder heroku/builder:26 --path .
```

## Configuration

`.env.local` (gitignored) holds local secrets. See `.env.local.example` for the template.

| Env var | Default | Where set | Purpose |
|---|---|---|---|
| `API_URL` | `http://localhost:8080` | `.env.local` / Heroku config | Backend base URL for server-side fetches |
| `PORT` | `3000` | Heroku sets it | Next.js auto-reads via `next start` |
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

## Heroku deploy

The buildpack chain on `quiniela-web`:

```bash
heroku create quiniela-web --stack heroku-24       # or heroku-26 if GA
heroku buildpacks:add -i 1 heroku-community/monorepo --app quiniela-web
heroku buildpacks:add -i 2 heroku/nodejs --app quiniela-web
heroku config:set APP_BASE=frontend --app quiniela-web
heroku config:set API_URL=https://quiniela-api.herokuapp.com --app quiniela-web
git push heroku master
```

The monorepo buildpack reads `APP_BASE=frontend` and only builds from this directory. The Node.js buildpack then sees `package.json`, picks Node 24 from `"engines": { "node": "24.x" }`, runs `npm ci && npm run build`, and starts via `Procfile` (`web: npm start` → `next start`, which reads `PORT`).

## Tests

None yet — Vitest + React Testing Library will land alongside the first non-trivial component (likely the bet entry form in PR 5 or 6). For server logic, prefer integration tests on the backend over duplicating the test in the frontend.

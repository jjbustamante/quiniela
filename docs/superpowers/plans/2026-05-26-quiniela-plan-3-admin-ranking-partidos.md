# Plan 3 — Admin results, ranking, partidos

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the v1 critical path before 2026-06-11. After this plan an admin can record real match results (and points are recomputed by the existing PL/pgSQL trigger), every player sees a live ranking with their position + estimated payout, and every player sees the schedule + their picks + the actuals on the Partidos screen. The deadline-lock UX is wired through to the group + knockout drill-ins so post-kickoff state is unambiguous.

**Architecture:** Three new backend feature packages (`admin/`, `ranking/`, `matches/`); three new frontend routes (`/admin/results`, real `/ranking`, real `/matches`); one extension to the existing public summary to expose the prize split. No new third-party deps. No schema changes — V005's scoring trigger already does the heavy lifting; we just write to `match.score_t1` / `score_t2` / `played` / `winner_id` from the admin endpoint and the trigger propagates points.

**Tech stack:** Same as Plans 1 + 2. Server actions for admin score writes. Reuses BracketService's `LockClock` + `Round` lookups to enforce deadlines.

---

## Reference

- Spec: [`docs/superpowers/specs/2026-05-25-quiniela-mvp-ui-design.md`](../specs/2026-05-25-quiniela-mvp-ui-design.md) — items 8 (lock UX), 9 (ranking), 10 (matches), 12 (admin results), 14 (pot/prize display)
- Plan 1 (foundation): [`2026-05-25-quiniela-plan-1-foundation-auth-invite.md`](2026-05-25-quiniela-plan-1-foundation-auth-invite.md)
- Plan 2 (bracket data + fill): [`2026-05-25-quiniela-plan-2-bracket-data-fill.md`](2026-05-25-quiniela-plan-2-bracket-data-fill.md)
- Scoring trigger (already shipped): `backend/src/main/resources/db/migration/V005__quinielas_bets_scoring.sql`
- Lock plumbing (already shipped): `backend/src/main/java/io/quiniela/api/bracket/LockClock.java` + `BracketService` lines 155–166

## Out of scope (deferred to Plan 4 / v1.1)

- Admin payments + prize-split editor (#13 in spec) — payments stay offline; treasurer can edit `prize_split` rows in SQL for v1
- Compare picks (#11) — popular but not critical for tournament function
- XLSX import/export (#15) — in-app fill works fine as the primary path

## Sequencing (16-day calendar, 2026-05-26 → 2026-06-11)

| Days | Task |
|---|---|
| 1–3 | Task 1: Admin results entry (highest risk; tournament can't run without it) |
| 4 | Task 2: Bracket lock UI |
| 5–7 | Task 3: Ranking endpoint + screen |
| 8–10 | Task 4: Partidos screen |
| 11 | Task 5: Prize-split + payout display |
| 12 | Task 6: Self-review + ops dry-run |
| 13–16 | Buffer: bugs, ops, soft launch with invited captains |

---

## Task 1: Admin results entry

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/admin/AdminResultsController.java`
- Create: `backend/src/main/java/io/quiniela/api/admin/AdminResultsService.java`
- Create: `backend/src/test/java/io/quiniela/api/admin/AdminResultsControllerIT.java`
- Create: `frontend/app/admin/results/page.tsx`
- Create: `frontend/app/admin/results/actions.ts` (server action)
- Create: `frontend/lib/api/admin.ts`
- Create: `frontend/components/admin/MatchResultRow.tsx`
- Modify: `backend/src/main/java/io/quiniela/api/config/SecurityConfig.java` (no path change needed — `/api/admin/**` already requires auth, role check is in service)
- Modify: `frontend/messages/en.json` + `es-CO.json` (add `admin.*` keys)

**Acceptance:**
- [ ] PUT `/api/admin/matches/{id}/result` with body `{ scoreT1, scoreT2 }` (both ints ≥ 0) sets `match.score_t1`, `match.score_t2`, `played = true`, computes `winner_id` (team1 / team2 / null for draw), and triggers the V005 PL/pgSQL function so every player's `quinielas.points` updates atomically.
- [ ] Endpoint returns 403 for non-admin users (verified by `users.role = 'admin'`).
- [ ] Endpoint returns 404 for unknown match id.
- [ ] Endpoint allows updating an already-played match (re-entry / correction); the trigger's "subtract old contribution, add new" math is exercised.
- [ ] `/admin/results` page renders only for `users.role = 'admin'` — non-admins get a 403 wrapper or redirect to `/home`.
- [ ] Page lists matches grouped by round (GROUP → R32 → R16 → QF → SF → 3°PUESTO → FINAL), filterable by round and group code.
- [ ] Each row shows team1/team2 + flags + kickoff timestamp + score inputs (0–20 each); save fires per-row server action.
- [ ] Saved rows visually flag "played"; corrections are allowed but show a "modified" badge.
- [ ] After a save, the row re-renders with the trigger's effect verified (e.g., fetch the match back).

**Implementation notes:**
- Admin role is already on `User` (`UserRole.ADMIN`). Add `requireAdmin(Jwt)` to a shared helper or inline the check in the service.
- `winner_id` is derived: scoreT1 > scoreT2 → team1; scoreT1 < scoreT2 → team2; equal → `null`. The trigger uses `winner_id`, so set it before the UPDATE.
- Don't add a separate endpoint to "unset" a result — for v1 admin can just re-enter the correct score; un-playing isn't a real workflow.
- Frontend page can be a Server Component with a Client Component per row (`MatchResultRow`) for the form. Server action does the PUT.

---

## Task 2: Bracket lock UI

**Files:**
- Modify: `frontend/lib/api/bracket.ts` — extend the response shape with `locked: boolean` per round
- Modify: `backend/src/main/java/io/quiniela/api/bracket/BracketService.java` — surface lock state in `BracketView` (groups + knockouts)
- Modify: `frontend/app/group/[groupId]/page.tsx` — render locked badge + read-only score cells when locked
- Modify: `frontend/app/knockout/[roundId]/page.tsx` — same
- Modify: `frontend/components/group/MatchRow.tsx` — pass-through `locked` prop, disable the numpad trigger
- Modify: `frontend/components/lobby/GroupCard.tsx` — show "🔒" + deadline date when group stage locked
- Modify: `frontend/messages/{en,es-CO}.json` — `lockedBadge`, `lockedAtFormat`, `lockedHelp`
- Create: `backend/src/test/java/io/quiniela/api/bracket/BracketLockUiIT.java` — verify `locked: true` returned for past deadlines

**Acceptance:**
- [x] `GET /api/bracket/me` response includes `locked: true | false` per group + per knockout round.
- [x] Past deadline = locked; before deadline = unlocked. Boundary at `tournament.group_stage_deadline` / `knockout_deadline` timestamps.
- [x] Group drill-in: when locked, score cells render as non-tap-able display values; numpad doesn't open; "🔒 Cerrado · 11.JUN 17:00" badge at top.
- [x] Knockout drill-in: same treatment per round.
- [x] Home lobby `GroupCard`: locked state shows "🔒" pill instead of progress count.
- [x] Server-side double-check: hitting `POST /api/bracket/bet` after the deadline returns the existing `BracketLockedException` (no change — verify still wired).

**Implementation notes:**
- Reuse the existing `LockClock.fetchTournamentDeadlines()` — don't re-query the schema from scratch.
- Boundary check is identical to the save-path check (`now.isAfter(deadline)`).
- Read-only cells visually = filled cells but with no pointer cursor + dimmer accent. Same component, `locked` prop suppresses the click handler.

---

## Task 3: Ranking endpoint + screen

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/ranking/RankingController.java`
- Create: `backend/src/main/java/io/quiniela/api/ranking/RankingService.java`
- Create: `backend/src/main/java/io/quiniela/api/ranking/RankingView.java` (DTO record)
- Create: `backend/src/test/java/io/quiniela/api/ranking/RankingControllerIT.java`
- Modify: `frontend/lib/api/ranking.ts` (new)
- Create: `frontend/app/ranking/page.tsx` (replace placeholder)
- Create: `frontend/components/ranking/RankingRow.tsx`
- Modify: `frontend/messages/{en,es-CO}.json` — `ranking.*` keys (header, columns, you, trend up/down/flat)

**Acceptance:**
- [x] `GET /api/ranking` returns `{ entries: [{ rank, userId, displayName, points, delta, isYou }], updatedAt }`, sorted by `points DESC, displayName ASC`.
- [x] `rank` ties share a number (1, 1, 3 — not 1, 2, 3) — `RANK()` semantics, not `ROW_NUMBER()`.
- [x] `delta` is rank change vs previous round (positive = moved up). For v1, store a `previous_rank` column in `quiniela` or recompute on the fly from a SQL snapshot table. Simplest: a new `quiniela_rank_snapshot` table updated by a cron / admin endpoint after each round closes. **Decide at implementation time** — if cron infra is too much, ship without delta (`delta: null`) and add in v1.1. → **Shipped `delta: null` for v1.**
- [x] `isYou` flag set for the row matching the JWT's user.
- [x] `/ranking` page renders the leaderboard with monospace font for points, "YOU" pill on your row, trend arrows (▲ delta_positive, ▼ delta_negative, "—" if `delta === 0 || null`).
- [x] Empty state (zero entries) renders "Aún no hay puntos · arranca el 11.JUN" — don't 500.
- [x] Page hits the endpoint with `cache: 'no-store'` (already the default in `lib/api/client.ts`).

> **Deploy regression resolved (2026-05-27, commits 9d5b26a → f496d16):** backend Cloud Run revision `quiniela-api-00021-nls` died at Flyway startup with `FATAL: remaining connection slots are reserved for roles with privileges of the pg_use_reserved_connections role`. Root cause: Cloud Run rolling deploys briefly overlap revisions, and Hikari's default 10-connection pool × 2 revisions exhausted Cloud SQL `db-f1-micro`'s default ~25 max_connections. Fix shipped in `f496d16`: cap HikariCP at `maximum-pool-size: 5` in `application-cloudrun.yml` + add `database_flags.max_connections=50` to `iac/cloud_sql.tf`. Revision `00022-lng` healthy in prod (`/api/ranking` returns 401 unauth as expected).

**Implementation notes:**
- Don't over-engineer the rank delta. If `quiniela_rank_snapshot` adds too much scope, ship `delta: null` and a follow-up plan.
- Top-3 payout estimates show in Task 5 (separate task to keep the diff focused).
- Page is a server component. No client interactivity needed beyond a refresh.

---

## Task 4: Partidos (schedule + results) screen

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/matches/MatchesController.java`
- Create: `backend/src/main/java/io/quiniela/api/matches/MatchesService.java`
- Create: `backend/src/test/java/io/quiniela/api/matches/MatchesControllerIT.java`
- Create: `frontend/lib/api/matches.ts`
- Create: `frontend/app/matches/page.tsx` (replace placeholder)
- Create: `frontend/components/matches/MatchTabs.tsx` (client — tab state)
- Create: `frontend/components/matches/MatchListItem.tsx` (server-renderable)
- Modify: `frontend/messages/{en,es-CO}.json` — `matches.{pasados,hoy,proximos,yourPick,result,hit,miss,upcoming,liveDot}` keys

**Acceptance:**
- [ ] `GET /api/matches` returns `{ past: [...], today: [...], upcoming: [...] }` where each match shape is `{ id, roundCode, groupCode, kickoffAt, team1: {code,name,flag}, team2: {code,name,flag}, score: {t1, t2} | null, played, yourPick: {t1, t2} | null, hit: 'exact' | 'winner' | 'goalDiff' | 'draw' | 'miss' | null }`.
- [ ] "today" window: kickoff ≥ start-of-day UTC and < start-of-tomorrow UTC (server's clock, not client's).
- [ ] "past" includes all matches with `kickoff < start-of-today`, sorted DESC.
- [ ] "upcoming" includes all matches with `kickoff ≥ start-of-tomorrow`, sorted ASC.
- [ ] User's pick comes from `bet` table joined on `(quiniela_id, match_id)` for the JWT's user; `null` if no bet exists.
- [ ] `hit` is computed using the same point classification logic as the V005 trigger — extract to a shared SQL function so both stay in sync.
- [ ] `/matches` page renders tab UI (Pasados / Hoy / Próximos), with a live-match indicator (red dot) next to today's matches whose kickoff has passed but `played` is `false`.
- [ ] Empty Hoy tab renders "Sin partidos hoy" (Spanish) / "No matches today" (English) — not a generic "no data" placeholder.

**Implementation notes:**
- The classification function (`hit`) is core scoring logic — duplicating it in Java and SQL invites drift. Either expose the V005 PL/pgSQL function via a SELECT, or extract a Java helper that gets unit-tested against the SQL with fixed cases. Pick at implementation time.
- Live-match indicator is best-effort — don't poll. A page refresh re-checks. (Auto-refresh is v1.1.)

---

## Task 5: Prize-split + payout display

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/tournament/PublicSummaryController.java` — extend response with `prizeSplit: [{rank, percentage, payoutCents}]` (computed from `prize_split` table + `potCents`)
- Modify: `frontend/lib/api/summary.ts` — extend `PublicSummary` type
- Modify: `frontend/app/ranking/page.tsx` — render payout next to top-3 rows
- Modify: `frontend/components/ranking/RankingRow.tsx` — accept optional `payoutLabel` prop
- Modify: `frontend/messages/{en,es-CO}.json` — `ranking.prizeFor` (e.g., "Premio")
- Update: `frontend/lib/api/summary.ts` `FALLBACK` constant — include hardcoded 80/15/5 split for offline safety

**Acceptance:**
- [ ] `/api/public/summary` response gains `prizeSplit: [{ rank: 1, percentage: 80, payoutCents: <potCents * 80 / 100> }, ...]`.
- [ ] Empty pool (zero panas) renders 0 payouts but full split percentages — not 500.
- [ ] `/ranking` shows "🥇 $XX" / "🥈 $YY" / "🥉 $ZZ" next to the top-3 rows.
- [ ] Landing page is NOT extended — pot is still withheld pre-auth per the spec decision.

**Implementation notes:**
- Tight task — one extension to an existing endpoint and one render in an existing page.

---

## Task 6: Self-review + ops dry-run

- [ ] Run all backend ITs (`./mvnw verify`) — green.
- [ ] Run frontend lint, typecheck, unit, e2e — green.
- [ ] Manually walk: invite a second test user, both fill brackets, log in as admin, enter a result for match #1, observe both rankings + Partidos pages update.
- [ ] Verify pot increases when a second pana joins.
- [ ] Verify locked UX by temporarily setting `tournament.group_stage_deadline` to a past timestamp in a scratch DB.
- [ ] Confirm GitHub Actions deploy pipeline still pushes images to Artifact Registry on `master` push.
- [ ] Run `gcloud run services describe quiniela-api` post-deploy to confirm the new revision is serving.

---

## Self-Review

Before considering this plan executed:
- [ ] Every task's acceptance checkboxes are checked.
- [ ] No regression in Plan 1/2 behavior (group fill, Paul, invite tree all still work).
- [ ] The "$0 pot · 0 panas" empty state is reachable but doesn't look broken.
- [ ] Admin-only routes return 403 (not 401, not redirect to login) for authenticated non-admins — easier to spot bugs.
- [ ] No new third-party dependencies were added without justification.

## Execution Handoff

Pair with `superpowers:executing-plans`. Mark tasks as you complete them with `- [x]`. After Task 1 + Task 2 are green, ship them — don't wait for the full plan. Each task can deploy independently.

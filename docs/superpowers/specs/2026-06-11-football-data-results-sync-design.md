# Football-data.org Results Auto-Sync — Design

**Date:** 2026-06-11
**Status:** Approved (design), pending spec review
**Author:** Juan + Claude

## Problem

Today the quiniela has no automatic results sync. The football-data.org
integration (`FootballDataLoader`) runs **once at startup**, and only when the
`team` table is empty — it seeds teams/standings/matches and then never runs
again. During the tournament, match results are entered **manually** by an
admin via `PUT /api/admin/matches/{matchId}/result`.

We want results (and fixture/knockout updates) to flow in automatically from
football-data.org during the World Cup, without hammering the free-tier API
and without manual data entry for every match.

### Live reopen scenario (drives a hard integrity requirement)

The pool is being **reopened** because some players hadn't filled all their
bets. But the **first group game has already been played and its points were
calculated manually**. Reopening lets those players add/edit bets. This creates
a sharp hazard for an overwrite-style sync:

> If auto-sync re-writes the already-played game's score, the `BEFORE UPDATE`
> trigger recomputes points — and would now score the game against **bets edited
> after reopening**, corrupting a result that was already finalized.

**Hard requirement:** auto-sync must **never touch a match that is already
`played` in our DB.** Already-scored games stay exactly as the manual
calculation left them, regardless of what the API returns or who edits a bet
afterward. (Whether late bets on an already-played game *should* be editable at
all is a separate concern owned by the reopen mechanism, not this sync; this
design simply guarantees the sync can't re-score a finished game.)

## Constraints that shape the design

- **Cloud Run scale-to-zero.** `quiniela-api` runs with
  `min_instance_count = 0` and `cpu_idle = false` (`iac/cloud_run.tf`). An
  in-process Spring `@Scheduled` timer will **not** fire reliably: when idle the
  instance is reclaimed, and even while alive CPU is throttled outside request
  handling. The trigger must come from **outside** the container.
- **Public service.** The api service has `allUsers` run.invoker
  (`iac/cloud_run.tf:212`) because the browser calls it directly. Cloud Run IAM
  therefore cannot gate a single route — `/internal/**` is reachable from the
  internet and must be protected **in-app**.
- **Free-tier API — no in-play data.** Our football-data.org subscription does
  **not** provide live/in-play scores. A match's result only appears in the API
  **after full-time** (and may lag by some propagation delay on the free tier).
  Polling *during* a match is therefore wasted calls. The design must poll a
  match only **after it could plausibly be over**, then keep checking until the
  API reports it FINISHED.
- **Free-tier rate limit.** ~10 req/min, uncertain. The design must call the API
  **only when a finished result is plausibly available** and never poll a match
  already final in our DB. One `getMatches()` call returns every match, so even
  several matches ending at once cost a single request per tick.
- **All-GCP stack.** Cloud Run, Cloud SQL, Secret Manager, Workload Identity,
  all Terraform-managed. Scheduling should stay in GCP for consistency.

## Decisions (from brainstorming)

| Decision | Choice |
|----------|--------|
| Reconciliation with manual results | **API is source of truth for not-yet-played matches only.** A match already `played = true` in our DB is **frozen** — auto-sync never rewrites its score, kickoff, or `played` flag (admin-only thereafter). This protects already-scored games from being re-scored against late-edited bets. |
| Scope | **Scores + kickoff/fixture/knockout-slot updates** (all from one `getMatches()` call). |
| Scheduling mechanism | **Cloud Scheduler (1×/day) + Cloud Tasks (per-match)** → HTTP endpoints on the api service. |
| Job shape | A daily planner refreshes fixtures and **enqueues one Cloud Task per match** at ~`kickoff + 105 min`; each task checks the result and **re-enqueues every 15 min** until FINISHED or `kickoff + 5 h`. |
| Why not a frequent cron | `cpu_idle = false` ⇒ warm-time bills like an always-on instance, and a 10-min cron never lets the instance scale to zero. Per-match tasks wake it **only around match ends**. |
| Schedule source | **Our own `match.kickoff_at`** (refreshed by the daily sync) — the planner never calls the third-party API just to learn the day's schedule. |
| Auth | **Shared-secret header** (`X-Sync-Token`) from Secret Manager, checked in-app (same for Cloud Scheduler and Cloud Tasks calls). |
| Per-match poll window | first check at `kickoff + 105 min`; re-enqueue every 15 min until FINISHED or `kickoff + 5 h` (then give up → daily sync / admin fallback). |
| Daily planner time | **06:00 America/Bogota** (11:00 UTC): full fixture refresh + enqueue tasks for matches kicking off in the next 24 h. |

### Why shared-secret, not OIDC

OIDC would be cleaner *if* Cloud Run IAM could gate the route — but the service
is public (`allUsers`), so IAM can't. Validating a Google-issued OIDC token
would mean adding a second JWT issuer to `SecurityConfig` alongside NextAuth.
A high-entropy shared secret over TLS is equally safe here and far less code.

### Why per-match Cloud Tasks instead of a frequent cron

The deciding factor is `cpu_idle = false` on the api service (set so Paul's
background work survives the 202 response). With CPU allocated for the whole
instance lifetime, **keeping the instance warm bills like an always-on
instance.** A naïve "poll every 10 min" cron arrives before Cloud Run's
keep-warm window elapses, so the instance never scales to zero — you'd pay for a
24/7 instance just to answer "no match is due." With `cpu_idle = false`, a cold
start (~10–15 s of billed startup CPU) is *far* cheaper than staying warm, so the
design should wake the instance **rarely and only when there's real work.**

Cloud Tasks fits exactly: a once-daily planner reads today's `kickoff_at` values
(already in our DB — no API call to learn the schedule) and enqueues one task per
match timed to ~`kickoff + 105 min`. Each task does one result check and
re-enqueues itself every 15 min until the match is FINISHED or the 5 h cap. The
instance is woken only in the ~30–60 min window around each match cluster and
scales to zero the rest of the day.

Rejected alternatives: a frequent cron (keeps the instance warm, the problem
above); a planner that *rewrites a Cloud Scheduler cron's schedule* each day
(needs `cloudscheduler.admin` IAM and fights Terraform's ownership of the job's
`schedule` field). Cloud Tasks keeps Terraform owning the static infra (queue +
daily job) while the app only *enqueues*, which is a clean, idiomatic boundary.

## Architecture

```
Cloud Scheduler (Terraform)          Cloud Tasks queue (Terraform)
  job: daily-plan                      results-sync-queue
  daily 11:00 UTC                        (per-match tasks, scheduleTime = kickoff+105m)
  POST /internal/sync/daily                 │ re-enqueued +15m until final / 5h cap
        │  + X-Sync-Token                    │  POST /internal/sync/results?matchId=N
        ▼                                    ▼   + X-Sync-Token
        └──────────────►  quiniela-api (Cloud Run, public)  ◄──────────────┘
                           SyncTokenFilter ── 401 if X-Sync-Token bad
                                  │
                           InternalSyncController
                            ├── /internal/sync/daily        → service.runDaily()
                            └── /internal/sync/results?matchId → service.syncMatch(id)
                                  │
                           FootballDataSyncService
                            ├── runDaily()    ── syncFull() then planToday() (enqueue tasks)
                            ├── syncFull()     ── getMatches() + upsertMatches()  (fixtures refresh)
                            ├── planToday()    ── read kickoff_at (DB); enqueue 1 task/match @ kickoff+105m
                            ├── syncMatch(id)  ── if already played → done (no API);
                            │                     else getMatches() + upsertMatches();
                            │                     if still not final → re-enqueue +15m (cap kickoff+5h)
                            └── upsertMatches()── existing UPSERT + `WHERE NOT match.played` freeze
                                  │
                           FootballDataClient.getMatches() → football-data.org
                                  │
                           match table ── BEFORE UPDATE trigger (V019) recomputes points
                                            (never fires for already-played rows)
```

> One `getMatches()` returns every match, so each task upserts *all* matches
> (idempotent; the freeze guard protects played rows) and inspects only its own
> `matchId` to decide whether to re-enqueue. Clustered matches cost one API call
> per task — bounded and well under the rate limit; a single-match
> `getMatch(id)` is a possible later optimization.
>
> **Manual path (testing/ops):** an admin can hit `POST /api/admin/sync/daily`
> or `POST /api/admin/sync/match/{id}` (Google-JWT + `AdminGuard`, no shared
> secret) to run the same `runDaily()` / `syncMatch()` on demand — see
> component 5.

## Components

### 1. `FootballDataSyncService` (new — `io.quiniela.api.footballdata`)

The reusable core. Extracts the match-upsert logic currently **inline** in
`FootballDataLoader` (the `INSERT ... ON CONFLICT (id) DO UPDATE` block) so both
the startup loader and the scheduled endpoint share one code path.

```
record SyncResult(boolean apiCalled, int matchesUpserted, String skippedReason)

SyncResult runDaily()        // called by the daily Cloud Scheduler job
  // if (!footballDataEnabled)  return skipped("integration disabled")
  // SyncResult r = syncFull()         // refresh fixtures/kickoffs first
  // int enqueued = planToday()        // then enqueue per-match tasks off fresh kickoff_at
  // return r.withEnqueued(enqueued)

int planToday()              // enqueue one Cloud Task per upcoming match
  // for each match WHERE tournament_id=1 AND played=false
  //                 AND kickoff_at BETWEEN now() AND now() + interval '24 hours':
  //   taskQueue.enqueue(
  //     url   = ".../internal/sync/results?matchId=" + match.id,
  //     when  = match.kickoff_at + match-min-duration-minutes,   // first check
  //     name  = "match-" + match.id + "-" + kickoffDate)         // dedup: idempotent enqueue
  // return count

SyncResult syncMatch(long matchId)    // called by each Cloud Task
  // if (!footballDataEnabled)         return skipped("integration disabled")
  // Match m = matches.find(matchId)
  // if (m == null || m.played)        return skipped("already final")   // NO API, no re-enqueue
  // resp = client.getMatches(code); upsertMatches(resp)                 // upsert all; freeze guard
  // if (matches.find(matchId).played) return done                       // got it
  // if (now() < m.kickoff_at + poll-window-hours)
  //        taskQueue.enqueue(sameUrl, when = now() + retry-interval-minutes, name = uniquePerAttempt)
  // else   log.warn("gave up on match {} after poll window", matchId)   // daily/admin fallback

SyncResult syncFull()        // ungated full upsert (fixtures + any finished results for unplayed rows)
  // if (!footballDataEnabled)  return skipped("integration disabled")
  // resp = client.getMatches(code); n = upsertMatches(resp); return SyncResult(true, n, null)

int upsertMatches(CompetitionMatchesResponse resp)   // the extracted UPSERT (freeze guard inside)
```

- **No in-play data → check after the match ends.** Our subscription only
  publishes a result after full-time, so the first task is timed to
  `kickoff_at + match-min-duration-minutes` (default **105** — earliest a
  regulation match can be over: 90' + half-time + stoppage).
- **Re-enqueue until final.** If the API doesn't yet show the match FINISHED, the
  task re-enqueues itself `retry-interval-minutes` later (default **15**) until
  `kickoff_at + poll-window-hours` (default **5 h** — absorbs extra time,
  penalties, and the free tier's propagation delay), then gives up. The daily
  `syncFull()` and manual admin entry are the fallbacks for a missed result.
- **Skip-already-synced** is explicit: `syncMatch` returns immediately with no
  API call and no re-enqueue once `m.played` is true. Between match windows the
  instance has no tasks to serve → **scales to zero**.
- **Source of truth — but only for not-yet-played matches.** `upsertMatches`
  writes `score_t1/score_t2/advanced_team_id/played/kickoff_at` for matches that
  are **not yet `played`**; team ids use `COALESCE` so a filled knockout slot
  isn't blanked by a later TBD payload. Writing the score columns fires the V019
  `BEFORE UPDATE` trigger that recomputes points.
- **Freeze already-played matches (the integrity guard).** The conflict-update
  carries a `WHERE NOT match.played` clause, so for a row already `played = true`
  the UPSERT is a **no-op** — the row isn't touched and the `BEFORE UPDATE`
  trigger **never fires**. This is the surgical fix for the reopen scenario
  below: it guarantees a game whose points were already calculated cannot be
  re-scored by a later sync (and, importantly, can't be re-scored against bets
  edited after reopening). Relying on value-equality would be unsafe — Postgres
  fires `BEFORE UPDATE` even when the new score equals the old — so we suppress
  the UPDATE entirely with the `WHERE`.

```sql
INSERT INTO match (id, ..., score_t1, score_t2, advanced_team_id, played, kickoff_at)
VALUES (...)
ON CONFLICT (id) DO UPDATE SET
  team_1_id        = COALESCE(EXCLUDED.team_1_id, match.team_1_id),
  team_2_id        = COALESCE(EXCLUDED.team_2_id, match.team_2_id),
  score_t1         = EXCLUDED.score_t1,
  score_t2         = EXCLUDED.score_t2,
  advanced_team_id = EXCLUDED.advanced_team_id,
  played           = EXCLUDED.played,
  kickoff_at       = EXCLUDED.kickoff_at
WHERE NOT match.played;     -- already-played games are frozen; trigger never fires
```

  This guard lives in `upsertMatches`, so **both** the results poll and the daily
  full-sync inherit it (the daily full-sync is the real risk — it upserts every
  match ungated). The startup loader inherits it too: the very first insert of an
  already-FINISHED match still records its result (INSERT path, not the
  conflict path), but a re-sync can never re-score a played row.

### 2. `FootballDataLoader` (refactor)

Replace the inline match-upsert block with a call to
`FootballDataSyncService.upsertMatches(...)`. Teams/standings seeding stays in
the loader. **No behavior change** at startup — pure extraction.

### 3. `ResultsTaskQueue` (new — Cloud Tasks adapter)

A thin wrapper over the Cloud Tasks client so `FootballDataSyncService` stays
testable (interface + a no-op/fake impl for tests).

```
void enqueue(long matchId, Instant when, String dedupName)
  // create an HTTP-target task in queue `app.sync.tasks.queue`:
  //   POST {app.sync.tasks.target-base}/internal/sync/results?matchId={matchId}
  //   header X-Sync-Token: {app.sync.token}
  //   scheduleTime = when
  //   name = dedupName   // Cloud Tasks dedups by name → idempotent re-enqueue / re-plan
```

Uses the Cloud Tasks Java client; authenticates as the api runtime SA (needs
`roles/cloudtasks.enqueuer`). Locally / in tests the bean is a fake that records
calls instead of hitting GCP (gated by `app.sync.tasks.enabled`).

### 4. `InternalSyncController` (new)

```
@RestController @RequestMapping("/internal/sync")
POST /daily               → ResponseEntity.ok(service.runDaily())          // Cloud Scheduler
POST /results?matchId={n} → ResponseEntity.ok(service.syncMatch(n))        // Cloud Tasks
```

Always returns **200** for handled cases (including "disabled", "already final",
"api error") so the scheduler/queue don't retry-storm on expected outcomes. The
`SyncResult` body is for logs/observability. (Genuine transient failures we *do*
want retried can return 5xx — Cloud Tasks retries with backoff per the queue
config; keep this list small and explicit.)

### 5. `AdminSyncController` (new) — manual / on-demand triggers

So a result sync can be run **on demand** (testing today's game, or forcing a
catch-up) without waiting for the 06:00 daily job and without handling the shared
secret by hand. Mirrors `PaulAdminController`: admin-only, reuses the existing
`AdminGuard` + Google-JWT auth — **no `X-Sync-Token`** (that secret is for
machine callers only).

```
@RestController @RequestMapping("/api/admin/sync")   // admin JWT, AdminGuard.requireAdmin
POST /daily                → service.runDaily()        // refresh fixtures + enqueue today's tasks now
POST /match/{matchId}      → service.syncMatch(id)     // immediate one-off check for a single match
```

- `POST /daily` is the button you'd hit a couple of hours before a game: it
  refreshes fixtures and enqueues the per-match task(s) immediately (the task
  itself still fires at `kickoff + 105 min`). Idempotent — Cloud Tasks dedup by
  name means re-clicking won't double-enqueue.
- `POST /match/{id}` forces an **immediate** check of one match (bypasses the
  task schedule) — handy to verify the result lands right after full-time.
  Still subject to the freeze guard, so it can't disturb an already-scored game.
- These are also trivially scriptable for ops (`curl` with the admin token), and
  a later admin-UI button can call them (fits the existing test-mode tooling).

> **Quick-test path for today's game:** deploy → call `POST /api/admin/sync/daily`
> from your admin session now → confirm a Cloud Task appears (GCP console /
> `gcloud tasks list`) scheduled at `kickoff + 105 min` → after full-time, the
> task (or a manual `POST /api/admin/sync/match/{id}`) upserts the result and the
> trigger scores it. No need to wait for tomorrow's 06:00 run.
>
> Independently, ops can also trigger the *machine* path with
> `gcloud scheduler jobs run quiniela-daily-plan --location=<region>` — that fires
> the real Cloud Scheduler job (header and all) on demand.

### 6. `SyncTokenFilter` (new) + `SecurityConfig` change

- `SecurityConfig`: add `/internal/**` to the `permitAll()` matcher (Spring's
  JWT auth does not apply; the token filter guards it instead).
- `SyncTokenFilter` (a `OncePerRequestFilter` scoped to `/internal/**`):
  compares the `X-Sync-Token` header against `app.sync.token` using a
  **constant-time** comparison (`MessageDigest.isEqual`). Missing/empty config
  token → always 401 (fail closed). Mismatch/absent header → 401. Both Cloud
  Scheduler (daily) and Cloud Tasks (per-match) present this header.

### 7. Config (`application.yml` + `application-cloudrun.yml`)

```yaml
app:
  sync:
    token: ${APP_SYNC_TOKEN:}            # shared secret; empty => endpoint 401s (fail closed)
    match-min-duration-minutes: ${APP_SYNC_MATCH_MIN_DURATION_MINUTES:105}  # first check after a match could be over
    poll-window-hours: ${APP_SYNC_POLL_WINDOW_HOURS:5}                      # re-enqueue until final, then give up
    retry-interval-minutes: ${APP_SYNC_RETRY_INTERVAL_MINUTES:15}           # gap between re-checks
    tasks:
      enabled: ${APP_SYNC_TASKS_ENABLED:false}   # false locally (fake queue); true on Cloud Run
      queue: ${APP_SYNC_TASKS_QUEUE:}            # full queue path projects/.../locations/.../queues/results-sync
      target-base: ${APP_SYNC_TASKS_TARGET_BASE:}# api base URL Cloud Tasks calls back (the api's own Cloud Run URL)
  football-data:
    enabled: ${APP_FOOTBALL_DATA_ENABLED:false}   # already exists; gates the sync too
```

### 8. IaC

**`iac/tasks.tf` (new):** `google_cloud_tasks_queue.results_sync` in the api
region, with a modest `rate_limits` / `retry_config` (e.g. max 5 attempts,
min/max backoff) so a flapping endpoint doesn't hammer the API.

**`iac/scheduler.tf` (new):** a single
`google_cloud_scheduler_job.daily_plan` — `schedule = "0 11 * * *"`,
`time_zone = "Etc/UTC"`, `http_target` POST to `${api.uri}/internal/sync/daily`,
header `X-Sync-Token = <secret value>`, minimal retry.

**`iac/secrets.tf`:** new `sync_token` secret. Generate a `random_password`,
store its value as a Secret Manager version (app reads it via `APP_SYNC_TOKEN`),
and pass the same value into the Cloud Scheduler header.

**`iac/cloud_run.tf`:** add to the api service — `APP_SYNC_TOKEN` (secret env,
mirroring `APP_FOOTBALL_DATA_API_KEY`), `APP_SYNC_TASKS_ENABLED=true`,
`APP_SYNC_TASKS_QUEUE` (the queue path), `APP_SYNC_TASKS_TARGET_BASE` (the api's
own URL). IAM: api runtime SA gets `roles/secretmanager.secretAccessor` on the
new secret and `roles/cloudtasks.enqueuer` on the queue.

**`iac/apis.tf`:** enable `cloudscheduler.googleapis.com` and
`cloudtasks.googleapis.com`.

> **State note:** the shared secret appears in Terraform state (Cloud Scheduler
> header values are plaintext in state). Acceptable for this project's state
> handling; documented so it's a known, not a surprise.

## Error handling

- **API failure** (network/auth/parse): logged at WARN, endpoint returns 200
  with `skippedReason` so the task isn't retried as if it were a transient
  infra error. A `syncMatch` that fails this way still re-enqueues its next
  check (within the poll window), so a flaky API self-heals on the next tick.
- **Idempotent:** the UPSERT is safe to run repeatedly; the freeze guard makes a
  double-fire on a played match a no-op. Cloud Tasks name-based dedup makes
  re-planning the same day idempotent.
- **Disabled / already final:** 200 no-op, no API call, no re-enqueue.
- **Bad/missing token:** 401, no work done.

## Known tradeoffs

- **Corrections to a played match are admin-only by design.** Because played
  matches are frozen from auto-sync (the integrity guard), if football-data.org
  later publishes a *corrected* score for a game that's already final in our DB,
  the sync will **not** pick it up — an admin must apply it via
  `PUT /api/admin/matches/{id}/result`. This is the deliberate trade for
  protecting already-scored bets during the reopen; "auto-correct finished
  games" is explicitly not a goal. `played = false` matches still get the API
  value automatically.
- **Result latency ≈ propagation + retry interval.** A result lands within one
  `retry-interval-minutes` (15 min) of the API publishing it, not instantly.
  Acceptable for a quiniela; tighten the interval if needed.
- **Added infra surface.** Cloud Tasks is a new dependency (queue + IAM +
  client). The payoff is the cost profile: the instance wakes only ~once/day to
  plan plus a handful of times around each match cluster, and **scales to zero**
  the rest of the day — versus a frequent cron that, with `cpu_idle = false`,
  would keep it warm ≈24/7 (the concern that drove this choice). Cloud Tasks
  itself is effectively free at this volume.

## Testing

- **Unit — `FootballDataSyncService`** (stub `FootballDataClient`, fake
  `ResultsTaskQueue`):
  - `planToday()` enqueues one task per not-yet-played match kicking off in the
    next 24 h, at `kickoff + min-duration`, with a stable dedup name; skips
    played/out-of-range matches.
  - `syncMatch()`: no-op + no API + no re-enqueue when disabled or already
    played; on a not-final match → calls client, upserts, **re-enqueues** at
    `now + retry-interval`; on a now-final match → upserts, **no** re-enqueue;
    past `kickoff + poll-window` → gives up (no re-enqueue), logs WARN.
  - `runDaily()` calls `syncFull()` then `planToday()`.
  - `upsertMatches()` mapping (reuse existing loader test fixtures).
- **Unit/DB — freeze guard (highest-value test).** Seed a match as
  `played = true` with a known score and known computed points. Run
  `upsertMatches()` with an API payload carrying a **different** score for that
  match. Assert: the row's score, `played`, and **computed points are
  unchanged** — and ideally that the `BEFORE UPDATE` trigger did not fire (e.g.
  via an `updated_at`/audit assertion). Mirror the bet-level concern: seed a bet
  *edited after* the match was scored and confirm a sync does not change that
  bet's awarded points. This is the regression test for the reopen hazard.
- **Unit — `SyncTokenFilter`:** 401 on missing header, wrong token, and empty
  configured token; pass-through on match.
- **Integration — `InternalSyncController`:** 401 without token; `POST /daily`
  with token → 200, refreshes fixtures + enqueues tasks (assert via fake queue);
  `POST /results?matchId` for a not-yet-played row whose stub is FINISHED → 200,
  points recomputed via the trigger; the same payload's already-played row stays
  frozen.
- **Integration — `AdminSyncController`:** non-admin caller → 403; admin
  `POST /api/admin/sync/daily` → enqueues via fake queue; admin
  `POST /api/admin/sync/match/{id}` → immediate check path (upserts a FINISHED
  stub, freezes a played one). Confirms the manual triggers reuse `AdminGuard`
  and need **no** `X-Sync-Token`.

## Out of scope

- A planner that *rewrites a Cloud Scheduler cron's schedule* at runtime
  (rejected — per-match Cloud Tasks wake the instance only when needed without
  app-owned Scheduler mutation).
- Per-match manual-result locking.
- Standings/teams re-sync after startup (teams are stable for the tournament).
- Knockout OFFICIAL-prediction gating for Paul (separate concern).

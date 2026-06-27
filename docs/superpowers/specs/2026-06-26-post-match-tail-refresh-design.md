# Post-match tail refresh for prompt bracket reveal

**Date:** 2026-06-26
**Status:** Approved (design)
**Extends:** `2026-06-11-football-data-results-sync-design.md`

## Problem

The knockout bracket fills in per stage: a round becomes bettable once the
group stage closes **and** its matches have teams assigned, and "earlier rounds
fill teams as prior rounds resolve" (`BracketService` round-unlock logic). Those
team assignments arrive **only** through `FootballDataSyncService.syncFull()`'s
`upsertMatches` (which COALESCEs team ids from the football-data.org feed).

Today the full-competition refresh happens in two ways:

1. **Daily cron** at `0 11 * * *` UTC = **06:00 America/Bogotá**
   (`iac/scheduler.tf`), which runs `runDaily()` (`syncFull()` + `planToday()`).
2. **Per-match polling**: `planToday()` enqueues a Cloud Task per unplayed match
   kicking off within 24h; each task calls `syncMatch()`, which re-pulls the
   full competition every `retry-interval-minutes` (5) for a
   `poll-window-hours` (5) window — **but stops the moment the match flips to
   `FINISHED`** (no re-enqueue).

**The gap:** football-data.org publishes the next round's pairings (and KO
advancements) *after* the deciding match finishes — sometimes minutes, sometimes
a couple of hours later. By then the per-match poll for that match has already
stopped. So a bracket that resolves at, say, 19:00 on June 28 is not picked up
until the **06:00 June 29** daily cron — costing players up to ~11 hours of
fill time before that stage's window closes.

Concretely: the group stage finishes **June 28 ~16:00 COT**; the R32 pairings
must appear ASAP so players have maximum time before the R32 window closes. The
same gap recurs at **every** knockout transition (R32→R16→QF→SF→F), since each
stage has its own closing window.

## Goal

After any match goes FINAL, keep re-pulling the full competition on a short tail
schedule so newly-resolved pairings/advancements surface within ~30 minutes,
without waiting for the daily cron.

## Approach

**Post-match tail refresh** (chosen over a frequent tournament-wide cron for its
surgical footprint — polling concentrated exactly around match completions, no
idle polling). Staleness target: **~30 minutes**.

`/internal/sync/results` cannot be reused for this: it early-returns
`"already final"` and skips the upsert once a match is played. We need a
structural-refresh path that always upserts regardless of any match's played
state — that is exactly `syncFull()`.

## Components

### 1. `SyncProperties` — two new fields

Bound from `app.sync.*` with sensible defaults:

- `tail-refresh-interval-minutes` → `tailRefreshIntervalMinutes`, default **30**
- `tail-window-hours` → `tailWindowHours`, default **3**

Defaults applied in the compact constructor (same pattern as the existing
`pollWindowHours` / `retryIntervalMinutes`). Env overrides
(`APP_SYNC_TAIL_REFRESH_INTERVAL_MINUTES`, `APP_SYNC_TAIL_WINDOW_HOURS`) are
available via the existing `${...}` binding but are **not** required for go-live.

### 2. New internal endpoint

`POST /internal/sync/fixtures` on `InternalSyncController` → `service.syncFull()`,
returning `200` with the `SyncResult` (consistent with the always-200 contract
so Cloud Tasks never retry-storms). Already protected by `SyncTokenFilter`, which
guards all of `/internal/**` with the `X-Sync-Token` shared secret.

### 3. `ResultsTaskQueue` — fixtures-refresh enqueue

Add to the interface:

```java
/** Schedule a POST to /internal/sync/fixtures at {@code when}. */
void enqueueFixturesRefresh(Instant when, String dedupName);
```

- **`CloudTasksResultsQueue`**: same as `enqueue`, but the target URL is
  `props.tasks().targetBase() + "/internal/sync/fixtures"` (no `matchId` param).
  Same `X-Sync-Token` header, same `AlreadyExistsException` dedup handling.
- **Noop impl** (`NoopResultsTaskQueue` lambda): log instead of enqueue.

The existing per-match `enqueue(...)` is unchanged.

### 4. `FootballDataSyncService.syncMatch` — schedule the tail

When a match transitions to FINAL (the existing `nowPlayed == true` branch),
before returning, schedule the tail series:

- Compute slots at `+interval, +2×interval, …` from `Instant.now()`, each
  **rounded up to the next interval-minute wall-clock boundary** (e.g. :00 / :30
  for a 30-min interval), continuing until the offset exceeds `tailWindowHours`.
- For each slot, call `queue.enqueueFixturesRefresh(slot, "fixtures-" +
  slot.getEpochSecond())`.

Rounding to fixed boundaries makes the dedup name **shared across matches**: many
matches finishing within the same window enqueue the *same* slot tasks, so Cloud
Tasks collapses them to **one full refresh per slot**, not one per match.

The tail fires after **any** final — group or knockout — uniformly covering the
group→R32 transition and every subsequent KO transition. A group-match final
that doesn't complete a group still just triggers an idempotent (deduped)
refresh; harmless.

A helper (e.g. `scheduleTailRefresh()`) encapsulates the slot computation so it
is unit-testable in isolation.

## Data flow

```
match flips FINISHED in football-data feed
  → next scheduled syncMatch() upserts it (played=true) + detects nowPlayed
  → scheduleTailRefresh(): enqueue fixtures-refresh tasks at rounded 30-min slots
    across the next 3h (deduped by slot)
  → each task → POST /internal/sync/fixtures → syncFull()
    → upsertMatches() COALESCEs newly-assigned next-round team ids
    → BracketService now reports those rounds as unlocked/bettable
```

## Safety & cost

- `syncFull` keeps the `WHERE NOT match.played` freeze guard → tail refreshes
  **never re-score** finalized games.
- `syncFull` does **not** call `planToday` → no per-match task storms.
- Cloud Tasks name-dedup + idempotent upsert → overlapping tails collapse;
  series is bounded by `tail-window-hours`.
- Cost: ≤ ~6 refreshes per 30-min slot over 3h, **shared** across all matches —
  trivial vs football-data.org's 10-calls/minute limit.

## Testing

- **Unit** (`FootballDataLoaderTest`-style, no Spring): given a match flips to
  FINAL, assert N tail tasks enqueued at the correct rounded times with the
  expected deduped slot names; given the match is still not played, assert no
  tail tasks scheduled. A fake `ResultsTaskQueue` captures `enqueueFixturesRefresh`
  calls. Inject a fixed "now" so slot rounding is deterministic.
- **Integration** (extend `FootballDataSyncServiceIT`): `POST
  /internal/sync/fixtures` invokes `syncFull` and upserts fixtures; a match that
  is already final is not re-scored by the fixtures path.

## Out of scope (YAGNI)

- No new Cloud Scheduler job; no IaC change required (defaults ship in
  `application.yml`).
- No frequent tournament-wide polling cron (rejected approach A).
- No change to the daily `runDaily()` / per-match results path.

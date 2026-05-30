# Compare feature ("Duelos") — design

> Spec date: 2026-05-30
> Status: approved, ready for implementation plan
> Spec item: #11 in `2026-05-25-quiniela-mvp-ui-design.md` ("Compare picks vs another player"),
> deferred out of Plan 3, now being built.

## Summary

Turn the `/compare` placeholder ("Próximamente") into a real screen — **"Duelos"** — that
lets a player compare their quiniela against the rest of the pool. Two modes behind a
segmented toggle:

- **1 vs 1** (head-to-head): pick one rival, see a four-column diff (`Partido / Tú / Rival / Real`)
  with the matches where you differ floated to the top and agreements collapsed.
- **Grupo** (consensus): for each match, the distribution of predicted scores as bars with
  counts, your pick highlighted, and a `Con la mayoría` / `Rebelde 🔥` tag. Scales to any
  group size (chosen over a per-person grid, which breaks past ~20 players).

UI copy is Spanish (source of truth); English secondary.

## Decisions captured during brainstorming

1. **Both modes, user-selectable** via a top toggle — not one or the other.
2. **Grupo = consensus view**, not a per-person avatar grid. Reason: the grid becomes a wall
   of initials at 20+ players. Consensus aggregates and adds a social hook ("you're the only
   rebel"). Per-person detail is served by 1 vs 1 mode instead.
3. **Privacy: per-round after lock.** A match's picks become visible to *other* players only
   once that match's round is locked (group-stage deadline for group matches; per-round lock
   for knockouts). Before lock, only your own pick is ever shown. Prevents copying.
4. **Rival list = everyone in the pool.** Single shared pool (`pool_id = 1`); no sub-groups.
5. **Head-to-head points tally** ("Vas ganando 12–9") is an **optional add-on**, not core.
   Diff/agreement counts are core; the points tally is built only if it stays simple.

## Architecture

### Backend — two read-only endpoints, server-side privacy gating

All visibility filtering happens on the server. The client never receives a pick it isn't
allowed to see, so there is no way to scrape unlocked predictions from the network response.

| Endpoint | Returns |
|----------|---------|
| `GET /api/compare/h2h?vs={userId}` | Per match: your pick, the rival's pick **only if that match's round is locked** (else `null`), actual score, `played`, and an `agree`/`differ`/`hidden` classification. Plus an optional head-to-head tally (see below). |
| `GET /api/compare/group` | Per match whose round is locked: a distribution `[{score, count}]` over all players' predictions, your pick, `majority` flag (your pick == the modal/most-picked score), `rebel` flag (you are the *only* player with your exact score). Matches whose round is not yet locked are returned in a `hidden` state with no distribution. |
| Rival picker list | **Reuse `GET /api/ranking`** — it already returns every player in the pool (`userId`, `displayName`). No new endpoint. |

**Rejected alternative:** a single "fat" endpoint returning every player's raw picks with the
diff/consensus computed client-side. Rejected because it would ship unlocked picks to the
browser, defeating the privacy gate.

### Privacy gate — shared lock helper

`BracketService` already computes whether a group / knockout round is locked (group-stage
deadline; per-round knockout lock). Extract that check into a single shared helper
(e.g. `MatchVisibility.isRevealable(match)` or a method on `LockClock`) so "is this match's
picks revealable to others" lives in exactly one place and both compare endpoints reuse it.
Your *own* pick is always revealable to yourself regardless of lock.

### Head-to-head points tally (optional)

"Vas ganando 12–9" = sum of points you vs. the rival earned on already-**played** matches in
the compared set, using the existing scoring rule (`V005__quinielas_bets_scoring.sql`). This
is the one piece that needs the per-match scoring formula replicated in a query (the
`quiniela.points` column is a per-player total, not per-match). Implement only if it stays
simple; otherwise ship without it and the 1 vs 1 summary shows just `Coinciden N · Difieren M`.

### Frontend

- `/compare` server component replaces the placeholder, rendered like `app/ranking/page.tsx`.
- **Mode + rival in the URL**: `/compare?mode=group` (default) and `/compare?mode=h2h&vs=42`.
  Refresh-safe and shareable; no client-only state for the primary view.
- Components:
  - `CompareModeToggle` — segmented `1 vs 1` / `Grupo`, swaps the `mode` query param.
  - `H2HCompare` — rival chip/picker + four-column diff table (differences first, agreements
    in a collapsible "Coinciden (N)" section).
  - `GroupConsensus` — per-match consensus bars with majority/rebel tags.
  - `RivalPicker` — selects from the pool (ranking list); writes `vs` to the URL.
- **Deep link**: tapping a row/avatar in Grupo navigates to `?mode=h2h&vs={thatUser}`.
- API libs in `frontend/lib/api/compare.ts` (`getH2H(vs)`, `getGroupConsensus()`), following
  the existing `lib/api/*.ts` pattern; types mirror the backend DTOs.

### States

- **Before group-stage lock**: whole screen shows a "los picks se revelan al cerrar la
  quiniela" message, reusing the current placeholder styling.
- **No rival selected** (1 vs 1): prompt to pick a rival.
- **Empty pool / only you**: friendly empty state.
- **Partially locked** (knockouts in progress): locked rounds reveal, unlocked rounds show `—`.

### i18n

New `compare.*` keys in `messages/es-CO.json` (source) and `messages/en.json`: titles, toggle
labels, column headers, majority/rebel tags, summary string, empty/locked messages. Remove or
repurpose the `placeholder.compareHeadline` / `compareHelp` keys once the real screen ships.

## Testing

Backend integration tests are the priority:

- Picks of other players are **hidden before** their round's lock and **revealed after** (both
  group and knockout rounds).
- Your own pick is always visible to you.
- Consensus distribution counts aggregate correctly; `majority` / `rebel` flags are right.
- H2H diff/agreement classification is correct; tally (if built) matches the scoring rule.

Frontend: component tests for `CompareModeToggle` (URL swap), `H2HCompare` (diff ordering,
agreements collapse), `GroupConsensus` (bar rendering, tags). Optional e2e smoke: load
`/compare`, toggle modes, axe scan.

## Out of scope

- Sub-groups / private leagues (single pool only).
- Notifications or "someone picked differently" alerts.
- Comparing across tournaments (schema is multi-tournament but only WC2026 ships).

## Build order (for the plan)

1. Shared privacy/lock helper extracted from `BracketService`.
2. `GET /api/compare/group` + consensus aggregation + tests.
3. `GET /api/compare/h2h` + diff classification + tests (tally last, optional).
4. Frontend `lib/api/compare.ts` + types.
5. `/compare` page + toggle + `GroupConsensus` + `H2HCompare` + states.
6. i18n keys; retire placeholder copy.
7. Frontend component tests; optional e2e.

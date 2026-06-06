# Home Adaptive Phase UX — Design

> **Status:** Approved design (brainstormed 2026-06-06). Next step: implementation plan.
> **Area:** `frontend` (read-only against existing backend APIs).

## Problem

The home page (`frontend/app/home/page.tsx`) always leads with the 12 group cards plus a fixed countdown hero and stat strip, regardless of where the tournament is. Once the group stage closes and knockouts begin, the group grid still dominates the screen while the *currently relevant* round (the one you should be filling, or the live results moving your score) is buried or absent. We want the home to **surface the currently-relevant phase by default**, with past phases reachable for history.

## Goal

Turn the home into an **adaptive dashboard** that re-focuses as the tournament progresses (group fill → group live → each knockout round → champion), while keeping every phase reachable. No new tournament data is required — everything is derived from existing APIs. **No live in-play scores** (our football-data subscription only provides final results after a match ends).

### Non-goals

- Live / in-progress scores (not available from the data provider).
- Rank-change arrows (the ranking API's `delta` is null in v1).
- Knockout per-round multiplier configuration (tracked separately in BACKLOG.md). The recap simply renders whatever `pointsEarned` the backend already computes.
- Backend changes. This is a frontend restructure over existing endpoints.

## The four blocks

The home renders the same vertical structure in every phase; only the content adapts:

1. **Focus card** — the one prominent, phase-dependent call to attention.
2. **Phase rail** — horizontal chips for all phases (done ✓ / open ● / locked 🔒), tap to drill in.
3. **Tu puesto strip** — your rank + points, with a link to the full Tabla.
4. **Resultados & próximos** — recent final results (with the points you earned on each) + the next fixture. Before any match is played, this block instead shows pot + countdown.

```
┌─────────────────────────────┐
│ TopBar (name, role badge)   │
├─────────────────────────────┤
│ FOCUS CARD  (adaptive)      │  ← fill / standing / champion
├─────────────────────────────┤
│ PHASE RAIL  ✓ ● 🔒 🔒 🔒 🔒 │  ← tap a chip → that phase
├─────────────────────────────┤
│ TU PUESTO   🥈 #3 · 88 pts →│  ← link to /ranking
├─────────────────────────────┤
│ RESULTADOS & PRÓXIMOS       │  ← finals +pts, then next fixture
│   Final ARG 3–1 ITA   +10   │     (pre-kickoff: pot + countdown)
│   Final BRA 2–0 GHA   +6    │
│   Hoy 20:00 FRA – JPN   ·   │
├─────────────────────────────┤
│ Action row: Invitar         │
├─────────────────────────────┤
│ BottomNav                   │
└─────────────────────────────┘
```

## Phase / focus-state computation

A single pure function derives the home state from the data. Priority order (first match wins):

| # | State | Condition | Focus card |
|---|-------|-----------|------------|
| 1 | `FILL_GROUP` | group stage open: `groupStageDeadline == null || now < groupStageDeadline` | "Llena tus grupos · {filled}/{total} · cierra en {countdown}" + CTA → `/groups` |
| 2 | `FILL_KNOCKOUT` | some knockout round is `unlocked && !locked` (open to bet). Pick the earliest such round by `sequence` = the current round. | "{round.name} · llena tu llave · {filled}/{total} · cierra en {countdown}" + CTA → `/knockout/{code}` |
| 3 | `LIVE` | group locked, no round open to fill, and matches remain unplayed | "{Fase de grupos\|Eliminatorias} · En juego · tu puesto #{rank} · {next round} abre en {x}" (no CTA) |
| 4 | `CHAMPION` | every match `played` (tournament finished) | "🏆 {ordinal} puesto · {points} pts · ganaste ${payout}" (payout only if prize-eligible) |

Notes:
- "Open to bet" for a knockout round means `unlocked` (group stage closed **and** the round's matches have teams assigned) **and** `!locked` (knockout deadline not passed). This already exists on `BracketView.knockouts[]`.
- In `FILL_GROUP`, if `filled == total` the card softens: kicker "Listo ✓", headline "Grupos completos", sub "Puedes ajustar hasta {deadline}", CTA secondary. Same softening for a fully-filled knockout round.
- `now` is evaluated server-side in the RSC (the page is an async server component), consistent with how the bracket lock is read today.

The function returns a discriminated union plus the derived data the four blocks need (rail items, standing, recap rows), so the page component is pure presentation.

## Block details

### Focus card (`components/lobby/FocusCard.tsx`)

Renders the dark poster with the optional ghost numeral (days-to-deadline) or 🏆. Props = the `HomeFocus` variant from the state function. Each variant supplies: kicker, headline, sub, optional CTA `{ label, href }`, ghost. Reuses the existing hero visual language (`bg-[var(--color-bg-ink)]`, gold kicker, red ghost).

### Phase rail (`components/lobby/PhaseRail.tsx`)

Horizontal, wrap/scroll row of chips, one per round in `bracket` order (`Grupos`, `16vos`/R32, `8vos`/R16, `4tos`/QF, `Semis`/SF, `Final`; `THIRD_PLACE` rendered as a compact `3er` chip adjacent to `Final`). Each chip:

- **done ✓** (green) — round fully played / in the past.
- **open ●** (gold) — the current fill target (matches the focus state).
- **locked 🔒** (muted) — not yet open.
- Label = round short name; tap target = `/knockout/{code}` for knockout rounds, `/groups` for the group chip.

Chip status is derived from the same bracket data (group: `locked`; knockout: `unlocked`/`locked`/`filled==total`).

### Tu puesto strip (`components/lobby/StandingStrip.tsx`)

Single row: `{medal?} #{rank} · {points} pts` on the left, `Ver tabla →` (link to `/ranking`) on the right. Data = `getRanking().entries.find(e => e.isYou)`. Medal emoji only for ranks 1–3 **and** only once someone has scored (reuse the spirit of `lib/ranking-payouts.ts` — no medal pre-scoring). No other players' names are shown (deliberately — they're too long for the row).

### Resultados & próximos (`components/lobby/ResultsRecap.tsx`)

Two parts, from the matches API (`getMatches()` → `MatchesView`, which is pre-split into `{ serverTime, past, today, upcoming }`):

- **Recent finals:** the last 2 `played` matches from `past` (by kickoff descending), each as `{flag} {code} {s1}–{s2} {code} {flag}` with the caller's `pointsEarned` shown as `+{n}` (green) when they bet on it. `pointsEarned` is already computed by the backend (`MatchView.pointsEarned`) — no client-side scoring.
- **Next fixture:** the earliest upcoming match (`today` then `upcoming`), shown with its kickoff time formatted in `me.timezone` (reuse `formatMatchDateTime`). `serverTime` is the authoritative `now` for any client-side comparison.
- **Pre-kickoff fallback:** when no match is played yet, this block instead shows pot (`summary.pool.potCents`) + pana count + countdown to kickoff (replacing the recap rows). This preserves the pot visibility the current home gives.

## Data sources

The page fetches in parallel (all existing endpoints):

| Source | Used for |
|--------|----------|
| `getMyBracket()` | focus state, rail chip states, deadlines, filled/total |
| `getRanking()` | Tu puesto strip (rank, points, isYou) |
| `getMatches()` | recent finals + next fixture (+ `pointsEarned`) |
| `getPublicSummary()` | tournament start date, pot, pana count (pre-kickoff block) |
| `getMe()` | timezone, role (TopBar) |

A future optimization could collapse these into one `/home` endpoint, but v1 reuses what exists.

## Components & files

**New**
- `lib/home-phase.ts` — pure `computeHomeState(bracket, ranking, matches, summary, now, tz)` → `{ focus, railItems, standing, recap }`. Pure + unit-tested (mirrors the repo's `ranking-payouts.ts` / `paul-feedback.ts` pattern).
- `components/lobby/FocusCard.tsx`
- `components/lobby/PhaseRail.tsx`
- `components/lobby/StandingStrip.tsx`
- `components/lobby/ResultsRecap.tsx`
- `app/groups/page.tsx` — group index: the 12 `GroupCard`s (reuses the existing component) that the home used to render inline. Destination for the Grupos chip / `FILL_GROUP` CTA.

**Modified**
- `app/home/page.tsx` — fetch the four sources, call `computeHomeState`, render the four blocks + invite action. Removes the inline group grid, the knockout list, the old countdown hero and stat strip (their content folds into the focus card / standing / recap).
- `messages/es-CO.json` + `messages/en.json` — new keys (see below). UI copy stays Spanish; en kept in parallel.

**Reused unchanged**
- `GroupCard`, `TopBar`, `BottomNav`, `InviteFriendsButton`, `formatMatchDateTime`, `tournament-format` helpers.

**Note on `PaulFillAllButton`:** "Paul fills it all" is a group-fill bulk action. It moves off the home dashboard onto `/groups` (next to the group cards it fills), where it's contextually correct. (It is unaffected by the recent Paul-feedback work beyond living in a new location.)

## i18n keys (new, under a `home` namespace)

Focus card per state (`fillGroupKicker`, `fillGroupHeadline`, `fillKnockoutKicker`, `liveHeadline`, `championHeadline`, …), rail labels (reuse round names where possible), `tuPuesto`, `verTabla`, `resultadosProximos`, `verPartidos`, `final` tag, `proximo`, and the pre-kickoff `pozo`/countdown line. Bilingual es-CO + en.

## States & edge cases

- **New user, pre-kickoff, 0 bets:** `FILL_GROUP`, "0/72", CTA "Empezar →". Standing: no score yet → no medal, show "#— · 0 pts" or "Aún sin puntos". Recap: pre-kickoff fallback (pot + countdown).
- **All tied at 0 (tournament not started):** standing shows your row without a medal (consistent with `ranking-payouts`).
- **Knockout round unlocked but teams not yet assigned:** already excluded by `unlocked` logic → stays 🔒.
- **`THIRD_PLACE`:** compact `3er` chip; not a separate focus state (folds into the knockout flow / CHAMPION).
- **Champion with no payout (out of prize positions):** drop the "ganaste $X" clause.

## Testing

- **`lib/home-phase.ts`** — Vitest unit tests for each state (FILL_GROUP open/full, FILL_KNOCKOUT selecting the earliest open round, LIVE, CHAMPION) and edge cases (no bets, all-zero ranking, pre-kickoff recap fallback). This is the bulk of the coverage — the rule lives here.
- **Components** — render tests for `FocusCard` (each variant), `PhaseRail` (chip states + tap hrefs), `StandingStrip` (medal gating), `ResultsRecap` (finals vs pre-kickoff fallback).
- **E2E** — extend the smoke test to assert the home renders the focus card + rail in the current (pre-kickoff) phase.

## Out of scope / future

- One consolidated `/home` API endpoint (perf).
- Live scores (provider limitation).
- Per-round knockout multipliers (separate BACKLOG item) — the recap/standing already reflect whatever the backend scores.
- Rank delta arrows (await the ranking snapshot table).

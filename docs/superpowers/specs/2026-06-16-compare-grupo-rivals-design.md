# Compare GRUPO — date organization + rival-aware drill-down

**Date:** 2026-06-16
**Status:** Approved design, pre-implementation
**Scope:** `/compare` — both tabs. The GRUPO ("group consensus") tab gets the date
organization *and* the rival-aware drill-down. The "1 VS 1" (H2H) tab gets the date
organization only (it is already a named 1v1 view, so the rivals-above/drill-down work
does not apply to it).

## Problem

The GRUPO tab shows, per match, an anonymized distribution of how everyone in the
pool predicted the scoreline (bars + counts + `CON LA MAYORÍA` / `REBELDE` badges).
Two gaps, surfaced now that the tournament is live:

1. **No date/phase organization.** The view groups matches by stage only. It lacks
   the Past / Hoy / Próximos partition that `/matches` already offers, so it's hard
   to find today's games or scan what's coming.
2. **It can't answer the ranking question.** The distribution tells you whether
   you're with the crowd, but not whether the people *ranked above you* share your
   pick. With games being scored, the user wants to judge their real chance to climb
   by seeing how their chase-list bet on each game.

## Goals

- Mirror the `/matches` Date↔Stage organization on the GRUPO tab, defaulting to
  **Date mode → Hoy (Today)**.
- Highlight, per scoreline, how many **rivals ranked above the caller** chose it.
- Let the caller drill into **named picks on demand** — both "who picked this
  scoreline" and "which of my rivals-above picked it".
- Preserve the existing privacy gate: no pick is ever revealed before a match locks.

## Non-goals (YAGNI)

- No rivals-above highlight or named drill-down on the H2H tab — H2H already shows the
  one rival's exact picks. H2H only gains the date organization.
- No new ranking/snapshot tables; reuse the current `quiniela.points` ordering.
- No reveal of any pick for an unrevealed (not-yet-locked, not-played) match.
- No changes to scoring rules.

## Definitions

- **Revealed match:** `played = true` OR its deadline has passed
  (`LockClock.isMatchRevealable()`). Only revealed matches appear in GRUPO. This is
  the existing behavior and the privacy boundary.
- **Rival above me:** a pool member (incl. bots) with **strictly more points** than
  the caller (`quiniela.points` strictly greater). Ties are *not* "above." Admins are
  excluded, consistent with `/api/ranking`.

## Architecture decision: lazy per-match drill-down (Approach A)

The main consensus payload stays light. It carries only the aggregate distribution
plus small **rivals-above counts** so the "↑ N" markers render without a second call.
Named picks for a match are fetched **on demand** from a new endpoint when the user
expands a bar or marker.

Rejected alternative (Approach B): embed every player's named pick for every match in
the main payload. Rejected because payload grows as `matches × players`, and it ships
everyone's names even for matches nobody expands.

---

## Part 1 — Date organization (both tabs)

Both `/compare` tabs get the `/matches` Date↔Stage toggle, defaulting to
**Date → Hoy**. GRUPO is detailed first; H2H follows the same pattern.

### 1a. GRUPO — Backend: `GET /api/compare/group`

Change the response from a flat `matches` list to the same partitioned shape
`/api/matches` uses, so the frontend can reuse identical date logic.

Current:
```java
public record GroupConsensusView(List<MatchConsensus> matches) {}
```

New:
```java
public record GroupConsensusView(
    Instant serverTime,            // for client-side live/today sync, like MatchesView
    List<MatchConsensus> past,     // kickoff < start_of_today (caller TZ), reverse-chronological
    List<MatchConsensus> today,    // [start_of_today, start_of_tomorrow), chronological
    List<MatchConsensus> upcoming) // kickoff >= start_of_tomorrow, chronological
{}
```

- Partition in the caller's timezone (`users.timezone`, fallback `America/Bogota`) —
  reuse the partition helper that `MatchesService` already applies.
- Only revealed matches are included (unchanged). "Próximos" therefore means
  locked-but-not-yet-kicked-off games (later group games, locked knockouts).
- Each list still ordered as in `/matches`: `past` reverse-chronological, `today` and
  `upcoming` chronological.

### 1b. GRUPO — Frontend: `frontend/components/compare/GroupConsensus.tsx`

- Add a Date↔Stage toggle identical to `MatchTabs.tsx` (`FECHA` / `FASE`).
- **Date mode (default):** tabs `Pasados | Hoy | Próximos`, default **Hoy**. Render
  each bucket's matches as the existing consensus cards. Mirror `/matches`' fallback
  when "Hoy" is empty (match whatever `MatchTabs` does — verify during implementation).
- **Stage mode:** unchanged — feed `past+today+upcoming` merged into the existing
  `groupMatchesByStage()` + `StageSection` collapsible-by-phase rendering.
- Update `frontend/lib/api/compare.ts` `GroupConsensusView` type to the partitioned
  shape with `serverTime`.

### 1c. H2H — Backend: `GET /api/compare/h2h` (rival param)

Partition the H2H result the same way. Current `H2HView` carries a flat
`matches: H2HMatch[]`; change it to:
```java
public record H2HView(
    Long rivalUserId,
    String rivalDisplayName,
    Integer myPoints,
    Integer rivalPoints,
    int agreeCount,
    int differCount,
    Instant serverTime,         // NEW
    List<H2HMatch> past,        // was: List<H2HMatch> matches
    List<H2HMatch> today,
    List<H2HMatch> upcoming) {}
```
- Same caller-timezone partition + only-revealed-matches rules as GRUPO.
- `agreeCount` / `differCount` stay totals across all revealed matches (the summary
  line is global, not per-bucket).
- `H2HMatch` is unchanged (it already carries `kickoffAt`, `roundCode`, `state`).

### 1d. H2H — Frontend: `frontend/components/compare/H2HCompare.tsx`

- Add the same Date↔Stage toggle, defaulting to **Date → Hoy**.
- **Date mode (default):** `Pasados | Hoy | Próximos` tabs. Within each bucket, render
  the existing You / Rival / Real table, preserving the current **differ-first**
  ordering (differences highlighted, agreements dimmed) so the rival's disagreements
  are what you see first.
- **Stage mode:** unchanged — the existing `groupMatchesByStage()` + `StageSection`
  per-phase tables.
- Update `frontend/lib/api/compare.ts` `H2HView` type to the partitioned shape.

---

## Part 2 — Rivals-above highlight (GRUPO only — backend + frontend)

### Backend: `CompareService.getGroupConsensus(userId)`

1. Compute the caller's points (from `quiniela` for `pool_id = 1`).
2. Identify rivals-above = pool members with strictly greater points (exclude admins;
   include bots). Capture their `quiniela_id` set and the total count
   (`rivalsAboveTotal`, constant across matches, returned per match for the summary).
3. Per match, count how many rivals-above actually have a bet (`rivalsAbovePicked`),
   and per scoreline how many of them picked it (`rivalsAboveCount`).

Extend the DTOs:
```java
public record ScoreCount(
    int scoreT1,
    int scoreT2,
    int count,
    int rivalsAboveCount) {}   // NEW: of `count`, how many are rivals ranked above me

public record MatchConsensus(
    Long matchId,
    String roundCode,
    String team1Code, String team1Flag,
    String team2Code, String team2Flag,
    String kickoffAt,
    Integer actualScoreT1, Integer actualScoreT2,
    boolean played,
    boolean revealed,
    Integer myScoreT1, Integer myScoreT2,
    List<ScoreCount> distribution,
    int totalPicks,
    boolean majority,
    boolean rebel,
    int rivalsAboveTotal,       // NEW: chase-list size = rivals ranked above me (constant across matches)
    int rivalsAbovePicked) {}   // NEW: of those, how many actually have a bet on THIS match
```

Note: `distribution` is the existing top-4-by-count. A rival-above could sit in a
scoreline outside the top 4. The match-level `rivalsAboveTotal` still reflects the
true total; the per-bar `rivalsAboveCount` only annotates visible bars. The full
breakdown (including off-top-4 scorelines) is available via the drill-down endpoint
(Part 3). Acceptable: the card gives the headline, the drill-down gives the complete
picture.

### Frontend: consensus card

- Per bar, when `rivalsAboveCount > 0`, render a subtle `↑ N` affordance (tappable —
  see Part 3).
- Match-level summary line when `rivalsAboveTotal > 0`, e.g.
  `5 por encima de ti` and, when the caller's own scoreline bar is visible, how many
  of the rivals-above share it (`N contigo`). Do NOT present a `contigo + distinto =
  total` arithmetic: `rivalsAbovePicked` can be less than `rivalsAboveTotal` (a rival
  may have no bet on this match), and rivals on off-top-4 scorelines aren't on any
  visible bar. The drill-down (Part 3) is the complete, exact breakdown.
- When `rivalsAboveTotal == 0` (caller is 1st, or tied at top), omit the rival UI
  entirely.

---

## Part 3 — Names-on-demand drill-down (new endpoint + frontend)

### Backend: new `GET /api/compare/match/{matchId}/picks`

- JWT-authenticated, same as the rest of `CompareController`.
- **Guard:** if the match is not revealed, return `403` (or empty per existing
  convention) — the privacy gate is enforced server-side, not just hidden in the UI.
- Response:
```java
public record MatchPick(
    String displayName,
    int rank,             // pool rank from the same RANK() ordering as /api/ranking
    int points,
    boolean isYou,
    boolean isBot,
    boolean isAboveMe,    // strictly more points than caller
    int scoreT1,
    int scoreT2,
    Integer pointsEarned) // points this pick earned for this match; null if unplayed
{}

public record MatchPicksView(
    Long matchId,
    Integer actualScoreT1,
    Integer actualScoreT2,
    boolean played,
    List<MatchPick> picks) // ordered by rank ASC, then displayName ASC
{}
```
- `pointsEarned` per pick for played matches reuses the existing per-bet scoring
  (`CompareService.scoreMatchForBet()` / `score_match_for_bet()`), so the drill-down
  can show ✓/✗ and the points each rival gained.

### Frontend: `frontend/lib/api/compare.ts`

```typescript
export async function getMatchPicks(matchId: number): Promise<MatchPicksView> {
  return api<MatchPicksView>(`/api/compare/match/${matchId}/picks`);
}
```
Fetched lazily on first expand; cache per match for the session to avoid refetching.

### Frontend: drill-down interactions

- **Tap a count bar (e.g. the `13`)** → expand the full named list filtered to that
  scoreline.
- **Tap the rivals `↑ N` marker** → expand the list filtered to `isAboveMe === true`
  (optionally also to that bar's scoreline when the marker sits on a bar).
- For played matches, show each pick's ✓/✗ and `pointsEarned`.
- Names render inside the existing card (accordion/expand), keeping the
  aggregate-first feel.

---

## Data model touchpoints (no schema changes)

- `quiniela.points` — rival-above ordering and rank (same source as `/api/ranking`).
- `bet (quiniela_id, match_id, score_t1, score_t2)` — individual picks.
- `match.kickoff_at`, `match.played`, `round.code` — date/phase organization.
- `users.timezone` — date partition (fallback `America/Bogota`).
- `users.role <> 'admin'`, `users.is_bot` — eligibility / bot flag.

All reads; no migrations.

## Testing

- **Backend unit (Surefire):**
  - rivals-above set = strictly-greater points; ties excluded; admins excluded; bots
    included.
  - `rivalsAboveCount` per scoreline sums correctly and never exceeds `count`.
  - `rivalsAboveTotal == 0` when caller is 1st or tied-for-1st.
  - date partition matches `/api/matches` boundaries for a given timezone.
- **Backend integration (Failsafe):**
  - `/api/compare/group` returns partitioned lists with rival counts for a seeded pool.
  - `/api/compare/match/{id}/picks` returns ranked named picks for a revealed match;
    returns 403/empty for an unrevealed match.
  - `/api/compare/h2h` returns partitioned past/today/upcoming for a chosen rival,
    with `agreeCount`/`differCount` as global totals.
- **Frontend (Vitest + MSW):**
  - GRUPO + H2H toggles default to Date → Hoy; switching to Fase renders stage sections.
  - `↑ N` marker shows only when `rivalsAboveCount > 0`; hidden when caller is 1st.
  - tapping a bar / marker triggers the picks fetch and renders the filtered list.
  - H2H date mode preserves differ-first ordering within each bucket.
- **E2E (Playwright):** smoke — load `/compare`, GRUPO tab, expand a bar, see names;
  axe scan stays clean.

## Rollout

- UI copy stays Spanish.
- No migration, no infra change; ships through the existing CI build → Trivy →
  Artifact Registry → `gcloud run deploy` on `master`.

# Quiniela Panas — MVP UI Design

**Status:** Approved for implementation planning
**Date:** 2026-05-25
**Deadline:** 2026-06-11 (World Cup kickoff)
**Stack:** Next.js 16 + TypeScript + Tailwind on Cloud Run, calling Spring Boot 4 API

## Goal

Ship a phone-first web app by 2026-06-11 that lets a small group of friends and family submit a quiniela (104 score predictions: 72 group-stage + 32 knockout matches), watch standings update as matches resolve, and compare picks against each other. Replaces the 2014 Rails app. Built for ~20-50 players in a single pool.

## Audience and constraints

- **Players:** friends and family. Spanish-speaking primary, English-speaking minority. Mixed tech literacy — power users alongside people who'd rather use Excel.
- **Pool size:** single pool for v1, ~20-50 players, real-money payout settled offline.
- **Primary device:** phone. Laptop is a fallback layout.
- **Deadline:** hard. Group-stage bets lock at kickoff (single deadline for the full group stage, per legacy rules). Knockout-round bets unlock after group stage finishes.

## Scope

### In scope for v1 (must ship by 2026-06-11)

1. Google sign-in via Auth.js
2. Invite-only access via `/join/:code` route
3. Lobby (home) showing group + knockout cards with completion state and countdown
4. Group drill-in to fill 6 match-score predictions per group
5. Knockout drill-in (6 sub-cards: R32, R16, QF, SF, 3°, Final), unlocks after group stage
6. Score input pattern: tap digit → slide-up numpad with recent-presets above
7. Ask Paul: inline 🐙 icon per match (one-shot suggestion) + "Que Paul llene todo" CTA
8. Bracket lock at kickoff — read-only after deadline
9. Ranking screen: overall + per-jornada views, trend arrows, "you" row highlighted
10. Schedule + results screen: tabs Pasados / Hoy / Próximos, live-match indicator, user's pick rendered under each fixture with ✓/✗
11. Compare picks vs another player: difference-first view, player picker, four-column layout (match / you / them / actual)
12. Admin results-entry screen, gated by `is_admin` flag
13. XLSX download/upload as an escape hatch for bracket fill
14. i18n scaffolding from day one (Spanish default, English secondary, switcher in header/profile menu)
15. Multi-tournament-ready schema and routes (only World Cup 2026 instance ships)

### Deferred to v1.1

- Google Sheets sync (view, write, or bidirectional)
- Tiebreaker prediction (v1 ties simply share rank — money split)
- Per-jornada email/push digest
- Multiple concurrent pools
- Public read-only share links
- Paul-as-a-player (auto-generated bracket competing in standings) — Paul ships only as an assistant in v1

### Out of scope

- Mobile native apps
- Payment integration (payouts stay offline)
- Match-level commentary, news feeds, or roster details beyond names + flags
- Live ranking animations (refresh-on-load is fine for v1)

## Information architecture

```
quiniela.dpdns.org
├── /                          → landing (signed-out) | redirect to /home (signed-in)
├── /join/:code                → invite landing — Google sign-in CTA, accepts code
├── /home                      → lobby (Mi Quiniela)
│   ├── /group/:groupId        → group drill-in (e.g. /group/B)
│   └── /knockout/:roundId     → knockout drill-in (R32, R16, QF, SF, P3, FINAL)
├── /ranking                   → Tabla
├── /matches                   → Partidos (schedule + results, tabs Pasados/Hoy/Próximos)
├── /compare/:opponent?        → vs (compare picks, optional opponent handle in URL)
├── /admin/results             → results entry, hidden behind is_admin
├── /api/auth/*                → Auth.js routes
├── /api/bracket/import        → XLSX upload endpoint
└── /api/bracket/export        → XLSX download endpoint
```

**Bottom nav (signed-in, all four tabs visible on every primary screen):**
`Mi Quiniela · Tabla · Partidos · vs`

## Key user flows

### First-time invite

`https://quiniela.dpdns.org/join/abc123` →
"Te invitaron a Quiniela Panas. Inicia sesión con Google para unirte." →
Google OAuth → user row created, `invite_code` consumed (single-use or N-use TBD in plan) → redirect to `/home` empty lobby.

### Fill bracket (the bulk of v1's work)

`/home` (lobby) → tap **Grupo B** card → `/group/B` drill-in →
For each of 6 matches: tap a score digit → slide-up numpad appears with 6 recent-preset chips above the keypad (`1-0`, `2-1`, `0-0`, etc.) → tap preset or punch in digits + ✓ → digit fills, autosave fires → repeat for second digit / next match →
Either: "Guardar y volver" → back to lobby with Grupo B at 6/6, or tap 🐙 inline → Paul's suggestion expands inline with "Aceptar" / "Ignorar".

Same pattern for knockout sub-cards once they unlock.

### Bulk fill (XLSX escape hatch)

`/home` → "⬇ Descargar plantilla" → server-rendered XLSX with the 104 rows pre-filled with team names + empty score cells → user edits on laptop → "⬆ Subir plantilla" → file picker → server parses, validates (team-name match, score is integer ≥ 0, all 104 rows present), responds with `{ saved: 104, errors: [] }` or per-row error list → user sees toast and lobby updates.

### Match day (spectator mode)

Open app → bottom nav → **Partidos** → "Hoy" tab → see today's fixtures with live scores → each fixture shows the user's pick + hit/miss → switch to **Tabla** to see the leaderboard → switch to **vs** to compare with someone in particular.

### Compare picks

`/compare` (no opponent yet) → tap player picker → choose Andrés → URL becomes `/compare/andres` (handle, not email — opponent identifier is a short user slug derived from display name + tiebreak suffix at sign-up) → default view "Diferencias" lists only matches where picks differ, sorted with finished matches first → user can switch to "Todo" tab to see every match including matches where they agreed.

## Screen inventory

| Screen | Route | Purpose | Key elements |
|--------|-------|---------|--------------|
| Invite landing | `/join/:code` | First-time entry | Pool name, "Te invitaron a…", Google sign-in button |
| Lobby | `/home` | Pick screen entry + progress | Countdown chip (T-Nd HH:MM), 12 group cards with progress bars, 6 knockout sub-cards (locked until group stage ends), "🐙 Paul llena todo" CTA, "Descargar/Subir XLSX" secondary actions |
| Group drill-in | `/group/:groupId` | Fill 6 match scores | Header with back arrow, group label, 6 match rows (team-team + score boxes + 🐙 icon), tap-to-numpad behavior, "Guardar y volver" + "Siguiente: Grupo C →" |
| Knockout drill-in | `/knockout/:roundId` | Fill knockout round picks | Same pattern as group drill-in; number of matches varies by round |
| Ranking | `/ranking` | Standings | Tabs General / Por jornada, rank pos with medal colors for top 3, trend arrow (▲/▼/─), points, "you" row highlighted |
| Schedule + results | `/matches` | Match calendar | Tabs Pasados / Hoy / Próximos, day labels, match rows with kick-off time, live indicator (pulsing dot), score or "— : —", user's pick + ✓/✗ |
| Compare | `/compare/:opponent?` | Head-to-head picks | Player picker, point gap chip, tabs Diferencias / Todo, 4-column table (match / you / them / actual) |
| Admin results | `/admin/results` | Enter actual match results | Same chrome as schedule, score fields editable, save per match, fires the existing PL/pgSQL scoring trigger |

## Visual design system

**Style: Broadcast / Sportsbook.** Dark navy background with neon-cyan accents — feels like a TV broadcast HUD. Monospace numbers throughout (scores, ranking, countdown). Uppercase chrome for nav and tab labels. Tailwind tokens to be defined in implementation plan.

**Palette (initial):**

| Token | Value | Use |
|-------|-------|-----|
| `bg.primary` | `#0a0e1a` | App background |
| `bg.elevated` | `#0f172a` | Cards, rows |
| `bg.header` | `#000814` | Top bar, bottom nav |
| `border.subtle` | `#1e293b` | Row dividers |
| `border.accent` | `#00d4ff` | Active tab underline, focus, primary CTA |
| `text.primary` | `#e2e8f0` | Body text |
| `text.muted` | `#64748b` | Meta, labels |
| `accent.cyan` | `#00d4ff` | Primary accent, "you" highlights, points |
| `accent.purple` | `#a855f7` | Compare-vs-opponent column, Paul brand |
| `state.good` | `#22c55e` | Hit indicator, live dot, completed groups |
| `state.bad` | `#ef4444` | Miss indicator, rank-down arrow |
| `state.warning` | `#fbbf24` | Rank #1 gold |

**Typography:**

- UI: Inter (system fallback)
- Numbers / mono: `ui-monospace` system stack
- Sizes: phone-first 0.78em base, uppercase labels at 0.75em with 0.5px letter-spacing

**Chrome conventions:**

- Top bar: pool/screen title in cyan uppercase, contextual meta in muted monospace right-aligned, 2px cyan bottom border
- Tabs: equal-width, uppercase, active tab gets cyan text + 2px underline
- Bottom nav: four icons-or-labels (Mi Quiniela · Tabla · Partidos · vs), 1px top border, active = cyan
- Cards: dark elevated background, 3px left border (gray idle, cyan when "done" / active)
- Score chips and ranking numbers always monospace

## Internationalization

- Library: `next-intl` (or equivalent) — to be decided in implementation plan
- Default locale: `es-CO`. Secondary: `en`. Structure supports adding more without code changes.
- All user-facing strings extracted to message files from day one — no hardcoded Spanish.
- Switcher placement: profile menu in the top bar (not bottom nav — too rare a change to deserve a tab slot).
- Locale-sensitive formatting: dates and times via `Intl.DateTimeFormat`, numbers via `Intl.NumberFormat`. Scores stay as plain integers.
- Team names: stored once per `teams` row, translated at render via message keys `team.ESP`, `team.ITA`, etc. Flags are emojis, not images.

## Accessibility

- Target WCAG 2 AA (matches the Playwright + axe-core baseline already in `frontend-ci.yml`).
- Keyboard navigation must work on the desktop layout — numpad opens on tap and on Enter; preset chips are buttons.
- Color is never the only signal: hit/miss is "✓"/"✗" + color, live match is "LIVE" text + pulsing dot + color.
- Contrast: cyan accent on navy background measured ≥ 4.5:1 for text use; the value `#00d4ff` on `#0a0e1a` passes.
- Touch targets ≥ 44px square for score digits, preset chips, and bottom-nav icons.
- Screen-reader labels for icon-only controls (back arrow, Paul icon, nav icons).

## Data and state notes that affect UI

- **Picks autosave per-tap.** No "Guardar" gate at the bottom of the drill-in. The "Guardar y volver" button is just navigation — the save already happened. Optimistic UI; rollback toast on server error.
- **Bracket lock is enforced server-side** (kickoff timestamp). UI mirrors by switching drill-ins to read-only after `now > deadline`, but the API is the authority.
- **Knockout sub-cards** stay locked in the lobby (`🔒`) until the server confirms group stage matches are all played. Knockout fixtures get filled in by the admin (or upstream sync) after each round resolves; the UI shows winner-of-X / loser-of-Y placeholders before that.
- **Ranking refreshes on screen mount.** No live websocket for v1. The PL/pgSQL trigger keeps `quinielas.points` correct; the API just reads.
- **Compare data fetching** can be heavy (104 rows × 2 players). Default tab is "Diferencias" to reduce payload feel; "Todo" loads on demand.
- **Paul backend** is out of UI scope but the UI assumes: `POST /api/paul/suggest?match_id=…` returns `{ score_t1, score_t2, reasoning }`; `POST /api/paul/fill` returns a full bracket.

## XLSX import/export contract

- Template is a single sheet, 104 rows, columns: `match_id` (hidden), `team_1`, `score_1`, `score_2`, `team_2`, `phase`, `round`, `kickoff_iso`.
- Score cells are integers ≥ 0; blank means "not predicted".
- Server matches rows by `match_id` first, falls back to `team_1` + `team_2` + `kickoff_iso` exact match. Mismatches are returned as per-row errors, no partial-save by default (all-or-nothing for v1).
- Export reflects current state; if user has 18/104 filled, the downloaded sheet has 18 cells populated.

## Open questions to resolve during planning

1. **Invite code single-use vs N-use?** Single-use is safer; N-use is easier for a "share in the WhatsApp" flow. Pick one in the plan.
2. **Locale auto-detection from browser?** Or always default to `es-CO` and let users switch manually?
3. **What does Paul actually use?** GPT-4-ish via Anthropic/OpenAI? Cached suggestions to avoid per-match LLM calls × 50 players? Plan-stage decision.
4. **Knockout fixture population:** is the admin entering "Spain advanced as A1" manually, or pulling from a public results feed? Affects admin UI scope.
5. **Pool name:** "Quiniela Panas" hardcoded or configurable? Affects landing copy.
6. **Avatar source:** Google profile pictures from Auth.js, or initials? Profile pictures need DOMAIN allowlisting in `next.config.ts`.

## Risks

- **Scope vs deadline.** 9 must-have features in 17 days is tight. If anything slips, candidates for late deferral (in priority order): Compare picks → XLSX escape hatch → Admin UI (replace with SQL script). Bracket fill + Ranking + Schedule are non-negotiable.
- **Locking edge case.** A player mid-numpad-tap at the exact kickoff second could lose the score they were entering. Mitigation: server rejects writes past deadline with a clear toast; UI shows "Bloqueado en T-00:00:30" warning for the last minute.
- **Live results latency.** No real-time feed in v1 — admin enters results manually. If admin is slow, ranking lags reality. Acceptable for a friends pool; communicate via WhatsApp.
- **Style B coldness.** Broadcast/sportsbook aesthetic is striking but may feel less warm than a friends-and-family product expects. Mitigation: Spanish copy stays casual ("Que Paul llene todo", "¡Completo!"), avoid finance/poker jargon, lean into emoji where appropriate.

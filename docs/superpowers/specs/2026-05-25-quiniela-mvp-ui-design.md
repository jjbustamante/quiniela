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
- **Money:** USD entry fee, USD prizes. Payments themselves happen off-platform (Zelle / cash / transfer); the app tracks who has paid, the running pot, and the configurable prize split (default 80% / 15% / 5% for the top 3).
- **Roles and invite tree:** three roles, two levels deep. The admin invites `captain`s (closest friends — the first wave). Each captain can invite `player`s (their own friends). Players cannot invite further. Captains are responsible for collecting their sub-group's entry fees and reconciling with the admin offline; the app reflects that responsibility by grouping the payments ledger by captain.
- **Primary device:** phone. Laptop is a fallback layout.
- **Deadline:** hard. Group-stage bets lock at kickoff (single deadline for the full group stage, per legacy rules). Knockout-round bets unlock after group stage finishes.

## Scope

### In scope for v1 (must ship by 2026-06-11)

1. Google sign-in via Auth.js
2. Invite-only access via personal links (`/join/:inviterHandle-:token`) — admin invites captains, captains invite players, players cannot invite further. Self-serve redemption (no admin approval). "Invitar amigos" CTA in the lobby is visible only to admin and captains.
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
13. Admin payments + prize-split screen (`/admin/payments`), gated by `is_admin` flag — mark players paid, view pot total, edit the prize-split percentages (frozen at kickoff)
14. Public pot/prize display on the lobby and ranking screens (pot total + prize-split percentages + estimated payouts next to top 3)
15. XLSX download/upload as an escape hatch for bracket fill
16. i18n scaffolding from day one (Spanish default, English secondary, switcher in header/profile menu)
17. Multi-tournament-ready schema and routes (only World Cup 2026 instance ships)

### Deferred to v1.1

- Google Sheets sync (view, write, or bidirectional)
- Tiebreaker prediction (v1 ties simply share rank — money split)
- Per-jornada email/push digest
- Multiple concurrent pools
- Public read-only share links
- Paul-as-a-player (auto-generated bracket competing in standings) — Paul ships only as an assistant in v1
- In-app payment integration (Stripe, PayPal, etc.) — v1 tracking only, payments stay offline

### Out of scope

- Mobile native apps
- Online payment processing (the app records payments, it does not collect them)
- Receipt generation, invoices, tax forms
- Match-level commentary, news feeds, or roster details beyond names + flags
- Live ranking animations (refresh-on-load is fine for v1)

## Information architecture

```
quiniela.dpdns.org
├── /                          → landing (signed-out) | redirect to /home (signed-in)
├── /join/:invitePath          → invite landing — Google sign-in CTA, accepts personal-link path (e.g. /join/juan-abc123)
├── /home                      → lobby (Mi Quiniela)
│   ├── /group/:groupId        → group drill-in (e.g. /group/B)
│   └── /knockout/:roundId     → knockout drill-in (R32, R16, QF, SF, P3, FINAL)
├── /ranking                   → Tabla
├── /matches                   → Partidos (schedule + results, tabs Pasados/Hoy/Próximos)
├── /compare/:opponent?        → vs (compare picks, optional opponent handle in URL)
├── /admin/results             → results entry, hidden behind is_admin
├── /admin/payments            → payments ledger + prize-split editor, hidden behind is_admin
├── /api/auth/*                → Auth.js routes
├── /api/bracket/import        → XLSX upload endpoint
└── /api/bracket/export        → XLSX download endpoint
```

**Bottom nav (signed-in, all four tabs visible on every primary screen):**
`Mi Quiniela · Tabla · Partidos · vs`

## Key user flows

### First-time invite

`https://quiniela.dpdns.org/join/juan-abc123` →
"Andrés te invitó a Quiniela Panas. Inicia sesión con Google para unirte." (inviter's name resolved from the path) →
Google OAuth → user row created with `invited_by_user_id` set, role assigned by inviter's role (admin → captain, captain → player) → redirect to `/home` empty lobby.

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
| Invite landing | `/join/:invitePath` | First-time entry | Pool name, inviter's display name ("Andrés te invitó a…"), Google sign-in button. Invalid/unknown path = friendly error + no sign-in CTA. |
| Lobby | `/home` | Pick screen entry + progress | Countdown chip (T-Nd HH:MM), pot chip ("Pot: $480 · 24 pagas"), 12 group cards with progress bars, 6 knockout sub-cards (locked until group stage ends), "🐙 Paul llena todo" CTA, "Descargar/Subir XLSX" secondary actions, "sin pagar" pill on the user's own profile chip if they haven't been marked paid, "Invitar amigos" CTA visible only to admin + captains |
| Group drill-in | `/group/:groupId` | Fill 6 match scores | Header with back arrow, group label, 6 match rows (team-team + score boxes + 🐙 icon), tap-to-numpad behavior, "Guardar y volver" + "Siguiente: Grupo C →" |
| Knockout drill-in | `/knockout/:roundId` | Fill knockout round picks | Same pattern as group drill-in; number of matches varies by round |
| Ranking | `/ranking` | Standings | Tabs General / Por jornada, prize-split header strip ("Pot $480 · 1° 80% · 2° 15% · 3° 5%"), rank pos with medal colors for top 3, estimated payout chip ($-amount) next to top 3, trend arrow (▲/▼/─), points, "you" row highlighted |
| Schedule + results | `/matches` | Match calendar | Tabs Pasados / Hoy / Próximos, day labels, match rows with kick-off time, live indicator (pulsing dot), score or "— : —", user's pick + ✓/✗ |
| Compare | `/compare/:opponent?` | Head-to-head picks | Player picker, point gap chip, tabs Diferencias / Todo, 4-column table (match / you / them / actual) |
| Admin results | `/admin/results` | Enter actual match results | Same chrome as schedule, score fields editable, save per match, fires the existing PL/pgSQL scoring trigger |
| Admin payments | `/admin/payments` | Track money in, configure prizes | Three stacked sections: (1) prize-split editor — three percentage inputs that must sum to 100, "Frozen at kickoff" badge after lock; (2) ledger grouped by captain — each captain shows their sub-group expanded (captain row + invitee rows beneath), per-captain subtotal of expected vs. collected, captain marked as "responsible for $X"; (3) admin's own row + any orphans (e.g. an admin direct invitee with no further invitees). Paid toggle, amount, paid-at timestamp, free-text note (e.g. "Zelle ref 8821"). Running pot chip at the top, CSV export button. |

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

## Roles and invite tree

### Three roles, max depth 2

- **`admin`** (Juan) — seeded directly via Flyway / SQL, not via the invite flow. Can do everything: invite anyone, edit results, mark payments, edit prize split.
- **`captain`** — anyone the admin invited. Captains are trusted friends, expected to bring their own circle. Can submit picks, can invite players, and are responsible offline for collecting money from their invitees and remitting to the admin.
- **`player`** — anyone a captain invited. Can submit picks. Cannot invite further. The tree dead-ends here.

### Rule

The invitee's role is fully determined by the inviter's role at sign-up time:

```
admin invites    → captain
captain invites  → player
player invites   → blocked (no invite UI shown)
```

`user.role` (enum) and `user.invited_by_user_id` (nullable FK) capture this. The role is set once at sign-up and is not editable by anyone except the admin via SQL (kept out of UI for v1).

### Invite UI

- Admin and captains see an **"Invitar amigos"** button on the lobby. Tapping it opens a sheet showing their personal invite URL (`/join/juan-abc123`), a "Copiar" button, and a "Compartir por WhatsApp" deep-link.
- The personal URL is stable per user — minted once at sign-up, infinite uses, never rotated unless the admin revokes (admin-only, edge case for v1). The admin's URL is minted by the same Flyway seed that creates the admin row, so the admin has a shareable link from day zero with no chicken-and-egg.
- Players don't see the button. The route to mint a link returns 403 for them.
- The invite landing page resolves the inviter's display name from the path for the "X te invitó" copy. Bad/expired paths render a friendly fallback with no CTA.

### Payment responsibility

The admin payments ledger groups by captain to mirror real-world accountability:

```
▾ Captain A — 3 invitees — paid 3/4 — $60 / $80
    A himself                  paid    $20
    A's invitee 1              paid    $20
    A's invitee 2              paid    $20
    A's invitee 3              pendiente  —
▾ Captain B — 1 invitee — paid 2/2 — $40 / $40
    ...
▸ Admin (Juan)                 paid    $20
```

The admin can mark any row paid (single source of truth), but the grouping makes it obvious which captain still owes money and for whom. v1.1 may let captains mark their own sub-group paid; v1 keeps that write capability admin-only.

## Payments and prizes

### Model

- **Currency:** USD. Storage as integer cents (`amount_cents`) to avoid floating-point. Format with `Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })` regardless of UI locale — money is the same number for everyone, only labels around it translate.
- **Entry fee:** single fixed amount per pool (default $20, configurable at pool creation). Stored on the `pool` row.
- **Pot:** computed live as `SUM(payment.amount_cents) WHERE pool_id = … AND status = 'paid'`. Re-derived on every read; no cached aggregate to drift.
- **Prize split:** three percentage rows in a `prize_split` table — `(pool_id, rank, percentage)`. Default `(1, 80), (2, 15), (3, 5)`. Constraint: percentages sum to 100. Editable in `/admin/payments` until `pool.locked_at`; UI disables the inputs and shows a "Frozen at kickoff" badge after lock.
- **Eligibility:** any player who has submitted a bracket is on the ranking. Payment status doesn't gate play or rank. At payout time, the admin reconciles offline — if a top-3 finisher hasn't paid, the admin manually moves the prize to the next eligible player. The app does not auto-skip unpaid players.

### Visibility

- **Public** (every signed-in player): pot total, count of paid players ("24 jugadas pagas"), the three prize-split percentages, estimated payouts on the top 3 ranking rows.
- **Self-only:** their own paid/unpaid state (a "sin pagar" pill on their profile chip in the lobby). Other players' payment status is not exposed.
- **Admin-only:** the full payment ledger — every player's paid state, amount, timestamp, note. Live total. CSV export.

### Tie handling for prize money

Ties on points share rank (already specified above). For prize money: the tied players split the combined money of the ranks they occupy. Example: if 1st and 2nd tie at 142 points, they each get `(80% + 15%) / 2 = 47.5%` of the pot. If 2nd, 3rd, and 4th all tie, the 2nd-and-3rd money (`15% + 5% = 20%`) splits three ways. 4th place earns prize money in this case only because they tied into a paying rank.

### Admin operations

- Mark paid: toggle on the player row; sets `paid_at = now()`, `amount_cents = pool.entry_fee_cents` (overridable for partial / over payments).
- Unmark paid: toggle off; sets `paid_at = null`. Audit-logged (`payment_history` table or `updated_at` + `updated_by`).
- Refund: not a v1 button. Admin un-marks paid and notes the refund in the free-text payment note. v1.1 can formalize this.
- Add a player who isn't on the invite list: not supported via this screen. Players must come in via a personal invite link (`/join/:invitePath`).

## Internationalization

- Library: `next-intl` (or equivalent) — to be decided in implementation plan
- Default locale: `es-CO`. Secondary: `en`. Structure supports adding more without code changes.
- All user-facing strings extracted to message files from day one — no hardcoded Spanish.
- Switcher placement: profile menu in the top bar (not bottom nav — too rare a change to deserve a tab slot).
- Locale-sensitive formatting: dates and times via `Intl.DateTimeFormat`. Money via `Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })` — the actual currency stays USD regardless of UI locale, only surrounding labels translate. Scores stay as plain integers.
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
- **Prize-split lock.** `pool.locked_at` is set at the same kickoff timestamp that locks brackets. After that timestamp, `/api/pool/prize-split` rejects writes (409); the admin UI mirrors with disabled inputs + "Frozen at kickoff" badge.
- **Pot freshness.** Lobby and ranking re-fetch pot total on screen mount. No live updates — a player marked paid mid-session sees the new pot when they navigate.

## XLSX import/export contract

- Template is a single sheet, 104 rows, columns: `match_id` (hidden), `team_1`, `score_1`, `score_2`, `team_2`, `phase`, `round`, `kickoff_iso`.
- Score cells are integers ≥ 0; blank means "not predicted".
- Server matches rows by `match_id` first, falls back to `team_1` + `team_2` + `kickoff_iso` exact match. Mismatches are returned as per-row errors, no partial-save by default (all-or-nothing for v1).
- Export reflects current state; if user has 18/104 filled, the downloaded sheet has 18 cells populated.

## Open questions to resolve during planning

1. **Locale auto-detection from browser?** Or always default to `es-CO` and let users switch manually?
2. **What does Paul actually use?** GPT-4-ish via Anthropic/OpenAI? Cached suggestions to avoid per-match LLM calls × 50 players? Plan-stage decision.
3. **Knockout fixture population:** is the admin entering "Spain advanced as A1" manually, or pulling from a public results feed? Affects admin UI scope.
4. **Pool name:** "Quiniela Panas" hardcoded or configurable? Affects landing copy.
5. **Avatar source:** Google profile pictures from Auth.js, or initials? Profile pictures need DOMAIN allowlisting in `next.config.ts`.
6. **Entry fee setup:** seeded via SQL/Flyway, or set in a one-time pool-creation screen? v1 has a single pool so a Flyway seed is fine, but it locks in $20 (or whatever) unless we add a config row to edit.
7. **Payment audit log:** is `payments.updated_at + updated_by` enough, or do we want a separate `payment_history` table with full event log (paid → un-paid → paid)? Affects refund/dispute traceability.
8. **Captain self-mark-paid in v1.1:** when v1.1 lets captains mark their own sub-group paid, do they also gain visibility into who owes them money? Likely a dedicated "/team" screen for captains. Out of scope for v1, just flagging.
9. **Personal invite URL slug format:** human-readable (`/join/juan-abc123`) is nicer but leaks identity to anyone with the link. Alternative: opaque token (`/join/k4nx9z`). Trade readability for privacy. Probably fine to leak identity in this audience but worth confirming.
10. **Orphan handling:** if a captain leaves the pool mid-tournament, what happens to their invitees? Admin takeover is the obvious move; out of v1 scope but worth thinking about before v1.1.

## Risks

- **Scope vs deadline.** Adding payment tracking + prize config pushes the must-have list to roughly a dozen headline features in 17 days — tight. If anything slips, candidates for late deferral (in priority order): Compare picks → XLSX escape hatch → prize-split editor (hardcode 80/15/5 + ship without admin edit) → Admin results screen (replace with SQL). Bracket fill + Ranking + Schedule + payment ledger are non-negotiable.
- **Money flow trust.** App tracks paid status but the actual money is in Zelle/cash. A mistake in the ledger creates real-world disputes. Mitigation: free-text note per payment, admin un-mark is auditable, CSV export so the admin can reconcile against bank statements weekly.
- **Locking edge case.** A player mid-numpad-tap at the exact kickoff second could lose the score they were entering. Mitigation: server rejects writes past deadline with a clear toast; UI shows "Bloqueado en T-00:00:30" warning for the last minute.
- **Live results latency.** No real-time feed in v1 — admin enters results manually. If admin is slow, ranking lags reality. Acceptable for a friends pool; communicate via WhatsApp.
- **Style B coldness.** Broadcast/sportsbook aesthetic is striking but may feel less warm than a friends-and-family product expects. Mitigation: Spanish copy stays casual ("Que Paul llene todo", "¡Completo!"), avoid finance/poker jargon, lean into emoji where appropriate.

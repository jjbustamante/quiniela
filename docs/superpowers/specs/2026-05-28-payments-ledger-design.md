# Payments Ledger (two-tier) — Design

> Plan 4 of the Quiniela Panas rewrite. Adds the spec's feature #13 (admin
> payments tracking) but with a **two-tier accountability model** that pulls
> the captain-marks-their-own-subgroup capability — originally filed under
> v1.1 — into v1. Scope for this build: **core ledger only** (prize-split
> editor UI, CSV export, and the lobby "sin pagar" pill are deferred).

## Context

The money flow in this pool mirrors the invite tree:

```
player  →(pays entry fee)→  captain  →(remits collected pile)→  admin
```

The app records who has paid at each hop. It does **not** collect money —
payments happen offline (Zelle / cash / transfer). The ledger is an
accountability tool so the admin can see, at a glance, which captains still
owe money and for whom.

### Current state (as of 2026-05-28, after Plan 3)

- `pool` table has `entry_fee_cents` (2000) and `locked_at` (nullable).
- `prize_split` table seeded 80/15/5.
- `pool_membership (pool_id, user_id, joined_at)` records who is in the pool.
- `users.invited_by_user_id` captures the invite tree; `users.role` is one of
  `admin | captain | player`.
- `PublicSummaryController` computes `potCents = panaCount * entry_fee_cents`
  (every member, not just paid members).
- `/admin/results` exists (Plan 3 Task 1). No payments surface exists.

## Key decisions

1. **Pot is unchanged.** `PublicSummaryController` keeps
   `pot = panas × entry_fee_cents`. The payments ledger is a separate
   accountability layer and does **not** drive the pot or the ranking
   payouts. This guarantees zero regression to shipped behavior. The
   ledger's "collected" totals are admin/captain-only and distinct from the
   public pot.

2. **Two-state ledger.** Each member has a `paid` state (player handed their
   entry fee to their captain — set by the captain or admin). Each captain
   additionally has a `settled` state (captain remitted their collected pile
   to the admin — set by the admin only). This makes
   collected-but-not-yet-remitted money visible.

3. **Lazy upsert.** A `payment` row is created on the first toggle, same
   pattern as `quiniela`/`bet`. Reads `LEFT JOIN`; an absent row means
   unpaid/unsettled.

## Data model — `V011__payments.sql`

Next migration number is V011 (V007 was skipped; current max is V010).

```sql
CREATE TABLE payment (
    pool_id           BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    paid              BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at           TIMESTAMPTZ,
    marked_paid_by    BIGINT REFERENCES users(id),
    amount_cents      INT,            -- nullable; NULL = use pool.entry_fee_cents
    note              TEXT,
    settled           BOOLEAN NOT NULL DEFAULT FALSE,   -- only meaningful for captains
    settled_at        TIMESTAMPTZ,
    marked_settled_by BIGINT REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (pool_id, user_id),
    CHECK (amount_cents IS NULL OR amount_cents >= 0)
);

CREATE INDEX idx_payment_pool ON payment(pool_id);
```

No seed data — rows appear as the admin/captains start marking payments.

## Authorization

| Action | Allowed for |
|---|---|
| Mark a player `paid` | admin, **or** that user's inviter (`users.invited_by_user_id == caller`) |
| Mark a captain `settled` | admin only |
| View own sub-group ledger | captain or admin |
| View full ledger | admin only |

A captain marking someone they did not invite → **403**. A player calling any
payments write → 403. Non-admin calling an `/api/admin/**` payments route → 403.

These checks live in the service layer with `ResponseStatusException`, the
same explicit-and-local pattern as `AdminResultsService.requireAdmin`
(we don't have `@EnableMethodSecurity` wired yet).

## Endpoints

### Captain-facing (captain or admin)

- `GET /api/payments/my-subgroup` → caller's invitees with each one's `paid`
  flag, plus the caller's subgroup `collectedCents / expectedCents` totals and
  the caller's own `settled` flag (read-only here — only the admin writes it).
  - `expectedCents` = (subgroup size) × `entry_fee_cents`.
  - `collectedCents` = Σ of paid members' `amount_cents` (fallback
    `entry_fee_cents` when null).
- `PUT /api/payments/{userId}/paid` — body `{ paid: boolean, amountCents?: int, note?: string }`.
  - Authz: caller is admin or `{userId}`'s inviter; else 403.
  - 404 if `{userId}` is not a pool member.
  - Upserts the payment row; sets `paid_at` + `marked_paid_by` when
    transitioning to paid.

### Admin-facing (admin only)

- `GET /api/admin/payments` → full ledger grouped by captain:
  - For each captain: the captain row (their own `paid` + `settled` flags),
    their invitee rows beneath, and a per-captain
    `collectedCents / expectedCents` subtotal.
  - The admin's own row + any orphans (admin direct-invitees who are not
    captains).
  - Pool totals: `potCents` (the same member-count figure as the public
    summary, for consistency), `paidCount`, `memberCount`.
- `PUT /api/admin/payments/{captainId}/settled` — body `{ settled: boolean }`.
  - Admin only. 404 if `{captainId}` is not a pool member. Upserts; sets
    `settled_at` + `marked_settled_by` on transition to settled.

## Frontend

### `/captain/payments` (captains + admin)

- Header: `collected $X / $Y` for the caller's sub-group, plus a read-only
  badge showing whether the admin has marked the caller `settled`.
- One row per invitee: name + a `paid` toggle. Toggling calls the server
  action → `PUT /api/payments/{userId}/paid` → refetch.
- Empty state ("aún no has invitado a nadie") when the caller has no invitees.

### `/admin/payments` (admin only)

- Running chip at top: pot + `paidCount / memberCount`.
- Ledger grouped by captain: captain row (with a `settled` toggle and the
  per-captain expected-vs-collected subtotal) followed by indented invitee
  rows (each with its `paid` state; admin can also toggle these).
- Final section: admin's own row + orphans.

### Entry points

- Lobby action row gains a contextual "Pagos" link, reusing the existing
  admin/captain visibility pattern from the "Invitar amigos" CTA:
  admin → `/admin/payments`, captain → `/captain/payments`.

### i18n

New `payments.*` keys in `messages/{en,es-CO}.json` (title, paid, settled,
collected/expected, subgroup empty state, toggle labels). UI copy in Spanish.

## Testing

### Backend ITs

- `PaymentControllerIT`: paid upsert round-trips; **authz** — non-inviter
  player → 403, captain cannot mark another captain's invitee → 403, admin
  can mark anyone; 404 for non-member; `my-subgroup` collected/expected
  totals correct (mixed paid/unpaid).
- `AdminPaymentControllerIT`: `settled` is admin-only (captain → 403);
  full-ledger grouping returns captains with nested invitees + correct
  subtotals; pot/paid/member counts present.

### Frontend

- typecheck + lint clean. (No new unit tests strictly required for core
  ledger; toggles are thin server-action wrappers.)

## Out of scope (deferred)

- **Prize-split editor UI** — split stays editable via SQL for v1
  (`UPDATE prize_split ...`). Freeze-at-kickoff (`pool.locked_at`) UI lands
  with the editor later.
- **CSV export** of the ledger.
- **`sin pagar` pill** on the lobby profile chip (self-only payment status).
- **Captain self-settle** beyond marking players (admin remains the only
  writer of `settled`).

## Migration / numbering note

Use `V011__payments.sql`. Do not reuse the skipped V007 slot.

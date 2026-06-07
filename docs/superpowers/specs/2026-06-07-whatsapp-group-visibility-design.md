# Design — Hierarchical WhatsApp-group visibility

> A "Join the community WhatsApp group" card on the home page, gated by a
> two-level visibility hierarchy: the **admin** owns the link and decides
> whether it's available to captains at all; each **captain** decides, per
> player, which of their players may see it. Default hidden everywhere
> (opt-in) — so a captain whose children are playing never accidentally
> surfaces a "join WhatsApp" prompt to them.

**Date:** 2026-06-07
**Branch:** `feat/whatsapp-group-visibility`
**Status:** approved, ready for implementation plan

## Background / grounding

- Roles + invite tree (V003): `users.role` ∈ {admin, captain, player};
  `users.invited_by_user_id` records each user's inviter. The tree is 2-level:
  admin → captains → players (V011 comment: the inviter is "their captain, or
  the admin"). So a captain's players = users where `invited_by_user_id = captainId`.
- Pool-level config is columns on the `pool` table (`entry_fee_cents`,
  `house_cut_percentage`), edited by `AdminPoolConfigService` via
  `/api/admin/pool-config`, surfaced in `/admin/config`. The community link is
  another pool-level setting.
- The home page (`frontend/app/home/page.tsx`) already fetches `getMe()`. The
  resolved visibility is exposed there — **not** via the public summary — so the
  link never reaches an unauthenticated endpoint.
- The captain↔players relationship is already surfaced: `getMySubgroup()`
  (`frontend/lib/api/payments.ts`) + `PaymentService` return the captain's
  subgroup (their invitees) for payment tracking, rendered with `SubgroupRow`.
  The captain WhatsApp roster mirrors this pattern (same "my invitees" query).
- `InviteFriendsSheet.tsx` already contains the WhatsApp brand glyph (SVG path)
  and the brand green `#25d366` — reuse them for the card.

## Data model (migration V020)

```
ALTER TABLE pool  ADD COLUMN whatsapp_group_url     VARCHAR(255);
ALTER TABLE pool  ADD COLUMN whatsapp_group_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN whatsapp_group_visible BOOLEAN NOT NULL DEFAULT false;
```

- `pool.whatsapp_group_url` — the invite link; null/blank = unset.
- `pool.whatsapp_group_enabled` — the **admin master switch** ("available to
  captains"). When false, the feature is fully dark for everyone.
- `users.whatsapp_group_visible` — **per-player**, default false; flipped on by
  the player's inviter (their captain, or the admin for direct invitees).
  Irrelevant for admin/captain themselves.

## Visibility resolution (single source of truth)

Computed server-side in `MeController`/me service and exposed as ONE field on
`MeResponse`: `whatsappGroupUrl: String | null`. The home card is then trivial:
render it iff the field is non-null.

```
if (pool.whatsapp_group_url is blank OR NOT pool.whatsapp_group_enabled) -> null
else if (role == ADMIN || role == CAPTAIN)                               -> url
else /* PLAYER */                                                        -> user.whatsapp_group_visible ? url : null
```

- Admin + captains always see the card when the master switch is on (they
  manage and share it).
- A player sees it only when their inviter has turned their flag on.

## (A) Backend

1. **Migration V020** as above.
2. **`Pool` entity** — add `whatsappGroupUrl` (String, nullable) +
   `whatsappGroupEnabled` (Boolean) fields + getters/setters.
3. **`User` entity** — add `whatsappGroupVisible` (Boolean) field + getter/setter.
4. **`MeResponse`** gains `String whatsappGroupUrl`; `MeController` injects
   `PoolRepository` (active pool id = 1L, the constant already used elsewhere)
   and computes the resolved value in `toResponse` (now an instance method, or
   pass the pool in). Read-only; no write.
5. **`AdminCommunityConfigService` + `AdminCommunityConfigController`** (new,
   mirrors `AdminPoolConfigService`): `GET/PUT /api/admin/community-config`,
   `adminGuard.requireAdmin`. View/Update record: `{ String url, boolean enabled }`.
   Validation: when `enabled` is true, `url` must be non-blank and start with
   `https://chat.whatsapp.com/`; when disabled, a blank url is allowed (the
   feature is simply dark). Trims the url; stores null for blank.
6. **`CaptainWhatsappService` + controller** (new): 
   - `GET /api/captain/whatsapp-roster` — returns the caller's **player**
     invitees (`invited_by_user_id = callerId AND role = 'player'`) as
     `{ userId, displayName, visible }[]`, ordered by display name. Caller must
     be ADMIN or CAPTAIN (PLAYER → 403). Filtering to player-role invitees keeps
     the admin's roster (whose direct invitees include captains) free of
     meaningless rows — captains see the card by role regardless of the flag.
     Reuse/extend the existing subgroup query (`PaymentService` already selects
     a captain's invitees) if practical; otherwise add
     `UserRepository.findByInvitedByUserIdAndRoleOrderByDisplayNameAsc`.
   - `PUT /api/captain/whatsapp-visibility` body `{ userId, visible }` — sets
     `whatsapp_group_visible` on the target. **Authz guard:** the target's
     `invited_by_user_id` must equal the caller's id (else 403) — a captain can
     only flip their own invitees. Admin may flip their own direct invitees the
     same way.

## (B) Frontend

1. **`WhatsappGroupCard`** (`components/lobby/`, server component) — props
   `{ url: string }` (only rendered by the parent when non-null). Estadio poster
   card: paper bg + ink border, WhatsApp-green glyph chip (reuse the
   `InviteFriendsSheet` SVG path), display-font title + subtitle, wrapped in
   `<a href={url} target="_blank" rel="noopener noreferrer">`. For ADMIN/CAPTAIN
   viewers it also renders a small "Gestionar quién lo ve →" link to
   `/captain/whatsapp` (the parent passes a `canManage` boolean = role is
   ADMIN|CAPTAIN).
2. **`home/page.tsx`** — after the invite section, render
   `{me.whatsappGroupUrl && <WhatsappGroupCard url={me.whatsappGroupUrl}
   canManage={me.role !== "PLAYER"} />}` in its own `<section>`.
3. **`MeResponse` type** (`lib/api/me.ts`) gains `whatsappGroupUrl: string | null`.
4. **Admin "Comunidad" section** in `/admin/config` — a new
   `CommunityConfigPanel` (mirrors `PoolConfigPanel`): a url text input + an
   "available to captains" checkbox + save; new `lib/api/community-config.ts`
   (`get`/`update`) + a `saveCommunityConfigAction` in `config/actions.ts`
   (revalidates `/admin/config`). Added as a third `<section>` with the
   `adminConfig.community` heading.
5. **`/captain/whatsapp` (new page)** — admin/captain only (PLAYER → redirect
   `/home`). Lists the roster with a per-row toggle (`WhatsappRosterRow`, a
   client component calling a `setVisibilityAction`). Mirrors the captain
   payments page shell. Empty-state when the caller has no invitees.
6. **NavDrawer** — add a captain/admin-gated link to `/captain/whatsapp`
   (label `nav.whatsappGroup`), placed near the existing payments link.

## i18n (es-CO + en)

- `lobby.whatsappGroupTitle` / `lobby.whatsappGroupSubtitle` / `lobby.whatsappGroupManage`
  - ES: "ÚNETE AL GRUPO" / "Coordina con las panas por WhatsApp" / "Gestionar quién lo ve"
  - EN: "JOIN THE GROUP" / "Chat with everyone on WhatsApp" / "Manage who sees it"
- `adminConfig.community` (section head) + `adminConfig.whatsappUrl` /
  `adminConfig.whatsappUrlHelp` / `adminConfig.whatsappEnabled`.
- `nav.whatsappGroup`.
- A new `captainWhatsapp` namespace: `title`, `intro`, `visibleOn`, `visibleOff`,
  `empty`. The WhatsApp **URL** is never i18n text.

## Testing

- **Backend ITs:**
  - Resolution matrix on `/api/me`: (enabled true/false) × (role admin/captain/player)
    × (player visible true/false) returns the url only in the cases above.
  - `AdminCommunityConfigService`: enabling with a blank/invalid url is rejected;
    enabling with a valid `chat.whatsapp.com` url persists; disabling allows blank.
  - Captain toggle authz: a captain flipping a user they did NOT invite → 403;
    flipping their own invitee → persists; PLAYER calling the roster/​toggle → 403.
- **Frontend component tests:**
  - `WhatsappGroupCard`: renders the link (href/target/rel) + title; shows the
    "manage" link only when `canManage`.
  - `CommunityConfigPanel`: typing a url + toggling enabled + save calls the action.
  - `WhatsappRosterRow`: toggling calls `setVisibilityAction` with `{userId, visible}`.
- **Gate:** `pnpm vitest run` + `pnpm typecheck` + `pnpm lint` (frontend);
  `./mvnw -q verify` (backend).

## Out of scope

- No per-player notifications, no bulk "show all to my players", no analytics.
- No dismiss/localStorage state on the card.
- No public-summary exposure (kept off the unauthenticated endpoint on purpose).
- The url is a plain pool-level constant-style setting — not validated against
  WhatsApp's API, only a format check (`https://chat.whatsapp.com/` prefix).

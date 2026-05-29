# Side-Drawer Navigation — Design

> Usability fix + IA improvement for the Quiniela Panas frontend. Adds a
> left side drawer (☰) for secondary features so the 4-tab bottom nav stays
> reserved for the principal features, and gives every page a reliable way
> back to `/home`. Replaces three concrete navigation defects found in the
> current build.

## Problem (verified in current code)

1. **Dead back affordance.** `TopBar` accepts a `back` prop that renders a
   `←` as a plain non-interactive `<span>` (`components/shell/TopBar.tsx`).
   It does nothing, and no page passes `back` anyway.
2. **Lying bottom nav.** Secondary pages pass the wrong `activeKey`:
   `/captain/payments` highlights "Tabla" (`activeKey="ranking"`); both
   `/admin/payments` and `/admin/results` highlight "Mi Quiniela"
   (`activeKey="myQuiniela"`). The bottom bar therefore misrepresents the
   current location.
3. **No way out.** From a payment or admin page, the only escape is tapping
   a bottom-nav tab. The top-right avatar (`UserMenu`) opens a dropdown whose
   only action is sign-out — no "home" link.

Result: a captain or admin who opens `/captain/payments` or `/admin/payments`
sees a list with no obvious way back, and the bottom nav points at the wrong
tab.

## Decisions (locked during brainstorming)

- **Pattern:** left side drawer opened by a ☰ button in the TopBar.
- **Drawer is global:** ☰ appears in the TopBar on every authenticated page
  (including `/home`), so there is one consistent menu site.
- **Bottom nav stays the 4 principal tabs** (Mi Quiniela / Tabla / Partidos /
  Comparar) and remains visible on secondary pages, with **no tab
  highlighted** when the current page is not one of the four.
- **Drop the avatar dropdown.** `UserMenu`'s only function is sign-out, which
  moves into the drawer. The avatar becomes a plain, non-interactive identity
  mark (gold initial) — or is removed if it reads as clickable.
- **Remove the lobby "Pagos" button** (the role-gated `<Link>` added to the
  `/home` action row in Plan 4 Task 7) — the drawer now covers it.

## Architecture

### New: `components/shell/NavDrawer.tsx` (client component)

A `"use client"` component that owns the ☰ trigger + the slide-in panel.
Mirrors the proven mechanics of `components/shell/UserMenu.tsx`:

- A ☰ icon button (`aria-label` = `nav.menu`, `aria-expanded` bound to open
  state).
- When open: a fixed full-screen backdrop (`z-40`, closes on click) + a panel
  sliding in from the left (`z-50`), poster styling (1.5px ink border, paper
  background, drop shadow) consistent with `UserMenu` / `InviteFriendsSheet`.
- Closes on: Escape keydown, backdrop click, and navigation (clicking any
  link — Next.js `<Link>` navigates; we also call `setOpen(false)` on click so
  the panel doesn't linger across the client transition).

**Props:** `{ role: "ADMIN" | "CAPTAIN" | "PLAYER" }` — passed down from
`TopBar` (which already reads `session.role`). The drawer needs the role to
gate items and to resolve the Pagos target.

**Items (top to bottom):**

| Item | Visible to | Target |
|---|---|---|
| Inicio | all | `/home` |
| Pagos | admin, captain | admin → `/admin/payments`; captain → `/captain/payments` |
| Resultados | admin only | `/admin/results` |
| — divider — | | |
| Cerrar sesión | all | `signOutAction` (a `<form action={signOutAction}>` submit, same as today's UserMenu) |

Player role sees only **Inicio** + **Cerrar sesión** above/below the divider.

Labels come from the `nav` i18n namespace (see i18n below). No hardcoded
Spanish in the component.

### Changed: `components/shell/TopBar.tsx`

- Add `<NavDrawer role={role} />` as the **first** child of the left cluster,
  before `<PaulBadge />`. `role` is already computed (`session?.role`); render
  the drawer only when `role` is present (authenticated).
- **Remove** the `back` prop from `TopBarProps` and the dead `←` `<span>`.
  Only one call site passes `back`: `app/group/[groupId]/page.tsx:49`. Grep
  confirms that page already renders its own in-content "← Mi Quiniela" link
  (`href="/home"`, lines 55–58), so removing the dead TopBar arrow loses
  nothing — just drop the `back` attribute from that `<TopBar>` call.
- **Remove** the `<UserMenu .../>` usage. Replace with a plain avatar mark:
  a non-interactive `<span>` (gold initial on ink, same visual as UserMenu's
  button but not a button) — or omit entirely. Keep `LocaleSwitcher` + the red
  "26" mark as-is.
- `signOutAction` import moves to `NavDrawer` (TopBar no longer needs it).

### Deleted: `components/shell/UserMenu.tsx` (+ its test)

Its sign-out responsibility moves into `NavDrawer`. Grep confirms `UserMenu`
is referenced only by `TopBar.tsx` and its own test `UserMenu.test.tsx`
(the `auth-actions.ts` hit is the shared `signOutAction` source, not a
dependency on UserMenu). So:

- Delete `components/shell/UserMenu.tsx` and `components/shell/UserMenu.test.tsx`.
- Add a `NavDrawer.test.tsx` covering the drawer's open/close + item visibility
  per role (replacing the coverage UserMenu.test.tsx provided for the dropdown).
- Update `components/shell/TopBar.test.tsx` if it asserts on UserMenu /
  sign-out / the `back` arrow — adjust those expectations to the new shell
  (drawer present, no UserMenu, no back span).

### Changed: `BottomNav` call sites (no component change)

`BottomNav`'s `activeKey` prop is **already optional**. Fix the three
secondary pages to pass no `activeKey` (honest "nothing active"):

- `app/captain/payments/page.tsx`: `<BottomNav />` (was `activeKey="ranking"`).
- `app/admin/payments/page.tsx`: `<BottomNav />` (was `activeKey="myQuiniela"`).
- `app/admin/results/page.tsx`: `<BottomNav />` (was `activeKey="myQuiniela"`).

Also fix `/captain/payments`'s TopBar `meta` (currently `tNav("ranking")`,
which mislabels the page) — set it to the collected/expected summary or drop
the meta. Pick: drop the misleading meta (`<TopBar title={t("title")} />`).

### Changed: `app/home/page.tsx`

Remove the role-gated "Pagos" `<Link>` from the action row (added in Plan 4
Task 7). Grep confirms `Link` is used **only** by that Pagos button on this
page (lines 123–128), so removing the button means removing the
`import Link from "next/link"` and the `tPay` translations binding too — both
become unused. Leave `InviteFriendsButton` + `PaulFillAllButton` in place.
(The group page keeps its own `Link` import — this only concerns `home/page.tsx`.)

## i18n

Add to the `nav` namespace in both `messages/es-CO.json` and
`messages/en.json`:

- `nav.menu` — aria-label. es: "Menú", en: "Menu".
- `nav.home` — es: "Inicio", en: "Home".
- `nav.payments` — es: "Pagos", en: "Payments".
- `nav.results` — es: "Resultados", en: "Results".

Reuse `common.signOut` (already exists: "Cerrar sesión" / "Sign out") for the
drawer's sign-out label — do not duplicate.

Key parity must hold across both locale files.

## Out of scope

- No change to the 4 bottom-nav tabs or their order.
- No new admin features — drawer only routes to pages that already exist
  (`/admin/payments`, `/admin/results`, `/captain/payments`, `/home`).
- No desktop-specific layout (e.g. a persistent left rail). The drawer is the
  mobile-first pattern; desktop gets the same drawer. A persistent sidebar can
  come later if desktop usage grows.
- Avatar is reduced to a static mark; a richer profile menu is deferred.

## Testing

- **Playwright smoke** (extend the existing e2e suite): authenticated as admin,
  load `/admin/payments`; open the drawer (click the ☰); assert the panel
  shows "Inicio", "Pagos", "Resultados", "Cerrar sesión"; click "Inicio" and
  assert the URL is `/home`. (If seeding an admin session in e2e is
  infeasible, fall back to asserting the drawer opens on `/home` and shows
  "Inicio" + "Cerrar sesión" for the default session.)
- **Gates:** `pnpm typecheck` clean, `pnpm lint` no new errors, `pnpm build`
  compiles (catches any server/client component boundary mistake — `NavDrawer`
  must be `"use client"` since it has state + handlers, while `TopBar` stays a
  server component that renders the client drawer as a child, exactly like it
  renders `UserMenu` today).

## Accessibility

- ☰ button: `aria-label={t("menu")}`, `aria-expanded`, `aria-controls`
  pointing at the panel id.
- Panel: `role="dialog"` (or `role="menu"` matching UserMenu's pattern),
  focus not trapped for v1 (UserMenu doesn't trap either — keep parity), but
  Escape closes.
- Each item is a real `<Link>` or a submit `<button>` (sign-out), keyboard
  reachable.

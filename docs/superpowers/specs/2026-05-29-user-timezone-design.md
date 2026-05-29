# Per-User Timezone Preference — Design

> Fixes match/deadline times rendering in raw server UTC (wrong for the
> Colombia/Venezuela audience) by storing each user's timezone server-side and
> formatting all instant fields in that zone. Adds a `/settings` page to change
> it. Ships BEFORE the admin-test-mode feature, which depends on correct date
> handling.

## Problem

Times display in the server's zone (UTC on Cloud Run). A 17:00 UTC kickoff shows
"17:00", but in Bogotá (UTC-5) it's 12:00 and in Caracas (UTC-4) it's 13:00. Two
distinct bug classes in the current code:

1. **Explicit UTC formatting** — `getUTCHours/Minutes/Date` in
   `lib/tournament-format.ts` (`deadlineShort`) and
   `components/admin/MatchResultRow.tsx`.
2. **Locale format with no `timeZone`** — `toLocaleTimeString("es-CO", …)`
   without a `timeZone` option in `components/matches/MatchTabs.tsx` and
   `components/group/GroupDrillIn.tsx`. These use the *runtime* zone: UTC on the
   SSR server, the browser's zone on the client → wrong on SSR AND a
   hydration-mismatch risk.

Google sign-in does NOT provide a usable timezone (the OIDC `zoneinfo` claim is
not populated by Google; current auth only stores `google_sub`, `email`,
`display_name`, `avatar_url`). So the timezone must be stored by us.

## Key insight

Persisting the timezone **server-side** (on the `users` row) is what makes this
clean: the server knows each user's zone at render time, so SSR produces correct
times with no flicker and no hydration mismatch — the failure mode of a
browser-only approach. Auto-detection from the browser is offered once in
settings as a convenience to populate the stored value.

## Decisions (locked in brainstorming)

1. Store an IANA timezone string per user; default `America/Bogota`.
2. Format **instant fields only** (`kickoffAt`, deadlines) in the user's zone.
   Date-only fields (`startDate`, `endDate`) have no time component and stay
   rendered as-is (UTC-parsed calendar dates) — changing them would be wrong.
3. New `/settings` page with a timezone selector + browser auto-detect; reached
   from the nav drawer. **Timezone only** for this build — language stays in the
   existing TopBar `LocaleSwitcher`.
4. Full feature now: schema + endpoint + settings page + formatter fixes.

## Migration numbering

This takes **V012** (`V012__user_timezone.sql`). The admin-test-mode spec
(`2026-05-29-admin-test-mode-design.md`) currently reserves V012 for
`test_mode`; since timezone ships first, **test mode shifts to V013** — update
that reference when the test-mode plan is written. (Action: note in the
test-mode spec.)

## Architecture

### Data — `V012__user_timezone.sql`
```sql
ALTER TABLE users ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/Bogota';
```
IANA zone (max 64 chars covers all zone names). Default `America/Bogota` so
existing rows + users who never open settings get a correct-for-most zone, not
raw UTC. Add `timezone` to the `User` JPA entity with getter + setter
(`@Column(name = "timezone")`).

### Backend
- `MeController.MeResponse` gains `String timezone`, populated from
  `u.getTimezone()`.
- New endpoint on `MeController`: `PUT /api/me/timezone` body `{ timezone:
  String }`.
  - JWT-authed; self-only (operates on the caller's own user id from the JWT
    subject — no userId in the path).
  - Validate with `java.time.ZoneId.of(timezone)` inside try/catch; on
    `DateTimeException`/`ZoneRulesException` throw
    `ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timezone")`.
  - Persist via `UserRepository`; return the updated `MeResponse` (or `{
    timezone }` — choose `MeResponse` for consistency so the client can refresh
    its me-state in one call).

### Frontend — formatting helper `lib/format-datetime.ts`
A single module owning instant→string formatting via `Intl.DateTimeFormat` with
an explicit `timeZone`:
```ts
// formatMatchDateTime("2026-06-12T17:00:00Z", "America/Bogota")
//   -> "12 JUN · 12:00"   (Spanish month abbrev, 24h, no seconds)
// formatDeadline(iso, tz) -> "12.JUN 12:00" style used by the lock badge
```
- Uses `Intl.DateTimeFormat("es-CO", { timeZone, day, month, hour, minute,
  hour12: false })` (or manual assembly to keep the exact existing "DD.MMM"
  Spanish-abbrev style — match current visual output, just zone-correct).
- Deterministic across SSR + client because the `timeZone` is passed explicitly
  (not the ambient runtime zone).
- Returns the raw ISO on parse failure (defensive, like today's `deadlineShort`).

### Frontend — replace buggy formatters
Each of these currently formats an instant with UTC or no-zone; route them
through `lib/format-datetime.ts`, passing the current user's `timezone` (already
available where `getMe()` is called, or thread it as a prop):
- `lib/tournament-format.ts` `deadlineShort(iso)` → `deadlineShort(iso, tz)`
  (zone-aware). Callers: group page + knockout page lock badges, ranking
  `updatedAt`.
- `components/matches/MatchTabs.tsx` `formatKickoff` → use the helper with the
  user tz (thread tz from the `/matches` page into `MatchTabs`).
- `components/matches/MatchListItem.tsx` — kickoff label comes from MatchTabs;
  ensure tz flows through.
- `components/group/GroupDrillIn.tsx` `formatKickoff` → helper + tz (thread from
  the group page).
- `components/admin/MatchResultRow.tsx` kickoff formatter → helper + tz (thread
  from the admin results page).
- Date-only helpers (`dateLong`, `dateRangeShort`, `hostLine`, `yearGlyph`,
  `daysUntil`, `formatDayMonth`, `parseISODate`) — UNCHANGED.

The pages that render these already fetch `getMe()` (home, group, matches,
admin) so `me.timezone` is available to thread down; where a page doesn't fetch
me yet, add the `getMe()` call. The `CountdownChip` operates on the date-only
`startDate` (no change).

### Frontend — `/settings` page
- `app/settings/page.tsx` — server component, authed (`redirect("/")` if no
  session), fetches `getMe()` for the current `timezone`. All roles.
- Renders a `TimezoneSetting` client component: a `<select>` of a curated zone
  list relevant to the audience + a "usar la del dispositivo" (use my device's)
  button that reads `Intl.DateTimeFormat().resolvedOptions().timeZone` and
  selects it. Curated list: `America/Bogota`, `America/Caracas`,
  `America/New_York`, `America/Mexico_City`, `America/Argentina/Buenos_Aires`,
  `Europe/Madrid`, `UTC` (covers the realistic spread; auto-detect handles the
  rest — if the detected zone isn't in the list, it's still submittable).
- Save → server action → `PUT /api/me/timezone` → `revalidatePath("/settings")`
  (and the change takes effect app-wide on next navigation since other pages
  re-fetch me).
- Shell: TopBar + BottomNav (no active key — secondary page, per the nav-drawer
  convention).
- Nav drawer: add an "Ajustes" / "Settings" item → `/settings`, visible to ALL
  roles (above the sign-out divider).

### i18n
- `settings` namespace: `title`, `timezoneLabel`, `timezoneHelp`, `useDevice`,
  `save`, `saved`. Spanish source + English mirror, parity.
- `nav.settings` ("Ajustes" / "Settings") for the drawer item.
- A short zone label in the time format (e.g. show times plainly; the settings
  page is where the zone is named — avoid cluttering every row with "COL").
  Decision: do NOT append a per-row zone label; the user set their own zone, so
  times are simply "their" times. (This differs from the earlier fixed-zone idea
  where a label was needed because the zone wasn't the viewer's.)

## Testing

Backend ITs (extend `MeControllerIT` or add one):
- `MeResponse` includes `timezone` (default `America/Bogota` for a fresh user).
- `PUT /api/me/timezone` with a valid zone persists + returns it.
- invalid zone (e.g. `"Mars/Phobos"`) → 400.
- unauth → 401.

Frontend:
- `lib/format-datetime.test.ts`: `2026-06-12T17:00:00Z` → "12:00" in
  `America/Bogota`, "13:00" in `America/Caracas`, "17:00" in `UTC` (asserts the
  zone is actually applied + SSR/client determinism).
- typecheck + lint + build.

## Out of scope
- Language preference on /settings (stays in TopBar switcher).
- Auto-saving the browser zone on first login without user action (we offer the
  button, but default stays Bogotá until the user chooses — avoids a silent
  write + keeps SSR deterministic on first paint).
- Per-row timezone labels.
- Changing date-only field rendering.
- Admin test mode (next feature; it depends on this shipping first, and will use
  V013).

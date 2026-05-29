# Plan 6 — Per-user timezone preference

**Status:** Ready for implementation
**Created:** 2026-05-29
**Design:** [`docs/superpowers/specs/2026-05-29-user-timezone-design.md`](../specs/2026-05-29-user-timezone-design.md)

> **For agentic workers:** REQUIRED SUB-SKILL — use superpowers:subagent-driven-development (preferred) or superpowers:executing-plans. Each task is one commit, test-first where there's testable logic. Backend gates from `backend/`: `./mvnw -B verify` (Spotless runs in verify; if it fails on format, `./mvnw spotless:apply` then re-verify — never hand-format). Frontend gates from `frontend/`: `pnpm typecheck`, `pnpm lint`, `pnpm build`, `pnpm test`. Commit locally; do NOT push until the final task.

**Goal:** Store each user's IANA timezone server-side and render all instant times (match kickoffs, deadlines) in that zone, fixing the raw-UTC display bug; add a `/settings` page to change it.

**Architecture:** New `users.timezone` column (V012, default `America/Bogota`) surfaced via `MeResponse` + a self-only `PUT /api/me/timezone`. A frontend `lib/format-datetime.ts` helper formats instant fields with an explicit `Intl` `timeZone` (no SSR flicker because the zone is server-known). The buggy UTC/no-zone formatters are replaced and fed `me.timezone`. A `/settings` page (reached from the nav drawer) lets users pick a zone or auto-detect from the browser.

**Tech Stack:** Spring Boot 4 + Java 25 + Flyway + Postgres (backend); Next.js 16 App Router + next-intl + Vitest (frontend).

---

## Conventions

- Backend paths under `backend/`, frontend under `frontend/`. Run gates from each.
- Role check / self-only auth pattern: JWT subject is the caller's user id
  (`Long.parseLong(jwt.getSubject())`). `MeController` already does this.
- Migration number: **V012** (`V012__user_timezone.sql`). V011 is the latest
  existing.
- **Instant fields** (full timestamps: `kickoffAt`, `groupStageDeadline`,
  `knockoutDeadline`, ranking `updatedAt`) format in the user's zone.
  **Date-only fields** (`startDate`, `endDate`) stay UTC-parsed — do NOT change
  them.
- Role strings are UPPERCASE (`"ADMIN" | "CAPTAIN" | "PLAYER"`).
- No `Date.now()` in component render (lint `react-hooks/purity`); no unused imports.
- Pre-existing acceptable lint warnings: `app/layout.tsx` custom-font + coverage
  artifacts. Don't add new ones.

## Progress

- [x] Task 1: V012 migration + User.timezone entity field
- [x] Task 2: MeResponse.timezone + PUT /api/me/timezone + IT
- [x] Task 3: lib/format-datetime.ts helper + unit test
- [x] Task 4: lib/api/me.ts type + replace buggy formatters (thread tz)
- [x] Task 5: /settings page + TimezoneSetting + server action + nav drawer item + i18n
- [x] Task 6: verify + ship

---

## Task 1: V012 migration + User.timezone field

**Files:**
- Create: `backend/src/main/resources/db/migration/V012__user_timezone.sql`
- Modify: `backend/src/main/java/io/quiniela/api/user/User.java`
- Create: `backend/src/test/java/io/quiniela/api/support/V012MigrationTest.java`

- [x] **Step 1: Write the failing migration test**

Create `V012MigrationTest.java`:
```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V012MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void usersHaveTimezoneColumnDefaultingToBogota() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'users' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("timezone");

    // Insert a user without specifying timezone; default applies.
    jdbc.update(
        "INSERT INTO users (google_sub, email, role) VALUES ('tz-default', 'tz@example.com', 'player')");
    String tz =
        jdbc.queryForObject(
            "SELECT timezone FROM users WHERE google_sub = 'tz-default'", String.class);
    assertThat(tz).isEqualTo("America/Bogota");
  }
}
```

- [x] **Step 2: Run to verify it fails**

`cd backend && ./mvnw verify` → FAIL (no `timezone` column).

- [x] **Step 3: Write the migration**

Create `V012__user_timezone.sql`:
```sql
-- V012: Per-user display timezone (IANA zone id). Instant fields (match
-- kickoffs, deadlines) are rendered in this zone. Default America/Bogota so
-- existing rows + users who never open settings get a correct-for-most zone
-- rather than raw UTC.

ALTER TABLE users ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/Bogota';
```

- [x] **Step 4: Add the entity field**

In `backend/src/main/java/io/quiniela/api/user/User.java`, add the column field
after `invitePath` (before `createdAt`):
```java
  @Column(nullable = false)
  private String timezone;
```
And add getter + setter near the other accessors:
```java
  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }
```
(The DB default fills it for inserts that omit it — e.g. the `new User(...)`
constructor path doesn't set it, and Hibernate will read the DB-applied value
back. No constructor change needed.)

- [x] **Step 5: Run to verify it passes**

`cd backend && ./mvnw verify` → PASS (V012MigrationTest green; all prior ITs green).

- [x] **Step 6: Commit**
```bash
git add backend/src/main/resources/db/migration/V012__user_timezone.sql \
        backend/src/main/java/io/quiniela/api/user/User.java \
        backend/src/test/java/io/quiniela/api/support/V012MigrationTest.java
git commit -m "feat(backend): V012 — users.timezone column + entity field"
```

---

## Task 2: MeResponse.timezone + PUT /api/me/timezone

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/me/MeController.java`
- Modify: `backend/src/test/java/io/quiniela/api/me/MeControllerIT.java`

- [x] **Step 1: Add failing IT cases**

In `MeControllerIT.java`, add these imports at the top (alongside the existing
ones):
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
```
And add these tests inside the class:
```java
  @Test
  void meIncludesTimezoneDefault() throws Exception {
    var u = new User("g-tz1", "tz1@example.com", "Tz One", null, UserRole.PLAYER);
    u.setInvitePath("tz1-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("America/Bogota"));
  }

  @Test
  void putTimezonePersistsValidZone() throws Exception {
    var u = new User("g-tz2", "tz2@example.com", "Tz Two", null, UserRole.PLAYER);
    u.setInvitePath("tz2-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(
            put("/api/me/timezone")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"America/Caracas\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("America/Caracas"));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.timezone").value("America/Caracas"));
  }

  @Test
  void putTimezoneRejectsInvalidZone() throws Exception {
    var u = new User("g-tz3", "tz3@example.com", "Tz Three", null, UserRole.PLAYER);
    u.setInvitePath("tz3-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(
            put("/api/me/timezone")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Mars/Phobos\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putTimezoneUnauthenticatedReturns401() throws Exception {
    mockMvc
        .perform(
            put("/api/me/timezone")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"UTC\"}"))
        .andExpect(status().isUnauthorized());
  }
```

- [x] **Step 2: Run to verify they fail**

`cd backend && ./mvnw verify` → FAIL (no `timezone` in MeResponse, no PUT route).

- [x] **Step 3: Update MeController**

Rewrite `backend/src/main/java/io/quiniela/api/me/MeController.java`:
```java
package io.quiniela.api.me;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
public class MeController {

  private final UserRepository users;

  public MeController(UserRepository users) {
    this.users = users;
  }

  public record MeResponse(
      Long id,
      String email,
      String displayName,
      String avatarUrl,
      String role,
      String invitePath,
      boolean canInvite,
      Long invitedByUserId,
      String timezone) {}

  public record TimezoneRequest(String timezone) {}

  @GetMapping
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(toResponse(u));
  }

  @PutMapping("/timezone")
  public ResponseEntity<MeResponse> setTimezone(
      @AuthenticationPrincipal Jwt jwt, @RequestBody TimezoneRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();

    String tz = req.timezone();
    if (tz == null || !isValidZone(tz)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timezone");
    }
    u.setTimezone(tz);
    users.save(u);
    return ResponseEntity.ok(toResponse(u));
  }

  private static boolean isValidZone(String tz) {
    try {
      ZoneId.of(tz);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static MeResponse toResponse(User u) {
    return new MeResponse(
        u.getId(),
        u.getEmail(),
        u.getDisplayName(),
        u.getAvatarUrl(),
        u.getRole().name(),
        u.getInvitePath(),
        u.getRole().canInvite(),
        u.getInvitedByUserId(),
        u.getTimezone());
  }
}
```

- [x] **Step 4: Run to verify they pass**

`cd backend && ./mvnw verify` → PASS (4 new cases green; spotless:apply first if format fails).

- [x] **Step 5: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/me/MeController.java \
        backend/src/test/java/io/quiniela/api/me/MeControllerIT.java
git commit -m "feat(backend): MeResponse.timezone + PUT /api/me/timezone"
```

---

## Task 3: lib/format-datetime.ts helper + unit test

**Files:**
- Create: `frontend/lib/format-datetime.ts`
- Create: `frontend/lib/format-datetime.test.ts`

The helper formats an instant (ISO timestamp) in an explicit IANA zone, matching
the app's existing "DD.MMM · HH:MM" Spanish-abbrev visual style. Deterministic on
SSR + client because the zone is passed in, not read from the runtime.

- [x] **Step 1: Write the failing test**

Create `frontend/lib/format-datetime.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { formatMatchDateTime, formatDeadline } from "./format-datetime";

describe("format-datetime", () => {
  const iso = "2026-06-12T17:00:00Z";

  it("formats a match instant in America/Bogota (UTC-5)", () => {
    expect(formatMatchDateTime(iso, "America/Bogota")).toBe("12 JUN · 12:00");
  });

  it("formats the same instant in America/Caracas (UTC-4)", () => {
    expect(formatMatchDateTime(iso, "America/Caracas")).toBe("12 JUN · 13:00");
  });

  it("formats in UTC when asked", () => {
    expect(formatMatchDateTime(iso, "UTC")).toBe("12 JUN · 17:00");
  });

  it("formatDeadline uses the lock-badge DD.MMM HH:MM shape", () => {
    expect(formatDeadline(iso, "America/Bogota")).toBe("12.JUN 12:00");
  });

  it("returns the raw iso on an unparseable input", () => {
    expect(formatMatchDateTime("not-a-date", "America/Bogota")).toBe("not-a-date");
  });

  it("falls back gracefully on a bad zone (returns iso)", () => {
    expect(formatMatchDateTime(iso, "Mars/Phobos")).toBe(iso);
  });
});
```

- [x] **Step 2: Run to verify it fails**

`cd frontend && pnpm test -- format-datetime 2>&1 | tail -15` → FAIL (module missing).

- [x] **Step 3: Implement the helper**

Create `frontend/lib/format-datetime.ts`:
```ts
const MONTH_ABBR_ES = [
  "ENE", "FEB", "MAR", "ABR", "MAY", "JUN",
  "JUL", "AGO", "SEP", "OCT", "NOV", "DIC",
];

/**
 * Extract day-of-month, 0-based month, hour and minute of an instant AS SEEN in
 * a given IANA zone, using Intl so DST and offset are correct. Returns null if
 * the instant or the zone is invalid.
 */
function partsInZone(
  iso: string,
  timeZone: string,
): { day: number; month: number; hour: string; minute: string } | null {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  try {
    const fmt = new Intl.DateTimeFormat("en-US", {
      timeZone,
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
    const parts = fmt.formatToParts(d);
    const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
    const day = Number(get("day"));
    const month = Number(get("month")) - 1;
    let hour = get("hour");
    if (hour === "24") hour = "00"; // Intl can emit "24" at midnight in some envs
    const minute = get("minute");
    if (!day || month < 0) return null;
    return { day, month, hour, minute };
  } catch {
    return null;
  }
}

/** "12 JUN · 12:00" — match row kickoff label, rendered in the user's zone. */
export function formatMatchDateTime(iso: string, timeZone: string): string {
  const p = partsInZone(iso, timeZone);
  if (!p) return iso;
  return `${String(p.day).padStart(2, "0")} ${MONTH_ABBR_ES[p.month]} · ${p.hour}:${p.minute}`;
}

/** "12.JUN 12:00" — lock-badge / deadline label, rendered in the user's zone. */
export function formatDeadline(iso: string, timeZone: string): string {
  const p = partsInZone(iso, timeZone);
  if (!p) return iso;
  return `${String(p.day).padStart(2, "0")}.${MONTH_ABBR_ES[p.month]} ${p.hour}:${p.minute}`;
}
```

- [x] **Step 4: Run to verify it passes**

`cd frontend && pnpm test -- format-datetime 2>&1 | tail -15` → all 6 green.

> If the exact string assertions differ by a leading zero (e.g. "12 JUN" vs
> "12 JUN" is fine, but a single-digit day like "2 JUN" vs "02 JUN"), the
> helper pads day to 2 digits — the tests use day 12 so this won't bite; keep
> the padStart.

- [x] **Step 5: Commit**
```bash
git add frontend/lib/format-datetime.ts frontend/lib/format-datetime.test.ts
git commit -m "feat(frontend): zone-aware instant formatter"
```

---

## Task 4: me.ts type + replace buggy formatters (thread timezone)

**Files:**
- Modify: `frontend/lib/api/me.ts`
- Modify: `frontend/lib/tournament-format.ts`
- Modify: `frontend/app/home/page.tsx`
- Modify: `frontend/app/ranking/page.tsx`
- Modify: `frontend/app/group/[groupId]/page.tsx`
- Modify: `frontend/app/knockout/[roundId]/page.tsx`
- Modify: `frontend/app/matches/page.tsx`
- Modify: `frontend/components/matches/MatchTabs.tsx`
- Modify: `frontend/components/group/GroupDrillIn.tsx`
- Modify: `frontend/app/admin/results/page.tsx`
- Modify: `frontend/components/admin/MatchResultRow.tsx`

No new behavior to unit-test here (covered by Task 3's helper test + existing
page rendering); this task is the wiring. Verify via typecheck + build at the end.

- [x] **Step 1: Add `timezone` to the me type**

In `frontend/lib/api/me.ts`, add `timezone: string;` to the `MeResponse` type
(it's the type returned by `getMe()`):
```ts
export type MeResponse = {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: "ADMIN" | "CAPTAIN" | "PLAYER";
  invitePath: string | null;
  canInvite: boolean;
  invitedByUserId: number | null;
  timezone: string;
};
```

- [x] **Step 2: Make `deadlineShort` zone-aware**

In `frontend/lib/tournament-format.ts`, replace the `deadlineShort` function
with a delegating wrapper (keep the date-only helpers untouched):
```ts
import { formatDeadline } from "./format-datetime";

/** "12.JUN 12:00" — deadline/lock label in the user's timezone. */
export function deadlineShort(iso: string, timeZone: string): string {
  return formatDeadline(iso, timeZone);
}
```
Remove the now-unused `MONTH_ABBR_ES` references ONLY if nothing else in the
file uses them — `formatDayMonth` still uses `MONTH_ABBR_ES`, so KEEP the array.
Remove the old `getUTC*`-based body of `deadlineShort` entirely (replaced above).

- [x] **Step 3: Update `deadlineShort` callers to pass tz**

Each caller fetches `me` and passes `me.timezone`. Pages that already fetch
`getMe`: `home`. Pages that must ADD a `getMe()` call: `ranking`, `group`,
`knockout`.

`app/home/page.tsx` (already has `me`): change
`deadlineShort(bracket.groupStageDeadline)` →
`deadlineShort(bracket.groupStageDeadline, me.timezone)`.

`app/ranking/page.tsx`: add `import { getMe } from "@/lib/api/me";`, fetch
`const me = await getMe();` near the other awaits, and change
`deadlineShort(ranking.updatedAt)` → `deadlineShort(ranking.updatedAt, me.timezone)`.

`app/group/[groupId]/page.tsx`: add `import { getMe } from "@/lib/api/me";`,
fetch `const me = await getMe();`, change
`deadlineShort(bracket.groupStageDeadline)` →
`deadlineShort(bracket.groupStageDeadline, me.timezone)`.

`app/knockout/[roundId]/page.tsx`: add `import { getMe } from "@/lib/api/me";`,
fetch `const me = await getMe();`, change
`deadlineShort(bracket.knockoutDeadline)` →
`deadlineShort(bracket.knockoutDeadline, me.timezone)`.

- [x] **Step 4: Thread tz into MatchTabs + GroupDrillIn (kickoff labels)**

`app/matches/page.tsx`: add `import { getMe } from "@/lib/api/me";`, fetch
`const me = await getMe();`, and pass to the tabs: `<MatchTabs view={view}
timeZone={me.timezone} />`.

`frontend/components/matches/MatchTabs.tsx`: change the signature to
`export function MatchTabs({ view, timeZone }: { view: MatchesView; timeZone: string })`,
add `import { formatMatchDateTime } from "@/lib/format-datetime";`, and replace
the `kickoff: formatKickoff(m.kickoffAt)` label with
`kickoff: formatMatchDateTime(m.kickoffAt, timeZone)`. Delete the local
`formatKickoff` function (now unused).

`frontend/components/group/GroupDrillIn.tsx`: this renders kickoff labels via a
local `formatKickoff` too. Add a `timeZone: string` prop to its signature, add
`import { formatMatchDateTime } from "@/lib/format-datetime";`, replace the local
`formatKickoff(m.kickoffAt)` usage with `formatMatchDateTime(m.kickoffAt,
timeZone)`, and delete the local `formatKickoff`. Then in
`app/group/[groupId]/page.tsx` pass `timeZone={me.timezone}` to `<GroupDrillIn
... />` (me already fetched in Step 3).

- [x] **Step 5: Fix the admin results kickoff formatter**

`frontend/components/admin/MatchResultRow.tsx` currently has, at line ~14:
`export function MatchResultRow({ match }: { match: AdminMatchRow }) {`, uses it
at line ~31: `const kickoff = formatKickoff(match.kickoffAt);`, and defines a
local `getUTC*`-based `formatKickoff` (lines ~139-144) plus its own `MONTH`
array (line ~146) producing the "12.JUN 12:00" shape.

Make these changes:
1. Change the signature to:
   `export function MatchResultRow({ match, timeZone }: { match: AdminMatchRow; timeZone: string }) {`
2. Add at the top: `import { formatDeadline } from "@/lib/format-datetime";`
   (use `formatDeadline` — it produces the exact "DD.MMM HH:MM" shape this row
   already uses; no space-vs-dot mismatch).
3. Change line ~31 to: `const kickoff = formatDeadline(match.kickoffAt, timeZone);`
4. Delete the local `formatKickoff` function AND the now-unused `MONTH` const
   (lint will flag them if left).

Then in `app/admin/results/page.tsx` (which already has `const me = await
getMe();` at line ~18 for the admin gate), change the row mapping at line ~42
from `<MatchResultRow key={m.matchId} match={m} />` to
`<MatchResultRow key={m.matchId} match={m} timeZone={me.timezone} />`.

- [x] **Step 6: Verify gates**
```bash
cd frontend && pnpm typecheck 2>&1 | tail -3
```
Expected: clean — any missed call site (a `deadlineShort` with one arg, or
`MatchTabs`/`GroupDrillIn`/`MatchResultRow` missing the new required prop) shows
up here as a type error. Fix until clean.
```bash
cd frontend && pnpm lint 2>&1 | tail -6
```
Expected: no new errors (watch for unused `formatKickoff`/imports you removed).
```bash
cd frontend && pnpm build 2>&1 | grep -E "Compiled|error|Error" | head
```
Expected: "Compiled successfully".

- [x] **Step 7: Commit**
```bash
git add frontend/lib/api/me.ts frontend/lib/tournament-format.ts \
        frontend/app/home/page.tsx frontend/app/ranking/page.tsx \
        "frontend/app/group/[groupId]/page.tsx" "frontend/app/knockout/[roundId]/page.tsx" \
        frontend/app/matches/page.tsx frontend/components/matches/MatchTabs.tsx \
        frontend/components/group/GroupDrillIn.tsx frontend/app/admin/results/page.tsx \
        frontend/components/admin/MatchResultRow.tsx
git commit -m "fix(frontend): render instant times in the user's timezone"
```

---

## Task 5: /settings page + TimezoneSetting + nav drawer item + i18n

**Files:**
- Create: `frontend/lib/api/settings.ts`
- Create: `frontend/app/settings/page.tsx`
- Create: `frontend/app/settings/actions.ts`
- Create: `frontend/components/settings/TimezoneSetting.tsx`
- Modify: `frontend/components/shell/NavDrawer.tsx`
- Modify: `frontend/messages/es-CO.json`
- Modify: `frontend/messages/en.json`

- [x] **Step 1: API client**

Create `frontend/lib/api/settings.ts`:
```ts
import { api } from "./client";

export async function setTimezone(timezone: string): Promise<void> {
  await api("/api/me/timezone", {
    method: "PUT",
    body: JSON.stringify({ timezone }),
  });
}
```

- [x] **Step 2: Server action**

Create `frontend/app/settings/actions.ts`:
```ts
"use server";

import { revalidatePath } from "next/cache";
import { setTimezone } from "@/lib/api/settings";

export async function saveTimezoneAction(timezone: string): Promise<void> {
  await setTimezone(timezone);
  revalidatePath("/settings");
}
```

- [x] **Step 3: TimezoneSetting client component**

Create `frontend/components/settings/TimezoneSetting.tsx`:
```tsx
"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";

const ZONES = [
  "America/Bogota",
  "America/Caracas",
  "America/New_York",
  "America/Mexico_City",
  "America/Argentina/Buenos_Aires",
  "Europe/Madrid",
  "UTC",
];

export function TimezoneSetting({
  current,
  saveAction,
}: {
  current: string;
  saveAction: (timezone: string) => Promise<void>;
}) {
  const t = useTranslations("settings");
  const [value, setValue] = useState(current);
  const [isPending, startTransition] = useTransition();
  const [saved, setSaved] = useState(false);

  // If the user's saved zone isn't in the curated list, include it so the
  // <select> can show it as the active option.
  const options = ZONES.includes(value) ? ZONES : [value, ...ZONES];

  function save(next: string) {
    setValue(next);
    setSaved(false);
    startTransition(async () => {
      await saveAction(next);
      setSaved(true);
    });
  }

  function useDevice() {
    const detected = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (detected) save(detected);
  }

  return (
    <div className="flex flex-col gap-3">
      <label className="chrome-label chrome-label-muted" htmlFor="tz-select">
        {t("timezoneLabel")}
      </label>
      <select
        id="tz-select"
        value={value}
        disabled={isPending}
        onChange={(e) => save(e.target.value)}
        className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)]"
      >
        {options.map((z) => (
          <option key={z} value={z}>
            {z}
          </option>
        ))}
      </select>
      <button
        type="button"
        onClick={useDevice}
        disabled={isPending}
        className="self-start font-mono text-[11px] font-bold uppercase tracking-[0.12em] text-[var(--color-accent-red)] disabled:opacity-50"
      >
        {t("useDevice")}
      </button>
      <p className="text-xs text-[var(--color-text-muted)]">{t("timezoneHelp")}</p>
      {saved && (
        <span className="chrome-label text-[var(--color-accent-green)]">{t("saved")}</span>
      )}
    </div>
  );
}
```

- [x] **Step 4: Settings page**

Create `frontend/app/settings/page.tsx`:
```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { TimezoneSetting } from "@/components/settings/TimezoneSetting";
import { saveTimezoneAction } from "./actions";

export default async function SettingsPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  const t = await getTranslations("settings");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl px-3 pt-4">
        <section className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4">
          <TimezoneSetting current={me.timezone} saveAction={saveTimezoneAction} />
        </section>
      </div>
      <BottomNav />
    </main>
  );
}
```

- [x] **Step 5: Nav drawer item**

In `frontend/components/shell/NavDrawer.tsx`, add a Settings link visible to ALL
roles, just before the divider/sign-out. After the `role === "ADMIN"` Resultados
block and before the `<div className="my-1 border-t ...">` divider, insert:
```tsx
            <Link href="/settings" className={linkClass} onClick={close}>
              {t("settings")}
            </Link>
```
(`t` is already `useTranslations("nav")` in this component.)

- [x] **Step 6: i18n**

Add to `frontend/messages/es-CO.json`: in the `nav` object add
`"settings": "Ajustes"`, and add a new top-level `settings` namespace:
```json
  "settings": {
    "title": "AJUSTES",
    "timezoneLabel": "Zona horaria",
    "timezoneHelp": "Las horas de los partidos se muestran en esta zona.",
    "useDevice": "Usar la de mi dispositivo",
    "save": "Guardar",
    "saved": "Guardado"
  },
```
Add the mirror to `frontend/messages/en.json`: in `nav` add
`"settings": "Settings"`, and:
```json
  "settings": {
    "title": "SETTINGS",
    "timezoneLabel": "Timezone",
    "timezoneHelp": "Match times are shown in this zone.",
    "useDevice": "Use my device's timezone",
    "save": "Save",
    "saved": "Saved"
  },
```

- [x] **Step 7: Verify gates + parity**
```bash
cd frontend && node -e "const es=require('./messages/es-CO.json'), en=require('./messages/en.json'); const p=(o)=>Object.keys(o).sort().join(','); console.log('nav parity:', p(es.nav)===p(en.nav)); console.log('settings parity:', p(es.settings)===p(en.settings));"
```
Expected: both `true`.
```bash
cd frontend && pnpm typecheck 2>&1 | tail -3 && pnpm lint 2>&1 | tail -6 && pnpm build 2>&1 | grep -E "Compiled|settings|error|Error" | head
```
Expected: typecheck clean; no new lint; build compiles with `/settings` route.

- [x] **Step 8: Commit**
```bash
git add frontend/lib/api/settings.ts frontend/app/settings/ \
        frontend/components/settings/TimezoneSetting.tsx \
        frontend/components/shell/NavDrawer.tsx \
        frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(frontend): /settings timezone preference + nav drawer item"
```

---

## Task 6: Verify end-to-end + ship

- [x] **Step 1: Full backend verify**
`cd backend && ./mvnw -B verify` — all ITs green (V012MigrationTest + 4 new
MeControllerIT cases included).

- [x] **Step 2: Full frontend sweep**
`cd frontend && pnpm typecheck && pnpm lint && pnpm test && pnpm build` —
typecheck clean; lint only the 2 pre-existing warnings; unit tests green
(format-datetime suite present); build compiles.

- [x] **Step 3: Confirm date-only fields untouched**
`git diff b4bd2e6..HEAD -- frontend/lib/tournament-format.ts` — confirm
`dateLong`, `dateRangeShort`, `hostLine`, `yearGlyph`, `daysUntil`,
`formatDayMonth`, `parseISODate` are unchanged; only `deadlineShort` changed.

- [x] **Step 4: Tick plan checkboxes, commit, push**
Mark all Progress + verification checkboxes `[x]`, then:
```bash
git add docs/superpowers/plans/2026-05-29-quiniela-plan-6-user-timezone.md
git commit -m "docs: Plan 6 complete — per-user timezone"
git push origin master
```

- [x] **Step 5: Watch CI + smoke prod**
Watch backend + frontend CI to green (`gh run watch <id> --exit-status`). Then:
```bash
curl -sS -o /dev/null -w "%{http_code}\n" https://laquinieladelospanas.com/
curl -sS -o /dev/null -w "%{http_code}\n" https://quiniela-api-ko2t5go6hq-uc.a.run.app/api/me
```
Expected: landing 200; `/api/me` 401 (unauth, route exists). Full timezone
behavior verified logged-in during the manual dry-run.

**Verification:**
- [x] Backend `./mvnw verify` green (V012 + MeController tz tests)
- [x] Frontend typecheck + lint + test + build clean
- [x] Date-only formatters unchanged
- [x] Backend + frontend CI green on `master`
- [x] `/settings` reachable; `/api/me/timezone` 401 unauth in prod

---

## Out of scope
Language on /settings (stays in TopBar); auto-saving the browser zone on login;
per-row zone labels; changing date-only field rendering; admin test mode (next,
uses V013).

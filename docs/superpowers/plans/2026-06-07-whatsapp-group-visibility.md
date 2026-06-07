# Hierarchical WhatsApp-Group Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Join the community WhatsApp group" card on the home page, gated by a two-level hierarchy — admin owns the link + a master switch, each captain opts in their own players per-player (default hidden).

**Architecture:** Pool-level columns hold the link + master switch; a per-user column holds per-player visibility. The home page reads ONE resolved field `me.whatsappGroupUrl` (computed server-side in `MeController`), so the link never touches a public endpoint. Admin edits the link/master in `/admin/config`; captains/admin manage per-player visibility in a new `/captain/whatsapp` roster.

**Tech Stack:** Spring Boot 4 + Postgres (Flyway, Testcontainers ITs, Docker) backend; Next.js 16 + React 19 + TS + Vitest/RTL frontend. Backend from `.worktrees/whatsapp-group/backend`; frontend from `.worktrees/whatsapp-group/frontend`.

---

## File Structure

**Backend — create**
- `db/migration/V020__whatsapp_group.sql` — 3 columns
- `admin/AdminCommunityConfigService.java` + `AdminCommunityConfigController.java` — admin link/master config
- `captain/CaptainWhatsappService.java` + `CaptainWhatsappController.java` — roster + per-player toggle

**Backend — modify**
- `pool/Pool.java` — `whatsappGroupUrl`, `whatsappGroupEnabled`
- `user/User.java` — `whatsappGroupVisible`; `user/UserRepository.java` — roster query
- `me/MeController.java` — resolve + expose `whatsappGroupUrl`
- IT files alongside

**Frontend — create**
- `components/lobby/WhatsappGroupCard.tsx` (+ test) — the home card
- `lib/api/community-config.ts` — admin config client
- `components/admin/CommunityConfigPanel.tsx` (+ test) — admin section panel
- `lib/api/captain-whatsapp.ts` — roster/toggle client
- `app/captain/whatsapp/page.tsx` + `app/captain/whatsapp/actions.ts` — captain page
- `components/captain/WhatsappRosterRow.tsx` (+ test) — per-row toggle

**Frontend — modify**
- `lib/api/me.ts` — `whatsappGroupUrl`
- `app/home/page.tsx` — render the card
- `app/admin/config/page.tsx` + `app/admin/config/actions.ts` — Comunidad section
- `components/shell/NavDrawer.tsx` — captain/admin link
- `messages/es-CO.json`, `messages/en.json` — i18n

---

## Task 1: Backend — schema, entity fields, and the `me` resolution

**Files:**
- Create: `backend/src/main/resources/db/migration/V020__whatsapp_group.sql`
- Modify: `backend/src/main/java/io/quiniela/api/pool/Pool.java`, `backend/src/main/java/io/quiniela/api/user/User.java`, `backend/src/main/java/io/quiniela/api/me/MeController.java`
- Test: `backend/src/test/java/io/quiniela/api/me/MeControllerIT.java` (create if absent; otherwise add to the existing me IT)

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V020__whatsapp_group.sql`:
```sql
-- V020: Community WhatsApp group link + two-level visibility.
--   pool.whatsapp_group_url      — the invite link (null/blank = unset)
--   pool.whatsapp_group_enabled  — admin master switch ("available to captains")
--   users.whatsapp_group_visible — per-player opt-in, flipped by their inviter
-- Default hidden everywhere: the feature is dark until the admin enables it AND
-- (for players) their captain turns them on.
ALTER TABLE pool  ADD COLUMN whatsapp_group_url     VARCHAR(255);
ALTER TABLE pool  ADD COLUMN whatsapp_group_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN whatsapp_group_visible BOOLEAN NOT NULL DEFAULT false;
```

- [ ] **Step 2: Add entity fields**

In `Pool.java`, after the `houseCutPercentage` field, add:
```java
  @Column(name = "whatsapp_group_url")
  private String whatsappGroupUrl;

  @Column(name = "whatsapp_group_enabled", nullable = false)
  private Boolean whatsappGroupEnabled = false;
```
Add getters/setters (match the file's existing accessor style):
```java
  public String getWhatsappGroupUrl() {
    return whatsappGroupUrl;
  }

  public void setWhatsappGroupUrl(String whatsappGroupUrl) {
    this.whatsappGroupUrl = whatsappGroupUrl;
  }

  public Boolean getWhatsappGroupEnabled() {
    return whatsappGroupEnabled;
  }

  public void setWhatsappGroupEnabled(Boolean whatsappGroupEnabled) {
    this.whatsappGroupEnabled = whatsappGroupEnabled;
  }
```
In `User.java`, after the `isBot` field, add:
```java
  @Column(name = "whatsapp_group_visible", nullable = false)
  private Boolean whatsappGroupVisible = false;
```
and getter/setter:
```java
  public Boolean getWhatsappGroupVisible() {
    return whatsappGroupVisible;
  }

  public void setWhatsappGroupVisible(Boolean whatsappGroupVisible) {
    this.whatsappGroupVisible = whatsappGroupVisible;
  }
```

- [ ] **Step 3: Write the failing IT for the resolution matrix**

Read the existing me-endpoint IT first (find it under `backend/src/test/java/io/quiniela/api/me/` or wherever `/api/me` is tested — `grep -rl "api/me" backend/src/test`). Mirror its setup helpers (user save, jwt issue, mockMvc). Add (adapt helper names to the file's real ones):
```java
  @Test
  void meExposesWhatsappUrlOnlyToEligibleViewers() throws Exception {
    // pool 1 has the link configured + enabled
    jdbc.update(
        "UPDATE pool SET whatsapp_group_url = 'https://chat.whatsapp.com/ABC', "
            + "whatsapp_group_enabled = true WHERE id = 1");

    var admin = saveUser("g-wa-admin", "admin@wa.test", "WA Admin", io.quiniela.api.user.UserRole.ADMIN);
    var captain = saveUser("g-wa-cap", "cap@wa.test", "WA Cap", io.quiniela.api.user.UserRole.CAPTAIN);
    var shown = saveUser("g-wa-on", "on@wa.test", "Player On", io.quiniela.api.user.UserRole.PLAYER);
    var hidden = saveUser("g-wa-off", "off@wa.test", "Player Off", io.quiniela.api.user.UserRole.PLAYER);
    jdbc.update("UPDATE users SET whatsapp_group_visible = true WHERE id = ?", shown.getId());

    expectUrl(admin, "https://chat.whatsapp.com/ABC");
    expectUrl(captain, "https://chat.whatsapp.com/ABC");
    expectUrl(shown, "https://chat.whatsapp.com/ABC");
    expectNullUrl(hidden);

    // master off → nobody, even admin
    jdbc.update("UPDATE pool SET whatsapp_group_enabled = false WHERE id = 1");
    expectNullUrl(admin);
  }

  private void expectUrl(io.quiniela.api.user.User u, String url) throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + jwt.issue(u)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.whatsappGroupUrl").value(url));
  }

  private void expectNullUrl(io.quiniela.api.user.User u) throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + jwt.issue(u)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.whatsappGroupUrl").value(org.hamcrest.Matchers.nullValue()));
  }
```
If the file has no `saveUser(sub,email,name,role)` helper, write users with `users.save(new User(sub, email, name, null, role))` inline instead.

- [ ] **Step 4: Run it, expect FAIL**

Run (from `backend/`): `./mvnw -q -Dtest=MeControllerIT test`
Expected: FAIL — `whatsappGroupUrl` path missing (and/or no such IT yet → create it mirroring the existing me test class annotations).

- [ ] **Step 5: Resolve + expose in `MeController`**

In `MeController.java`: inject `PoolRepository` (constructor) and import `io.quiniela.api.pool.Pool`, `io.quiniela.api.pool.PoolRepository`, `io.quiniela.api.user.UserRole`. Add `whatsappGroupUrl` as the last field of `MeResponse`:
```java
  public record MeResponse(
      Long id,
      String email,
      String displayName,
      String avatarUrl,
      String role,
      String invitePath,
      boolean canInvite,
      Long invitedByUserId,
      String timezone,
      String whatsappGroupUrl) {}
```
Add the constant + resolver and thread the pool through `toResponse`:
```java
  private static final Long ACTIVE_POOL_ID = 1L;

  private static String resolveWhatsappUrl(User u, Pool pool) {
    String url = pool.getWhatsappGroupUrl();
    if (url == null || url.isBlank() || !Boolean.TRUE.equals(pool.getWhatsappGroupEnabled())) {
      return null;
    }
    if (u.getRole() == UserRole.ADMIN || u.getRole() == UserRole.CAPTAIN) {
      return url;
    }
    return Boolean.TRUE.equals(u.getWhatsappGroupVisible()) ? url : null;
  }
```
Change `toResponse` to be a non-static instance method (or static taking the pool) that fetches the pool and passes the resolved url:
```java
  private MeResponse toResponse(User u) {
    Pool pool = pools.findById(ACTIVE_POOL_ID).orElse(null);
    String waUrl = pool == null ? null : resolveWhatsappUrl(u, pool);
    return new MeResponse(
        u.getId(),
        u.getEmail(),
        u.getDisplayName(),
        u.getAvatarUrl(),
        u.getRole().name(),
        u.getInvitePath(),
        u.getRole().canInvite(),
        u.getInvitedByUserId(),
        u.getTimezone(),
        waUrl);
  }
```
(`pools` is the injected `PoolRepository` field. Remove `static` from `toResponse`; both call sites — `me()` and `setTimezone()` — already call `toResponse(u)` on the instance, so they keep working.)

- [ ] **Step 6: Run it, expect PASS**

Run (from `backend/`): `./mvnw -q -Dtest=MeControllerIT test` → PASS. If formatting is rejected, `./mvnw -q spotless:apply` first.

- [ ] **Step 7: Commit**
```bash
git add backend/src/main/resources/db/migration/V020__whatsapp_group.sql backend/src/main/java/io/quiniela/api/pool/Pool.java backend/src/main/java/io/quiniela/api/user/User.java backend/src/main/java/io/quiniela/api/me/MeController.java backend/src/test/java/io/quiniela/api/me/MeControllerIT.java
git commit -m "feat(whatsapp): schema + me resolution for community group visibility"
```

---

## Task 2: Backend — admin community config (link + master switch)

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/admin/AdminCommunityConfigService.java`, `backend/src/main/java/io/quiniela/api/admin/AdminCommunityConfigController.java`
- Test: `backend/src/test/java/io/quiniela/api/admin/AdminCommunityConfigIT.java`

> Mirror the existing `io.quiniela.api.payment.AdminPoolConfigService` + `AdminPoolConfigController` exactly for structure (constant `ACTIVE_POOL_ID = 1L`, `PoolRepository` + `AdminGuard`, `requireAdmin`, view/update records, `@ExceptionHandler(IllegalArgumentException)` → 400). Read those two files first.

- [ ] **Step 1: Write the failing IT**

Create `AdminCommunityConfigIT.java` (mirror the annotations/superclass of `AdminPoolConfigService`'s IT — look for an existing admin config IT to copy the harness):
```java
  @Test
  void adminReadsAndUpdatesCommunityConfig() throws Exception {
    var admin = saveUser("g-cc-admin", "a@cc.test", "CC Admin", io.quiniela.api.user.UserRole.ADMIN);
    String token = jwt.issue(admin);

    // enable with a valid url
    mockMvc
        .perform(
            put("/api/admin/community-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://chat.whatsapp.com/ABC\",\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://chat.whatsapp.com/ABC"))
        .andExpect(jsonPath("$.enabled").value(true));

    // enabling with a non-whatsapp url is rejected
    mockMvc
        .perform(
            put("/api/admin/community-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://evil.example/x\",\"enabled\":true}"))
        .andExpect(status().isBadRequest());

    // a player is forbidden
    var player = saveUser("g-cc-p", "p@cc.test", "P", io.quiniela.api.user.UserRole.PLAYER);
    mockMvc
        .perform(get("/api/admin/community-config").header("Authorization", "Bearer " + jwt.issue(player)))
        .andExpect(status().isForbidden());
  }
```

- [ ] **Step 2: Run it, expect FAIL**

Run: `./mvnw -q -Dtest=AdminCommunityConfigIT test` → FAIL (no endpoint).

- [ ] **Step 3: Implement the service**

Create `AdminCommunityConfigService.java`:
```java
package io.quiniela.api.admin;

import io.quiniela.api.pool.Pool;
import io.quiniela.api.pool.PoolRepository;
import io.quiniela.api.user.AdminGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only community config: the WhatsApp group link + the "available to captains" switch. */
@Service
public class AdminCommunityConfigService {

  private static final Long ACTIVE_POOL_ID = 1L;
  private static final String WHATSAPP_PREFIX = "https://chat.whatsapp.com/";

  private final PoolRepository pools;
  private final AdminGuard adminGuard;

  public AdminCommunityConfigService(PoolRepository pools, AdminGuard adminGuard) {
    this.pools = pools;
    this.adminGuard = adminGuard;
  }

  public record CommunityConfigView(String url, boolean enabled) {}

  public record UpdateRequest(String url, Boolean enabled) {}

  @Transactional(readOnly = true)
  public CommunityConfigView getConfig(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return view(pool());
  }

  @Transactional
  public CommunityConfigView updateConfig(Long callerId, UpdateRequest req) {
    adminGuard.requireAdmin(callerId);
    boolean enabled = Boolean.TRUE.equals(req.enabled());
    String url = req.url() == null ? null : req.url().trim();
    if (url != null && url.isBlank()) {
      url = null;
    }
    if (enabled) {
      if (url == null || !url.startsWith(WHATSAPP_PREFIX)) {
        throw new IllegalArgumentException(
            "A valid WhatsApp invite link (" + WHATSAPP_PREFIX + "…) is required to enable");
      }
    }
    Pool pool = pool();
    pool.setWhatsappGroupUrl(url);
    pool.setWhatsappGroupEnabled(enabled);
    pools.save(pool);
    return view(pool);
  }

  private Pool pool() {
    return pools
        .findById(ACTIVE_POOL_ID)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pool not found"));
  }

  private CommunityConfigView view(Pool pool) {
    return new CommunityConfigView(
        pool.getWhatsappGroupUrl(), Boolean.TRUE.equals(pool.getWhatsappGroupEnabled()));
  }
}
```

- [ ] **Step 4: Implement the controller**

Create `AdminCommunityConfigController.java` (mirror `AdminPoolConfigController`):
```java
package io.quiniela.api.admin;

import io.quiniela.api.admin.AdminCommunityConfigService.CommunityConfigView;
import io.quiniela.api.admin.AdminCommunityConfigService.UpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only community config: WhatsApp group link + master switch. */
@RestController
@RequestMapping("/api/admin/community-config")
public class AdminCommunityConfigController {

  private final AdminCommunityConfigService service;

  public AdminCommunityConfigController(AdminCommunityConfigService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<CommunityConfigView> get(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getConfig(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping
  public ResponseEntity<CommunityConfigView> update(
      @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.updateConfig(Long.parseLong(jwt.getSubject()), req));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
```
(`AdminGuard.requireAdmin` throws a `ResponseStatusException(FORBIDDEN)` for non-admins — that yields the 403 the IT expects. Confirm by reading `AdminGuard`.)

- [ ] **Step 5: Run it, expect PASS**

Run: `./mvnw -q -Dtest=AdminCommunityConfigIT test` → PASS. `spotless:apply` if needed.

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/admin/AdminCommunityConfigService.java backend/src/main/java/io/quiniela/api/admin/AdminCommunityConfigController.java backend/src/test/java/io/quiniela/api/admin/AdminCommunityConfigIT.java
git commit -m "feat(whatsapp): admin community-config endpoint (link + master switch)"
```

---

## Task 3: Backend — captain roster + per-player toggle

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/captain/CaptainWhatsappService.java`, `backend/src/main/java/io/quiniela/api/captain/CaptainWhatsappController.java`
- Modify: `backend/src/main/java/io/quiniela/api/user/UserRepository.java`
- Test: `backend/src/test/java/io/quiniela/api/captain/CaptainWhatsappIT.java`

- [ ] **Step 1: Add the roster query**

In `UserRepository.java`, import `io.quiniela.api.user.UserRole` is same-package (no import needed) and `java.util.List`, then add:
```java
  java.util.List<User> findByInvitedByUserIdAndRoleOrderByDisplayNameAsc(Long inviterId, UserRole role);
```

- [ ] **Step 2: Write the failing IT**

Create `CaptainWhatsappIT.java` (mirror an existing controller IT harness):
```java
  @Test
  void captainSeesOwnPlayersAndTogglesThem() throws Exception {
    var cap = saveUser("g-cw-cap", "cap@cw.test", "Cap", io.quiniela.api.user.UserRole.CAPTAIN);
    var mine = saveUser("g-cw-mine", "mine@cw.test", "Mine", io.quiniela.api.user.UserRole.PLAYER);
    var theirs = saveUser("g-cw-theirs", "theirs@cw.test", "Theirs", io.quiniela.api.user.UserRole.PLAYER);
    jdbc.update("UPDATE users SET invited_by_user_id = ? WHERE id = ?", cap.getId(), mine.getId());
    // theirs invited by someone else
    var other = saveUser("g-cw-other", "other@cw.test", "Other", io.quiniela.api.user.UserRole.CAPTAIN);
    jdbc.update("UPDATE users SET invited_by_user_id = ? WHERE id = ?", other.getId(), theirs.getId());

    String token = jwt.issue(cap);
    // roster shows only my player
    mockMvc
        .perform(get("/api/captain/whatsapp-roster").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$..userId").value(org.hamcrest.Matchers.contains(mine.getId().intValue())));

    // toggling my player on succeeds
    mockMvc
        .perform(
            put("/api/captain/whatsapp-visibility")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + mine.getId() + ",\"visible\":true}"))
        .andExpect(status().isOk());
    Boolean v =
        jdbc.queryForObject(
            "SELECT whatsapp_group_visible FROM users WHERE id = ?", Boolean.class, mine.getId());
    org.junit.jupiter.api.Assertions.assertTrue(v);

    // toggling someone else's player is forbidden
    mockMvc
        .perform(
            put("/api/captain/whatsapp-visibility")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + theirs.getId() + ",\"visible\":true}"))
        .andExpect(status().isForbidden());

    // a player calling the roster is forbidden
    mockMvc
        .perform(get("/api/captain/whatsapp-roster").header("Authorization", "Bearer " + jwt.issue(mine)))
        .andExpect(status().isForbidden());
  }
```

- [ ] **Step 3: Run it, expect FAIL**

Run: `./mvnw -q -Dtest=CaptainWhatsappIT test` → FAIL (no endpoint).

- [ ] **Step 4: Implement the service**

Create `CaptainWhatsappService.java`:
```java
package io.quiniela.api.captain;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lets a captain (or the admin, for their direct invitees) control which of their PLAYER invitees
 * may see the community WhatsApp link. A caller may only read/flip users they personally invited.
 */
@Service
public class CaptainWhatsappService {

  private final UserRepository users;

  public CaptainWhatsappService(UserRepository users) {
    this.users = users;
  }

  public record RosterEntry(Long userId, String displayName, boolean visible) {}

  public record VisibilityRequest(Long userId, Boolean visible) {}

  @Transactional(readOnly = true)
  public List<RosterEntry> roster(Long callerId) {
    requireInviter(callerId);
    return users.findByInvitedByUserIdAndRoleOrderByDisplayNameAsc(callerId, UserRole.PLAYER).stream()
        .map(
            u ->
                new RosterEntry(
                    u.getId(), u.getDisplayName(), Boolean.TRUE.equals(u.getWhatsappGroupVisible())))
        .toList();
  }

  @Transactional
  public void setVisibility(Long callerId, VisibilityRequest req) {
    requireInviter(callerId);
    if (req.userId() == null) {
      throw new IllegalArgumentException("userId is required");
    }
    User target =
        users
            .findById(req.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (!callerId.equals(target.getInvitedByUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your invitee");
    }
    target.setWhatsappGroupVisible(Boolean.TRUE.equals(req.visible()));
    users.save(target);
  }

  private void requireInviter(Long callerId) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    if (caller.getRole() != UserRole.ADMIN && caller.getRole() != UserRole.CAPTAIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Captains only");
    }
  }
}
```

- [ ] **Step 5: Implement the controller**

Create `CaptainWhatsappController.java`:
```java
package io.quiniela.api.captain;

import io.quiniela.api.captain.CaptainWhatsappService.RosterEntry;
import io.quiniela.api.captain.CaptainWhatsappService.VisibilityRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Captain/admin: manage which of their PLAYER invitees may see the WhatsApp group link. */
@RestController
@RequestMapping("/api/captain")
public class CaptainWhatsappController {

  private final CaptainWhatsappService service;

  public CaptainWhatsappController(CaptainWhatsappService service) {
    this.service = service;
  }

  @GetMapping("/whatsapp-roster")
  public ResponseEntity<List<RosterEntry>> roster(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.roster(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping("/whatsapp-visibility")
  public ResponseEntity<Void> setVisibility(
      @AuthenticationPrincipal Jwt jwt, @RequestBody VisibilityRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    service.setVisibility(Long.parseLong(jwt.getSubject()), req);
    return ResponseEntity.ok().build();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
```

- [ ] **Step 6: Run it, expect PASS**

Run: `./mvnw -q -Dtest=CaptainWhatsappIT test` → PASS. `spotless:apply` if needed.

- [ ] **Step 7: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/captain backend/src/main/java/io/quiniela/api/user/UserRepository.java backend/src/test/java/io/quiniela/api/captain/CaptainWhatsappIT.java
git commit -m "feat(whatsapp): captain roster + per-player visibility toggle"
```

---

## Task 4: Frontend — home card

**Files:**
- Modify: `frontend/lib/api/me.ts`, `frontend/app/home/page.tsx`, `frontend/messages/es-CO.json`, `frontend/messages/en.json`
- Create: `frontend/components/lobby/WhatsappGroupCard.tsx`, `frontend/components/lobby/WhatsappGroupCard.test.tsx`

- [ ] **Step 1: Type + i18n**

In `frontend/lib/api/me.ts`, add to `MeResponse` (after `timezone`):
```ts
  whatsappGroupUrl: string | null;
```
In `messages/es-CO.json` `lobby` object add:
```json
    "whatsappGroupTitle": "ÚNETE AL GRUPO",
    "whatsappGroupSubtitle": "Coordina con las panas por WhatsApp",
    "whatsappGroupManage": "Gestionar quién lo ve",
```
In `messages/en.json` `lobby` object add:
```json
    "whatsappGroupTitle": "JOIN THE GROUP",
    "whatsappGroupSubtitle": "Chat with everyone on WhatsApp",
    "whatsappGroupManage": "Manage who sees it",
```
(Keep JSON valid — mind the comma on the line you insert after.)

- [ ] **Step 2: Write the failing test**

Create `frontend/components/lobby/WhatsappGroupCard.test.tsx`:
```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import { WhatsappGroupCard } from "./WhatsappGroupCard";

const messages = {
  lobby: {
    whatsappGroupTitle: "ÚNETE AL GRUPO",
    whatsappGroupSubtitle: "Coordina con las panas por WhatsApp",
    whatsappGroupManage: "Gestionar quién lo ve",
  },
};

function renderCard(props: { url: string; canManage: boolean }) {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <WhatsappGroupCard {...props} />
    </NextIntlClientProvider>,
  );
}

describe("WhatsappGroupCard", () => {
  it("links to the group url in a new tab", () => {
    renderCard({ url: "https://chat.whatsapp.com/ABC", canManage: false });
    const link = screen.getByRole("link", { name: /únete al grupo/i });
    expect(link).toHaveAttribute("href", "https://chat.whatsapp.com/ABC");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("shows the manage link only when canManage", () => {
    const { rerender } = renderCard({ url: "https://chat.whatsapp.com/ABC", canManage: false });
    expect(screen.queryByRole("link", { name: /gestionar/i })).not.toBeInTheDocument();
    rerender(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <WhatsappGroupCard url="https://chat.whatsapp.com/ABC" canManage />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("link", { name: /gestionar/i })).toBeInTheDocument();
  });
});
```
> NOTE: `WhatsappGroupCard` is a server component but uses only `useTranslations` from next-intl, which works under `NextIntlClientProvider` in tests. If the project's other server-component tests use a different harness (check an existing `*.test.tsx` for a server component like `FocusCard`), mirror that instead.

- [ ] **Step 3: Run it, expect FAIL**

Run (from `frontend/`): `pnpm vitest run components/lobby/WhatsappGroupCard.test.tsx` → FAIL (module not found).

- [ ] **Step 4: Implement the card**

Create `frontend/components/lobby/WhatsappGroupCard.tsx` (reuse the WhatsApp glyph path from `components/invite/InviteFriendsSheet.tsx`):
```tsx
import Link from "next/link";
import { useTranslations } from "next-intl";

/**
 * Home "join the community WhatsApp group" card. Server component — the parent
 * (home page) only renders it when the resolved url is present, so visibility
 * is already decided server-side. `canManage` adds a captain/admin link to the
 * per-player roster.
 */
export function WhatsappGroupCard({ url, canManage }: { url: string; canManage: boolean }) {
  const t = useTranslations("lobby");
  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-3 px-4 py-3 hover:bg-[var(--color-accent-gold)]"
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center bg-[#25d366] text-[var(--color-bg-ink)]">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893A11.821 11.821 0 0 0 20.464 3.488" />
          </svg>
        </span>
        <span className="min-w-0">
          <span className="block font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)]">
            {t("whatsappGroupTitle")}
          </span>
          <span className="block truncate text-xs text-[var(--color-text-muted)]">
            {t("whatsappGroupSubtitle")}
          </span>
        </span>
      </a>
      {canManage && (
        <Link
          href="/captain/whatsapp"
          className="block border-t-[1.5px] border-dashed border-[var(--color-line-ink)] px-4 py-2 text-right font-display text-[11px] font-bold uppercase tracking-[0.04em] text-[var(--color-text-muted)] hover:text-[var(--color-accent-red)]"
        >
          {t("whatsappGroupManage")} →
        </Link>
      )}
    </div>
  );
}
```

- [ ] **Step 5: Wire it into the home page**

In `frontend/app/home/page.tsx`, add the import:
```tsx
import { WhatsappGroupCard } from "@/components/lobby/WhatsappGroupCard";
```
After the existing invite `<section>` (the one wrapping `InviteFriendsButton`), add:
```tsx
        {me.whatsappGroupUrl && (
          <section className="mx-3 mt-3">
            <WhatsappGroupCard url={me.whatsappGroupUrl} canManage={me.role !== "PLAYER"} />
          </section>
        )}
```

- [ ] **Step 6: Run tests + typecheck, expect PASS**

Run (from `frontend/`): `pnpm vitest run components/lobby/WhatsappGroupCard.test.tsx` → PASS; `pnpm typecheck` → exit 0.

- [ ] **Step 7: Commit**
```bash
git add frontend/lib/api/me.ts frontend/app/home/page.tsx frontend/components/lobby/WhatsappGroupCard.tsx frontend/components/lobby/WhatsappGroupCard.test.tsx frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(whatsapp): home community-group card"
```

---

## Task 5: Frontend — admin "Comunidad" config section

**Files:**
- Create: `frontend/lib/api/community-config.ts`, `frontend/components/admin/CommunityConfigPanel.tsx`, `frontend/components/admin/CommunityConfigPanel.test.tsx`
- Modify: `frontend/app/admin/config/actions.ts`, `frontend/app/admin/config/page.tsx`, `frontend/messages/es-CO.json`, `frontend/messages/en.json`

> Mirror `frontend/lib/api/pool-config.ts`, `frontend/app/admin/config/actions.ts` (`savePoolConfigAction`), and `frontend/components/admin/PoolConfigPanel.tsx`. Read those first.

- [ ] **Step 1: API client**

Create `frontend/lib/api/community-config.ts`:
```ts
import { api } from "./client";

export type CommunityConfig = { url: string | null; enabled: boolean };

export async function getCommunityConfig(): Promise<CommunityConfig> {
  return api<CommunityConfig>("/api/admin/community-config");
}

export async function updateCommunityConfig(input: {
  url: string;
  enabled: boolean;
}): Promise<CommunityConfig> {
  return api<CommunityConfig>("/api/admin/community-config", {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
```

- [ ] **Step 2: Server action**

In `frontend/app/admin/config/actions.ts`, add the import and action (mirror `savePoolConfigAction`'s try/catch + `revalidatePath`):
```ts
import { updateCommunityConfig } from "@/lib/api/community-config";
```
```ts
export async function saveCommunityConfigAction(input: {
  url: string;
  enabled: boolean;
}): Promise<SaveResult> {
  try {
    await updateCommunityConfig(input);
    revalidatePath("/admin/config");
    return { ok: true };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, error: e.message };
    throw e;
  }
}
```

- [ ] **Step 3: i18n keys**

In `messages/es-CO.json` `adminConfig` object add:
```json
    "community": "Comunidad",
    "whatsappUrl": "Enlace del grupo de WhatsApp",
    "whatsappUrlHelp": "Debe empezar con https://chat.whatsapp.com/",
    "whatsappEnabled": "Disponible para los capitanes",
    "save": "Guardar",
    "saved": "Guardado",
```
> If `adminConfig.save`/`saved` already exist, do NOT duplicate them — reuse the existing keys in the panel. Check first.

In `messages/en.json` `adminConfig` object add the English equivalents:
```json
    "community": "Community",
    "whatsappUrl": "WhatsApp group link",
    "whatsappUrlHelp": "Must start with https://chat.whatsapp.com/",
    "whatsappEnabled": "Available to captains",
```
(plus `save`/`saved` only if not already present).

- [ ] **Step 4: Write the failing panel test**

Create `frontend/components/admin/CommunityConfigPanel.test.tsx`:
```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { CommunityConfigPanel } from "./CommunityConfigPanel";

const messages = {
  adminConfig: {
    whatsappUrl: "Enlace del grupo de WhatsApp",
    whatsappUrlHelp: "Debe empezar con https://chat.whatsapp.com/",
    whatsappEnabled: "Disponible para los capitanes",
    save: "Guardar",
    saved: "Guardado",
  },
};

function renderPanel(saveAction = vi.fn().mockResolvedValue({ ok: true })) {
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <CommunityConfigPanel
        config={{ url: null, enabled: false }}
        saveAction={saveAction}
      />
    </NextIntlClientProvider>,
  );
  return saveAction;
}

describe("CommunityConfigPanel", () => {
  it("submits the url and enabled flag", async () => {
    const saveAction = renderPanel();
    await userEvent.type(
      screen.getByLabelText(/enlace del grupo/i),
      "https://chat.whatsapp.com/ABC",
    );
    await userEvent.click(screen.getByLabelText(/disponible para los capitanes/i));
    await userEvent.click(screen.getByRole("button", { name: /guardar/i }));
    expect(saveAction).toHaveBeenCalledWith({
      url: "https://chat.whatsapp.com/ABC",
      enabled: true,
    });
  });
});
```

- [ ] **Step 5: Run it, expect FAIL**

Run: `pnpm vitest run components/admin/CommunityConfigPanel.test.tsx` → FAIL (module not found).

- [ ] **Step 6: Implement the panel**

Create `frontend/components/admin/CommunityConfigPanel.tsx` (client component; mirror `PoolConfigPanel`'s state/save/feedback pattern — read it to match the save button + status styling):
```tsx
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { CommunityConfig } from "@/lib/api/community-config";

type SaveResult = { ok: true } | { ok: false; error: string };

export function CommunityConfigPanel({
  config,
  saveAction,
}: {
  config: CommunityConfig;
  saveAction: (input: { url: string; enabled: boolean }) => Promise<SaveResult>;
}) {
  const t = useTranslations("adminConfig");
  const [url, setUrl] = useState(config.url ?? "");
  const [enabled, setEnabled] = useState(config.enabled);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function save() {
    setBusy(true);
    setError(null);
    setSaved(false);
    const res = await saveAction({ url: url.trim(), enabled });
    setBusy(false);
    if (res.ok) {
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } else {
      setError(res.error);
    }
  }

  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4">
      <label htmlFor="wa-url" className="chrome-label chrome-label-muted">
        {t("whatsappUrl")}
      </label>
      <input
        id="wa-url"
        type="url"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        placeholder="https://chat.whatsapp.com/…"
        className="mt-1 w-full border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-primary)] px-3 py-2 font-mono text-sm"
      />
      <p className="mt-1 text-xs text-[var(--color-text-muted)]">{t("whatsappUrlHelp")}</p>

      <label className="mt-3 flex items-center gap-2 text-sm font-bold">
        <input
          type="checkbox"
          checked={enabled}
          onChange={(e) => setEnabled(e.target.checked)}
        />
        {t("whatsappEnabled")}
      </label>

      <button
        type="button"
        onClick={save}
        disabled={busy}
        className="mt-4 w-full bg-[var(--color-bg-ink)] py-2.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)] disabled:opacity-50"
      >
        {saved ? t("saved") : t("save")}
      </button>
      {error && <p className="mt-2 text-xs text-[var(--color-accent-red)]">{error}</p>}
    </div>
  );
}
```

- [ ] **Step 7: Add the section to the config page**

In `frontend/app/admin/config/page.tsx`: add imports
```tsx
import { getCommunityConfig } from "@/lib/api/community-config";
import { CommunityConfigPanel } from "@/components/admin/CommunityConfigPanel";
import { saveCommunityConfigAction } from "./actions";
```
(Extend the existing `./actions` import rather than duplicating it.) Fetch the config alongside the others:
```tsx
  const community = await getCommunityConfig();
```
Add a third `<section>` after the Points section:
```tsx
        <section>
          <h2 className={sectionHeadClass}>{t("community")}</h2>
          <CommunityConfigPanel config={community} saveAction={saveCommunityConfigAction} />
        </section>
```

- [ ] **Step 8: Run tests + typecheck, expect PASS**

Run: `pnpm vitest run components/admin/CommunityConfigPanel.test.tsx` → PASS; `pnpm typecheck` → exit 0.

- [ ] **Step 9: Commit**
```bash
git add frontend/lib/api/community-config.ts frontend/components/admin/CommunityConfigPanel.tsx frontend/components/admin/CommunityConfigPanel.test.tsx frontend/app/admin/config/actions.ts frontend/app/admin/config/page.tsx frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(whatsapp): admin Comunidad config section"
```

---

## Task 6: Frontend — captain roster page

**Files:**
- Create: `frontend/lib/api/captain-whatsapp.ts`, `frontend/app/captain/whatsapp/page.tsx`, `frontend/app/captain/whatsapp/actions.ts`, `frontend/components/captain/WhatsappRosterRow.tsx`, `frontend/components/captain/WhatsappRosterRow.test.tsx`
- Modify: `frontend/components/shell/NavDrawer.tsx`, `frontend/messages/es-CO.json`, `frontend/messages/en.json`

- [ ] **Step 1: API client**

Create `frontend/lib/api/captain-whatsapp.ts`:
```ts
import { api } from "./client";

export type RosterEntry = { userId: number; displayName: string; visible: boolean };

export async function getWhatsappRoster(): Promise<RosterEntry[]> {
  return api<RosterEntry[]>("/api/captain/whatsapp-roster");
}

export async function setWhatsappVisibility(input: {
  userId: number;
  visible: boolean;
}): Promise<void> {
  await api<void>("/api/captain/whatsapp-visibility", {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
```

- [ ] **Step 2: i18n keys**

In `messages/es-CO.json` add a new top-level `captainWhatsapp` object (and the nav key in the existing `nav` object):
```json
  "captainWhatsapp": {
    "title": "GRUPO DE WHATSAPP",
    "intro": "Elige qué jugadores pueden ver el enlace del grupo. Por defecto está oculto.",
    "visibleOn": "Visible",
    "visibleOff": "Oculto",
    "empty": "No tienes jugadores invitados todavía."
  },
```
and in `nav`: `"whatsappGroup": "Grupo WhatsApp",`. In `messages/en.json` mirror:
```json
  "captainWhatsapp": {
    "title": "WHATSAPP GROUP",
    "intro": "Choose which players can see the group link. Hidden by default.",
    "visibleOn": "Visible",
    "visibleOff": "Hidden",
    "empty": "You have no invited players yet."
  },
```
and `nav.whatsappGroup`: `"WhatsApp Group"`.

- [ ] **Step 3: Server action**

Create `frontend/app/captain/whatsapp/actions.ts`:
```ts
"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api/client";
import { setWhatsappVisibility } from "@/lib/api/captain-whatsapp";

export type ToggleResult = { ok: true } | { ok: false; error: string };

export async function setVisibilityAction(input: {
  userId: number;
  visible: boolean;
}): Promise<ToggleResult> {
  try {
    await setWhatsappVisibility(input);
    revalidatePath("/captain/whatsapp");
    return { ok: true };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, error: e.message };
    throw e;
  }
}
```

- [ ] **Step 4: Write the failing row test**

Create `frontend/components/captain/WhatsappRosterRow.test.tsx`:
```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { WhatsappRosterRow } from "./WhatsappRosterRow";

const messages = { captainWhatsapp: { visibleOn: "Visible", visibleOff: "Oculto" } };

function renderRow(visible: boolean, action = vi.fn().mockResolvedValue({ ok: true })) {
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <WhatsappRosterRow
        entry={{ userId: 7, displayName: "Ana", visible }}
        setVisibilityAction={action}
      />
    </NextIntlClientProvider>,
  );
  return action;
}

describe("WhatsappRosterRow", () => {
  it("toggles an off player on", async () => {
    const action = renderRow(false);
    await userEvent.click(screen.getByRole("button"));
    expect(action).toHaveBeenCalledWith({ userId: 7, visible: true });
  });

  it("toggles an on player off", async () => {
    const action = renderRow(true);
    await userEvent.click(screen.getByRole("button"));
    expect(action).toHaveBeenCalledWith({ userId: 7, visible: false });
  });
});
```

- [ ] **Step 5: Run it, expect FAIL**

Run: `pnpm vitest run components/captain/WhatsappRosterRow.test.tsx` → FAIL (module not found).

- [ ] **Step 6: Implement the row**

Create `frontend/components/captain/WhatsappRosterRow.tsx`:
```tsx
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { RosterEntry } from "@/lib/api/captain-whatsapp";

type ToggleResult = { ok: true } | { ok: false; error: string };

export function WhatsappRosterRow({
  entry,
  setVisibilityAction,
}: {
  entry: RosterEntry;
  setVisibilityAction: (input: { userId: number; visible: boolean }) => Promise<ToggleResult>;
}) {
  const t = useTranslations("captainWhatsapp");
  const [visible, setVisible] = useState(entry.visible);
  const [busy, setBusy] = useState(false);

  async function toggle() {
    const next = !visible;
    setBusy(true);
    const res = await setVisibilityAction({ userId: entry.userId, visible: next });
    setBusy(false);
    if (res.ok) setVisible(next);
  }

  return (
    <div className="flex items-center justify-between gap-3 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5">
      <span className="truncate font-display text-sm font-extrabold uppercase tracking-tight">
        {entry.displayName}
      </span>
      <button
        type="button"
        onClick={toggle}
        disabled={busy}
        aria-pressed={visible}
        className={`shrink-0 px-2 py-1 font-mono text-[10px] font-bold uppercase tracking-[0.12em] disabled:opacity-50 ${
          visible
            ? "bg-[#25d366] text-[var(--color-bg-ink)]"
            : "bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]"
        }`}
      >
        {visible ? t("visibleOn") : t("visibleOff")}
      </button>
    </div>
  );
}
```

- [ ] **Step 7: Implement the page**

Create `frontend/app/captain/whatsapp/page.tsx` (mirror the captain payments page shell — `TopBar`/`BottomNav`, PLAYER → redirect):
```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getWhatsappRoster } from "@/lib/api/captain-whatsapp";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { WhatsappRosterRow } from "@/components/captain/WhatsappRosterRow";
import { setVisibilityAction } from "./actions";

export default async function CaptainWhatsappPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");
  const me = await getMe();
  if (me.role === "PLAYER") redirect("/home");

  const roster = await getWhatsappRoster();
  const t = await getTranslations("captainWhatsapp");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl px-3 pt-4">
        <p className="mb-3 text-sm text-[var(--color-text-muted)]">{t("intro")}</p>
        {roster.length === 0 ? (
          <p className="border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center text-sm text-[var(--color-text-muted)]">
            {t("empty")}
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {roster.map((entry) => (
              <WhatsappRosterRow
                key={entry.userId}
                entry={entry}
                setVisibilityAction={setVisibilityAction}
              />
            ))}
          </div>
        )}
      </div>
      <BottomNav />
    </main>
  );
}
```

- [ ] **Step 8: Add the NavDrawer link**

In `frontend/components/shell/NavDrawer.tsx`, inside the `(role === "ADMIN" || role === "CAPTAIN")` block (where the payments link lives), add:
```tsx
            <Link href="/captain/whatsapp" className={linkClass} onClick={close}>
              {t("whatsappGroup")}
            </Link>
```
(`t` here is `useTranslations("nav")`, already in the component.)

- [ ] **Step 9: Run tests + typecheck, expect PASS**

Run: `pnpm vitest run components/captain/WhatsappRosterRow.test.tsx` → PASS; `pnpm typecheck` → exit 0.

- [ ] **Step 10: Commit**
```bash
git add frontend/lib/api/captain-whatsapp.ts frontend/app/captain/whatsapp frontend/components/captain/WhatsappRosterRow.tsx frontend/components/captain/WhatsappRosterRow.test.tsx frontend/components/shell/NavDrawer.tsx frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(whatsapp): captain per-player visibility roster page"
```

---

## Task 7: Full verification

**Files:** none (gate only).

- [ ] **Step 1: Frontend** (from `frontend/`): `pnpm vitest run` (all green), `pnpm typecheck` (exit 0), `pnpm lint` (0 errors; the pre-existing `app/layout.tsx` custom-font warning is OK).
- [ ] **Step 2: Backend** (from `backend/`): `./mvnw -q verify` (BUILD SUCCESS).
- [ ] **Step 3 (optional manual):** as admin, `/admin/config` → Comunidad → paste the link + enable → home shows the card; as a captain, `/captain/whatsapp` → flip a player on → that player's home shows the card; a player left off sees nothing.
- [ ] **Step 4:** `git add -A && git commit -m "chore: verification fixups" || echo clean`

---

## Self-Review (completed during planning)

- **Spec coverage:** migration V020 (Task 1) ✓; Pool/User fields (Task 1) ✓; me resolution + `whatsappGroupUrl` field with the exact role/enabled/visible logic (Task 1) ✓; admin community-config service+controller+validation (Task 2) ✓; captain roster (player-role only) + per-player toggle + invited-by authz (Task 3) ✓; home card reusing the InviteFriendsSheet glyph + canManage manage-link (Task 4) ✓; admin Comunidad section mirroring PoolConfig (Task 5) ✓; captain page + NavDrawer link (Task 6) ✓; i18n in lobby/adminConfig/nav/captainWhatsapp, both locales (Tasks 4–6) ✓; getMe (authed) used, not public summary ✓.
- **Placeholder scan:** none — full code for every new file; "mirror this exact existing file" instructions name real files (`AdminPoolConfigService`, `PoolConfigPanel`, `pool-config.ts`, captain payments page) and were read during planning.
- **Type consistency:** `whatsappGroupUrl` is the field name on both `MeResponse` (Java record, Task 1) and `MeResponse` (TS type, Task 4). `CommunityConfig {url, enabled}` matches the backend `CommunityConfigView {url, enabled}` (Tasks 2/5). `RosterEntry {userId, displayName, visible}` matches the backend `RosterEntry` record (Tasks 3/6). The toggle payload `{userId, visible}` matches `VisibilityRequest` (Tasks 3/6). `SaveResult`/`ToggleResult` mirror the existing `config/actions.ts` `SaveResult` shape.
- **Migration number:** V020 confirmed as the next free number (latest is V019).

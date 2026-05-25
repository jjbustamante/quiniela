# Plan 1 — Foundation, Auth roles, Invite tree

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the foundational layer of the Quiniela 2026 MVP — i18n, design system, app shell, role-based auth, and the 2-level invite tree — so that a friend who receives a personal invite link can sign in with Google, get the right role (captain or player), and land in an empty lobby ready for Plan 2's bracket-fill UI.

**Architecture:** Backend (Spring Boot 4 + Flyway) gets a V003 migration that upgrades `users.is_admin` to a `role` enum, adds `invited_by_user_id` + `invite_path`, and seeds a `pool` + `prize_split` + `pool_membership` model. `/auth/google` gains an optional `invitePath` parameter that resolves the inviter and inherits the right role at sign-up. A new `/api/invite/{path}` resolver and `/api/me` round out the read side. Frontend gets `next-intl` set up with `es-CO`/`en` catalogs, Tailwind 4 design tokens for Style B (broadcast), reusable shell components (TopBar, BottomNav, LocaleSwitcher), an invite-landing route at `/join/[invitePath]`, an auth-aware `/home` lobby (locked cards, no real data yet — Plan 2 fills the data), and an `InviteFriendsSheet` modal gated by role.

**Tech Stack:** Spring Boot 4, Java 25, Flyway, JUnit 5, Testcontainers (Postgres), Next.js 16 (App Router), TypeScript, Tailwind 4 (`@theme` in CSS), Auth.js v5, `next-intl`, Vitest + React Testing Library + MSW v2, Playwright + axe-core.

---

## Reference

- Spec: [`docs/superpowers/specs/2026-05-25-quiniela-mvp-ui-design.md`](../specs/2026-05-25-quiniela-mvp-ui-design.md)
- Legacy reference for User shape: `legacy/db/schema.rb`, `legacy/app/models/user.rb`
- Backend conventions: `backend/CLAUDE.md`
- Frontend conventions: `frontend/CLAUDE.md`
- Personal CI plugin defaults already applied: see root `CLAUDE.md` "Wave 1/2 applied 2026-05-24"

## File Structure

### Backend (`backend/`)

- Create: `src/main/resources/db/migration/V003__roles_invite_tree_pool.sql` — schema migration for roles, invite tree, pool, prize split, pool membership; seeds default pool + prize split.
- Modify: `src/main/java/io/quiniela/api/user/User.java` — drop `admin` boolean, add `role` enum, `invitedByUserId`, `invitePath`.
- Create: `src/main/java/io/quiniela/api/user/UserRole.java` — enum `ADMIN`, `CAPTAIN`, `PLAYER`.
- Modify: `src/main/java/io/quiniela/api/user/UserRepository.java` — add `findByInvitePath`.
- Create: `src/main/java/io/quiniela/api/pool/Pool.java`, `PoolRepository.java`, `PrizeSplit.java`, `PrizeSplitRepository.java`, `PoolMembership.java`, `PoolMembershipRepository.java`.
- Create: `src/main/java/io/quiniela/api/invite/InviteController.java` — `GET /api/invite/{invitePath}`.
- Create: `src/main/java/io/quiniela/api/invite/InvitePathGenerator.java` — slugify + random suffix utility.
- Modify: `src/main/java/io/quiniela/api/auth/AuthController.java` — accept optional `invitePath`, assign role, generate path, add to pool.
- Create: `src/main/java/io/quiniela/api/me/MeController.java` — `GET /api/me` returns the current authenticated user.
- Modify: `src/main/java/io/quiniela/api/config/SecurityConfig.java` — make `/api/invite/**` public (resolved before sign-in).
- Tests:
  - `src/test/java/io/quiniela/api/invite/InvitePathGeneratorTest.java`
  - `src/test/java/io/quiniela/api/invite/InviteControllerIT.java` (Testcontainers)
  - `src/test/java/io/quiniela/api/auth/AuthControllerIT.java` (Testcontainers)
  - `src/test/java/io/quiniela/api/me/MeControllerIT.java` (Testcontainers)
  - `src/test/java/io/quiniela/api/support/AbstractIntegrationTest.java` — shared Testcontainers base class.

### Frontend (`frontend/`)

- Create: `i18n/request.ts` — `next-intl` config (locale negotiation).
- Create: `i18n/routing.ts` — locale list + default.
- Create: `messages/es-CO.json`, `messages/en.json`.
- Modify: `middleware.ts` (or create) — `next-intl` middleware.
- Modify: `app/layout.tsx` — wire `NextIntlClientProvider`, lang from request locale.
- Modify: `app/globals.css` — add Style B `@theme` tokens.
- Create: `components/shell/TopBar.tsx`, `components/shell/BottomNav.tsx`, `components/shell/LocaleSwitcher.tsx`.
- Create: `lib/api/client.ts` — minimal typed fetch wrapper using `API_URL` + bearer token from `auth()`.
- Create: `lib/api/me.ts` — `getMe()`, `MeResponse` type.
- Create: `lib/api/invite.ts` — `resolveInvite(path)`, `InviteResolution` type.
- Create: `app/join/[invitePath]/page.tsx` — invite landing (Server Component).
- Create: `app/join/[invitePath]/setInviteCookie.ts` — server action that sets the `invitePath` cookie.
- Modify: `lib/auth.ts` — read `invitePath` cookie in the `jwt` callback, forward to backend, clear cookie.
- Create: `app/home/page.tsx` — authenticated lobby (Server Component).
- Create: `components/lobby/GroupCardSkeleton.tsx`, `components/lobby/KnockoutLockedCard.tsx`, `components/lobby/CountdownChip.tsx` (placeholder values for Plan 1; Plan 2 wires data).
- Create: `components/invite/InviteFriendsSheet.tsx` — modal with copy + WhatsApp share.
- Create: `components/invite/InviteFriendsButton.tsx` — role-gated trigger.
- Modify: `app/page.tsx` — redirect signed-in users to `/home`.
- Tests:
  - `components/shell/TopBar.test.tsx`
  - `components/shell/BottomNav.test.tsx`
  - `components/invite/InviteFriendsButton.test.tsx`
  - `components/invite/InviteFriendsSheet.test.tsx`
  - `mocks/handlers.ts` — add `/api/invite/*`, `/api/me`, `/auth/google` mocks.
  - `e2e/invite-landing.e2e.ts`
  - `e2e/home-lobby.e2e.ts`

---

## Task 1: V003 migration — roles, invite tree, pool, prize split

**Files:**
- Create: `backend/src/main/resources/db/migration/V003__roles_invite_tree_pool.sql`
- Test: `backend/src/test/java/io/quiniela/api/support/AbstractIntegrationTest.java`

- [ ] **Step 1: Add Testcontainers Postgres dependency**

Add to `backend/pom.xml` (inside `<dependencies>`), if not already present:

```xml
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <version>${testcontainers.version}</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>${testcontainers.version}</version>
  <scope>test</scope>
</dependency>
```

And in `<properties>`:

```xml
<testcontainers.version>1.20.4</testcontainers.version>
```

(Externalize the version per `feedback_maven_externalize_versions`.)

- [ ] **Step 2: Create the shared integration-test base class**

Create `backend/src/test/java/io/quiniela/api/support/AbstractIntegrationTest.java`:

```java
package io.quiniela.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("quiniela_test")
          .withUsername("quiniela")
          .withPassword("test");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.enabled", () -> "true");
  }
}
```

- [ ] **Step 3: Write the failing migration test**

Create `backend/src/test/java/io/quiniela/api/support/V003MigrationTest.java`:

```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V003MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void usersTableHasRoleAndInviteFields() {
    var jdbc = new JdbcTemplate(dataSource);

    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'users' ORDER BY column_name",
            String.class);

    assertThat(columns).contains("role", "invited_by_user_id", "invite_path");
    assertThat(columns).doesNotContain("is_admin");
  }

  @Test
  void poolAndPrizeSplitSeeded() {
    var jdbc = new JdbcTemplate(dataSource);

    Long poolCount = jdbc.queryForObject("SELECT COUNT(*) FROM pool", Long.class);
    Long prizeCount = jdbc.queryForObject("SELECT COUNT(*) FROM prize_split WHERE pool_id = 1", Long.class);

    assertThat(poolCount).isEqualTo(1L);
    assertThat(prizeCount).isEqualTo(3L);
  }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run from `backend/`:

```bash
./mvnw -Dtest=V003MigrationTest test
```

Expected: FAIL — migration file doesn't exist yet, or columns don't match.

- [ ] **Step 5: Write the V003 migration**

Create `backend/src/main/resources/db/migration/V003__roles_invite_tree_pool.sql`:

```sql
-- V003: Roles, invite tree, pool, prize split, pool membership.
--
-- - Replaces users.is_admin (boolean) with users.role (enum-as-VARCHAR).
-- - Adds the 2-level invite tree: invited_by_user_id + invite_path.
-- - Introduces pool, prize_split, pool_membership — multi-pool-ready schema
--   even though v1 ships a single hard-coded pool.
-- - Seeds the default pool (id=1) and the 80/15/5 prize split.

-- ── users upgrade ──────────────────────────────────────────────────────────

ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'player';
UPDATE users SET role = 'admin' WHERE is_admin = true;
ALTER TABLE users DROP COLUMN is_admin;
ALTER TABLE users ADD CONSTRAINT users_role_chk
  CHECK (role IN ('admin', 'captain', 'player'));

ALTER TABLE users ADD COLUMN invited_by_user_id BIGINT
  REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE users ADD COLUMN invite_path VARCHAR(64) UNIQUE;
CREATE INDEX idx_users_invite_path ON users(invite_path);

-- ── pool ───────────────────────────────────────────────────────────────────

CREATE TABLE pool (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT NOT NULL REFERENCES tournament(id),
    name            VARCHAR(255) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    entry_fee_cents INT NOT NULL DEFAULT 2000,
    locked_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pool_tournament ON pool(tournament_id);

-- ── prize_split ────────────────────────────────────────────────────────────

CREATE TABLE prize_split (
    pool_id    BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    rank       INT NOT NULL,
    percentage INT NOT NULL,
    PRIMARY KEY (pool_id, rank),
    CHECK (rank >= 1),
    CHECK (percentage > 0 AND percentage <= 100)
);

-- ── pool_membership ────────────────────────────────────────────────────────

CREATE TABLE pool_membership (
    pool_id    BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (pool_id, user_id)
);

CREATE INDEX idx_pool_membership_user ON pool_membership(user_id);

-- ── seed default pool + prize split ────────────────────────────────────────

-- Tournament row must exist already (V001 + V002 don't seed one, so the seed
-- below assumes a Plan 2 task will insert tournament id=1. To keep V003
-- forward-compatible, seed conditionally.)

INSERT INTO tournament (id, slug, name, start_date, end_date, status)
VALUES (1, 'fifa-wc-2026', 'Copa Mundial FIFA 2026', '2026-06-11', '2026-07-19', 'UPCOMING')
ON CONFLICT (id) DO NOTHING;

-- Force sequence past id=1 so future BIGSERIAL inserts don't collide.
SELECT setval('tournament_id_seq', GREATEST(1, (SELECT MAX(id) FROM tournament)));

INSERT INTO pool (id, tournament_id, name, currency, entry_fee_cents)
VALUES (1, 1, 'Quiniela Panas', 'USD', 2000)
ON CONFLICT (id) DO NOTHING;

SELECT setval('pool_id_seq', GREATEST(1, (SELECT MAX(id) FROM pool)));

INSERT INTO prize_split (pool_id, rank, percentage) VALUES
    (1, 1, 80),
    (1, 2, 15),
    (1, 3, 5)
ON CONFLICT (pool_id, rank) DO NOTHING;
```

- [ ] **Step 6: Re-run the test to verify it passes**

Run:

```bash
./mvnw -Dtest=V003MigrationTest test
```

Expected: PASS — schema matches assertions, seed rows exist.

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/resources/db/migration/V003__roles_invite_tree_pool.sql \
        backend/src/test/java/io/quiniela/api/support/AbstractIntegrationTest.java \
        backend/src/test/java/io/quiniela/api/support/V003MigrationTest.java
git commit -m "feat(backend): V003 migration — roles, invite tree, pool, prize split"
```

---

## Task 2: User entity + UserRole enum + repository

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/user/User.java`
- Create: `backend/src/main/java/io/quiniela/api/user/UserRole.java`
- Modify: `backend/src/main/java/io/quiniela/api/user/UserRepository.java`
- Test: `backend/src/test/java/io/quiniela/api/user/UserRepositoryIT.java`

- [ ] **Step 1: Write the failing repository test**

Create `backend/src/test/java/io/quiniela/api/user/UserRepositoryIT.java`:

```java
package io.quiniela.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryIT extends AbstractIntegrationTest {

  @Autowired UserRepository users;

  @Test
  void persistsUserWithRoleAndInvitePath() {
    var u = new User("google-sub-1", "a@example.com", "Alice", null, UserRole.CAPTAIN);
    u.setInvitePath("alice-x1y2z3");

    users.save(u);

    var found = users.findByInvitePath("alice-x1y2z3");
    assertThat(found).isPresent();
    assertThat(found.get().getRole()).isEqualTo(UserRole.CAPTAIN);
    assertThat(found.get().getDisplayName()).isEqualTo("Alice");
  }

  @Test
  void findByInvitePathReturnsEmptyForUnknown() {
    assertThat(users.findByInvitePath("does-not-exist")).isEmpty();
  }
}
```

- [ ] **Step 2: Run to see the failure**

```bash
./mvnw -Dtest=UserRepositoryIT test
```

Expected: FAIL — `UserRole` doesn't exist, `setInvitePath` / `findByInvitePath` don't exist.

- [ ] **Step 3: Create the UserRole enum**

Create `backend/src/main/java/io/quiniela/api/user/UserRole.java`:

```java
package io.quiniela.api.user;

public enum UserRole {
  ADMIN,
  CAPTAIN,
  PLAYER;

  public boolean canInvite() {
    return this == ADMIN || this == CAPTAIN;
  }

  /** Role assigned to a new sign-up invited by someone of this role. */
  public UserRole invitee() {
    return switch (this) {
      case ADMIN -> CAPTAIN;
      case CAPTAIN -> PLAYER;
      case PLAYER -> throw new IllegalStateException("Players cannot invite");
    };
  }
}
```

- [ ] **Step 4: Update the User entity**

Rewrite `backend/src/main/java/io/quiniela/api/user/User.java`:

```java
package io.quiniela.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "google_sub", nullable = false, unique = true)
  private String googleSub;

  @Column(nullable = false)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  @Column(name = "invited_by_user_id")
  private Long invitedByUserId;

  @Column(name = "invite_path", unique = true)
  private String invitePath;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {}

  public User(String googleSub, String email, String displayName, String avatarUrl, UserRole role) {
    this.googleSub = googleSub;
    this.email = email;
    this.displayName = displayName;
    this.avatarUrl = avatarUrl;
    this.role = role;
  }

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getGoogleSub() { return googleSub; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getAvatarUrl() { return avatarUrl; }
  public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
  public UserRole getRole() { return role; }
  public void setRole(UserRole role) { this.role = role; }
  public Long getInvitedByUserId() { return invitedByUserId; }
  public void setInvitedByUserId(Long id) { this.invitedByUserId = id; }
  public String getInvitePath() { return invitePath; }
  public void setInvitePath(String invitePath) { this.invitePath = invitePath; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
```

- [ ] **Step 5: Update UserRepository**

Replace `backend/src/main/java/io/quiniela/api/user/UserRepository.java`:

```java
package io.quiniela.api.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByGoogleSub(String googleSub);

  Optional<User> findByInvitePath(String invitePath);

  Optional<User> findByEmail(String email);
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
./mvnw -Dtest=UserRepositoryIT test
```

Expected: PASS.

Also run the full backend suite to catch breakage in `AuthController` (which still calls the old `isAdmin()` / constructor signature):

```bash
./mvnw test
```

Expected: compile errors in `AuthController.java` — that's Task 5; for now isolate by skipping:

```bash
./mvnw -Dtest='!AuthControllerIT,!QuinielaApiApplicationTests' test
```

(If `QuinielaApiApplicationTests` red because of `User.java` compile, that's fine — Task 5 fixes it.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/user/User.java \
        backend/src/main/java/io/quiniela/api/user/UserRole.java \
        backend/src/main/java/io/quiniela/api/user/UserRepository.java \
        backend/src/test/java/io/quiniela/api/user/UserRepositoryIT.java
git commit -m "feat(backend): UserRole enum, role + invite_path on User entity"
```

---

## Task 3: Pool, PrizeSplit, PoolMembership entities

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/pool/Pool.java`
- Create: `backend/src/main/java/io/quiniela/api/pool/PoolRepository.java`
- Create: `backend/src/main/java/io/quiniela/api/pool/PrizeSplit.java`, `PrizeSplitId.java`, `PrizeSplitRepository.java`
- Create: `backend/src/main/java/io/quiniela/api/pool/PoolMembership.java`, `PoolMembershipId.java`, `PoolMembershipRepository.java`
- Test: `backend/src/test/java/io/quiniela/api/pool/PoolRepositoryIT.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/pool/PoolRepositoryIT.java`:

```java
package io.quiniela.api.pool;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PoolRepositoryIT extends AbstractIntegrationTest {

  @Autowired PoolRepository pools;
  @Autowired PrizeSplitRepository splits;

  @Test
  void defaultPoolIsSeeded() {
    var pool = pools.findById(1L).orElseThrow();
    assertThat(pool.getName()).isEqualTo("Quiniela Panas");
    assertThat(pool.getCurrency()).isEqualTo("USD");
    assertThat(pool.getEntryFeeCents()).isEqualTo(2000);
    assertThat(pool.getLockedAt()).isNull();
  }

  @Test
  void seedPrizeSplitSumsTo100() {
    var rows = splits.findByPoolIdOrderByRankAsc(1L);
    assertThat(rows).hasSize(3);
    assertThat(rows.stream().mapToInt(PrizeSplit::getPercentage).sum()).isEqualTo(100);
    assertThat(rows.get(0).getRank()).isEqualTo(1);
    assertThat(rows.get(0).getPercentage()).isEqualTo(80);
  }
}
```

- [ ] **Step 2: Run the test (it will fail to compile)**

```bash
./mvnw -Dtest=PoolRepositoryIT test
```

Expected: FAIL — classes don't exist.

- [ ] **Step 3: Create Pool entity + repository**

Create `backend/src/main/java/io/quiniela/api/pool/Pool.java`:

```java
package io.quiniela.api.pool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pool")
public class Pool {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "entry_fee_cents", nullable = false)
  private Integer entryFeeCents;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Pool() {}

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() { this.updatedAt = Instant.now(); }

  public Long getId() { return id; }
  public Long getTournamentId() { return tournamentId; }
  public String getName() { return name; }
  public String getCurrency() { return currency; }
  public Integer getEntryFeeCents() { return entryFeeCents; }
  public Instant getLockedAt() { return lockedAt; }
  public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
}
```

Create `backend/src/main/java/io/quiniela/api/pool/PoolRepository.java`:

```java
package io.quiniela.api.pool;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoolRepository extends JpaRepository<Pool, Long> {}
```

- [ ] **Step 4: Create PrizeSplit (composite key) entity + repository**

Create `backend/src/main/java/io/quiniela/api/pool/PrizeSplitId.java`:

```java
package io.quiniela.api.pool;

import java.io.Serializable;
import java.util.Objects;

public class PrizeSplitId implements Serializable {
  private Long poolId;
  private Integer rank;

  public PrizeSplitId() {}
  public PrizeSplitId(Long poolId, Integer rank) { this.poolId = poolId; this.rank = rank; }

  public Long getPoolId() { return poolId; }
  public Integer getRank() { return rank; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PrizeSplitId other)) return false;
    return Objects.equals(poolId, other.poolId) && Objects.equals(rank, other.rank);
  }

  @Override
  public int hashCode() { return Objects.hash(poolId, rank); }
}
```

Create `backend/src/main/java/io/quiniela/api/pool/PrizeSplit.java`:

```java
package io.quiniela.api.pool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "prize_split")
@IdClass(PrizeSplitId.class)
public class PrizeSplit {

  @Id
  @Column(name = "pool_id")
  private Long poolId;

  @Id
  @Column(name = "rank")
  private Integer rank;

  @Column(nullable = false)
  private Integer percentage;

  protected PrizeSplit() {}

  public PrizeSplit(Long poolId, Integer rank, Integer percentage) {
    this.poolId = poolId;
    this.rank = rank;
    this.percentage = percentage;
  }

  public Long getPoolId() { return poolId; }
  public Integer getRank() { return rank; }
  public Integer getPercentage() { return percentage; }
  public void setPercentage(Integer percentage) { this.percentage = percentage; }
}
```

Create `backend/src/main/java/io/quiniela/api/pool/PrizeSplitRepository.java`:

```java
package io.quiniela.api.pool;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrizeSplitRepository extends JpaRepository<PrizeSplit, PrizeSplitId> {

  List<PrizeSplit> findByPoolIdOrderByRankAsc(Long poolId);
}
```

- [ ] **Step 5: Create PoolMembership entity + repository**

Create `backend/src/main/java/io/quiniela/api/pool/PoolMembershipId.java`:

```java
package io.quiniela.api.pool;

import java.io.Serializable;
import java.util.Objects;

public class PoolMembershipId implements Serializable {
  private Long poolId;
  private Long userId;

  public PoolMembershipId() {}
  public PoolMembershipId(Long poolId, Long userId) { this.poolId = poolId; this.userId = userId; }

  public Long getPoolId() { return poolId; }
  public Long getUserId() { return userId; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PoolMembershipId other)) return false;
    return Objects.equals(poolId, other.poolId) && Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() { return Objects.hash(poolId, userId); }
}
```

Create `backend/src/main/java/io/quiniela/api/pool/PoolMembership.java`:

```java
package io.quiniela.api.pool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pool_membership")
@IdClass(PoolMembershipId.class)
public class PoolMembership {

  @Id
  @Column(name = "pool_id")
  private Long poolId;

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  protected PoolMembership() {}

  public PoolMembership(Long poolId, Long userId) {
    this.poolId = poolId;
    this.userId = userId;
  }

  @PrePersist
  void onCreate() { this.joinedAt = Instant.now(); }

  public Long getPoolId() { return poolId; }
  public Long getUserId() { return userId; }
  public Instant getJoinedAt() { return joinedAt; }
}
```

Create `backend/src/main/java/io/quiniela/api/pool/PoolMembershipRepository.java`:

```java
package io.quiniela.api.pool;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoolMembershipRepository extends JpaRepository<PoolMembership, PoolMembershipId> {

  boolean existsByPoolIdAndUserId(Long poolId, Long userId);
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./mvnw -Dtest=PoolRepositoryIT test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/pool/ \
        backend/src/test/java/io/quiniela/api/pool/PoolRepositoryIT.java
git commit -m "feat(backend): Pool, PrizeSplit, PoolMembership entities"
```

---

## Task 4: InvitePathGenerator utility

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/invite/InvitePathGenerator.java`
- Test: `backend/src/test/java/io/quiniela/api/invite/InvitePathGeneratorTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/io/quiniela/api/invite/InvitePathGeneratorTest.java`:

```java
package io.quiniela.api.invite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvitePathGeneratorTest {

  InvitePathGenerator gen = new InvitePathGenerator();

  @Test
  void buildsSlugFromDisplayName() {
    String path = gen.generate("Juan Bustamante");
    assertThat(path).startsWith("juan-bustamante-");
    assertThat(path).matches("juan-bustamante-[a-z0-9]{6}");
  }

  @Test
  void stripsAccentsAndNonAlphanumeric() {
    assertThat(gen.generate("José Núñez")).startsWith("jose-nunez-");
    assertThat(gen.generate("Carla O'Brien")).startsWith("carla-obrien-");
  }

  @Test
  void fallsBackToUserWhenNameIsBlank() {
    assertThat(gen.generate("")).startsWith("user-");
    assertThat(gen.generate(null)).startsWith("user-");
  }

  @Test
  void capsSlugLengthAt32CharsBeforeSuffix() {
    String veryLong = "abcdefghijklmnopqrstuvwxyz0123456789ABC";
    String path = gen.generate(veryLong);
    assertThat(path.split("-")[0].length()).isLessThanOrEqualTo(32);
  }
}
```

- [ ] **Step 2: Run to see failure**

```bash
./mvnw -Dtest=InvitePathGeneratorTest test
```

Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement the generator**

Create `backend/src/main/java/io/quiniela/api/invite/InvitePathGenerator.java`:

```java
package io.quiniela.api.invite;

import java.security.SecureRandom;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

/**
 * Builds a personal invite path slug + 6-character random suffix. Example: "juan-bustamante-x1y2z3".
 * Suffix uses a-z + 0-9 (alphabet of 36, so 36^6 ≈ 2.1B — collision-free at our scale).
 */
@Component
public class InvitePathGenerator {

  private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int SUFFIX_LEN = 6;
  private static final int SLUG_MAX_LEN = 32;
  private final SecureRandom random = new SecureRandom();

  public String generate(String displayName) {
    return slugify(displayName) + "-" + randomSuffix();
  }

  private String slugify(String name) {
    if (name == null || name.isBlank()) return "user";
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
        .toLowerCase();
    String alnum = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    if (alnum.isEmpty()) return "user";
    return alnum.length() > SLUG_MAX_LEN ? alnum.substring(0, SLUG_MAX_LEN) : alnum;
  }

  private String randomSuffix() {
    var sb = new StringBuilder(SUFFIX_LEN);
    for (int i = 0; i < SUFFIX_LEN; i++) {
      sb.append(SUFFIX_ALPHABET.charAt(random.nextInt(SUFFIX_ALPHABET.length())));
    }
    return sb.toString();
  }
}
```

- [ ] **Step 4: Run the test**

```bash
./mvnw -Dtest=InvitePathGeneratorTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/invite/InvitePathGenerator.java \
        backend/src/test/java/io/quiniela/api/invite/InvitePathGeneratorTest.java
git commit -m "feat(backend): InvitePathGenerator for personal invite slugs"
```

---

## Task 5: AuthController accepts invitePath + assigns role + joins pool

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/auth/AuthController.java`
- Test: `backend/src/test/java/io/quiniela/api/auth/AuthControllerIT.java`

This task glues together Tasks 2–4. We bring the existing `/auth/google` flow back to a compiling state (Task 2 broke it), make it role-aware, and have it auto-mint the inviter path + pool membership for new signups.

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/io/quiniela/api/auth/AuthControllerIT.java`:

```java
package io.quiniela.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.quiniela.api.pool.PoolMembershipRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PoolMembershipRepository memberships;
  @MockBean GoogleTokenService googleTokenService;

  @Test
  void firstSignInWithoutInvitePathRejected() throws Exception {
    var payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-stranger");
    payload.setEmail("stranger@example.com");
    payload.set("name", "Stranger");
    payload.set("picture", "https://example.com/p.jpg");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void firstSignInWithAdminEmailBecomesAdminAndJoinsPool() throws Exception {
    var payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-admin");
    payload.setEmail("admin@example.com");
    payload.set("name", "Juan");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.invitePath").isNotEmpty());

    var admin = users.findByEmail("admin@example.com").orElseThrow();
    assertThat(memberships.existsByPoolIdAndUserId(1L, admin.getId())).isTrue();
  }

  @Test
  void signUpViaCaptainInviteBecomesPlayer() throws Exception {
    var captain = new User("g-captain", "cap@example.com", "Cap", null, UserRole.CAPTAIN);
    captain.setInvitePath("cap-abc123");
    captain = users.save(captain);

    var payload = new GoogleIdToken.Payload();
    payload.setSubject("g-friend");
    payload.setEmail("friend@example.com");
    payload.set("name", "Friend");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\",\"invitePath\":\"cap-abc123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("PLAYER"));

    var friend = users.findByEmail("friend@example.com").orElseThrow();
    assertThat(friend.getInvitedByUserId()).isEqualTo(captain.getId());
    assertThat(friend.getInvitePath()).isNull(); // players don't get a personal path
  }

  @Test
  void signUpViaAdminInviteBecomesCaptainWithOwnPath() throws Exception {
    var admin = new User("g-admin", "admin@example.com", "Juan", null, UserRole.ADMIN);
    admin.setInvitePath("juan-xyz789");
    admin = users.save(admin);

    var payload = new GoogleIdToken.Payload();
    payload.setSubject("g-newcap");
    payload.setEmail("andres@example.com");
    payload.set("name", "Andrés");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\",\"invitePath\":\"juan-xyz789\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("CAPTAIN"))
        .andExpect(jsonPath("$.invitePath").isNotEmpty());
  }
}
```

- [ ] **Step 2: Run to see failure**

```bash
./mvnw -Dtest=AuthControllerIT test
```

Expected: FAIL — compile errors from Task 2 still pending, plus tests will assert behavior we haven't built.

- [ ] **Step 3: Add admin-emails property to application.yml**

Modify `backend/src/main/resources/application.yml` — add (or confirm exists) under `app:`:

```yaml
app:
  admin-emails: ${APP_ADMIN_EMAILS:admin@example.com}
```

For tests, also add `backend/src/test/resources/application.yml`:

```yaml
app:
  admin-emails: admin@example.com
```

- [ ] **Step 4: Rewrite AuthController**

Replace `backend/src/main/java/io/quiniela/api/auth/AuthController.java`:

```java
package io.quiniela.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import io.quiniela.api.invite.InvitePathGenerator;
import io.quiniela.api.pool.PoolMembership;
import io.quiniela.api.pool.PoolMembershipRepository;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.security.GeneralSecurityException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private static final Long DEFAULT_POOL_ID = 1L;

  private final GoogleTokenService googleTokenService;
  private final JwtService jwtService;
  private final UserRepository users;
  private final PoolMembershipRepository memberships;
  private final InvitePathGenerator pathGenerator;
  private final Set<String> adminEmails;

  public AuthController(
      GoogleTokenService googleTokenService,
      JwtService jwtService,
      UserRepository users,
      PoolMembershipRepository memberships,
      InvitePathGenerator pathGenerator,
      @Value("${app.admin-emails:}") String adminEmailsCsv) {
    this.googleTokenService = googleTokenService;
    this.jwtService = jwtService;
    this.users = users;
    this.memberships = memberships;
    this.pathGenerator = pathGenerator;
    this.adminEmails =
        adminEmailsCsv.isBlank()
            ? Set.of()
            : Set.of(adminEmailsCsv.toLowerCase().split("\\s*,\\s*"));
  }

  public record GoogleSignInRequest(String idToken, String invitePath) {}

  public record SessionResponse(
      String token,
      Long userId,
      String email,
      String name,
      String role,
      String invitePath) {}

  @PostMapping("/google")
  @Transactional
  public ResponseEntity<?> signInWithGoogle(@RequestBody GoogleSignInRequest body) {
    if (body == null || body.idToken() == null || body.idToken().isBlank()) {
      return ResponseEntity.badRequest().body("Missing idToken");
    }

    Payload claims;
    try {
      claims = googleTokenService.verify(body.idToken());
    } catch (GeneralSecurityException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google ID token");
    }

    String googleSub = claims.getSubject();
    String email = claims.getEmail();
    String name = (String) claims.get("name");
    String picture = (String) claims.get("picture");

    var existing = users.findByGoogleSub(googleSub);
    User user;
    if (existing.isPresent()) {
      // Returning user — refresh profile, ignore invitePath.
      user = existing.get();
      user.setEmail(email);
      user.setDisplayName(name);
      user.setAvatarUrl(picture);
    } else {
      // New user — must have a path in: admin-email match OR a valid invitePath.
      UserRole role;
      Long inviterId = null;

      if (email != null && adminEmails.contains(email.toLowerCase())) {
        role = UserRole.ADMIN;
      } else if (body.invitePath() != null && !body.invitePath().isBlank()) {
        var inviter = users.findByInvitePath(body.invitePath());
        if (inviter.isEmpty()) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
              .body("Invitación no válida");
        }
        role = inviter.get().getRole().invitee();
        inviterId = inviter.get().getId();
      } else {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("Necesitas una invitación");
      }

      user = new User(googleSub, email, name, picture, role);
      user.setInvitedByUserId(inviterId);

      // Admin + captain get a personal invite path; players don't.
      if (role.canInvite()) {
        user.setInvitePath(generateUniquePath(name));
      }

      user = users.save(user);

      // Join the default pool.
      if (!memberships.existsByPoolIdAndUserId(DEFAULT_POOL_ID, user.getId())) {
        memberships.save(new PoolMembership(DEFAULT_POOL_ID, user.getId()));
      }
    }

    String sessionToken = jwtService.issue(user);

    return ResponseEntity.ok(
        new SessionResponse(
            sessionToken,
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole().name(),
            user.getInvitePath()));
  }

  private String generateUniquePath(String name) {
    // Retry up to 5 times for the (vanishingly rare) collision case.
    for (int i = 0; i < 5; i++) {
      String candidate = pathGenerator.generate(name);
      if (users.findByInvitePath(candidate).isEmpty()) return candidate;
    }
    throw new IllegalStateException("Could not mint a unique invite path");
  }
}
```

- [ ] **Step 5: Update JwtService signature if needed**

Verify `JwtService.issue(User)` already exists and accepts the new `User` (role-aware) shape:

```bash
grep -n "public String issue" backend/src/main/java/io/quiniela/api/auth/JwtService.java
```

If the method signature still references `isAdmin()`, update it to read `getRole().name()` into the JWT claim. Example replacement for the `issue` method:

```java
public String issue(User user) {
  var now = Instant.now();
  var claims = JwtClaimsSet.builder()
      .issuer("quiniela-api")
      .issuedAt(now)
      .expiresAt(now.plusSeconds(jwtTtlSeconds))
      .subject(String.valueOf(user.getId()))
      .claim("email", user.getEmail())
      .claim("name", user.getDisplayName())
      .claim("role", user.getRole().name())
      .build();
  // ... existing signing
}
```

(Exact body depends on the current implementation — leave the signing logic alone, just swap the admin claim for role.)

- [ ] **Step 6: Update SessionResponse consumers**

Auth.js in `frontend/lib/auth.ts` currently reads `data.admin` (a boolean). It now needs to read `data.role` (a string). Add a `role` field to the augmented `Session` type. This is fixed in Task 10 but flag it here so the failing frontend tests in Task 10 are expected.

- [ ] **Step 7: Run the AuthController tests**

```bash
./mvnw -Dtest=AuthControllerIT test
```

Expected: PASS, all four scenarios.

- [ ] **Step 8: Run the full backend suite to confirm nothing else regressed**

```bash
./mvnw verify
```

Expected: PASS. If `QuinielaApiApplicationTests` (H2-based) fails, switch its DB dialect or skip — H2 won't support some Postgres-specific migration syntax (e.g. `BIGSERIAL`). Acceptable mitigation: rename to `*IT` and reroute through the Testcontainers base, OR add `@Sql` test slice.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/auth/AuthController.java \
        backend/src/main/java/io/quiniela/api/auth/JwtService.java \
        backend/src/main/resources/application.yml \
        backend/src/test/resources/application.yml \
        backend/src/test/java/io/quiniela/api/auth/AuthControllerIT.java
git commit -m "feat(backend): role-aware sign-in + invite-path resolution + pool join"
```

---

## Task 6: Invite resolver endpoint + /api/me

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/invite/InviteController.java`
- Create: `backend/src/main/java/io/quiniela/api/me/MeController.java`
- Modify: `backend/src/main/java/io/quiniela/api/config/SecurityConfig.java`
- Test: `backend/src/test/java/io/quiniela/api/invite/InviteControllerIT.java`, `backend/src/test/java/io/quiniela/api/me/MeControllerIT.java`

- [ ] **Step 1: Write the failing invite-resolver test**

Create `backend/src/test/java/io/quiniela/api/invite/InviteControllerIT.java`:

```java
package io.quiniela.api.invite;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InviteControllerIT extends AbstractIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;

  @Test
  void resolvesValidPath() throws Exception {
    var captain = new User("g-cap", "cap@example.com", "Captain Marvel", null, UserRole.CAPTAIN);
    captain.setInvitePath("captain-marvel-q1w2e3");
    users.save(captain);

    mockMvc
        .perform(get("/api/invite/captain-marvel-q1w2e3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.inviterDisplayName").value("Captain Marvel"))
        .andExpect(jsonPath("$.inviterRole").value("CAPTAIN"));
  }

  @Test
  void unknownPathReturns404() throws Exception {
    mockMvc.perform(get("/api/invite/does-not-exist-xxx")).andExpect(status().isNotFound());
  }
}
```

- [ ] **Step 2: Run to see failure**

```bash
./mvnw -Dtest=InviteControllerIT test
```

Expected: FAIL.

- [ ] **Step 3: Implement the controller**

Create `backend/src/main/java/io/quiniela/api/invite/InviteController.java`:

```java
package io.quiniela.api.invite;

import io.quiniela.api.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invite")
public class InviteController {

  private final UserRepository users;

  public InviteController(UserRepository users) { this.users = users; }

  public record InviteResolution(
      boolean valid, String inviterDisplayName, String inviterRole) {}

  @GetMapping("/{invitePath}")
  public ResponseEntity<InviteResolution> resolve(@PathVariable String invitePath) {
    return users
        .findByInvitePath(invitePath)
        .filter(u -> u.getRole().canInvite())
        .map(
            u ->
                ResponseEntity.ok(
                    new InviteResolution(true, u.getDisplayName(), u.getRole().name())))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
```

- [ ] **Step 4: Make `/api/invite/**` public in SecurityConfig**

Modify `backend/src/main/java/io/quiniela/api/config/SecurityConfig.java`:

```java
.authorizeHttpRequests(
    authz ->
        authz
            .requestMatchers("/actuator/**", "/auth/**", "/api/invite/**")
            .permitAll()
            .anyRequest()
            .authenticated())
```

- [ ] **Step 5: Run the invite test**

```bash
./mvnw -Dtest=InviteControllerIT test
```

Expected: PASS.

- [ ] **Step 6: Write the failing /api/me test**

Create `backend/src/test/java/io/quiniela/api/me/MeControllerIT.java`:

```java
package io.quiniela.api.me;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeControllerIT extends AbstractIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;

  @Test
  void returnsCurrentUser() throws Exception {
    var u = new User("g-1", "me@example.com", "Me", null, UserRole.CAPTAIN);
    u.setInvitePath("me-abc123");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(u.getId()))
        .andExpect(jsonPath("$.role").value("CAPTAIN"))
        .andExpect(jsonPath("$.invitePath").value("me-abc123"))
        .andExpect(jsonPath("$.canInvite").value(true));
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }
}
```

- [ ] **Step 7: Run to see failure**

```bash
./mvnw -Dtest=MeControllerIT test
```

Expected: FAIL.

- [ ] **Step 8: Implement /api/me**

Create `backend/src/main/java/io/quiniela/api/me/MeController.java`:

```java
package io.quiniela.api.me;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

  private final UserRepository users;

  public MeController(UserRepository users) { this.users = users; }

  public record MeResponse(
      Long id,
      String email,
      String displayName,
      String avatarUrl,
      String role,
      String invitePath,
      boolean canInvite,
      Long invitedByUserId) {}

  @GetMapping
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(
        new MeResponse(
            u.getId(),
            u.getEmail(),
            u.getDisplayName(),
            u.getAvatarUrl(),
            u.getRole().name(),
            u.getInvitePath(),
            u.getRole().canInvite(),
            u.getInvitedByUserId()));
  }
}
```

- [ ] **Step 9: Run the /api/me test**

```bash
./mvnw -Dtest=MeControllerIT test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/io/quiniela/api/invite/InviteController.java \
        backend/src/main/java/io/quiniela/api/me/MeController.java \
        backend/src/main/java/io/quiniela/api/config/SecurityConfig.java \
        backend/src/test/java/io/quiniela/api/invite/InviteControllerIT.java \
        backend/src/test/java/io/quiniela/api/me/MeControllerIT.java
git commit -m "feat(backend): /api/invite/{path} resolver + /api/me"
```

---

## Task 7: Frontend — next-intl scaffold (es-CO + en)

**Files:**
- Create: `frontend/i18n/routing.ts`, `frontend/i18n/request.ts`
- Create: `frontend/middleware.ts`
- Create: `frontend/messages/es-CO.json`, `frontend/messages/en.json`
- Modify: `frontend/app/layout.tsx`, `frontend/next.config.ts`

- [ ] **Step 1: Install next-intl**

From `frontend/`:

```bash
pnpm add next-intl@^4
```

- [ ] **Step 2: Configure routing**

Create `frontend/i18n/routing.ts`:

```ts
import { defineRouting } from "next-intl/routing";

export const routing = defineRouting({
  locales: ["es-CO", "en"],
  defaultLocale: "es-CO",
  // Keep URLs un-prefixed for the default locale — friends paste WhatsApp links
  // without expecting an /es-CO/ prefix.
  localePrefix: "as-needed",
});

export type Locale = (typeof routing.locales)[number];
```

Create `frontend/i18n/request.ts`:

```ts
import { getRequestConfig } from "next-intl/server";
import { routing } from "./routing";

export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  const locale = (routing.locales as readonly string[]).includes(requested ?? "")
    ? (requested as (typeof routing.locales)[number])
    : routing.defaultLocale;
  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
  };
});
```

- [ ] **Step 3: Wire the middleware**

Create `frontend/middleware.ts`:

```ts
import createMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

export default createMiddleware(routing);

export const config = {
  // Match everything except API, auth, static, image-opt, public assets.
  matcher: ["/((?!api|_next|.*\\..*).*)"],
};
```

- [ ] **Step 4: Plug into next.config.ts**

Replace `frontend/next.config.ts` with:

```ts
import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");

const nextConfig: NextConfig = {};

export default withNextIntl(nextConfig);
```

- [ ] **Step 5: Seed the message catalogs**

Create `frontend/messages/es-CO.json`:

```json
{
  "common": {
    "appName": "Quiniela Panas",
    "signIn": "Iniciar sesión con Google",
    "signOut": "Cerrar sesión",
    "loading": "Cargando…"
  },
  "landing": {
    "title": "Quiniela 2026",
    "subtitle": "La quiniela de los panas para el Mundial. ⚽ Pitazo inicial: 11 de junio.",
    "backendUp": "Backend operando",
    "backendDown": "Backend no disponible"
  },
  "invite": {
    "invitedBy": "{name} te invitó a la Quiniela Panas",
    "joinPrompt": "Inicia sesión con Google para unirte.",
    "invalid": "Invitación no válida",
    "invalidHelp": "Verifica el enlace con quien te invitó."
  },
  "lobby": {
    "title": "Mi quiniela",
    "potChip": "Pot: {pot} · {paid} pagas",
    "countdown": "{days} días hasta el primer partido",
    "groupsHeading": "Fase de grupos",
    "knockoutsHeading": "Eliminatorias",
    "knockoutsLocked": "Se abre tras la fase de grupos",
    "askPaulFillAll": "🐙 Que Paul llene todo",
    "downloadXlsx": "⬇ Descargar plantilla",
    "uploadXlsx": "⬆ Subir plantilla",
    "inviteFriends": "Invitar amigos",
    "unpaidPill": "Sin pagar"
  },
  "nav": {
    "myQuiniela": "Mi Quiniela",
    "ranking": "Tabla",
    "matches": "Partidos",
    "compare": "vs"
  },
  "invite_sheet": {
    "title": "Invita a un amigo",
    "linkLabel": "Tu enlace personal",
    "copy": "Copiar",
    "copied": "Copiado",
    "whatsapp": "Compartir por WhatsApp",
    "whatsappMessage": "Te invito a la Quiniela Panas para el Mundial 2026 ⚽ {url}"
  }
}
```

Create `frontend/messages/en.json` with the same keys, English values:

```json
{
  "common": {
    "appName": "Quiniela Panas",
    "signIn": "Sign in with Google",
    "signOut": "Sign out",
    "loading": "Loading…"
  },
  "landing": {
    "title": "Quiniela 2026",
    "subtitle": "The friends-and-family bracket for the World Cup. ⚽ Kickoff: June 11.",
    "backendUp": "Backend up",
    "backendDown": "Backend unavailable"
  },
  "invite": {
    "invitedBy": "{name} invited you to Quiniela Panas",
    "joinPrompt": "Sign in with Google to join.",
    "invalid": "Invalid invite",
    "invalidHelp": "Double-check the link with whoever sent it."
  },
  "lobby": {
    "title": "My bracket",
    "potChip": "Pot: {pot} · {paid} paid",
    "countdown": "{days} days to first match",
    "groupsHeading": "Group stage",
    "knockoutsHeading": "Knockouts",
    "knockoutsLocked": "Unlocks after group stage",
    "askPaulFillAll": "🐙 Let Paul fill everything",
    "downloadXlsx": "⬇ Download template",
    "uploadXlsx": "⬆ Upload template",
    "inviteFriends": "Invite friends",
    "unpaidPill": "Unpaid"
  },
  "nav": {
    "myQuiniela": "My Bracket",
    "ranking": "Ranking",
    "matches": "Matches",
    "compare": "vs"
  },
  "invite_sheet": {
    "title": "Invite a friend",
    "linkLabel": "Your personal link",
    "copy": "Copy",
    "copied": "Copied",
    "whatsapp": "Share via WhatsApp",
    "whatsappMessage": "Join my Quiniela Panas bracket for World Cup 2026 ⚽ {url}"
  }
}
```

- [ ] **Step 6: Wrap the root layout with the provider**

Replace `frontend/app/layout.tsx`:

```tsx
import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import "./globals.css";

export const metadata: Metadata = {
  title: "Quiniela Panas — Mundial 2026",
  description: "Quiniela para el Mundial 2026.",
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const locale = await getLocale();
  const messages = await getMessages();
  return (
    <html lang={locale}>
      <body className="min-h-screen bg-[var(--bg-primary)] text-[var(--text-primary)] antialiased">
        <NextIntlClientProvider locale={locale} messages={messages}>
          {children}
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
```

- [ ] **Step 7: Verify build + tests still pass**

```bash
pnpm typecheck && pnpm lint && pnpm test
```

Expected: PASS. Existing `e2e/smoke.e2e.ts` will likely fail until Task 8 wires the Style B palette since `globals.css` may reference undefined tokens — fix in Task 8.

- [ ] **Step 8: Commit**

```bash
git add frontend/i18n/ frontend/middleware.ts \
        frontend/messages/ \
        frontend/next.config.ts frontend/app/layout.tsx \
        frontend/package.json frontend/pnpm-lock.yaml
git commit -m "feat(frontend): next-intl scaffolding with es-CO + en catalogs"
```

---

## Task 8: Frontend — Tailwind 4 design tokens (Style B Broadcast)

**Files:**
- Modify: `frontend/app/globals.css`

- [ ] **Step 1: Replace `globals.css` with the Broadcast token set**

Tailwind 4 uses CSS-first `@theme` rather than `tailwind.config.ts`. Replace `frontend/app/globals.css`:

```css
@import "tailwindcss";

@theme {
  /* ── Broadcast / sportsbook palette ─────────────────────────────────── */
  --color-bg-primary: #0a0e1a;
  --color-bg-elevated: #0f172a;
  --color-bg-header: #000814;
  --color-border-subtle: #1e293b;
  --color-border-accent: #00d4ff;
  --color-text-primary: #e2e8f0;
  --color-text-muted: #64748b;
  --color-accent-cyan: #00d4ff;
  --color-accent-purple: #a855f7;
  --color-state-good: #22c55e;
  --color-state-bad: #ef4444;
  --color-state-warning: #fbbf24;

  /* Typography */
  --font-sans: "Inter", ui-sans-serif, system-ui, sans-serif;
  --font-mono: ui-monospace, "SFMono-Regular", "Menlo", monospace;

  /* Sizing tokens for phone-first density */
  --radius-card: 0.5rem;
  --radius-pill: 9999px;
}

/* Bare element resets */
html, body {
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-family: var(--font-sans);
}

/* Monospace numerals where it matters (scores, pot, countdown) */
.font-mono-num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

/* Uppercase chrome utility */
.chrome-label {
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-muted);
}
```

- [ ] **Step 2: Update the existing landing page to use tokens**

Modify `frontend/app/page.tsx` — replace hardcoded Tailwind colors (`bg-zinc-50`, `text-black`, etc.) with token-driven utilities and route signed-in users to `/home`. Replace the file with:

```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { AuthButton } from "@/components/AuthButton";

type HealthResult =
  | { state: "up"; status: string }
  | { state: "down"; status: string }
  | { state: "error"; message: string };

async function fetchHealth(): Promise<HealthResult> {
  const base = process.env.API_URL ?? "http://localhost:8080";
  try {
    const res = await fetch(`${base}/actuator/health`, { cache: "no-store" });
    if (!res.ok) return { state: "down", status: `HTTP ${res.status}` };
    const data = (await res.json()) as { status?: string };
    const status = data.status ?? "UNKNOWN";
    return { state: status === "UP" ? "up" : "down", status };
  } catch (err) {
    return {
      state: "error",
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

export default async function Home() {
  const session = await auth();
  if (session?.userId) redirect("/home");

  const t = await getTranslations("landing");
  const health = await fetchHealth();
  const dotColor =
    health.state === "up"
      ? "bg-[var(--color-state-good)]"
      : health.state === "down"
        ? "bg-[var(--color-state-warning)]"
        : "bg-[var(--color-state-bad)]";

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 py-24">
      <div className="flex flex-col items-center gap-3 text-center">
        <h1 className="text-5xl font-semibold tracking-tight text-[var(--color-accent-cyan)] uppercase">
          {t("title")}
        </h1>
        <p className="text-lg text-[var(--color-text-muted)]">{t("subtitle")}</p>
      </div>

      <div className="rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-5 py-4">
        <div className="flex items-center gap-3">
          <span className={`inline-block h-2.5 w-2.5 rounded-full ${dotColor}`} />
          <span className="font-mono-num text-sm text-[var(--color-text-primary)]">
            {health.state === "error" ? t("backendDown") : `${t("backendUp")} · ${health.status}`}
          </span>
        </div>
      </div>

      <AuthButton />
    </main>
  );
}
```

- [ ] **Step 3: Run typecheck + lint + smoke E2E**

```bash
pnpm typecheck && pnpm lint
pnpm e2e
```

Expected: PASS. The Playwright smoke + axe scan should remain green.

- [ ] **Step 4: Commit**

```bash
git add frontend/app/globals.css frontend/app/page.tsx
git commit -m "feat(frontend): Style B broadcast design tokens + landing refresh"
```

---

## Task 9: Frontend — Shell components (TopBar, BottomNav, LocaleSwitcher)

**Files:**
- Create: `frontend/components/shell/TopBar.tsx`, `BottomNav.tsx`, `LocaleSwitcher.tsx`
- Test: `frontend/components/shell/TopBar.test.tsx`, `BottomNav.test.tsx`

- [ ] **Step 1: Write the failing BottomNav test**

Create `frontend/components/shell/BottomNav.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import { BottomNav } from "./BottomNav";

const messages = {
  nav: {
    myQuiniela: "Mi Quiniela",
    ranking: "Tabla",
    matches: "Partidos",
    compare: "vs",
  },
};

describe("BottomNav", () => {
  it("renders four tabs with translated labels", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <BottomNav activeKey="myQuiniela" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("link", { name: /mi quiniela/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /tabla/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /partidos/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /vs/i })).toBeInTheDocument();
  });

  it("marks the active tab with aria-current", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <BottomNav activeKey="ranking" />
      </NextIntlClientProvider>,
    );
    const active = screen.getByRole("link", { name: /tabla/i });
    expect(active).toHaveAttribute("aria-current", "page");
  });
});
```

- [ ] **Step 2: Run to see failure**

```bash
pnpm test BottomNav
```

Expected: FAIL.

- [ ] **Step 3: Implement BottomNav**

Create `frontend/components/shell/BottomNav.tsx`:

```tsx
import Link from "next/link";
import { useTranslations } from "next-intl";

export type BottomNavKey = "myQuiniela" | "ranking" | "matches" | "compare";

const NAV: ReadonlyArray<{ key: BottomNavKey; href: string }> = [
  { key: "myQuiniela", href: "/home" },
  { key: "ranking", href: "/ranking" },
  { key: "matches", href: "/matches" },
  { key: "compare", href: "/compare" },
];

export function BottomNav({ activeKey }: { activeKey?: BottomNavKey }) {
  const t = useTranslations("nav");
  return (
    <nav
      aria-label="primary"
      className="fixed inset-x-0 bottom-0 flex border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-header)]"
    >
      {NAV.map(({ key, href }) => {
        const isActive = key === activeKey;
        return (
          <Link
            key={key}
            href={href}
            aria-current={isActive ? "page" : undefined}
            className={`chrome-label flex flex-1 justify-center py-3 ${
              isActive
                ? "text-[var(--color-accent-cyan)]"
                : "text-[var(--color-text-muted)]"
            }`}
          >
            {t(key)}
          </Link>
        );
      })}
    </nav>
  );
}
```

- [ ] **Step 4: Run BottomNav test**

```bash
pnpm test BottomNav
```

Expected: PASS.

- [ ] **Step 5: Implement LocaleSwitcher**

Create `frontend/components/shell/LocaleSwitcher.tsx`:

```tsx
"use client";

import { useLocale } from "next-intl";
import { useRouter, usePathname } from "next/navigation";

export function LocaleSwitcher() {
  const router = useRouter();
  const pathname = usePathname();
  const locale = useLocale();

  function switchTo(next: string) {
    if (next === locale) return;
    // next-intl middleware handles prefix; cookie keeps the choice sticky.
    document.cookie = `NEXT_LOCALE=${next}; path=/; max-age=31536000; samesite=lax`;
    router.refresh();
  }

  return (
    <div className="flex gap-1 chrome-label">
      <button
        onClick={() => switchTo("es-CO")}
        aria-pressed={locale === "es-CO"}
        className={`px-2 py-1 ${locale === "es-CO" ? "text-[var(--color-accent-cyan)]" : ""}`}
      >
        ES
      </button>
      <button
        onClick={() => switchTo("en")}
        aria-pressed={locale === "en"}
        className={`px-2 py-1 ${locale === "en" ? "text-[var(--color-accent-cyan)]" : ""}`}
      >
        EN
      </button>
    </div>
  );
}
```

Note: relying on `NEXT_LOCALE` cookie for sticky locale. With `localePrefix: "as-needed"`, `next-intl` reads the cookie when present.

- [ ] **Step 6: Implement TopBar**

Create `frontend/components/shell/TopBar.tsx`:

```tsx
import { useTranslations } from "next-intl";
import { LocaleSwitcher } from "./LocaleSwitcher";

export type TopBarProps = {
  title?: string;
  meta?: React.ReactNode;
};

export function TopBar({ title, meta }: TopBarProps) {
  const t = useTranslations("common");
  return (
    <header className="flex items-center justify-between border-b-2 border-[var(--color-border-accent)] bg-[var(--color-bg-header)] px-3 py-3">
      <div className="flex items-baseline gap-3">
        <span className="chrome-label text-[var(--color-accent-cyan)]">
          {title ?? t("appName")}
        </span>
        {meta && (
          <span className="font-mono-num text-xs text-[var(--color-text-muted)]">{meta}</span>
        )}
      </div>
      <LocaleSwitcher />
    </header>
  );
}
```

- [ ] **Step 7: Add a TopBar test**

Create `frontend/components/shell/TopBar.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import { TopBar } from "./TopBar";

const messages = { common: { appName: "Quiniela Panas" } };

describe("TopBar", () => {
  it("renders default title from i18n when none provided", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <TopBar />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText("Quiniela Panas")).toBeInTheDocument();
  });

  it("renders custom title + meta", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <TopBar title="Tabla" meta="JOR 14" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText("Tabla")).toBeInTheDocument();
    expect(screen.getByText("JOR 14")).toBeInTheDocument();
  });
});
```

- [ ] **Step 8: Run shell tests**

```bash
pnpm test components/shell
```

Expected: PASS for both files.

- [ ] **Step 9: Commit**

```bash
git add frontend/components/shell/
git commit -m "feat(frontend): TopBar, BottomNav, LocaleSwitcher shell components"
```

---

## Task 10: Frontend — Auth integration with invitePath cookie

**Files:**
- Modify: `frontend/lib/auth.ts`
- Modify: `frontend/types/next-auth.d.ts`
- Create: `frontend/lib/api/client.ts`, `frontend/lib/api/invite.ts`, `frontend/lib/api/me.ts`

- [ ] **Step 1: Augment the Session type for role + invitePath**

Replace `frontend/types/next-auth.d.ts`:

```ts
import "next-auth";

declare module "next-auth" {
  interface Session {
    backendToken?: string;
    userId?: number;
    role?: "ADMIN" | "CAPTAIN" | "PLAYER";
    invitePath?: string | null;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    backendToken?: string;
    userId?: number;
    role?: "ADMIN" | "CAPTAIN" | "PLAYER";
    invitePath?: string | null;
  }
}
```

- [ ] **Step 2: Update `lib/auth.ts` to read the invitePath cookie**

Replace `frontend/lib/auth.ts`:

```ts
import NextAuth from "next-auth";
import Google from "next-auth/providers/google";
import { cookies } from "next/headers";

const apiUrl = process.env.API_URL ?? "http://localhost:8080";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [Google],
  callbacks: {
    async jwt({ token, account }) {
      if (account?.id_token) {
        // Read (and clear) the invite path captured by /join/[invitePath].
        const jar = await cookies();
        const invitePath = jar.get("invitePath")?.value ?? null;
        if (invitePath) jar.delete("invitePath");

        try {
          const res = await fetch(`${apiUrl}/auth/google`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ idToken: account.id_token, invitePath }),
          });
          if (res.ok) {
            const data = (await res.json()) as {
              token: string;
              userId: number;
              role: "ADMIN" | "CAPTAIN" | "PLAYER";
              invitePath: string | null;
            };
            token.backendToken = data.token;
            token.userId = data.userId;
            token.role = data.role;
            token.invitePath = data.invitePath;
          } else {
            console.error("Backend /auth/google rejected:", res.status, await res.text());
          }
        } catch (err) {
          console.error("Backend /auth/google call failed:", err);
        }
      }
      return token;
    },
    async session({ session, token }) {
      return {
        ...session,
        backendToken: token.backendToken,
        userId: token.userId,
        role: token.role,
        invitePath: token.invitePath,
      };
    },
  },
});
```

- [ ] **Step 3: Add the typed API client**

Create `frontend/lib/api/client.ts`:

```ts
import { auth } from "@/lib/auth";

const base = process.env.API_URL ?? "http://localhost:8080";

export type FetchOptions = Omit<RequestInit, "headers"> & {
  headers?: Record<string, string>;
  authed?: boolean;
};

export async function api<T>(path: string, opts: FetchOptions = {}): Promise<T> {
  const { authed = true, headers = {}, ...rest } = opts;
  if (authed) {
    const session = await auth();
    if (session?.backendToken) headers["Authorization"] = `Bearer ${session.backendToken}`;
  }
  const res = await fetch(`${base}${path}`, {
    cache: "no-store",
    ...rest,
    headers: { "Content-Type": "application/json", ...headers },
  });
  if (!res.ok) {
    const txt = await res.text();
    throw new ApiError(res.status, txt || res.statusText);
  }
  return (await res.json()) as T;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}
```

- [ ] **Step 4: Add invite + me wrappers**

Create `frontend/lib/api/invite.ts`:

```ts
import { api, ApiError } from "./client";

export type InviteResolution = {
  valid: boolean;
  inviterDisplayName: string;
  inviterRole: "ADMIN" | "CAPTAIN";
};

export async function resolveInvite(path: string): Promise<InviteResolution | null> {
  try {
    return await api<InviteResolution>(`/api/invite/${encodeURIComponent(path)}`, { authed: false });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}
```

Create `frontend/lib/api/me.ts`:

```ts
import { api } from "./client";

export type MeResponse = {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: "ADMIN" | "CAPTAIN" | "PLAYER";
  invitePath: string | null;
  canInvite: boolean;
  invitedByUserId: number | null;
};

export async function getMe(): Promise<MeResponse> {
  return api<MeResponse>("/api/me");
}
```

- [ ] **Step 5: Update MSW handlers for the new endpoints**

Modify `frontend/mocks/handlers.ts` (append, keeping existing handlers):

```ts
import { http, HttpResponse } from "msw";

export const handlers = [
  // existing handlers (health, etc.) stay above

  http.get(`${process.env.API_URL ?? "http://localhost:8080"}/api/invite/:path`, ({ params }) => {
    if (params.path === "valid-captain-abc") {
      return HttpResponse.json({
        valid: true,
        inviterDisplayName: "Andrés",
        inviterRole: "CAPTAIN",
      });
    }
    return new HttpResponse(null, { status: 404 });
  }),

  http.get(`${process.env.API_URL ?? "http://localhost:8080"}/api/me`, () =>
    HttpResponse.json({
      id: 1,
      email: "juan@example.com",
      displayName: "Juan",
      avatarUrl: null,
      role: "ADMIN",
      invitePath: "juan-abc123",
      canInvite: true,
      invitedByUserId: null,
    }),
  ),
];
```

- [ ] **Step 6: Run frontend tests**

```bash
pnpm typecheck && pnpm test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/lib/auth.ts frontend/types/next-auth.d.ts \
        frontend/lib/api/ frontend/mocks/handlers.ts
git commit -m "feat(frontend): auth carries invitePath through Auth.js, role on session"
```

---

## Task 11: Frontend — /join/[invitePath] landing page

**Files:**
- Create: `frontend/app/join/[invitePath]/page.tsx`
- Create: `frontend/app/join/[invitePath]/actions.ts`
- Test: `frontend/e2e/invite-landing.e2e.ts`

- [ ] **Step 1: Write the failing E2E test**

Create `frontend/e2e/invite-landing.e2e.ts`:

```ts
import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("invite landing resolves inviter and shows sign-in", async ({ page }) => {
  await page.goto("/join/valid-captain-abc");
  await expect(page.getByText(/Andrés te invitó/i)).toBeVisible();
  await expect(page.getByRole("button", { name: /iniciar sesión con Google/i })).toBeVisible();

  // A11y: no violations on the invite page.
  const a11y = await new AxeBuilder({ page }).analyze();
  expect(a11y.violations).toEqual([]);
});

test("invalid invite shows friendly error and no sign-in CTA", async ({ page }) => {
  await page.goto("/join/totally-bogus");
  await expect(page.getByText(/invitación no válida/i)).toBeVisible();
  await expect(page.getByRole("button", { name: /iniciar sesión/i })).toHaveCount(0);
});
```

- [ ] **Step 2: Run to confirm failure (page doesn't exist)**

```bash
pnpm e2e -g "invite landing"
```

Expected: FAIL (404).

Note: MSW handlers run in unit tests, not in the production `next start` server used by Playwright. For the E2E pass, the backend either needs to be running real, OR we need a fixture mock. For now, **skip the test** until the next task wires the backend stub. Add `test.skip(...)` if needed, then unskip once Plan 2 brings up the full backend in CI.

Document this in the test file:

```ts
// SKIP: requires a running backend with the seeded invite. Unskip once
// Plan 2 task "tournament seed + tests against running backend" lands.
test.skip(...);
```

- [ ] **Step 3: Create the cookie-setting server action**

Create `frontend/app/join/[invitePath]/actions.ts`:

```ts
"use server";

import { cookies } from "next/headers";

export async function rememberInvitePath(path: string) {
  const jar = await cookies();
  jar.set("invitePath", path, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    maxAge: 60 * 30, // 30 minutes
    path: "/",
  });
}
```

- [ ] **Step 4: Build the page**

Create `frontend/app/join/[invitePath]/page.tsx`:

```tsx
import { getTranslations } from "next-intl/server";
import { resolveInvite } from "@/lib/api/invite";
import { AuthButton } from "@/components/AuthButton";
import { TopBar } from "@/components/shell/TopBar";
import { rememberInvitePath } from "./actions";

type Params = { invitePath: string };

export default async function JoinPage({
  params,
}: {
  params: Promise<Params>;
}) {
  const { invitePath } = await params;
  const t = await getTranslations("invite");

  const resolution = await resolveInvite(invitePath);
  if (!resolution) {
    return (
      <main className="flex min-h-screen flex-col">
        <TopBar />
        <section className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <h1 className="text-2xl font-semibold text-[var(--color-state-bad)]">
            {t("invalid")}
          </h1>
          <p className="text-[var(--color-text-muted)]">{t("invalidHelp")}</p>
        </section>
      </main>
    );
  }

  // Capture the invite path in a cookie so it's available during the
  // Auth.js OAuth round-trip.
  await rememberInvitePath(invitePath);

  return (
    <main className="flex min-h-screen flex-col">
      <TopBar />
      <section className="flex flex-1 flex-col items-center justify-center gap-6 px-6 text-center">
        <div className="space-y-2">
          <h1 className="text-3xl font-semibold text-[var(--color-text-primary)]">
            {t("invitedBy", { name: resolution.inviterDisplayName })}
          </h1>
          <p className="text-[var(--color-text-muted)]">{t("joinPrompt")}</p>
        </div>
        <AuthButton />
      </section>
    </main>
  );
}
```

- [ ] **Step 5: Run typecheck + lint**

```bash
pnpm typecheck && pnpm lint
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/app/join/
git commit -m "feat(frontend): /join/[invitePath] landing with inviter resolution + cookie capture"
```

---

## Task 12: Frontend — /home empty lobby + role-gated invite CTA

**Files:**
- Create: `frontend/app/home/page.tsx`
- Create: `frontend/components/lobby/CountdownChip.tsx`, `PotChip.tsx`, `GroupCardSkeleton.tsx`, `KnockoutLockedCard.tsx`
- Create: `frontend/components/invite/InviteFriendsButton.tsx`, `InviteFriendsSheet.tsx`
- Test: `frontend/components/invite/InviteFriendsButton.test.tsx`, `InviteFriendsSheet.test.tsx`

- [ ] **Step 1: Write the failing InviteFriendsButton test**

Create `frontend/components/invite/InviteFriendsButton.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import { InviteFriendsButton } from "./InviteFriendsButton";

const messages = { lobby: { inviteFriends: "Invitar amigos" } };

describe("InviteFriendsButton", () => {
  it("renders for admin", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsButton role="ADMIN" invitePath="juan-abc123" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("button", { name: /invitar amigos/i })).toBeInTheDocument();
  });

  it("renders for captain", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsButton role="CAPTAIN" invitePath="cap-q1w2e3" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("button", { name: /invitar amigos/i })).toBeInTheDocument();
  });

  it("renders nothing for player", () => {
    const { container } = render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsButton role="PLAYER" invitePath={null} />
      </NextIntlClientProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
```

- [ ] **Step 2: Run to confirm failure**

```bash
pnpm test InviteFriendsButton
```

Expected: FAIL.

- [ ] **Step 3: Implement InviteFriendsSheet**

Create `frontend/components/invite/InviteFriendsSheet.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";

export function InviteFriendsSheet({
  invitePath,
  onClose,
}: {
  invitePath: string;
  onClose: () => void;
}) {
  const t = useTranslations("invite_sheet");
  const locale = useLocale();
  const [copied, setCopied] = useState(false);

  const fullUrl =
    typeof window !== "undefined"
      ? `${window.location.origin}/join/${invitePath}`
      : `/join/${invitePath}`;

  async function copy() {
    await navigator.clipboard.writeText(fullUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  const waText = t("whatsappMessage", { url: fullUrl });
  const waHref = `https://wa.me/?text=${encodeURIComponent(waText)}`;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t("title")}
      lang={locale}
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/60"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-t-xl border-t-2 border-[var(--color-border-accent)] bg-[var(--color-bg-elevated)] p-5 space-y-4"
      >
        <h2 className="chrome-label text-[var(--color-accent-cyan)]">{t("title")}</h2>
        <div>
          <span className="chrome-label">{t("linkLabel")}</span>
          <p className="break-all rounded bg-[var(--color-bg-primary)] px-3 py-2 font-mono-num text-sm">
            {fullUrl}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={copy}
            className="flex-1 rounded border border-[var(--color-border-accent)] py-3 chrome-label text-[var(--color-accent-cyan)]"
          >
            {copied ? t("copied") : t("copy")}
          </button>
          <a
            href={waHref}
            target="_blank"
            rel="noopener noreferrer"
            className="flex-1 rounded bg-[var(--color-state-good)] py-3 text-center chrome-label text-black"
          >
            {t("whatsapp")}
          </a>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Implement InviteFriendsButton**

Create `frontend/components/invite/InviteFriendsButton.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { InviteFriendsSheet } from "./InviteFriendsSheet";

type Role = "ADMIN" | "CAPTAIN" | "PLAYER";

export function InviteFriendsButton({
  role,
  invitePath,
}: {
  role: Role;
  invitePath: string | null;
}) {
  const t = useTranslations("lobby");
  const [open, setOpen] = useState(false);

  if (role === "PLAYER" || !invitePath) return null;

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="w-full rounded border border-[var(--color-border-accent)] py-3 chrome-label text-[var(--color-accent-cyan)]"
      >
        {t("inviteFriends")}
      </button>
      {open && <InviteFriendsSheet invitePath={invitePath} onClose={() => setOpen(false)} />}
    </>
  );
}
```

- [ ] **Step 5: Run the test**

```bash
pnpm test InviteFriendsButton
```

Expected: PASS.

- [ ] **Step 6: Implement the lobby placeholder components**

Create `frontend/components/lobby/CountdownChip.tsx`:

```tsx
"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";

const KICKOFF = new Date("2026-06-11T17:00:00Z").getTime();

export function CountdownChip() {
  const t = useTranslations("lobby");
  const [days, setDays] = useState(daysUntil());
  useEffect(() => {
    const id = setInterval(() => setDays(daysUntil()), 60_000);
    return () => clearInterval(id);
  }, []);
  return (
    <span className="font-mono-num text-xs text-[var(--color-accent-cyan)] border border-[var(--color-border-accent)] rounded px-2 py-1">
      {t("countdown", { days })}
    </span>
  );
}

function daysUntil() {
  return Math.max(0, Math.ceil((KICKOFF - Date.now()) / (1000 * 60 * 60 * 24)));
}
```

Create `frontend/components/lobby/PotChip.tsx`:

```tsx
import { useTranslations } from "next-intl";

export function PotChip({ potCents, paidCount }: { potCents: number; paidCount: number }) {
  const t = useTranslations("lobby");
  const pot = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    potCents / 100,
  );
  return (
    <span className="font-mono-num text-xs text-[var(--color-state-good)] border border-[var(--color-border-subtle)] rounded px-2 py-1">
      {t("potChip", { pot, paid: paidCount })}
    </span>
  );
}
```

Create `frontend/components/lobby/GroupCardSkeleton.tsx`:

```tsx
export function GroupCardSkeleton({ letter }: { letter: string }) {
  return (
    <div className="flex items-center justify-between border-l-3 border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-3 py-3">
      <div>
        <strong className="text-sm">Grupo {letter}</strong>
        <div className="chrome-label">0/6</div>
      </div>
      <div className="h-1 w-14 bg-[var(--color-border-subtle)]" />
    </div>
  );
}
```

Create `frontend/components/lobby/KnockoutLockedCard.tsx`:

```tsx
import { useTranslations } from "next-intl";

export function KnockoutLockedCard() {
  const t = useTranslations("lobby");
  return (
    <div className="flex items-center justify-between border-l-3 border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-3 py-3 opacity-50">
      <div>
        <strong className="text-sm">🔒 {t("knockoutsHeading")}</strong>
        <div className="chrome-label">{t("knockoutsLocked")}</div>
      </div>
    </div>
  );
}
```

- [ ] **Step 7: Build the /home lobby page**

Create `frontend/app/home/page.tsx`:

```tsx
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CountdownChip } from "@/components/lobby/CountdownChip";
import { PotChip } from "@/components/lobby/PotChip";
import { GroupCardSkeleton } from "@/components/lobby/GroupCardSkeleton";
import { KnockoutLockedCard } from "@/components/lobby/KnockoutLockedCard";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";

const GROUP_LETTERS = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"];

export default async function HomePage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  const t = await getTranslations("lobby");

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={t("title")} meta={`${me.displayName} · 0/104`} />

      <div className="flex flex-wrap gap-2 px-3 py-3">
        <CountdownChip />
        <PotChip potCents={0} paidCount={0} />
      </div>

      <section className="px-3 space-y-1">
        <span className="chrome-label">{t("groupsHeading")}</span>
        {GROUP_LETTERS.map((letter) => (
          <GroupCardSkeleton key={letter} letter={letter} />
        ))}
      </section>

      <section className="px-3 py-3 space-y-1">
        <span className="chrome-label">{t("knockoutsHeading")}</span>
        <KnockoutLockedCard />
      </section>

      <section className="px-3 py-3 space-y-2">
        <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
      </section>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}
```

- [ ] **Step 8: Add InviteFriendsSheet smoke test**

Create `frontend/components/invite/InviteFriendsSheet.test.tsx`:

```tsx
import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { InviteFriendsSheet } from "./InviteFriendsSheet";

const messages = {
  invite_sheet: {
    title: "Invita a un amigo",
    linkLabel: "Tu enlace personal",
    copy: "Copiar",
    copied: "Copiado",
    whatsapp: "Compartir por WhatsApp",
    whatsappMessage: "Únete: {url}",
  },
};

describe("InviteFriendsSheet", () => {
  beforeEach(() => {
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
  });

  it("renders the personal link and WhatsApp share link", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsSheet invitePath="juan-abc123" onClose={() => {}} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText(/juan-abc123/)).toBeInTheDocument();
    const wa = screen.getByRole("link", { name: /WhatsApp/i });
    expect(wa.getAttribute("href")).toContain("wa.me");
    expect(wa.getAttribute("href")).toContain(encodeURIComponent("juan-abc123"));
  });

  it("copies the link on click", async () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsSheet invitePath="juan-abc123" onClose={() => {}} />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: /copiar/i }));
    expect(navigator.clipboard.writeText).toHaveBeenCalled();
  });
});
```

- [ ] **Step 9: Run all frontend tests**

```bash
pnpm typecheck && pnpm lint && pnpm test
```

Expected: PASS.

- [ ] **Step 10: Run e2e smoke (skip invite landing if backend isn't running)**

```bash
pnpm e2e -g "smoke"
```

Expected: PASS (the original smoke test).

- [ ] **Step 11: Commit**

```bash
git add frontend/app/home/ frontend/components/lobby/ frontend/components/invite/
git commit -m "feat(frontend): /home lobby with countdown/pot chips + role-gated invite CTA"
```

---

## Self-Review

**1. Spec coverage:**

- Spec item 1 (Google sign-in via Auth.js) → already in repo, refreshed in Task 10.
- Spec item 2 (invite-only access via personal links, role-gated CTA) → Tasks 1, 4, 5, 11, 12.
- Spec items 3–8 (lobby/bracket fill/scoring/locking) → deliberately deferred to Plan 2.
- Spec items 9–14 (spectator + admin + XLSX) → deferred to Plan 3.
- Spec item 13 (admin payments + prize-split) data model → seeded in V003 (Task 1); UI deferred to Plan 3.
- Spec item 16 (i18n scaffolding) → Task 7.
- Spec item 17 (multi-tournament schema) → confirmed in V001/V002; V003 (Task 1) seeds tournament id=1 and pool id=1.
- App shell + visual style B → Tasks 8 (tokens), 9 (TopBar/BottomNav/LocaleSwitcher).
- Routes `/`, `/join/:invitePath`, `/home` → Tasks 8, 11, 12.

**2. Placeholder scan:** no "TBD" / "TODO" / "implement later" anywhere. Test code is fully specified. Migration SQL is complete. The only deliberate skip is the Playwright invite-landing test that needs a real backend (annotated `test.skip(...)` with a documented unskip condition).

**3. Type consistency:**

- `UserRole` enum names (`ADMIN`/`CAPTAIN`/`PLAYER`) are consistent across backend, JWT claim (`role`), `SessionResponse.role`, frontend `Session.role`, `MeResponse.role`, `InviteResolution.inviterRole`.
- `InviteResolution` shape matches between backend `InviteController.InviteResolution`, frontend `lib/api/invite.ts`, MSW handler, and the `/join/[invitePath]/page.tsx` consumer.
- `MeResponse` shape matches between backend `MeController.MeResponse`, frontend `lib/api/me.ts`, MSW handler, and `/home/page.tsx`.
- `invitePath` is `string | null` everywhere (admin/captain have one, player has `null`).
- `BottomNavKey` union (`"myQuiniela"|"ranking"|"matches"|"compare"`) matches the `nav.*` i18n keys in both message catalogs.

No drift found.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-25-quiniela-plan-1-foundation-auth-invite.md`. Two execution options:

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

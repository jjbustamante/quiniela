# Plan 4 — Payments ledger (two-tier)

**Status:** Ready for implementation
**Created:** 2026-05-28
**Design:** [`docs/superpowers/specs/2026-05-28-payments-ledger-design.md`](../specs/2026-05-28-payments-ledger-design.md)

> **For agentic workers:** REQUIRED SUB-SKILL — use superpowers:subagent-driven-development (preferred) or superpowers:executing-plans. Each task is one commit, test-first (write failing test → verify red → implement → verify green → commit), matching the established Plan 1–3 rhythm in this repo.

## Goal

Add a two-tier payments accountability ledger. A captain marks their own
invitees `paid` (the player handed over their entry fee); the admin marks a
captain `settled` (the captain remitted their collected pile). **The pot is
unchanged** — `PublicSummaryController` keeps `pot = panas × entry_fee`, so
nothing here touches the public pot or ranking payouts.

Scope: core ledger only. Prize-split editor UI, CSV export, and the lobby
"sin pagar" pill are **out of scope** (deferred, see design doc).

## Conventions (match the existing codebase)

- Backend: Spring Boot 4, Java 25, Flyway plain-SQL migrations. Writes via JPA
  entity + repository (mirror `bet/Bet.java` + `BetId.java`); aggregate reads
  via `JdbcTemplate` (mirror `ranking/RankingService.java`,
  `matches/MatchesService.java`).
- Role checks live in the service via `ResponseStatusException` (mirror
  `admin/AdminResultsService.requireAdmin`), not Spring Security annotations.
  `SecurityConfig` already requires auth on everything except `/api/public/**`
  + `/actuator/**`, so JWT presence is guaranteed; the service does the
  role/ownership gate.
- Spotless + Google Java Format runs on `verify`; if a task fails on format,
  run `./mvnw spotless:apply` then re-verify (do not hand-format).
- Frontend: Next.js 16, server components + server actions, `next-intl`,
  Spanish UI copy. API client at `frontend/lib/api/client.ts` (`api<T>()`,
  `cache: 'no-store'` default).
- Run backend from `backend/` with `./mvnw -B verify`. Run frontend gates
  from `frontend/` with `pnpm typecheck` + `pnpm lint`.
- DB facts: `users(id, display_name, role, invited_by_user_id)`,
  `role ∈ {admin,captain,player}`; `pool(id=1, entry_fee_cents=2000)`;
  `pool_membership(pool_id, user_id)`; `UserRepository.findByInvitedByUserId(Long)`
  and `PoolMembershipRepository.findUserIdsByPoolId(Long)` already exist.
- `ACTIVE_POOL_ID = 1L`.

## Progress

- [x] Task 1: V011 payment migration + migration test
- [x] Task 2: Payment entity + PaymentId + PaymentRepository
- [x] Task 3: PaymentService + PaymentController (captain: my-subgroup + mark paid) + IT
- [x] Task 4: AdminPaymentService + AdminPaymentController (full ledger + mark settled) + IT
- [x] Task 5: Frontend captain payments — lib/api/payments.ts + /captain/payments + actions
- [x] Task 6: Frontend admin payments — /admin/payments + actions
- [x] Task 7: i18n keys + lobby "Pagos" entry-point link
- [ ] Task 8: Verify end-to-end + ship

---

## Task 1: V011 payment migration + migration test

**Goal:** Land the `payment` table.

**Files:**
- Create: `backend/src/main/resources/db/migration/V011__payments.sql`
- Create: `backend/src/test/java/io/quiniela/api/support/V011MigrationTest.java`

**Step 1: Write the failing migration test**

Create `V011MigrationTest.java` (mirror `V005MigrationTest`):

```java
package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V011MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void paymentTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'payment' AND table_schema = 'public'",
            String.class);
    assertThat(columns)
        .contains(
            "pool_id",
            "user_id",
            "paid",
            "paid_at",
            "marked_paid_by",
            "amount_cents",
            "note",
            "settled",
            "settled_at",
            "marked_settled_by");
  }
}
```

**Step 2:** `cd backend && ./mvnw verify` — expect FAIL (table missing).

**Step 3: Write the migration**

Create `V011__payments.sql`:

```sql
-- V011: Two-tier payments ledger.
--
-- `paid`    — the member handed their entry fee to the person who invited them
--             (their captain, or the admin). Set by that inviter or the admin.
-- `settled` — a captain remitted their collected pile to the admin. Set by the
--             admin only; meaningless (stays false) for non-captains.
--
-- The pot is NOT derived from this table — PublicSummaryController keeps
-- pot = members × entry_fee. This ledger is an accountability layer only.
--
-- Rows are created lazily on first toggle (LEFT JOIN on read; absent = unpaid).

CREATE TABLE payment (
    pool_id           BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    paid              BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at           TIMESTAMPTZ,
    marked_paid_by    BIGINT REFERENCES users(id),
    amount_cents      INT,            -- NULL = use pool.entry_fee_cents
    note              TEXT,
    settled           BOOLEAN NOT NULL DEFAULT FALSE,
    settled_at        TIMESTAMPTZ,
    marked_settled_by BIGINT REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (pool_id, user_id),
    CHECK (amount_cents IS NULL OR amount_cents >= 0)
);

CREATE INDEX idx_payment_pool ON payment(pool_id);
```

**Step 4:** `./mvnw verify` — expect PASS (all prior ITs still green).

**Step 5: Commit**
```
git add backend/src/main/resources/db/migration/V011__payments.sql \
        backend/src/test/java/io/quiniela/api/support/V011MigrationTest.java
git commit -m "feat(backend): V011 migration — payment ledger table"
```

---

## Task 2: Payment entity + PaymentId + PaymentRepository

**Goal:** JPA write-path for payment rows (mirror `bet/Bet.java` + `BetId.java`).

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/payment/Payment.java`
- Create: `backend/src/main/java/io/quiniela/api/payment/PaymentId.java`
- Create: `backend/src/main/java/io/quiniela/api/payment/PaymentRepository.java`
- Create: `backend/src/test/java/io/quiniela/api/payment/PaymentRepositoryIT.java`

**Step 1: Write the failing test**

```java
package io.quiniela.api.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentRepositoryIT extends AbstractIntegrationTest {

  @Autowired PaymentRepository payments;
  @Autowired UserRepository users;

  @Test
  void upsertsAndReadsBack() {
    var u = new User("g-pay1", "pay1@example.com", "Pay One", null, UserRole.PLAYER);
    u.setInvitePath("pay1-abc");
    u = users.save(u);

    var p = new Payment(1L, u.getId());
    p.markPaid(99L, 2000, "Zelle 8821");
    payments.save(p);

    var fetched = payments.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    assertThat(fetched.isPaid()).isTrue();
    assertThat(fetched.getAmountCents()).isEqualTo(2000);
    assertThat(fetched.getNote()).isEqualTo("Zelle 8821");
    assertThat(fetched.isSettled()).isFalse();
  }
}
```

**Step 2:** `./mvnw verify` — expect FAIL (classes missing).

**Step 3: Create `PaymentId.java`** (mirror `BetId`):

```java
package io.quiniela.api.payment;

import java.io.Serializable;
import java.util.Objects;

public class PaymentId implements Serializable {
  private Long poolId;
  private Long userId;

  public PaymentId() {}

  public PaymentId(Long poolId, Long userId) {
    this.poolId = poolId;
    this.userId = userId;
  }

  public Long getPoolId() { return poolId; }
  public Long getUserId() { return userId; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PaymentId other)) return false;
    return Objects.equals(poolId, other.poolId) && Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() { return Objects.hash(poolId, userId); }
}
```

**Step 4: Create `Payment.java`:**

```java
package io.quiniela.api.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "payment")
@IdClass(PaymentId.class)
public class Payment {

  @Id
  @Column(name = "pool_id")
  private Long poolId;

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(nullable = false)
  private boolean paid;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "marked_paid_by")
  private Long markedPaidBy;

  @Column(name = "amount_cents")
  private Integer amountCents;

  @Column private String note;

  @Column(nullable = false)
  private boolean settled;

  @Column(name = "settled_at")
  private Instant settledAt;

  @Column(name = "marked_settled_by")
  private Long markedSettledBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Payment() {}

  public Payment(Long poolId, Long userId) {
    this.poolId = poolId;
    this.userId = userId;
  }

  /** Set/clear the paid flag, stamping who + when on transition to paid. */
  public void setPaid(boolean paid, Long byUserId, Integer amountCents, String note) {
    this.paid = paid;
    this.amountCents = amountCents;
    this.note = note;
    if (paid) {
      this.paidAt = Instant.now();
      this.markedPaidBy = byUserId;
    } else {
      this.paidAt = null;
      this.markedPaidBy = null;
    }
  }

  /** Convenience for tests. */
  public void markPaid(Long byUserId, Integer amountCents, String note) {
    setPaid(true, byUserId, amountCents, note);
  }

  public void setSettled(boolean settled, Long byUserId) {
    this.settled = settled;
    if (settled) {
      this.settledAt = Instant.now();
      this.markedSettledBy = byUserId;
    } else {
      this.settledAt = null;
      this.markedSettledBy = null;
    }
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

  public Long getPoolId() { return poolId; }
  public Long getUserId() { return userId; }
  public boolean isPaid() { return paid; }
  public Integer getAmountCents() { return amountCents; }
  public String getNote() { return note; }
  public boolean isSettled() { return settled; }
}
```

**Step 5: Create `PaymentRepository.java`:**

```java
package io.quiniela.api.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, PaymentId> {

  Optional<Payment> findByPoolIdAndUserId(Long poolId, Long userId);
}
```

**Step 6:** `./mvnw verify` — expect PASS. **Step 7:** commit:
```
git add backend/src/main/java/io/quiniela/api/payment/ \
        backend/src/test/java/io/quiniela/api/payment/PaymentRepositoryIT.java
git commit -m "feat(backend): Payment entity + repository"
```

---

## Task 3: PaymentService + PaymentController (captain-facing)

**Goal:** `GET /api/payments/my-subgroup` + `PUT /api/payments/{userId}/paid` with inviter-or-admin authz.

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/payment/PaymentService.java`
- Create: `backend/src/main/java/io/quiniela/api/payment/PaymentController.java`
- Create: `backend/src/test/java/io/quiniela/api/payment/PaymentControllerIT.java`

**Step 1: Write the failing IT** (mirror `MatchesControllerIT` setup with MockMvc + JwtService).

Cases (all on pool 1; helpers create users + memberships):
- `requiresAuth`: `GET /api/payments/my-subgroup` with no token → 401.
- `captainSeesOwnInviteesWithTotals`: captain C invited players P1, P2 (both pool members); mark P1 paid; `GET /api/payments/my-subgroup` as C → `subgroup.length == 2`, `expectedCents == 2*2000`, `collectedCents == 2000`, P1.paid true, P2.paid false.
- `captainMarksOwnPlayerPaid`: `PUT /api/payments/{P1}/paid` body `{"paid":true}` as C → 200, response paid true; re-GET shows collected updated.
- `captainCannotMarkOtherCaptainsPlayer`: captain C2 (didn't invite P1) → `PUT /api/payments/{P1}/paid` → 403.
- `playerCannotMarkAnyone`: player P1 → `PUT /api/payments/{P2}/paid` → 403.
- `adminCanMarkAnyone`: admin → `PUT /api/payments/{P1}/paid` → 200.
- `unknownMemberReturns404`: admin → `PUT /api/payments/{nonMemberUserId}/paid` → 404.

Membership helper:
```java
private void addMember(Long userId) {
  jdbc.update("INSERT INTO pool_membership (pool_id, user_id, joined_at) "
      + "VALUES (1, ?, NOW()) ON CONFLICT DO NOTHING", userId);
}
```
User helper sets `invited_by_user_id` via `u.setInvitedByUserId(captainId)` before save (confirm setter exists on `User`; if not, set via `jdbc.update("UPDATE users SET invited_by_user_id = ? WHERE id = ?", ...)`).

**Step 2:** `./mvnw verify` — expect FAIL.

**Step 3: Create `PaymentService.java`:**

```java
package io.quiniela.api.payment;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

  static final Long ACTIVE_POOL_ID = 1L;

  private final PaymentRepository payments;
  private final UserRepository users;
  private final JdbcTemplate jdbc;

  public PaymentService(PaymentRepository payments, UserRepository users, DataSource ds) {
    this.payments = payments;
    this.users = users;
    this.jdbc = new JdbcTemplate(ds);
  }

  public record SubgroupMember(
      Long userId, String displayName, boolean paid, Integer amountCents) {}

  public record SubgroupView(
      long expectedCents, long collectedCents, boolean ownSettled, List<SubgroupMember> members) {}

  public record MarkPaidRequest(boolean paid, Integer amountCents, String note) {}

  public record PaymentRowView(Long userId, boolean paid, Integer amountCents, String note) {}

  private int entryFeeCents() {
    return jdbc.queryForObject(
        "SELECT entry_fee_cents FROM pool WHERE id = ?", Integer.class, ACTIVE_POOL_ID);
  }

  @Transactional(readOnly = true)
  public SubgroupView mySubgroup(Long callerId) {
    int fee = entryFeeCents();
    List<SubgroupMember> members =
        jdbc.query(
            """
            SELECT u.id AS user_id, u.display_name AS display_name,
                   COALESCE(p.paid, false) AS paid, p.amount_cents AS amount_cents
            FROM users u
            JOIN pool_membership pm ON pm.user_id = u.id AND pm.pool_id = ?
            LEFT JOIN payment p ON p.user_id = u.id AND p.pool_id = ?
            WHERE u.invited_by_user_id = ?
            ORDER BY u.display_name ASC
            """,
            (rs, n) ->
                new SubgroupMember(
                    rs.getLong("user_id"),
                    rs.getString("display_name"),
                    rs.getBoolean("paid"),
                    (Integer) rs.getObject("amount_cents")),
            ACTIVE_POOL_ID,
            ACTIVE_POOL_ID,
            callerId);

    long expected = (long) members.size() * fee;
    long collected =
        members.stream()
            .filter(SubgroupMember::paid)
            .mapToLong(m -> m.amountCents() != null ? m.amountCents() : fee)
            .sum();
    boolean ownSettled =
        payments.findByPoolIdAndUserId(ACTIVE_POOL_ID, callerId).map(Payment::isSettled).orElse(false);
    return new SubgroupView(expected, collected, ownSettled, members);
  }

  @Transactional
  public PaymentRowView markPaid(Long callerId, Long targetUserId, MarkPaidRequest req) {
    User caller =
        users.findById(callerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    User target =
        users
            .findById(targetUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown user"));

    boolean isMember =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pool_membership WHERE pool_id = ? AND user_id = ?)",
                Boolean.class,
                ACTIVE_POOL_ID,
                targetUserId));
    if (!isMember) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a pool member");

    boolean allowed =
        caller.getRole() == UserRole.ADMIN || callerId.equals(target.getInvitedByUserId());
    if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your invitee");

    Payment p =
        payments
            .findByPoolIdAndUserId(ACTIVE_POOL_ID, targetUserId)
            .orElseGet(() -> new Payment(ACTIVE_POOL_ID, targetUserId));
    p.setPaid(req.paid(), callerId, req.amountCents(), req.note());
    payments.save(p);
    return new PaymentRowView(targetUserId, p.isPaid(), p.getAmountCents(), p.getNote());
  }
}
```

**Step 4: Create `PaymentController.java`:**

```java
package io.quiniela.api.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

  private final PaymentService service;

  public PaymentController(PaymentService service) {
    this.service = service;
  }

  @GetMapping("/my-subgroup")
  public ResponseEntity<PaymentService.SubgroupView> mySubgroup(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.mySubgroup(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping("/{userId}/paid")
  public ResponseEntity<PaymentService.PaymentRowView> markPaid(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long userId,
      @RequestBody PaymentService.MarkPaidRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.markPaid(Long.parseLong(jwt.getSubject()), userId, req));
  }
}
```

**Step 5:** `./mvnw verify` — expect PASS (run `spotless:apply` first if format fails). **Step 6:** commit:
```
git add backend/src/main/java/io/quiniela/api/payment/PaymentService.java \
        backend/src/main/java/io/quiniela/api/payment/PaymentController.java \
        backend/src/test/java/io/quiniela/api/payment/PaymentControllerIT.java
git commit -m "feat(backend): captain payments — my-subgroup + mark paid"
```

---

## Task 4: AdminPaymentService + AdminPaymentController (admin-facing)

**Goal:** `GET /api/admin/payments` (full ledger grouped by captain) + `PUT /api/admin/payments/{captainId}/settled`, admin-only.

**Files:**
- Create: `backend/src/main/java/io/quiniela/api/payment/AdminPaymentService.java`
- Create: `backend/src/main/java/io/quiniela/api/payment/AdminPaymentController.java`
- Create: `backend/src/test/java/io/quiniela/api/payment/AdminPaymentControllerIT.java`

**Ledger grouping rules:**
- A **captain group** = each user with `role = 'captain'` who is a pool member.
  Its `members` = pool members with `invited_by_user_id = captain.id`. Subtotal
  `expectedCents = members.size * fee`, `collectedCents = Σ paid amounts`.
  Group carries the captain's own `paid` + `settled` flags.
- **Orphans group** = pool members who are neither captains nor invited by a
  captain (e.g. admin's direct player invitees) + the admin's own row. Use a
  group with `captain == null`.
- Pool totals: `potCents` = `memberCount * fee` (same figure as public
  summary), `paidCount` = members with `paid = true`, `memberCount`.

**Step 1: Write the failing IT.** Cases:
- `requiresAdmin`: non-admin (captain) `GET /api/admin/payments` → 403.
- `groupsByCaptainWithSubtotals`: admin A; captain C invited P1,P2; P1 paid;
  `GET` as admin → a captain group for C with 2 members + `expectedCents
  4000` + `collectedCents 2000`; totals present.
- `settledIsAdminOnly`: captain → `PUT /api/admin/payments/{C}/settled`
  `{"settled":true}` → 403; admin → 200, and C's group now `settled true`.
- `settledUnknownMember404`: admin → `PUT .../{nonMember}/settled` → 404.

**Step 2:** `./mvnw verify` — FAIL.

**Step 3: Create `AdminPaymentService.java`** — `requireAdmin(callerId)` (copy the
pattern from `AdminResultsService`), a `JdbcTemplate` query that pulls all pool
members with their role, inviter, paid/amount, settled; assemble groups in
Java. Records:

```java
public record LedgerMember(Long userId, String displayName, String role,
    boolean paid, Integer amountCents, String note);
public record CaptainGroup(Long captainId, String captainName, boolean captainPaid,
    boolean captainSettled, long expectedCents, long collectedCents, List<LedgerMember> members);
public record LedgerView(long potCents, long paidCount, long memberCount,
    List<CaptainGroup> captains, List<LedgerMember> orphans);
```

`markSettled(callerId, captainId, settled)`: `requireAdmin`; 404 if captainId
not a member; upsert `Payment` via `setSettled(settled, callerId)`; return the
captain's row view.

**Step 4: Create `AdminPaymentController.java`** under `@RequestMapping("/api/admin/payments")` with `GET` → `getLedger(callerId)` and `PUT /{captainId}/settled`.

**Step 5:** `./mvnw verify` — PASS (spotless:apply if needed). **Step 6:** commit:
```
git add backend/src/main/java/io/quiniela/api/payment/AdminPaymentService.java \
        backend/src/main/java/io/quiniela/api/payment/AdminPaymentController.java \
        backend/src/test/java/io/quiniela/api/payment/AdminPaymentControllerIT.java
git commit -m "feat(backend): admin payments ledger + mark settled"
```

---

## Task 5: Frontend captain payments

**Goal:** `/captain/payments` page where a captain marks their invitees paid.

**Files:**
- Create: `frontend/lib/api/payments.ts`
- Create: `frontend/app/captain/payments/page.tsx`
- Create: `frontend/app/captain/payments/actions.ts`
- Create: `frontend/components/payments/SubgroupRow.tsx` (client — paid toggle)

**Step 1: `lib/api/payments.ts`** — types + clients mirroring `lib/api/ranking.ts`:

```ts
import { api } from "./client";

export type SubgroupMember = {
  userId: number;
  displayName: string;
  paid: boolean;
  amountCents: number | null;
};
export type SubgroupView = {
  expectedCents: number;
  collectedCents: number;
  ownSettled: boolean;
  members: SubgroupMember[];
};

export async function getMySubgroup(): Promise<SubgroupView> {
  return api<SubgroupView>("/api/payments/my-subgroup");
}

export async function markPaid(
  userId: number,
  paid: boolean,
  amountCents?: number,
  note?: string,
): Promise<void> {
  await api(`/api/payments/${userId}/paid`, {
    method: "PUT",
    body: JSON.stringify({ paid, amountCents: amountCents ?? null, note: note ?? null }),
  });
}
```

**Step 2: `actions.ts`** — server action wrapping `markPaid` + `revalidatePath("/captain/payments")` (mirror `app/group/[groupId]/actions.ts`).

**Step 3: `page.tsx`** — server component: `auth()` guard → redirect `/` if no session; fetch `getMySubgroup()`; render a `collected $X / $Y` header (use `formatPot` from `lib/tournament-format`), an own-settled badge, and a `SubgroupRow` per member. Empty state when `members.length === 0`. `TopBar` + `BottomNav` shell like `/ranking`.

**Step 4: `SubgroupRow.tsx`** — `"use client"`; renders name + a paid toggle button that calls the server action inside `useTransition`. Poster styling consistent with `RankingRow`.

**Step 5:** `pnpm typecheck && pnpm lint` — clean. **Step 6:** commit:
```
git add frontend/lib/api/payments.ts frontend/app/captain/ frontend/components/payments/
git commit -m "feat(frontend): captain payments screen"
```

---

## Task 6: Frontend admin payments

**Goal:** `/admin/payments` full ledger.

**Files:**
- Modify: `frontend/lib/api/payments.ts` — add `LedgerView` types + `getAdminLedger()` + `markSettled()`.
- Create: `frontend/app/admin/payments/page.tsx`
- Create: `frontend/app/admin/payments/actions.ts`
- Create: `frontend/components/payments/CaptainGroup.tsx` (client — settled toggle + nested invitee paid toggles reuse the Task-5 action)

**Step 1:** extend `lib/api/payments.ts`:

```ts
export type LedgerMember = {
  userId: number; displayName: string; role: string;
  paid: boolean; amountCents: number | null; note: string | null;
};
export type CaptainGroup = {
  captainId: number; captainName: string; captainPaid: boolean; captainSettled: boolean;
  expectedCents: number; collectedCents: number; members: LedgerMember[];
};
export type LedgerView = {
  potCents: number; paidCount: number; memberCount: number;
  captains: CaptainGroup[]; orphans: LedgerMember[];
};

export async function getAdminLedger(): Promise<LedgerView> {
  return api<LedgerView>("/api/admin/payments");
}
export async function markSettled(captainId: number, settled: boolean): Promise<void> {
  await api(`/api/admin/payments/${captainId}/settled`, {
    method: "PUT", body: JSON.stringify({ settled }),
  });
}
```

**Step 2: `actions.ts`** — server actions for `markSettled` + reuse `markPaid`, both `revalidatePath("/admin/payments")`.

**Step 3: `page.tsx`** — server component; guard non-admins by redirecting to
`/home` (mirror `app/admin/results/page.tsx`'s admin gate — read `me.role`
via `getMe()`, redirect if `me.role !== "ADMIN"` — UPPERCASE, see Task 7
casing note). Render: pot + `paidCount /
memberCount` chip; one `CaptainGroup` per captain (captain row with settled
toggle + expected/collected subtotal, nested invitee rows with paid toggles);
an orphans section.

**Step 4: `CaptainGroup.tsx`** — client; settled toggle (admin action) + nested invitee paid toggles.

**Step 5:** `pnpm typecheck && pnpm lint` — clean. **Step 6:** commit:
```
git add frontend/lib/api/payments.ts frontend/app/admin/payments/ frontend/components/payments/CaptainGroup.tsx
git commit -m "feat(frontend): admin payments ledger screen"
```

---

## Task 7: i18n + lobby entry-point link

**Goal:** Spanish/English copy + a contextual "Pagos" link in the lobby.

**Files:**
- Modify: `frontend/messages/es-CO.json` + `frontend/messages/en.json` — add a `payments` namespace (`title`, `paid`, `unpaid`, `settled`, `notSettled`, `collected`, `expected`, `subgroupEmpty`, `potChip`, `markPaid`, `markSettled`).
- Modify: `frontend/app/home/page.tsx` — add a "Pagos" link in the action row gated on role. **Note the casing**: `getMe()` returns `me.role` UPPERCASE (`"ADMIN" | "CAPTAIN" | "PLAYER"`) even though the DB stores lowercase — match the existing `InviteFriendsButton` which gates on `role === "PLAYER"`. So: `me.role === "ADMIN"` → link to `/admin/payments`; `me.role === "CAPTAIN"` → link to `/captain/payments`; `me.role === "PLAYER"` → render nothing.

**Step 1:** add keys to both message files (Spanish copy is the source of truth; English mirrors). **Step 2:** add the gated link. **Step 3:** `pnpm typecheck && pnpm lint`. **Step 4:** commit:
```
git add frontend/messages/ frontend/app/home/page.tsx
git commit -m "feat(frontend): payments i18n + lobby entry point"
```

---

## Task 8: Verify end-to-end + ship

**Goal:** Whole-suite green, plan checkboxes ticked, deployed.

**Step 1:** `cd backend && ./mvnw -B verify` — all ITs green (expect 57 prior + new payment/admin-payment ITs).
**Step 2:** `cd frontend && pnpm typecheck && pnpm lint` — clean (allow the 2 pre-existing warnings: layout custom-font + any coverage artifacts).
**Step 3:** Tick all acceptance + Progress checkboxes in this plan.
**Step 4:** Confirm `PublicSummaryController` is untouched (pot still member-count) — `git diff` shows no change to it.
**Step 5:** Commit the plan checkbox updates, push `master`, watch backend + frontend CI to green (the HikariCP pool cap from `f496d16` should keep the rolling deploy safe).
**Step 6:** Smoke prod: `curl -s -o /dev/null -w "%{http_code}" https://quiniela-api-ko2t5go6hq-uc.a.run.app/api/payments/my-subgroup` → expect 401 (unauth).

**Verification:**
- [x] Backend `./mvnw verify` green
- [x] Frontend typecheck + lint clean
- [x] `PublicSummaryController` unchanged (pot semantics preserved)
- [ ] Backend + frontend CI green on `master`
- [ ] `/api/payments/my-subgroup` returns 401 unauth in prod

---

## Out of scope (do not build)

Prize-split editor UI, CSV export, lobby "sin pagar" pill, captain self-settle.
Prize split stays SQL-editable for v1.

package io.quiniela.api.payment;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only payments ledger.
 *
 * <p>The ledger groups every pool member by captain (members whose {@code invited_by_user_id}
 * points at a captain in the pool). Non-captain members not invited by a captain are surfaced as
 * orphans (e.g. the admin, or players the admin invited directly).
 *
 * <p>Role check mirrors {@link io.quiniela.api.admin.AdminResultsService} — explicit local guard
 * rather than {@code @EnableMethodSecurity}.
 */
@Service
public class AdminPaymentService {

  private static final Long ACTIVE_POOL_ID = 1L;

  // ── record types ──────────────────────────────────────────────────────────

  public record LedgerMember(
      Long userId,
      String displayName,
      String role,
      boolean paid,
      Integer amountCents,
      String note) {}

  public record CaptainGroup(
      Long captainId,
      String captainName,
      boolean captainPaid,
      boolean captainSettled,
      long expectedCents,
      long collectedCents,
      List<LedgerMember> members) {}

  public record LedgerView(
      long potCents,
      long paidCount,
      long memberCount,
      List<CaptainGroup> captains,
      List<LedgerMember> orphans) {}

  public record SettledRequest(boolean settled) {}

  public record SettledRowView(Long captainId, boolean settled) {}

  // ── internal projection row ────────────────────────────────────────────────

  private record MemberRow(
      Long userId,
      String displayName,
      String role,
      Long invitedByUserId,
      boolean paid,
      Integer amountCents,
      String note,
      boolean settled) {}

  // ── dependencies ──────────────────────────────────────────────────────────

  private final UserRepository users;
  private final PaymentRepository payments;
  private final JdbcTemplate jdbc;

  public AdminPaymentService(UserRepository users, PaymentRepository payments, JdbcTemplate jdbc) {
    this.users = users;
    this.payments = payments;
    this.jdbc = jdbc;
  }

  // ── public API ────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public LedgerView getLedger(Long callerId) {
    requireAdmin(callerId);

    int entryFeeCents = entryFeeCents();

    List<MemberRow> rows =
        jdbc.query(
            """
            SELECT u.id, u.display_name, u.role, u.invited_by_user_id,
                   COALESCE(p.paid, false)    AS paid,
                   p.amount_cents,
                   p.note,
                   COALESCE(p.settled, false) AS settled
            FROM users u
            JOIN pool_membership pm ON pm.user_id = u.id AND pm.pool_id = ?
            LEFT JOIN payment p ON p.user_id = u.id AND p.pool_id = ?
            ORDER BY u.display_name ASC
            """,
            (rs, i) ->
                new MemberRow(
                    rs.getLong("id"),
                    rs.getString("display_name"),
                    rs.getString("role"),
                    rs.getObject("invited_by_user_id", Long.class),
                    rs.getBoolean("paid"),
                    rs.getObject("amount_cents", Integer.class),
                    rs.getString("note"),
                    rs.getBoolean("settled")),
            ACTIVE_POOL_ID,
            ACTIVE_POOL_ID);

    // Build the captain-id set (all pool members whose role = 'captain').
    Set<Long> captainIds =
        rows.stream()
            .filter(r -> "captain".equals(r.role()))
            .map(MemberRow::userId)
            .collect(Collectors.toSet());

    // Build captain group buckets keyed by captain user id.
    Map<Long, List<MemberRow>> captainMembers = new HashMap<>();
    for (Long cid : captainIds) {
      captainMembers.put(cid, new ArrayList<>());
    }

    Map<Long, MemberRow> captainRowById = new HashMap<>();
    List<MemberRow> orphans = new ArrayList<>();

    for (MemberRow row : rows) {
      if ("captain".equals(row.role())) {
        captainRowById.put(row.userId(), row);
        // captain itself goes in its own group header, not the members list
      } else {
        // non-captain: goes under their captain if inviter is in captain set
        if (row.invitedByUserId() != null && captainIds.contains(row.invitedByUserId())) {
          captainMembers.get(row.invitedByUserId()).add(row);
        } else {
          orphans.add(row);
        }
      }
    }

    // Assemble CaptainGroup list sorted by captain display name.
    List<CaptainGroup> captainGroups =
        captainIds.stream()
            .sorted(Comparator.comparing(cid -> captainRowById.get(cid).displayName()))
            .map(
                cid -> {
                  MemberRow capRow = captainRowById.get(cid);
                  List<LedgerMember> members =
                      captainMembers.get(cid).stream()
                          .sorted(Comparator.comparing(MemberRow::displayName))
                          .map(AdminPaymentService::toMember)
                          .toList();
                  long expected = (long) members.size() * entryFeeCents;
                  long collected =
                      members.stream()
                          .filter(LedgerMember::paid)
                          .mapToLong(m -> m.amountCents() != null ? m.amountCents() : entryFeeCents)
                          .sum();
                  return new CaptainGroup(
                      capRow.userId(),
                      capRow.displayName(),
                      capRow.paid(),
                      capRow.settled(),
                      expected,
                      collected,
                      members);
                })
            .toList();

    // Sort orphans by display name.
    List<LedgerMember> orphanMembers =
        orphans.stream()
            .sorted(Comparator.comparing(MemberRow::displayName))
            .map(AdminPaymentService::toMember)
            .toList();

    long memberCount = rows.size();
    long paidCount = rows.stream().filter(MemberRow::paid).count();
    long potCents = memberCount * entryFeeCents;

    return new LedgerView(potCents, paidCount, memberCount, captainGroups, orphanMembers);
  }

  @Transactional
  public SettledRowView markSettled(Long callerId, Long captainId, boolean settled) {
    requireAdmin(callerId);

    // 404 if the target user is not a pool member.
    boolean isMember =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM pool_membership WHERE pool_id = ? AND user_id = ?",
                Boolean.class,
                ACTIVE_POOL_ID,
                captainId));
    if (!isMember) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a pool member");
    }

    Payment payment =
        payments
            .findByPoolIdAndUserId(ACTIVE_POOL_ID, captainId)
            .orElseGet(() -> new Payment(ACTIVE_POOL_ID, captainId));
    payment.setSettled(settled, callerId);
    Payment saved = payments.save(payment);
    return new SettledRowView(captainId, saved.isSettled());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void requireAdmin(Long callerUserId) {
    User caller =
        users
            .findById(callerUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  private int entryFeeCents() {
    Integer fee =
        jdbc.queryForObject(
            "SELECT entry_fee_cents FROM pool WHERE id = ?", Integer.class, ACTIVE_POOL_ID);
    return fee != null ? fee : 0;
  }

  private static LedgerMember toMember(MemberRow row) {
    return new LedgerMember(
        row.userId(), row.displayName(), row.role(), row.paid(), row.amountCents(), row.note());
  }
}

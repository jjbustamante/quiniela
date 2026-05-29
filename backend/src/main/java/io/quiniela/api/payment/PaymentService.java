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
        payments
            .findByPoolIdAndUserId(ACTIVE_POOL_ID, callerId)
            .map(Payment::isSettled)
            .orElse(false);
    return new SubgroupView(expected, collected, ownSettled, members);
  }

  @Transactional
  public PaymentRowView markPaid(Long callerId, Long targetUserId, MarkPaidRequest req) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
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

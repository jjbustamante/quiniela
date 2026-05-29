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

  public Long getPoolId() {
    return poolId;
  }

  public Long getUserId() {
    return userId;
  }

  public boolean isPaid() {
    return paid;
  }

  public Integer getAmountCents() {
    return amountCents;
  }

  public String getNote() {
    return note;
  }

  public boolean isSettled() {
    return settled;
  }
}

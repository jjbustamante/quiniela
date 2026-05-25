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
  void onCreate() {
    this.joinedAt = Instant.now();
  }

  public Long getPoolId() {
    return poolId;
  }

  public Long getUserId() {
    return userId;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }
}

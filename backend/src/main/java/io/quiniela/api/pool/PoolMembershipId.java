package io.quiniela.api.pool;

import java.io.Serializable;
import java.util.Objects;

public class PoolMembershipId implements Serializable {
  private Long poolId;
  private Long userId;

  public PoolMembershipId() {}

  public PoolMembershipId(Long poolId, Long userId) {
    this.poolId = poolId;
    this.userId = userId;
  }

  public Long getPoolId() {
    return poolId;
  }

  public Long getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PoolMembershipId other)) return false;
    return Objects.equals(poolId, other.poolId) && Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(poolId, userId);
  }
}

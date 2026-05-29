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

  public Long getPoolId() {
    return poolId;
  }

  public Long getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PaymentId other)) return false;
    return Objects.equals(poolId, other.poolId) && Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(poolId, userId);
  }
}

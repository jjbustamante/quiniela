package io.quiniela.api.pool;

import java.io.Serializable;
import java.util.Objects;

public class PrizeSplitId implements Serializable {
  private Long poolId;
  private Integer rank;

  public PrizeSplitId() {}

  public PrizeSplitId(Long poolId, Integer rank) {
    this.poolId = poolId;
    this.rank = rank;
  }

  public Long getPoolId() {
    return poolId;
  }

  public Integer getRank() {
    return rank;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PrizeSplitId other)) return false;
    return Objects.equals(poolId, other.poolId) && Objects.equals(rank, other.rank);
  }

  @Override
  public int hashCode() {
    return Objects.hash(poolId, rank);
  }
}

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

  public Long getPoolId() {
    return poolId;
  }

  public Integer getRank() {
    return rank;
  }

  public Integer getPercentage() {
    return percentage;
  }

  public void setPercentage(Integer percentage) {
    this.percentage = percentage;
  }
}

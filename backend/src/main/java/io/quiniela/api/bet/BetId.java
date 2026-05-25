package io.quiniela.api.bet;

import java.io.Serializable;
import java.util.Objects;

public class BetId implements Serializable {
  private Long quinielaId;
  private Long matchId;

  public BetId() {}

  public BetId(Long quinielaId, Long matchId) {
    this.quinielaId = quinielaId;
    this.matchId = matchId;
  }

  public Long getQuinielaId() {
    return quinielaId;
  }

  public Long getMatchId() {
    return matchId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BetId other)) return false;
    return Objects.equals(quinielaId, other.quinielaId) && Objects.equals(matchId, other.matchId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(quinielaId, matchId);
  }
}

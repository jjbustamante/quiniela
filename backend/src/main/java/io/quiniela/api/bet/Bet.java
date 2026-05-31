package io.quiniela.api.bet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bet")
@IdClass(BetId.class)
public class Bet {

  @Id
  @Column(name = "quiniela_id")
  private Long quinielaId;

  @Id
  @Column(name = "match_id")
  private Long matchId;

  @Column(name = "score_t1", nullable = false)
  private Integer scoreT1;

  @Column(name = "score_t2", nullable = false)
  private Integer scoreT2;

  @Column(name = "predicted_winner_id")
  private Long predictedWinnerId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Bet() {}

  public Bet(Long quinielaId, Long matchId, Integer scoreT1, Integer scoreT2) {
    this.quinielaId = quinielaId;
    this.matchId = matchId;
    this.scoreT1 = scoreT1;
    this.scoreT2 = scoreT2;
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

  public Long getQuinielaId() {
    return quinielaId;
  }

  public Long getMatchId() {
    return matchId;
  }

  public Integer getScoreT1() {
    return scoreT1;
  }

  public void setScoreT1(Integer scoreT1) {
    this.scoreT1 = scoreT1;
  }

  public Integer getScoreT2() {
    return scoreT2;
  }

  public void setScoreT2(Integer scoreT2) {
    this.scoreT2 = scoreT2;
  }

  public Long getPredictedWinnerId() {
    return predictedWinnerId;
  }

  public void setPredictedWinnerId(Long predictedWinnerId) {
    this.predictedWinnerId = predictedWinnerId;
  }
}

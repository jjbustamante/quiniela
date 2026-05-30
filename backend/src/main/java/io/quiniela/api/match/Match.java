package io.quiniela.api.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "match")
public class Match {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(name = "round_id", nullable = false)
  private Long roundId;

  @Column(name = "group_code", length = 1)
  private String groupCode;

  @Column(name = "team_1_id")
  private Long team1Id;

  @Column(name = "team_2_id")
  private Long team2Id;

  @Column(name = "score_t1")
  private Integer scoreT1;

  @Column(name = "score_t2")
  private Integer scoreT2;

  @Column(name = "winner_id")
  private Long winnerId;

  @Column(nullable = false)
  private Boolean played;

  @Column(name = "kickoff_at", nullable = false)
  private Instant kickoffAt;

  @Column(name = "match_parent_1_id")
  private Long matchParent1Id;

  @Column(name = "match_parent_2_id")
  private Long matchParent2Id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Match() {}

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.played == null) this.played = false;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getTournamentId() {
    return tournamentId;
  }

  public Long getRoundId() {
    return roundId;
  }

  public String getGroupCode() {
    return groupCode;
  }

  public Long getTeam1Id() {
    return team1Id;
  }

  public void setTeam1Id(Long team1Id) {
    this.team1Id = team1Id;
  }

  public Long getTeam2Id() {
    return team2Id;
  }

  public void setTeam2Id(Long team2Id) {
    this.team2Id = team2Id;
  }

  public Integer getScoreT1() {
    return scoreT1;
  }

  public void setScoreT1(Integer s) {
    this.scoreT1 = s;
  }

  public Integer getScoreT2() {
    return scoreT2;
  }

  public void setScoreT2(Integer s) {
    this.scoreT2 = s;
  }

  public Long getWinnerId() {
    return winnerId;
  }

  public void setWinnerId(Long winnerId) {
    this.winnerId = winnerId;
  }

  public Boolean getPlayed() {
    return played;
  }

  public void setPlayed(Boolean played) {
    this.played = played;
  }

  public Instant getKickoffAt() {
    return kickoffAt;
  }

  public Long getMatchParent1Id() {
    return matchParent1Id;
  }

  public void setMatchParent1Id(Long matchParent1Id) {
    this.matchParent1Id = matchParent1Id;
  }

  public Long getMatchParent2Id() {
    return matchParent2Id;
  }

  public void setMatchParent2Id(Long matchParent2Id) {
    this.matchParent2Id = matchParent2Id;
  }
}

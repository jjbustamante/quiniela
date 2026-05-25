package io.quiniela.api.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "round")
public class Round {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(nullable = false, length = 16)
  private String code;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false)
  private Integer sequence;

  protected Round() {}

  public Long getId() {
    return id;
  }

  public Long getTournamentId() {
    return tournamentId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public Integer getSequence() {
    return sequence;
  }
}

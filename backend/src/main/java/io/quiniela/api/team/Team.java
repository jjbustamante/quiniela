package io.quiniela.api.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "team")
public class Team {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(nullable = false, length = 8)
  private String code;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(name = "group_code", length = 1)
  private String groupCode;

  @Column(name = "flag_emoji", length = 8)
  private String flagEmoji;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Team() {}

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

  public String getGroupCode() {
    return groupCode;
  }

  public String getFlagEmoji() {
    return flagEmoji;
  }
}

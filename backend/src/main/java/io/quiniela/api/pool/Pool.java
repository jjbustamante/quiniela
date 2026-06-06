package io.quiniela.api.pool;

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
@Table(name = "pool")
public class Pool {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "entry_fee_cents", nullable = false)
  private Integer entryFeeCents;

  @Column(name = "house_cut_percentage", nullable = false)
  private Integer houseCutPercentage;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Pool() {}

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

  public Long getId() {
    return id;
  }

  public Long getTournamentId() {
    return tournamentId;
  }

  public String getName() {
    return name;
  }

  public String getCurrency() {
    return currency;
  }

  public Integer getEntryFeeCents() {
    return entryFeeCents;
  }

  public void setEntryFeeCents(Integer entryFeeCents) {
    this.entryFeeCents = entryFeeCents;
  }

  public Integer getHouseCutPercentage() {
    return houseCutPercentage;
  }

  public void setHouseCutPercentage(Integer houseCutPercentage) {
    this.houseCutPercentage = houseCutPercentage;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public void setLockedAt(Instant lockedAt) {
    this.lockedAt = lockedAt;
  }
}

package io.quiniela.api.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tournament")
public class Tournament {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false)
  private String name;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(nullable = false)
  private String status;

  @Column(name = "host_country_codes")
  private String hostCountryCodes;

  @Column(name = "opening_venue")
  private String openingVenue;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Tournament() {}

  public Long getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public String getStatus() {
    return status;
  }

  public String getHostCountryCodes() {
    return hostCountryCodes;
  }

  public String getOpeningVenue() {
    return openingVenue;
  }
}

package io.quiniela.api.paul;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "paul_prediction")
public class PaulPrediction {

  public static final String KIND_CANDIDATE = "CANDIDATE";
  public static final String KIND_OFFICIAL = "OFFICIAL";
  public static final String SOURCE_AI = "AI";
  public static final String SOURCE_FALLBACK = "FALLBACK";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "match_id", nullable = false)
  private Long matchId;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(nullable = false, length = 64)
  private String model;

  @Column(nullable = false, length = 16)
  private String kind;

  @Column(name = "score_t1", nullable = false)
  private Integer scoreT1;

  @Column(name = "score_t2", nullable = false)
  private Integer scoreT2;

  @Column private BigDecimal confidence;

  @Column private String reasoning;

  @Column(name = "reasoning_lang", nullable = false, length = 8)
  private String reasoningLang = "es";

  @Column(nullable = false, length = 16)
  private String source = SOURCE_AI;

  @Column(name = "generated_at", nullable = false, updatable = false)
  private Instant generatedAt;

  protected PaulPrediction() {}

  public PaulPrediction(
      Long matchId,
      String provider,
      String model,
      String kind,
      Integer scoreT1,
      Integer scoreT2,
      BigDecimal confidence,
      String reasoning,
      String reasoningLang,
      String source) {
    this.matchId = matchId;
    this.provider = provider;
    this.model = model;
    this.kind = kind;
    this.scoreT1 = scoreT1;
    this.scoreT2 = scoreT2;
    this.confidence = confidence;
    this.reasoning = reasoning;
    this.reasoningLang = reasoningLang;
    this.source = source;
  }

  @PrePersist
  void onCreate() {
    this.generatedAt = Instant.now();
    if (this.reasoningLang == null) this.reasoningLang = "es";
    if (this.source == null) this.source = SOURCE_AI;
  }

  public Long getId() {
    return id;
  }

  public Long getMatchId() {
    return matchId;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public String getKind() {
    return kind;
  }

  public Integer getScoreT1() {
    return scoreT1;
  }

  public Integer getScoreT2() {
    return scoreT2;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public String getReasoning() {
    return reasoning;
  }

  public String getReasoningLang() {
    return reasoningLang;
  }

  public String getSource() {
    return source;
  }
}

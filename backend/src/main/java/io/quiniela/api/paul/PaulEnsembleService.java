package io.quiniela.api.paul;

import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaulEnsembleService {

  private static final Long TOURNAMENT_ID = 1L;
  static final String ENSEMBLE_MODEL_LABEL = "ensemble";

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final PaulPredictionRepository predictions;
  private final PaulOracle oracle;
  private final PaulProperties props;
  private final TransactionTemplate tx;

  public PaulEnsembleService(
      MatchRepository matches,
      RoundRepository rounds,
      PaulPredictionRepository predictions,
      PaulOracle oracle,
      PaulProperties props,
      PlatformTransactionManager txManager) {
    this.matches = matches;
    this.rounds = rounds;
    this.predictions = predictions;
    this.oracle = oracle;
    this.props = props;
    this.tx = new TransactionTemplate(txManager);
  }

  /** For each OPEN match with candidates, synthesize one OFFICIAL pick via the ensemble judge. */
  public int synthesizeOpen() {
    return synthesizeOpen(PaulProgress.NOOP);
  }

  public int synthesizeOpen(PaulProgress progress) {
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, Instant.now());
    progress.start(open.size());
    int created = 0;
    for (Match m : open) {
      Boolean did = tx.execute(s -> synthesizeForMatch(m.getId()));
      if (Boolean.TRUE.equals(did)) created++;
      progress.tick();
    }
    return created;
  }

  boolean synthesizeForMatch(Long matchId) {
    List<PaulPrediction> candidates =
        predictions.findByMatchIdAndKind(matchId, PaulPrediction.KIND_CANDIDATE);
    if (candidates.isEmpty()) return false;

    Match m = matches.findById(matchId).orElseThrow();
    Round r = rounds.findById(m.getRoundId()).orElseThrow();
    boolean knockout = !"GROUP".equals(r.getCode());

    PaulProperties.ModelSpec es = props.ensembleSpec();
    PaulPrediction official;
    try {
      PaulPredictionResult res =
          oracle.predict(
              systemPrompt(), candidatePrompt(candidates, m, knockout), es.provider(), es.model());
      int s1 = Math.max(0, res.scoreT1());
      int s2 = Math.max(0, res.scoreT2());
      Long pwid = (knockout && s1 == s2) ? officialAdvancing(res.advancing(), m, candidates) : null;
      official =
          official(
              matchId,
              s1,
              s2,
              clamp(res.confidence()),
              res.reasoning(),
              PaulPrediction.SOURCE_AI,
              pwid);
    } catch (RuntimeException e) {
      PaulPrediction pick = candidates.get(0);
      Long pwid =
          (knockout && pick.getScoreT1().equals(pick.getScoreT2()))
              ? pick.getPredictedWinnerId()
              : null;
      official =
          official(
              matchId,
              pick.getScoreT1(),
              pick.getScoreT2(),
              null,
              "Paul consultó a sus otros yo y se quedó con su instinto.",
              PaulPrediction.SOURCE_FALLBACK,
              pwid);
    }

    // Replace any existing official for this match. flush() only when a delete
    // happened, so the insert doesn't collide with the old row on the UNIQUE
    // (match_id, model, kind) constraint within this transaction.
    predictions
        .findByMatchIdAndModelAndKind(matchId, ENSEMBLE_MODEL_LABEL, PaulPrediction.KIND_OFFICIAL)
        .ifPresent(
            existing -> {
              predictions.delete(existing);
              predictions.flush();
            });
    predictions.save(official);
    return true;
  }

  /**
   * Resolve the official advancing team for a knockout draw: the judge's pick when valid, else the
   * first candidate's stored winner, else team 1.
   */
  private Long officialAdvancing(String advancing, Match m, List<PaulPrediction> candidates) {
    if ("LOCAL".equalsIgnoreCase(advancing)) return m.getTeam1Id();
    if ("VISITANTE".equalsIgnoreCase(advancing)) return m.getTeam2Id();
    for (PaulPrediction c : candidates) {
      if (c.getPredictedWinnerId() != null) return c.getPredictedWinnerId();
    }
    return m.getTeam1Id();
  }

  private PaulPrediction official(
      Long matchId, int s1, int s2, BigDecimal conf, String reasoning, String source, Long pwid) {
    return new PaulPrediction(
        "paul",
        matchId,
        props.ensembleSpec().provider(),
        ENSEMBLE_MODEL_LABEL,
        PaulPrediction.KIND_OFFICIAL,
        s1,
        s2,
        conf,
        reasoning,
        "es",
        source,
        pwid);
  }

  private String systemPrompt() {
    return """
        Eres "Pulpo Paul". Te doy varias predicciones que TÚ MISMO hiciste con
        distintos modelos para un partido. Decide tu marcador OFICIAL final, que es
        el que jugarás como competidor. Responde SOLO con el esquema estructurado.
        "reasoning" en español, divertido y de oráculo, 1-2 frases.
        """;
  }

  private String candidatePrompt(List<PaulPrediction> candidates, Match m, boolean knockout) {
    StringBuilder sb = new StringBuilder("Mis predicciones previas:\n");
    for (PaulPrediction c : candidates) {
      sb.append("- ")
          .append(c.getModel())
          .append(": ")
          .append(c.getScoreT1())
          .append('-')
          .append(c.getScoreT2());
      if (knockout && c.getPredictedWinnerId() != null) {
        sb.append(" (avanza ")
            .append(c.getPredictedWinnerId().equals(m.getTeam1Id()) ? "LOCAL" : "VISITANTE")
            .append(')');
      }
      if (c.getReasoning() != null) sb.append(" (").append(c.getReasoning()).append(')');
      sb.append('\n');
    }
    sb.append(
        knockout
            ? "Da tu marcador oficial. Si es empate, indica \"advancing\" (LOCAL/VISITANTE)."
            : "Da tu marcador oficial.");
    return sb.toString();
  }

  private static BigDecimal clamp(double c) {
    double v = Math.max(0.0, Math.min(1.0, c));
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
  }
}

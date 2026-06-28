package io.quiniela.api.paul;

import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.team.Team;
import io.quiniela.api.team.TeamRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaulPredictionService {

  private static final Long TOURNAMENT_ID = 1L;

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final TeamRepository teams;
  private final PaulPredictionRepository predictions;
  private final PaulOracle oracle;
  private final MatchContextBuilder context;
  private final PaulProperties props;
  private final TransactionTemplate tx;

  public PaulPredictionService(
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams,
      PaulPredictionRepository predictions,
      PaulOracle oracle,
      MatchContextBuilder context,
      PaulProperties props,
      PlatformTransactionManager txManager) {
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
    this.predictions = predictions;
    this.oracle = oracle;
    this.context = context;
    this.props = props;
    this.tx = new TransactionTemplate(txManager);
  }

  /** Regenerate CANDIDATE predictions for every OPEN match (teams set, future kickoff) × model. */
  public int generateOpen() {
    return generateOpen(PaulProgress.NOOP);
  }

  /**
   * Per-item-transactional variant: each (match, model) candidate is written in its own short
   * transaction so a multi-minute batch never holds one connection open (and partial progress
   * survives a crash). Reports against {@code progress} for live job tracking. Covers the group
   * stage (pre-kickoff) and each knockout round as its matches resolve.
   */
  public int generateOpen(PaulProgress progress) {
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, Instant.now());
    Map<Long, Round> roundById = new HashMap<>();
    rounds
        .findByTournamentIdOrderBySequenceAsc(TOURNAMENT_ID)
        .forEach(r -> roundById.put(r.getId(), r));

    List<PaulProperties.ModelSpec> roster = props.roster();
    progress.start(open.size() * roster.size());
    int created = 0;
    for (Match m : open) {
      Round r = roundById.get(m.getRoundId());
      for (PaulProperties.ModelSpec spec : roster) {
        tx.executeWithoutResult(s -> upsertCandidate(m, r, spec));
        created++;
        progress.tick();
      }
    }
    return created;
  }

  private void upsertCandidate(Match m, Round r, PaulProperties.ModelSpec spec) {
    String model = spec.model();
    Team t1 = m.getTeam1Id() == null ? null : teams.findById(m.getTeam1Id()).orElse(null);
    Team t2 = m.getTeam2Id() == null ? null : teams.findById(m.getTeam2Id()).orElse(null);
    boolean knockout = !"GROUP".equals(r.getCode());

    PaulPrediction p;
    try {
      String userPrompt =
          context.userPrompt(
              r.getCode(),
              m.getGroupCode(),
              name(t1),
              code(t1),
              ranking(t1),
              name(t2),
              code(t2),
              ranking(t2));
      PaulPredictionResult res =
          oracle.predict(context.systemPrompt(), userPrompt, spec.provider(), model);
      int s1 = Math.max(0, res.scoreT1());
      int s2 = Math.max(0, res.scoreT2());
      Long pwid = (knockout && s1 == s2) ? advancingTeamId(res.advancing(), m, t1, t2) : null;
      p =
          new PaulPrediction(
              "paul",
              m.getId(),
              spec.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              s1,
              s2,
              clampConfidence(res.confidence()),
              res.reasoning(),
              "es",
              PaulPrediction.SOURCE_AI,
              pwid);
    } catch (RuntimeException e) {
      int[] s = deterministicStub(m.getId());
      Long pwid = (knockout && s[0] == s[1]) ? advancingTeamId(null, m, t1, t2) : null;
      p =
          new PaulPrediction(
              "paul",
              m.getId(),
              spec.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              s[0],
              s[1],
              null,
              "Paul prefirió no arriesgar esta vez.",
              "es",
              PaulPrediction.SOURCE_FALLBACK,
              pwid);
    }

    predictions
        .findByMatchIdAndModelAndKind(m.getId(), model, PaulPrediction.KIND_CANDIDATE)
        .ifPresent(
            existing -> {
              predictions.delete(existing);
              predictions.flush();
            });
    predictions.save(p);
  }

  /**
   * Map the model's advancing pick to a team id, falling back to the higher-ranked team (lower FIFA
   * number) and finally to team 1 — so a knockout draw prediction always names an advancing team.
   */
  static Long advancingTeamId(String advancing, Match m, Team t1, Team t2) {
    if ("LOCAL".equalsIgnoreCase(advancing)) return m.getTeam1Id();
    if ("VISITANTE".equalsIgnoreCase(advancing)) return m.getTeam2Id();
    Integer r1 = t1 == null ? null : t1.getFifaRanking();
    Integer r2 = t2 == null ? null : t2.getFifaRanking();
    if (r1 != null && r2 != null && !r1.equals(r2)) {
      return r1 < r2 ? m.getTeam1Id() : m.getTeam2Id();
    }
    return m.getTeam1Id();
  }

  /** Deterministic stub (same formula the original dumb Paul used). */
  static int[] deterministicStub(Long matchId) {
    long seed = matchId * 17L + 11L;
    return new int[] {(int) (seed % 4), (int) ((seed / 5) % 3)};
  }

  private static BigDecimal clampConfidence(double c) {
    double v = Math.max(0.0, Math.min(1.0, c));
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
  }

  private static String name(Team t) {
    return t == null ? "Por definir" : t.getName();
  }

  private static String code(Team t) {
    return t == null ? "TBD" : t.getCode();
  }

  private static Integer ranking(Team t) {
    return t == null ? null : t.getFifaRanking();
  }
}

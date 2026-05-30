package io.quiniela.api.paul;

import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.team.Team;
import io.quiniela.api.team.TeamRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  public PaulPredictionService(
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams,
      PaulPredictionRepository predictions,
      PaulOracle oracle,
      MatchContextBuilder context,
      PaulProperties props) {
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
    this.predictions = predictions;
    this.oracle = oracle;
    this.context = context;
    this.props = props;
  }

  /** Regenerate CANDIDATE predictions for every group match × every configured model. */
  @Transactional
  public int generateAllGroup() {
    Long groupRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "GROUP").orElseThrow().getId();
    List<Match> groupMatches =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, groupRoundId);

    int created = 0;
    for (Match m : groupMatches) {
      for (String model : props.models()) {
        upsertCandidate(m, model);
        created++;
      }
    }
    return created;
  }

  private void upsertCandidate(Match m, String model) {
    Team t1 = m.getTeam1Id() == null ? null : teams.findById(m.getTeam1Id()).orElse(null);
    Team t2 = m.getTeam2Id() == null ? null : teams.findById(m.getTeam2Id()).orElse(null);

    PaulPrediction p;
    try {
      String userPrompt =
          context.userPrompt(
              "GROUP",
              m.getGroupCode(),
              name(t1),
              code(t1),
              ranking(t1),
              name(t2),
              code(t2),
              ranking(t2));
      PaulPredictionResult r = oracle.predict(context.systemPrompt(), userPrompt, model);
      p =
          new PaulPrediction(
              m.getId(),
              props.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              Math.max(0, r.scoreT1()),
              Math.max(0, r.scoreT2()),
              clampConfidence(r.confidence()),
              r.reasoning(),
              "es",
              PaulPrediction.SOURCE_AI);
    } catch (RuntimeException e) {
      int[] s = deterministicStub(m.getId());
      p =
          new PaulPrediction(
              m.getId(),
              props.provider(),
              model,
              PaulPrediction.KIND_CANDIDATE,
              s[0],
              s[1],
              null,
              "Paul prefirió no arriesgar esta vez.",
              "es",
              PaulPrediction.SOURCE_FALLBACK);
    }

    // Replace any existing candidate for (match, model, CANDIDATE) to keep regeneration idempotent.
    predictions
        .findByMatchIdAndModelAndKind(m.getId(), model, PaulPrediction.KIND_CANDIDATE)
        .ifPresent(
            existing -> {
              predictions.delete(existing);
              predictions.flush();
            });
    predictions.save(p);
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

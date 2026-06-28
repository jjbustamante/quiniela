package io.quiniela.api.paul;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.bracket.BracketService;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaulService {

  private static final Long DEFAULT_POOL_ID = 1L;
  private static final Long TOURNAMENT_ID = 1L;

  private final MatchRepository matches;
  private final QuinielaRepository quinielas;
  private final BetRepository bets;
  private final PaulPredictionRepository predictions;
  private final UserRepository users;
  private final BracketService bracket;
  private final PaulProperties props;

  public PaulService(
      MatchRepository matches,
      QuinielaRepository quinielas,
      BetRepository bets,
      PaulPredictionRepository predictions,
      UserRepository users,
      BracketService bracket,
      PaulProperties props) {
    this.matches = matches;
    this.quinielas = quinielas;
    this.bets = bets;
    this.predictions = predictions;
    this.users = users;
    this.bracket = bracket;
    this.props = props;
  }

  public record Suggestion(
      Integer scoreT1, Integer scoreT2, String reasoning, Long predictedWinnerId) {}

  public record FillResult(int created) {}

  /**
   * Returns a random cached CANDIDATE prediction for the match (Paul "changes his mind"). Falls
   * back to the deterministic stub when Paul hasn't analyzed this match yet.
   */
  public Suggestion suggestForMatch(Long matchId) {
    matches.findById(matchId).orElseThrow();
    List<PaulPrediction> candidates =
        predictions.findByOracleAndMatchIdAndKind("paul", matchId, PaulPrediction.KIND_CANDIDATE);
    if (!candidates.isEmpty()) {
      PaulPrediction pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
      return new Suggestion(
          pick.getScoreT1(), pick.getScoreT2(), pick.getReasoning(), pick.getPredictedWinnerId());
    }
    return stub(matchId);
  }

  private Suggestion stub(Long matchId) {
    long seed = matchId * 17L + 11L;
    int t1 = (int) (seed % 4);
    int t2 = (int) ((seed / 5) % 3);
    return new Suggestion(
        t1, t2, "Paul cree que es un partido " + (t1 + t2 > 2 ? "abierto" : "cerrado") + ".", null);
  }

  @Transactional
  public FillResult fillAllForUser(Long userId) {
    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, userId)
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, userId)));

    List<Bet> existing = bets.findByQuinielaId(q.getId());
    Set<Long> alreadyBet = new HashSet<>();
    existing.forEach(b -> alreadyBet.add(b.getMatchId()));

    // Fill every OPEN match (teams set, kickoff in the future) the user has not bet:
    // group matches pre-kickoff and the currently-open knockout round. Persisting goes
    // through bracket.saveBet so the deadline/kickoff lock and draw-only winner rule are
    // enforced in exactly one place.
    List<Match> open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                TOURNAMENT_ID, java.time.Instant.now());

    int created = 0;
    for (Match m : open) {
      if (alreadyBet.contains(m.getId())) continue;
      Suggestion s = suggestForMatch(m.getId());
      bracket.saveBet(
          userId,
          new BracketService.SaveBetRequest(
              m.getId(), s.scoreT1(), s.scoreT2(), s.predictedWinnerId()));
      created++;
    }
    return new FillResult(created);
  }

  public record RevealResult(int betsCreated) {}

  /**
   * Each oracle "decides to play": create its quiniela (idempotent) and snapshot every OFFICIAL
   * prediction for that oracle as one of its bets. Skips matches already bet, so repeated calls are
   * no-ops.
   *
   * <p>Scope assumption (v1): only group-stage matches have OFFICIAL predictions, so this snapshots
   * the group bracket. If knockout OFFICIAL predictions are added later, this would also snapshot
   * those — gate by stage here before generating knockout officials so oracles never bet on
   * still-TBD knockout fixtures.
   */
  @Transactional
  public RevealResult reveal() {
    int created = 0;
    for (Oracle bot : props.allOracles()) {
      User u =
          users
              .findByGoogleSub(bot.googleSub())
              .orElseThrow(
                  () -> new IllegalStateException("Bot user not seeded: " + bot.googleSub()));
      Quiniela q =
          quinielas
              .findByPoolIdAndUserId(DEFAULT_POOL_ID, u.getId())
              .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, u.getId())));

      Set<Long> already = new HashSet<>();
      bets.findByQuinielaId(q.getId()).forEach(b -> already.add(b.getMatchId()));

      for (PaulPrediction official :
          predictions.findByOracleAndKind(bot.key(), PaulPrediction.KIND_OFFICIAL)) {
        if (already.contains(official.getMatchId())) continue;
        Bet bet =
            new Bet(q.getId(), official.getMatchId(), official.getScoreT1(), official.getScoreT2());
        bet.setPredictedWinnerId(official.getPredictedWinnerId());
        bets.save(bet);
        created++;
      }
    }
    return new RevealResult(created);
  }
}

package io.quiniela.api.paul;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.bracket.BracketService;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.RoundRepository;
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

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final QuinielaRepository quinielas;
  private final BetRepository bets;
  private final PaulPredictionRepository predictions;
  private final UserRepository users;
  private final BracketService bracket;

  public PaulService(
      MatchRepository matches,
      RoundRepository rounds,
      QuinielaRepository quinielas,
      BetRepository bets,
      PaulPredictionRepository predictions,
      UserRepository users,
      BracketService bracket) {
    this.matches = matches;
    this.rounds = rounds;
    this.quinielas = quinielas;
    this.bets = bets;
    this.predictions = predictions;
    this.users = users;
    this.bracket = bracket;
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
        predictions.findByMatchIdAndKind(matchId, PaulPrediction.KIND_CANDIDATE);
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

    // For v1, only fill group-stage matches. Knockout matches don't have
    // resolved teams yet — Paul can't reason about "Group A winner vs Group B
    // runner-up" placeholders. v1.1 fills knockouts after group stage closes.
    Long groupRoundId = rounds.findByTournamentIdAndCode(1L, "GROUP").orElseThrow().getId();
    List<Match> ms = matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(1L, groupRoundId);

    int created = 0;
    for (Match m : ms) {
      if (alreadyBet.contains(m.getId())) continue;
      // Paul's only job is to predict a score. Persisting it goes through the
      // shared bracket save path so the betting-deadline lock (and every other
      // validation) is enforced in exactly one place — no parallel logic here.
      Suggestion s = suggestForMatch(m.getId());
      bracket.saveBet(
          userId, new BracketService.SaveBetRequest(m.getId(), s.scoreT1(), s.scoreT2(), null));
      created++;
    }
    return new FillResult(created);
  }

  public record RevealResult(int betsCreated) {}

  /**
   * Paul "decides to play": create his quiniela (idempotent) and snapshot every OFFICIAL prediction
   * as one of his bets. Skips matches he already has a bet for, so repeated calls are no-ops.
   *
   * <p>Scope assumption (v1): only group-stage matches have OFFICIAL predictions, so this snapshots
   * the group bracket. If knockout OFFICIAL predictions are added later, this would also snapshot
   * those — gate by stage here before generating knockout officials so Paul never bets on still-TBD
   * knockout fixtures.
   */
  @Transactional
  public RevealResult reveal() {
    User paul =
        users
            .findByGoogleSub("paul-bot-oracle")
            .orElseThrow(() -> new IllegalStateException("Paul bot user not seeded"));
    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, paul.getId())
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, paul.getId())));

    Set<Long> already = new HashSet<>();
    bets.findByQuinielaId(q.getId()).forEach(b -> already.add(b.getMatchId()));

    int created = 0;
    for (PaulPrediction official : predictions.findByKind(PaulPrediction.KIND_OFFICIAL)) {
      if (already.contains(official.getMatchId())) continue;
      bets.save(
          new Bet(q.getId(), official.getMatchId(), official.getScoreT1(), official.getScoreT2()));
      created++;
    }
    return new RevealResult(created);
  }
}

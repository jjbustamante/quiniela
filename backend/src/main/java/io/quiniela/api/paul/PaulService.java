package io.quiniela.api.paul;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaulService {

  private static final Long DEFAULT_POOL_ID = 1L;

  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final QuinielaRepository quinielas;
  private final BetRepository bets;

  public PaulService(
      MatchRepository matches,
      RoundRepository rounds,
      QuinielaRepository quinielas,
      BetRepository bets) {
    this.matches = matches;
    this.rounds = rounds;
    this.quinielas = quinielas;
    this.bets = bets;
  }

  public record Suggestion(Integer scoreT1, Integer scoreT2, String reasoning) {}

  public record FillResult(int created) {}

  /**
   * Deterministic stub: scores derive from match id so the same match always gets the same
   * prediction (helps reproducible debugging). Distribution skews toward low-scoring realistic
   * results: 0-0 .. 3-2.
   */
  public Suggestion suggestForMatch(Long matchId) {
    matches.findById(matchId).orElseThrow();
    long seed = matchId * 17L + 11L;
    int t1 = (int) (seed % 4);
    int t2 = (int) ((seed / 5) % 3);
    return new Suggestion(
        t1, t2, "Paul cree que es un partido " + (t1 + t2 > 2 ? "abierto" : "cerrado") + ".");
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
      Suggestion s = suggestForMatch(m.getId());
      bets.save(new Bet(q.getId(), m.getId(), s.scoreT1(), s.scoreT2()));
      created++;
    }
    return new FillResult(created);
  }
}

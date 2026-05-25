package io.quiniela.api.bracket;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.team.Team;
import io.quiniela.api.team.TeamRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BracketService {

  private static final Long DEFAULT_POOL_ID = 1L;
  private static final Long DEFAULT_TOURNAMENT_ID = 1L;

  private final QuinielaRepository quinielas;
  private final BetRepository bets;
  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final TeamRepository teams;

  public BracketService(
      QuinielaRepository quinielas,
      BetRepository bets,
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams) {
    this.quinielas = quinielas;
    this.bets = bets;
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
  }

  public record MatchView(
      Long id,
      String team1Code,
      String team1Name,
      String team1Flag,
      String team2Code,
      String team2Name,
      String team2Flag,
      String kickoffAt,
      Integer betScoreT1,
      Integer betScoreT2,
      Integer actualScoreT1,
      Integer actualScoreT2,
      boolean played) {}

  public record GroupView(String code, int filled, int total, List<MatchView> matches) {}

  public record KnockoutRoundView(
      String code, String name, int filled, int total, boolean unlocked, List<MatchView> matches) {}

  public record BracketView(
      Long quinielaId,
      int totalMatches,
      int totalBets,
      List<GroupView> groups,
      List<KnockoutRoundView> knockouts) {}

  @Transactional
  public BracketView getMyBracket(Long userId) {
    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, userId)
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, userId)));

    List<Bet> myBets = bets.findByQuinielaId(q.getId());
    Map<Long, Bet> betByMatch = myBets.stream().collect(Collectors.toMap(Bet::getMatchId, b -> b));

    Map<Long, Team> teamById = new HashMap<>();
    teams.findAll().forEach(t -> teamById.put(t.getId(), t));

    List<GroupView> groups = new ArrayList<>();
    for (String code : List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L")) {
      List<Match> ms =
          matches.findByTournamentIdAndGroupCodeOrderByKickoffAtAsc(DEFAULT_TOURNAMENT_ID, code);
      List<MatchView> mvs = ms.stream().map(m -> toView(m, teamById, betByMatch)).toList();
      int filled = (int) mvs.stream().filter(v -> v.betScoreT1() != null).count();
      groups.add(new GroupView(code, filled, ms.size(), mvs));
    }

    List<KnockoutRoundView> ko = new ArrayList<>();
    boolean unlocked = false; // toggled true by the lock logic once group stage closes.
    for (Round r : rounds.findAll()) {
      if ("GROUP".equals(r.getCode())) continue;
      List<Match> ms =
          matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(DEFAULT_TOURNAMENT_ID, r.getId());
      List<MatchView> mvs = ms.stream().map(m -> toView(m, teamById, betByMatch)).toList();
      int filled = (int) mvs.stream().filter(v -> v.betScoreT1() != null).count();
      ko.add(new KnockoutRoundView(r.getCode(), r.getName(), filled, ms.size(), unlocked, mvs));
    }

    int totalMatches = (int) matches.count();
    return new BracketView(q.getId(), totalMatches, myBets.size(), groups, ko);
  }

  private MatchView toView(Match m, Map<Long, Team> teamById, Map<Long, Bet> betByMatch) {
    Team t1 = m.getTeam1Id() == null ? null : teamById.get(m.getTeam1Id());
    Team t2 = m.getTeam2Id() == null ? null : teamById.get(m.getTeam2Id());
    Bet b = betByMatch.get(m.getId());
    return new MatchView(
        m.getId(),
        t1 == null ? null : t1.getCode(),
        t1 == null ? null : t1.getName(),
        t1 == null ? null : t1.getFlagEmoji(),
        t2 == null ? null : t2.getCode(),
        t2 == null ? null : t2.getName(),
        t2 == null ? null : t2.getFlagEmoji(),
        m.getKickoffAt().toString(),
        b == null ? null : b.getScoreT1(),
        b == null ? null : b.getScoreT2(),
        m.getScoreT1(),
        m.getScoreT2(),
        Boolean.TRUE.equals(m.getPlayed()));
  }
}

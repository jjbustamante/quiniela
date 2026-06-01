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
  private final LockClock lockClock;

  public BracketService(
      QuinielaRepository quinielas,
      BetRepository bets,
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams,
      LockClock lockClock) {
    this.quinielas = quinielas;
    this.bets = bets;
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
    this.lockClock = lockClock;
  }

  public record MatchView(
      Long id,
      Long team1Id,
      String team1Code,
      String team1Name,
      String team1Flag,
      Long team2Id,
      String team2Code,
      String team2Name,
      String team2Flag,
      String kickoffAt,
      Integer betScoreT1,
      Integer betScoreT2,
      Long betPredictedWinnerId,
      Integer actualScoreT1,
      Integer actualScoreT2,
      boolean played) {}

  public record GroupView(
      String code, int filled, int total, boolean locked, List<MatchView> matches) {}

  public record KnockoutRoundView(
      String code,
      String name,
      int filled,
      int total,
      boolean unlocked,
      boolean locked,
      List<MatchView> matches) {}

  public record BracketView(
      Long quinielaId,
      int totalMatches,
      int totalBets,
      String groupStageDeadline,
      String knockoutDeadline,
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

    var deadlines = lockClock.fetchTournamentDeadlines(DEFAULT_TOURNAMENT_ID);
    java.time.Instant now = java.time.Instant.now();
    boolean groupLocked =
        deadlines.groupStageDeadline() != null && now.isAfter(deadlines.groupStageDeadline());
    boolean knockoutLocked =
        deadlines.knockoutDeadline() != null && now.isAfter(deadlines.knockoutDeadline());

    List<GroupView> groups = new ArrayList<>();
    for (String code : List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L")) {
      List<Match> ms =
          matches.findByTournamentIdAndGroupCodeOrderByKickoffAtAsc(DEFAULT_TOURNAMENT_ID, code);
      List<MatchView> mvs = ms.stream().map(m -> toView(m, teamById, betByMatch)).toList();
      int filled = (int) mvs.stream().filter(v -> v.betScoreT1() != null).count();
      groups.add(new GroupView(code, filled, ms.size(), groupLocked, mvs));
    }

    List<KnockoutRoundView> ko = new ArrayList<>();
    for (Round r : rounds.findByTournamentIdOrderBySequenceAsc(DEFAULT_TOURNAMENT_ID)) {
      if ("GROUP".equals(r.getCode())) continue;
      List<Match> ms =
          matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(DEFAULT_TOURNAMENT_ID, r.getId());
      // A round is only unlocked (bettable) once the group stage closes AND its
      // matches have teams assigned — earlier rounds fill teams as prior rounds resolve.
      boolean roundUnlocked = groupLocked && ms.stream().anyMatch(m -> m.getTeam1Id() != null);
      List<MatchView> mvs = ms.stream().map(m -> toView(m, teamById, betByMatch)).toList();
      int filled = (int) mvs.stream().filter(v -> v.betScoreT1() != null).count();
      ko.add(
          new KnockoutRoundView(
              r.getCode(), r.getName(), filled, ms.size(), roundUnlocked, knockoutLocked, mvs));
    }

    int totalMatches = (int) matches.count();
    return new BracketView(
        q.getId(),
        totalMatches,
        myBets.size(),
        deadlines.groupStageDeadline() == null ? null : deadlines.groupStageDeadline().toString(),
        deadlines.knockoutDeadline() == null ? null : deadlines.knockoutDeadline().toString(),
        groups,
        ko);
  }

  private MatchView toView(Match m, Map<Long, Team> teamById, Map<Long, Bet> betByMatch) {
    Team t1 = m.getTeam1Id() == null ? null : teamById.get(m.getTeam1Id());
    Team t2 = m.getTeam2Id() == null ? null : teamById.get(m.getTeam2Id());
    Bet b = betByMatch.get(m.getId());
    return new MatchView(
        m.getId(),
        m.getTeam1Id(),
        t1 == null ? null : t1.getCode(),
        t1 == null ? null : t1.getName(),
        t1 == null ? null : t1.getFlagEmoji(),
        m.getTeam2Id(),
        t2 == null ? null : t2.getCode(),
        t2 == null ? null : t2.getName(),
        t2 == null ? null : t2.getFlagEmoji(),
        m.getKickoffAt().toString(),
        b == null ? null : b.getScoreT1(),
        b == null ? null : b.getScoreT2(),
        b == null ? null : b.getPredictedWinnerId(),
        m.getScoreT1(),
        m.getScoreT2(),
        Boolean.TRUE.equals(m.getPlayed()));
  }

  public static class BracketLockedException extends RuntimeException {
    public BracketLockedException(String msg) {
      super(msg);
    }
  }

  public record SaveBetRequest(
      Long matchId, Integer scoreT1, Integer scoreT2, Long predictedWinnerId) {}

  public record BetView(Long matchId, Integer scoreT1, Integer scoreT2, Long predictedWinnerId) {}

  @Transactional
  public BetView saveBet(Long userId, SaveBetRequest req) {
    if (req.matchId() == null || req.scoreT1() == null || req.scoreT2() == null) {
      throw new IllegalArgumentException("matchId, scoreT1, scoreT2 required");
    }
    if (req.scoreT1() < 0 || req.scoreT2() < 0 || req.scoreT1() > 30 || req.scoreT2() > 30) {
      throw new IllegalArgumentException("scores out of range");
    }

    io.quiniela.api.match.Match match =
        matches
            .findById(req.matchId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown match"));

    io.quiniela.api.match.Round round = rounds.findById(match.getRoundId()).orElseThrow();
    var t = lockClock.fetchTournamentDeadlines(match.getTournamentId());
    java.time.Instant now = java.time.Instant.now();
    boolean isGroup = "GROUP".equals(round.getCode());
    java.time.Instant deadline = isGroup ? t.groupStageDeadline() : t.knockoutDeadline();
    if (deadline != null && now.isAfter(deadline)) {
      throw new BracketLockedException("Bets locked for this round");
    }

    // For knockout draw bets, predictedWinnerId must be one of the match's teams.
    Long predictedWinnerId = req.predictedWinnerId();
    if (!isGroup && req.scoreT1().equals(req.scoreT2()) && predictedWinnerId != null) {
      if (!predictedWinnerId.equals(match.getTeam1Id())
          && !predictedWinnerId.equals(match.getTeam2Id())) {
        throw new IllegalArgumentException("predictedWinnerId must be a team in this match");
      }
    }

    Quiniela q =
        quinielas
            .findByPoolIdAndUserId(DEFAULT_POOL_ID, userId)
            .orElseGet(() -> quinielas.save(new Quiniela(DEFAULT_POOL_ID, userId)));

    Bet bet =
        bets.findByQuinielaIdAndMatchId(q.getId(), req.matchId())
            .orElseGet(() -> new Bet(q.getId(), req.matchId(), req.scoreT1(), req.scoreT2()));
    bet.setScoreT1(req.scoreT1());
    bet.setScoreT2(req.scoreT2());
    // Clear predictedWinnerId for non-draw or group bets; only relevant for knockout draws.
    bet.setPredictedWinnerId(
        !isGroup && req.scoreT1().equals(req.scoreT2()) ? predictedWinnerId : null);
    bets.save(bet);
    return new BetView(req.matchId(), req.scoreT1(), req.scoreT2(), bet.getPredictedWinnerId());
  }
}

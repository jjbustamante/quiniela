package io.quiniela.api.admin;

import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminTestService {

  static final Long TOURNAMENT_ID = 1L;
  static final Long POOL_ID = 1L;

  private final UserRepository users;
  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final JdbcTemplate jdbc;

  public AdminTestService(
      UserRepository users, MatchRepository matches, RoundRepository rounds, DataSource ds) {
    this.users = users;
    this.matches = matches;
    this.rounds = rounds;
    this.jdbc = new JdbcTemplate(ds);
  }

  public record TestState(
      boolean testMode,
      String groupStageDeadline,
      String knockoutDeadline,
      String currentRoundCode,
      int roundsRemaining) {}

  public record ModeView(boolean testMode) {}

  public record CleanResult(int betsDeleted, int matchesReset) {}

  public record DeadlinesView(String groupStageDeadline, String knockoutDeadline) {}

  void requireAdmin(Long callerId) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  void requireTestModeEnabled() {
    Boolean on =
        jdbc.queryForObject(
            "SELECT test_mode FROM tournament WHERE id = ?", Boolean.class, TOURNAMENT_ID);
    if (!Boolean.TRUE.equals(on)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Test mode is off");
    }
  }

  @Transactional(readOnly = true)
  public TestState getState(Long callerId) {
    requireAdmin(callerId);
    boolean testMode =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT test_mode FROM tournament WHERE id = ?", Boolean.class, TOURNAMENT_ID));
    String gs =
        jdbc.queryForObject(
            "SELECT TO_CHAR(group_stage_deadline AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')"
                + " FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    String ko =
        jdbc.queryForObject(
            "SELECT TO_CHAR(knockout_deadline AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')"
                + " FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    String currentRound = currentRoundCode();
    int remaining =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT r.id) FROM round r "
                + "JOIN match m ON m.round_id = r.id AND m.tournament_id = ? "
                + "WHERE r.tournament_id = ? AND m.played = false",
            Integer.class,
            TOURNAMENT_ID,
            TOURNAMENT_ID);
    return new TestState(testMode, gs, ko, currentRound, remaining);
  }

  /** Lowest-sequence round code that still has an unplayed match, or null. */
  String currentRoundCode() {
    return jdbc.query(
        "SELECT r.code FROM round r "
            + "JOIN match m ON m.round_id = r.id AND m.tournament_id = ? "
            + "WHERE r.tournament_id = ? AND m.played = false "
            + "ORDER BY r.sequence ASC LIMIT 1",
        rs -> rs.next() ? rs.getString("code") : null,
        TOURNAMENT_ID,
        TOURNAMENT_ID);
  }

  @Transactional
  public ModeView setMode(Long callerId, boolean enabled) {
    requireAdmin(callerId);
    jdbc.update(
        "UPDATE tournament SET test_mode = ?, updated_at = NOW() WHERE id = ?",
        enabled,
        TOURNAMENT_ID);
    return new ModeView(enabled);
  }

  @Transactional
  public CleanResult clean(Long callerId) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    int bets = jdbc.update("DELETE FROM bet");
    int matches =
        jdbc.update(
            "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false "
                + "WHERE tournament_id = ?",
            TOURNAMENT_ID);
    jdbc.update("UPDATE quiniela SET points = 0, updated_at = NOW() WHERE pool_id = ?", POOL_ID);
    jdbc.update(
        "UPDATE payment SET paid=false, paid_at=NULL, marked_paid_by=NULL, "
            + "settled=false, settled_at=NULL, marked_settled_by=NULL, updated_at=NOW() "
            + "WHERE pool_id = ?",
        POOL_ID);
    return new CleanResult(bets, matches);
  }

  public record SimulateRoundResult(
      String roundCode, int matchesPlayed, String advancedToRoundCode) {}

  public record SimulateAllResult(int roundsSimulated, int totalMatchesPlayed) {}

  private static int randomGoals() {
    int r = ThreadLocalRandom.current().nextInt(10);
    if (r < 4) return 0;
    if (r < 7) return 1;
    if (r < 9) return 2;
    return ThreadLocalRandom.current().nextInt(3, 5);
  }

  @Transactional
  public SimulateRoundResult simulateRound(Long callerId) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    return simulateCurrentRound();
  }

  private SimulateRoundResult simulateCurrentRound() {
    String roundCode = currentRoundCode();
    if (roundCode == null) return new SimulateRoundResult(null, 0, null);

    Round round = rounds.findByTournamentIdAndCode(TOURNAMENT_ID, roundCode).orElseThrow();
    boolean knockout = !"GROUP".equals(roundCode);

    List<Match> roundMatches =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, round.getId());

    int played = 0;
    for (Match m : roundMatches) {
      if (Boolean.TRUE.equals(m.getPlayed())) continue;
      if (m.getTeam1Id() == null || m.getTeam2Id() == null) continue;
      int s1 = randomGoals();
      int s2 = randomGoals();
      Long winner;
      if (s1 > s2) winner = m.getTeam1Id();
      else if (s2 > s1) winner = m.getTeam2Id();
      else
        winner =
            knockout
                ? (ThreadLocalRandom.current().nextBoolean() ? m.getTeam1Id() : m.getTeam2Id())
                : null;
      m.setScoreT1(s1);
      m.setScoreT2(s2);
      m.setWinnerId(winner);
      m.setPlayed(true);
      matches.save(m);
      played++;
    }

    String advancedTo = null;
    if (knockout) {
      advancedTo = advanceFromRound(round.getId());
    }
    return new SimulateRoundResult(roundCode, played, advancedTo);
  }

  private String advanceFromRound(Long playedRoundId) {
    List<Match> all =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, playedRoundId);
    java.util.Map<Long, Long> winnerByMatch = new java.util.HashMap<>();
    for (Match m : all) {
      if (m.getWinnerId() != null) winnerByMatch.put(m.getId(), m.getWinnerId());
    }
    if (winnerByMatch.isEmpty()) return null;

    String advancedRoundCode = null;
    for (Match child : matches.findAll()) {
      if (!TOURNAMENT_ID.equals(child.getTournamentId())) continue;
      Long p1 = child.getMatchParent1Id();
      Long p2 = child.getMatchParent2Id();
      if (p1 == null || p2 == null) continue;
      if (!winnerByMatch.containsKey(p1) || !winnerByMatch.containsKey(p2)) continue;
      if (child.getTeam1Id() == null) {
        jdbc.update(
            "UPDATE match SET team_1_id = ?, team_2_id = ?, updated_at = NOW() WHERE id = ?",
            winnerByMatch.get(p1),
            winnerByMatch.get(p2),
            child.getId());
        advancedRoundCode = roundCodeOf(child.getRoundId());
      }
    }
    return advancedRoundCode;
  }

  private String roundCodeOf(Long roundId) {
    return jdbc.queryForObject("SELECT code FROM round WHERE id = ?", String.class, roundId);
  }

  @Transactional
  public SimulateAllResult simulateAll(Long callerId) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    int roundsSimulated = 0;
    int total = 0;
    for (int i = 0; i < 7; i++) {
      SimulateRoundResult r = simulateCurrentRound();
      if (r.roundCode() == null || r.matchesPlayed() == 0) break;
      roundsSimulated++;
      total += r.matchesPlayed();
    }
    return new SimulateAllResult(roundsSimulated, total);
  }

  @Transactional
  public DeadlinesView setDeadlines(
      Long callerId, String groupStageDeadline, String knockoutDeadline) {
    requireAdmin(callerId);
    requireTestModeEnabled();
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = ?::timestamptz, "
            + "knockout_deadline = ?::timestamptz, updated_at = NOW() WHERE id = ?",
        groupStageDeadline,
        knockoutDeadline,
        TOURNAMENT_ID);
    String gs =
        jdbc.queryForObject(
            "SELECT TO_CHAR(group_stage_deadline AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')"
                + " FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    String ko =
        jdbc.queryForObject(
            "SELECT TO_CHAR(knockout_deadline AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')"
                + " FROM tournament WHERE id = ?",
            String.class,
            TOURNAMENT_ID);
    return new DeadlinesView(gs, ko);
  }
}

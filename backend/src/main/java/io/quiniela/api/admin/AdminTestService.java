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

  /** A team's standing within its group, after the group matches are played. */
  record Standing(Long teamId, String groupCode, int points, int goalDiff, int goalsFor) {}

  /**
   * Compute standings for every group from played GROUP matches. Returns, per group code, its teams
   * ordered best-first by: points (3/1/0) -> goal difference -> goals for -> team id (deterministic
   * final tiebreak).
   */
  java.util.Map<String, List<Standing>> groupStandings() {
    Long groupRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "GROUP").orElseThrow().getId();
    List<Match> groupMatches =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, groupRoundId);

    record Acc(String group, int pts, int gf, int ga) {}
    java.util.Map<Long, Acc> acc = new java.util.HashMap<>();
    for (Match m : groupMatches) {
      if (!Boolean.TRUE.equals(m.getPlayed())) continue;
      Long t1 = m.getTeam1Id();
      Long t2 = m.getTeam2Id();
      if (t1 == null || t2 == null) continue;
      int s1 = m.getScoreT1() == null ? 0 : m.getScoreT1();
      int s2 = m.getScoreT2() == null ? 0 : m.getScoreT2();
      String g = m.getGroupCode();
      int p1 = s1 > s2 ? 3 : s1 == s2 ? 1 : 0;
      int p2 = s2 > s1 ? 3 : s1 == s2 ? 1 : 0;
      Acc a1 = acc.getOrDefault(t1, new Acc(g, 0, 0, 0));
      Acc a2 = acc.getOrDefault(t2, new Acc(g, 0, 0, 0));
      acc.put(t1, new Acc(g, a1.pts() + p1, a1.gf() + s1, a1.ga() + s2));
      acc.put(t2, new Acc(g, a2.pts() + p2, a2.gf() + s2, a2.ga() + s1));
    }

    java.util.Map<String, List<Standing>> byGroup = new java.util.TreeMap<>();
    acc.forEach(
        (teamId, a) ->
            byGroup
                .computeIfAbsent(a.group(), k -> new java.util.ArrayList<>())
                .add(new Standing(teamId, a.group(), a.pts(), a.gf() - a.ga(), a.gf())));
    for (List<Standing> list : byGroup.values()) {
      list.sort(
          java.util.Comparator.comparingInt(Standing::points)
              .reversed()
              .thenComparing(java.util.Comparator.comparingInt(Standing::goalDiff).reversed())
              .thenComparing(java.util.Comparator.comparingInt(Standing::goalsFor).reversed())
              .thenComparingLong(Standing::teamId));
    }
    return byGroup;
  }

  /**
   * After the group round is played, fill the 32 R32 team slots from group standings and wire the
   * parent-pointer tree (R16<-R32, QF<-R16, SF<-QF, FINAL<-SF, THIRD_PLACE<-SF) so advanceFromRound
   * can carry winners onward. Idempotent: no-op if R32 already has teams. Test-only (caller is
   * gated). NOTE: a valid test bracket, NOT FIFA's official slotting matrix.
   */
  private void seedKnockoutBracket() {
    Long r32RoundId = rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "R32").orElseThrow().getId();
    List<Match> r32 =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, r32RoundId);
    if (r32.isEmpty()) return;
    if (r32.get(0).getTeam1Id() != null) return; // already seeded

    var byGroup = groupStandings();

    List<Standing> winners = new java.util.ArrayList<>();
    List<Standing> runnersUp = new java.util.ArrayList<>();
    List<Standing> thirds = new java.util.ArrayList<>();
    for (List<Standing> g : byGroup.values()) {
      if (g.size() > 0) winners.add(g.get(0));
      if (g.size() > 1) runnersUp.add(g.get(1));
      if (g.size() > 2) thirds.add(g.get(2));
    }
    thirds.sort(
        java.util.Comparator.comparingInt(Standing::points)
            .reversed()
            .thenComparing(java.util.Comparator.comparingInt(Standing::goalDiff).reversed())
            .thenComparing(java.util.Comparator.comparingInt(Standing::goalsFor).reversed())
            .thenComparingLong(Standing::teamId));
    List<Standing> bestThirds = thirds.subList(0, Math.min(8, thirds.size()));

    List<Long> seeds = new java.util.ArrayList<>();
    winners.forEach(s -> seeds.add(s.teamId()));
    runnersUp.forEach(s -> seeds.add(s.teamId()));
    bestThirds.forEach(s -> seeds.add(s.teamId()));

    if (seeds.size() < 32 || r32.size() < 16)
      return; // incomplete data; don't wire a broken bracket

    for (int i = 0; i < 16; i++) {
      Match m = r32.get(i);
      m.setTeam1Id(seeds.get(i));
      m.setTeam2Id(seeds.get(31 - i));
      matches.save(m);
    }

    wireParents("R32", "R16");
    wireParents("R16", "QF");
    wireParents("QF", "SF");
    wireFinalAndThird();
  }

  /**
   * For each child match in childCode (ordered by kickoff), set its two parents to consecutive
   * parent-round matches (also ordered by kickoff): child i <- parents[2i], parents[2i+1].
   */
  private void wireParents(String parentCode, String childCode) {
    Long parentRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, parentCode).orElseThrow().getId();
    Long childRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, childCode).orElseThrow().getId();
    List<Match> parents =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, parentRoundId);
    List<Match> children =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, childRoundId);
    for (int i = 0; i < children.size() && (2 * i + 1) < parents.size(); i++) {
      Match child = children.get(i);
      child.setMatchParent1Id(parents.get(2 * i).getId());
      child.setMatchParent2Id(parents.get(2 * i + 1).getId());
      matches.save(child);
    }
  }

  /** FINAL and THIRD_PLACE both take the two SF matches as parents. */
  private void wireFinalAndThird() {
    Long sfRoundId = rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "SF").orElseThrow().getId();
    List<Match> sf =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, sfRoundId);
    if (sf.size() < 2) return;
    Long sf1 = sf.get(0).getId();
    Long sf2 = sf.get(1).getId();
    for (String code : new String[] {"FINAL", "THIRD_PLACE"}) {
      Long roundId = rounds.findByTournamentIdAndCode(TOURNAMENT_ID, code).orElseThrow().getId();
      List<Match> ms =
          matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, roundId);
      if (ms.isEmpty()) continue;
      Match m = ms.get(0);
      m.setMatchParent1Id(sf1);
      m.setMatchParent2Id(sf2);
      matches.save(m);
    }
  }

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
    jdbc.update(
        "UPDATE match SET team_1_id=NULL, team_2_id=NULL, "
            + "match_parent_1_id=NULL, match_parent_2_id=NULL, updated_at=NOW() "
            + "WHERE tournament_id = ? AND round_id <> "
            + "(SELECT id FROM round WHERE tournament_id = ? AND code = 'GROUP')",
        TOURNAMENT_ID,
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
      // Knockout rounds need a winner: break ties by giving one goal to a random
      // team (simulates extra time / penalties). The DB trigger derives winner_id
      // from the scores, so we must avoid a draw in the persisted score columns.
      if (knockout && s1 == s2) {
        if (ThreadLocalRandom.current().nextBoolean()) s1++;
        else s2++;
      }
      Long winner;
      if (s1 > s2) winner = m.getTeam1Id();
      else winner = m.getTeam2Id();
      if (!knockout && s1 == s2) winner = null;
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
      if ("SF".equals(roundCode)) {
        fillThirdPlaceFromSfLosers(round.getId());
      }
    } else if ("GROUP".equals(roundCode) && played > 0) {
      seedKnockoutBracket();
      advancedTo = "R32";
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
        child.setTeam1Id(winnerByMatch.get(p1));
        child.setTeam2Id(winnerByMatch.get(p2));
        matches.save(child);
        advancedRoundCode = roundCodeOf(child.getRoundId());
      }
    }
    return advancedRoundCode;
  }

  /** After SF is played, set the third-place match's teams to the two SF losers. */
  private void fillThirdPlaceFromSfLosers(Long sfRoundId) {
    List<Match> sf =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, sfRoundId);
    List<Long> losers = new java.util.ArrayList<>();
    for (Match m : sf) {
      if (m.getWinnerId() == null || m.getTeam1Id() == null || m.getTeam2Id() == null) continue;
      losers.add(m.getWinnerId().equals(m.getTeam1Id()) ? m.getTeam2Id() : m.getTeam1Id());
    }
    if (losers.size() < 2) return;
    Long thirdRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "THIRD_PLACE").orElseThrow().getId();
    List<Match> third =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, thirdRoundId);
    if (third.isEmpty()) return;
    Match thirdMatch = third.get(0);
    thirdMatch.setTeam1Id(losers.get(0));
    thirdMatch.setTeam2Id(losers.get(1));
    matches.save(thirdMatch);
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

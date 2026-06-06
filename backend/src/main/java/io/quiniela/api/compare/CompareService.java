package io.quiniela.api.compare;

import io.quiniela.api.bracket.LockClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompareService {

  private static final Long POOL_ID = 1L;
  private static final Long TOURNAMENT_ID = 1L;

  private final JdbcTemplate jdbc;
  private final LockClock lockClock;

  public CompareService(DataSource ds, LockClock lockClock) {
    this.jdbc = new JdbcTemplate(ds);
    this.lockClock = lockClock;
  }

  public record ScoreCount(int scoreT1, int scoreT2, int count) {}

  public record MatchConsensus(
      Long matchId,
      String roundCode,
      String team1Code,
      String team1Flag,
      String team2Code,
      String team2Flag,
      String kickoffAt,
      Integer actualScoreT1,
      Integer actualScoreT2,
      boolean played,
      boolean revealed,
      Integer myScoreT1,
      Integer myScoreT2,
      List<ScoreCount> distribution,
      int totalPicks,
      boolean majority,
      boolean rebel) {}

  public record GroupConsensusView(List<MatchConsensus> matches) {}

  public record H2HMatch(
      Long matchId,
      String roundCode,
      String team1Code,
      String team1Flag,
      String team2Code,
      String team2Flag,
      String kickoffAt,
      Integer actualScoreT1,
      Integer actualScoreT2,
      boolean played,
      boolean revealed,
      Integer myScoreT1,
      Integer myScoreT2,
      Integer rivalScoreT1,
      Integer rivalScoreT2,
      String state) {} // "agree" | "differ" | "hidden"

  public record H2HView(
      Long rivalUserId,
      String rivalDisplayName,
      int agreeCount,
      int differCount,
      Integer myPoints,
      Integer rivalPoints,
      List<H2HMatch> matches) {}

  private record MatchMeta(
      long id,
      String roundCode,
      String t1Code,
      String t1Flag,
      String t2Code,
      String t2Flag,
      String kickoffAt,
      Integer actualT1,
      Integer actualT2,
      boolean played) {}

  private List<MatchMeta> fetchMatchMeta() {
    return jdbc.query(
        """
        SELECT m.id, r.code AS round_code, m.kickoff_at, m.score_t1, m.score_t2, m.played,
               t1.code AS t1_code, t1.flag_emoji AS t1_flag,
               t2.code AS t2_code, t2.flag_emoji AS t2_flag
        FROM match m
        JOIN round r ON r.id = m.round_id
        LEFT JOIN team t1 ON t1.id = m.team_1_id
        LEFT JOIN team t2 ON t2.id = m.team_2_id
        WHERE m.tournament_id = ?
        ORDER BY m.kickoff_at ASC, m.id ASC
        """,
        (rs, n) ->
            new MatchMeta(
                rs.getLong("id"),
                rs.getString("round_code"),
                rs.getString("t1_code"),
                rs.getString("t1_flag"),
                rs.getString("t2_code"),
                rs.getString("t2_flag"),
                rs.getTimestamp("kickoff_at").toInstant().toString(),
                (Integer) rs.getObject("score_t1"),
                (Integer) rs.getObject("score_t2"),
                rs.getBoolean("played")),
        TOURNAMENT_ID);
  }

  private Map<Long, int[]> fetchBetsForUser(Long userId) {
    Map<Long, int[]> out = new HashMap<>();
    jdbc.query(
        """
        SELECT b.match_id, b.score_t1, b.score_t2
        FROM bet b JOIN quiniela q ON q.id = b.quiniela_id
        WHERE q.pool_id = ? AND q.user_id = ?
        """,
        rs -> {
          out.put(rs.getLong("match_id"), new int[] {rs.getInt("score_t1"), rs.getInt("score_t2")});
        },
        POOL_ID,
        userId);
    return out;
  }

  @Transactional(readOnly = true)
  public GroupConsensusView getGroupConsensus(Long userId) {
    var deadlines = lockClock.fetchTournamentDeadlines(TOURNAMENT_ID);
    Instant now = Instant.now();

    Map<Long, int[]> myBets = fetchBetsForUser(userId);

    Map<Long, Map<String, Integer>> dist = new HashMap<>();
    jdbc.query(
        """
        SELECT b.match_id, b.score_t1, b.score_t2, COUNT(*) AS cnt
        FROM bet b JOIN quiniela q ON q.id = b.quiniela_id
        WHERE q.pool_id = ?
        GROUP BY b.match_id, b.score_t1, b.score_t2
        """,
        rs -> {
          long mid = rs.getLong("match_id");
          String key = rs.getInt("score_t1") + ":" + rs.getInt("score_t2");
          dist.computeIfAbsent(mid, k -> new HashMap<>()).put(key, rs.getInt("cnt"));
        },
        POOL_ID);

    List<MatchConsensus> out = new ArrayList<>();
    for (MatchMeta m : fetchMatchMeta()) {
      boolean revealed = LockClock.isMatchRevealable(now, deadlines, m.roundCode());
      int[] mine = myBets.get(m.id());
      Integer myT1 = mine == null ? null : mine[0];
      Integer myT2 = mine == null ? null : mine[1];

      List<ScoreCount> distribution = new ArrayList<>();
      int total = 0;
      boolean majority = false;
      boolean rebel = false;

      if (revealed) {
        Map<String, Integer> counts = dist.getOrDefault(m.id(), Map.of());
        int max = 0;
        for (var e : counts.entrySet()) {
          String[] parts = e.getKey().split(":");
          int c = e.getValue();
          total += c;
          max = Math.max(max, c);
          distribution.add(
              new ScoreCount(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), c));
        }
        distribution.sort((a, b) -> b.count() - a.count());
        if (mine != null) {
          int myCount = counts.getOrDefault(myT1 + ":" + myT2, 0);
          int peak = max;
          long peakCount = counts.values().stream().filter(v -> v == peak).count();
          majority = myCount > 0 && myCount == max && peakCount == 1;
          rebel = myCount == 1 && total > 1;
        }
      }

      out.add(
          new MatchConsensus(
              m.id(),
              m.roundCode(),
              m.t1Code(),
              m.t1Flag(),
              m.t2Code(),
              m.t2Flag(),
              m.kickoffAt(),
              m.actualT1(),
              m.actualT2(),
              m.played(),
              revealed,
              myT1,
              myT2,
              distribution,
              total,
              majority,
              rebel));
    }
    return new GroupConsensusView(out);
  }

  /**
   * Java mirror of the DB scoring function {@code score_match_for_bet} (current shape: the V010
   * additive model — see {@code db/migration/V010__additive_scoring.sql}). Used for the
   * head-to-head points tally previewed in the Duelos view.
   *
   * <p>Additive components — group / knockout (×2):
   *
   * <ul>
   *   <li>outcome (winner-or-draw bucket): 3 / 6
   *   <li>team-1 exact score: 2 / 4
   *   <li>team-2 exact score: 2 / 4
   *   <li>goal difference (signed): 1 / 2 — suppressed when the score is exact
   * </ul>
   *
   * <p>SCOPE: this 5-arg form intentionally omits V016's knockout-regulation-draw refinement
   * (predicted_winner_id vs advanced_team_id), which the H2H preview does not model. The H2H call
   * sites pass no winner ids, which is exactly the DB function invoked with NULL winner args — so
   * the two agree on every input the preview actually produces. {@code ScoringDivergenceTest} pins
   * this contract against the live DB function. Full single-source-of-truth consolidation is
   * tracked in BACKLOG.md (the two copies drifted once already: this mirror was stale at the V005
   * ladder while the DB had moved to V010+).
   */
  static int scoreMatchForBet(
      boolean knockout, int betT1, int betT2, Integer actualT1, Integer actualT2) {
    if (actualT1 == null || actualT2 == null) return 0;

    boolean exact = betT1 == actualT1 && betT2 == actualT2;
    int betWinner = Integer.compare(betT1, betT2);
    int actualWinner = Integer.compare(actualT1, actualT2);

    int total = 0;
    if (betWinner == actualWinner) total += 3; // outcome bucket (includes correct draw)
    if (betT1 == actualT1) total += 2; // team-1 exact
    if (betT2 == actualT2) total += 2; // team-2 exact
    if (!exact && (betT1 - betT2) == (actualT1 - actualT2)) total += 1; // signed goal difference

    return knockout ? total * 2 : total;
  }

  @Transactional(readOnly = true)
  public H2HView getH2H(Long userId, Long rivalUserId) {
    if (rivalUserId == null) throw new IllegalArgumentException("vs (rival user id) required");

    Integer rivalCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM quiniela q WHERE q.pool_id = ? AND q.user_id = ?",
            Integer.class,
            POOL_ID,
            rivalUserId);
    boolean rivalInPool = rivalCount != null && rivalCount > 0;
    if (!rivalInPool && !rivalUserId.equals(userId)) {
      throw new IllegalArgumentException("Unknown rival");
    }
    String rivalName =
        jdbc.query(
            "SELECT display_name FROM users WHERE id = ?",
            rs -> rs.next() ? rs.getString(1) : null,
            rivalUserId);

    var deadlines = lockClock.fetchTournamentDeadlines(TOURNAMENT_ID);
    Instant now = Instant.now();
    Map<Long, int[]> myBets = fetchBetsForUser(userId);
    Map<Long, int[]> rivalBets = fetchBetsForUser(rivalUserId);

    int agree = 0;
    int differ = 0;
    int myPoints = 0;
    int rivalPoints = 0;
    List<H2HMatch> matches = new ArrayList<>();
    for (MatchMeta m : fetchMatchMeta()) {
      boolean revealed = LockClock.isMatchRevealable(now, deadlines, m.roundCode());
      int[] mine = myBets.get(m.id());
      int[] theirs = rivalBets.get(m.id());
      Integer myT1 = mine == null ? null : mine[0];
      Integer myT2 = mine == null ? null : mine[1];
      Integer rvT1 = (revealed && theirs != null) ? theirs[0] : null;
      Integer rvT2 = (revealed && theirs != null) ? theirs[1] : null;

      String state;
      if (!revealed) {
        state = "hidden";
      } else if (mine != null && theirs != null && mine[0] == theirs[0] && mine[1] == theirs[1]) {
        state = "agree";
        agree++;
      } else if (mine != null && theirs != null) {
        state = "differ";
        differ++;
      } else {
        // Revealed, but one (or both) of us never picked — counts as a difference only
        // when at least one side has a pick, so agreeCount + differCount always equals
        // the number of revealed matches where at least one side participated.
        state = "differ";
        if (mine != null || theirs != null) {
          differ++;
        }
      }

      if (revealed && m.played() && m.actualT1() != null && m.actualT2() != null) {
        boolean knockout = !"GROUP".equals(m.roundCode());
        if (mine != null) {
          myPoints += scoreMatchForBet(knockout, mine[0], mine[1], m.actualT1(), m.actualT2());
        }
        if (theirs != null) {
          rivalPoints +=
              scoreMatchForBet(knockout, theirs[0], theirs[1], m.actualT1(), m.actualT2());
        }
      }

      matches.add(
          new H2HMatch(
              m.id(),
              m.roundCode(),
              m.t1Code(),
              m.t1Flag(),
              m.t2Code(),
              m.t2Flag(),
              m.kickoffAt(),
              m.actualT1(),
              m.actualT2(),
              m.played(),
              revealed,
              myT1,
              myT2,
              rvT1,
              rvT2,
              state));
    }
    return new H2HView(rivalUserId, rivalName, agree, differ, myPoints, rivalPoints, matches);
  }
}

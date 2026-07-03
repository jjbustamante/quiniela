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

  public record ScoreCount(int scoreT1, int scoreT2, int count, int rivalsAboveCount) {}

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
      boolean rebel,
      int rivalsAboveTotal,
      int rivalsAbovePicked) {}

  public record GroupConsensusView(
      Instant serverTime,
      List<MatchConsensus> past,
      List<MatchConsensus> today,
      List<MatchConsensus> upcoming) {}

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
      Instant serverTime,
      List<H2HMatch> past,
      List<H2HMatch> today,
      List<H2HMatch> upcoming) {}

  public record MatchPick(
      String displayName,
      int rank,
      int points,
      boolean isYou,
      boolean isBot,
      boolean isAboveMe,
      int scoreT1,
      int scoreT2,
      Integer pointsEarned) {}

  public record MatchPicksView(
      Long matchId,
      Integer actualScoreT1,
      Integer actualScoreT2,
      boolean played,
      List<MatchPick> picks) {}

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
      boolean played,
      int pointsMultiplier) {}

  private record DayBounds(Instant startOfToday, Instant startOfTomorrow) {}

  private DayBounds dayBounds(Long userId) {
    Instant[] b =
        jdbc.queryForObject(
            "SELECT DATE_TRUNC('day', NOW() AT TIME ZONE u.tz) AT TIME ZONE u.tz AS today_start, "
                + "(DATE_TRUNC('day', NOW() AT TIME ZONE u.tz) + INTERVAL '1 day') AT TIME ZONE u.tz "
                + "  AS tomorrow_start "
                + "FROM (SELECT COALESCE((SELECT timezone FROM users WHERE id = ?), "
                + "                      'America/Bogota') AS tz) u",
            (rs, n) ->
                new Instant[] {
                  rs.getTimestamp("today_start").toInstant(),
                  rs.getTimestamp("tomorrow_start").toInstant()
                },
            userId);
    return new DayBounds(b[0], b[1]);
  }

  private List<MatchMeta> fetchMatchMeta() {
    return jdbc.query(
        """
        SELECT m.id, r.code AS round_code, m.kickoff_at, m.score_t1, m.score_t2, m.played,
               r.points_multiplier AS points_multiplier,
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
                rs.getBoolean("played"),
                rs.getInt("points_multiplier")),
        TOURNAMENT_ID);
  }

  /** Each round's own earliest kickoff — the moment its picks become safe to reveal. */
  private static Map<String, Instant> firstKickoffByRound(List<MatchMeta> allMatches) {
    Map<String, Instant> out = new HashMap<>();
    for (MatchMeta m : allMatches) {
      Instant k = Instant.parse(m.kickoffAt());
      out.merge(m.roundCode(), k, (a, b) -> a.isBefore(b) ? a : b);
    }
    return out;
  }

  /**
   * A played match can no longer be bet on, so its picks are safe to reveal regardless of
   * deadlines. Otherwise GROUP reveals at the shared group-stage deadline; every knockout round
   * reveals at ITS OWN first kickoff, not a single tournament-wide knockout deadline — each round
   * gets its own small fill-before-kickoff window (see
   * docs/superpowers/specs/2026-07-03-per-round-knockout-deadline-design.md).
   */
  private static boolean isRevealed(
      MatchMeta m,
      Instant now,
      LockClock.TournamentDeadlines deadlines,
      Map<String, Instant> firstKickoffByRound) {
    if (m.played()) return true;
    if ("GROUP".equals(m.roundCode())) {
      return LockClock.isGroupRevealable(now, deadlines);
    }
    Instant firstKickoff = firstKickoffByRound.get(m.roundCode());
    return firstKickoff != null && now.isAfter(firstKickoff);
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

    Integer myPointsObj =
        jdbc.query(
            "SELECT points FROM quiniela WHERE pool_id = ? AND user_id = ?",
            rs -> rs.next() ? rs.getInt("points") : null,
            POOL_ID,
            userId);
    int myPoints = myPointsObj == null ? 0 : myPointsObj;

    Integer rivalsAboveTotalObj =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM quiniela q JOIN users u ON u.id = q.user_id
            WHERE q.pool_id = ? AND u.role <> 'admin' AND q.points > ?
            """,
            Integer.class,
            POOL_ID,
            myPoints);
    int rivalsAboveTotal = rivalsAboveTotalObj == null ? 0 : rivalsAboveTotalObj;

    Map<Long, Map<String, int[]>> dist = new HashMap<>(); // key -> [count, aboveCount]
    jdbc.query(
        """
        SELECT b.match_id, b.score_t1, b.score_t2,
               COUNT(*) AS cnt,
               COUNT(*) FILTER (WHERE u.role <> 'admin' AND q.points > ?) AS above_cnt
        FROM bet b
        JOIN quiniela q ON q.id = b.quiniela_id
        JOIN users u ON u.id = q.user_id
        WHERE q.pool_id = ?
        GROUP BY b.match_id, b.score_t1, b.score_t2
        """,
        rs -> {
          long mid = rs.getLong("match_id");
          String key = rs.getInt("score_t1") + ":" + rs.getInt("score_t2");
          dist.computeIfAbsent(mid, k -> new HashMap<>())
              .put(key, new int[] {rs.getInt("cnt"), rs.getInt("above_cnt")});
        },
        myPoints,
        POOL_ID);

    List<MatchMeta> allMatches = fetchMatchMeta();
    Map<String, Instant> firstKickoffByRound = firstKickoffByRound(allMatches);
    List<MatchConsensus> out = new ArrayList<>();
    for (MatchMeta m : allMatches) {
      boolean revealed = isRevealed(m, now, deadlines, firstKickoffByRound);
      int[] mine = myBets.get(m.id());
      Integer myT1 = mine == null ? null : mine[0];
      Integer myT2 = mine == null ? null : mine[1];

      List<ScoreCount> distribution = new ArrayList<>();
      int total = 0;
      int rivalsAbovePicked = 0;
      boolean majority = false;
      boolean rebel = false;

      if (revealed) {
        Map<String, int[]> counts = dist.getOrDefault(m.id(), Map.of());
        int max = 0;
        for (var e : counts.entrySet()) {
          String[] parts = e.getKey().split(":");
          int c = e.getValue()[0];
          int above = e.getValue()[1];
          total += c;
          rivalsAbovePicked += above;
          max = Math.max(max, c);
          distribution.add(
              new ScoreCount(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), c, above));
        }
        distribution.sort((a, b) -> b.count() - a.count());
        if (mine != null) {
          int myCount =
              counts.containsKey(myT1 + ":" + myT2) ? counts.get(myT1 + ":" + myT2)[0] : 0;
          int peak = max;
          long peakCount = counts.values().stream().filter(v -> v[0] == peak).count();
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
              rebel,
              rivalsAboveTotal,
              rivalsAbovePicked));
    }
    DayBounds bounds = dayBounds(userId);
    List<MatchConsensus> past = new ArrayList<>();
    List<MatchConsensus> today = new ArrayList<>();
    List<MatchConsensus> upcoming = new ArrayList<>();
    for (MatchConsensus mc : out) {
      Instant k = Instant.parse(mc.kickoffAt());
      if (k.isBefore(bounds.startOfToday())) past.add(mc);
      else if (k.isBefore(bounds.startOfTomorrow())) today.add(mc);
      else upcoming.add(mc);
    }
    past.sort((a, b) -> Instant.parse(b.kickoffAt()).compareTo(Instant.parse(a.kickoffAt())));
    return new GroupConsensusView(
        Instant.now(), List.copyOf(past), List.copyOf(today), List.copyOf(upcoming));
  }

  /**
   * Java mirror of the DB scoring function {@code score_match_for_bet} (current shape: the V010
   * additive model with the V019 per-round {@code multiplier}). Used for the head-to-head points
   * tally previewed in the Duelos view.
   *
   * <p>Additive components (before the round multiplier): outcome bucket 3, each-team exact 2,
   * signed goal difference 1 (suppressed when exact). The result is multiplied by the round's
   * {@code points_multiplier} (1 for the group stage, configurable for knockout rounds).
   *
   * <p>SCOPE: this form omits V016's knockout-regulation-draw refinement (predicted/advanced winner
   * ids), which the H2H preview does not model — the H2H call sites pass no winner ids, exactly the
   * DB function invoked with NULL winner args. {@code ScoringDivergenceTest} pins this contract.
   */
  static int scoreMatchForBet(
      int multiplier, int betT1, int betT2, Integer actualT1, Integer actualT2) {
    if (actualT1 == null || actualT2 == null) return 0;

    boolean exact = betT1 == actualT1 && betT2 == actualT2;
    int betWinner = Integer.compare(betT1, betT2);
    int actualWinner = Integer.compare(actualT1, actualT2);

    int total = 0;
    if (betWinner == actualWinner) total += 3; // outcome bucket (includes correct draw)
    if (betT1 == actualT1) total += 2; // team-1 exact
    if (betT2 == actualT2) total += 2; // team-2 exact
    if (!exact && (betT1 - betT2) == (actualT1 - actualT2)) total += 1; // signed goal difference

    return total * multiplier;
  }

  @Transactional(readOnly = true)
  public MatchPicksView getMatchPicks(Long userId, Long matchId) {
    MatchMeta meta =
        jdbc.query(
            """
            SELECT m.id, r.code AS round_code, m.kickoff_at, m.score_t1, m.score_t2, m.played,
                   r.points_multiplier AS points_multiplier,
                   t1.code AS t1_code, t1.flag_emoji AS t1_flag,
                   t2.code AS t2_code, t2.flag_emoji AS t2_flag
            FROM match m
            JOIN round r ON r.id = m.round_id
            LEFT JOIN team t1 ON t1.id = m.team_1_id
            LEFT JOIN team t2 ON t2.id = m.team_2_id
            WHERE m.id = ? AND m.tournament_id = ?
            """,
            rs ->
                rs.next()
                    ? new MatchMeta(
                        rs.getLong("id"),
                        rs.getString("round_code"),
                        rs.getString("t1_code"),
                        rs.getString("t1_flag"),
                        rs.getString("t2_code"),
                        rs.getString("t2_flag"),
                        rs.getTimestamp("kickoff_at").toInstant().toString(),
                        (Integer) rs.getObject("score_t1"),
                        (Integer) rs.getObject("score_t2"),
                        rs.getBoolean("played"),
                        rs.getInt("points_multiplier"))
                    : null,
            matchId,
            TOURNAMENT_ID);
    if (meta == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Unknown match");
    }

    var deadlines = lockClock.fetchTournamentDeadlines(TOURNAMENT_ID);
    Map<String, Instant> firstKickoffByRound = firstKickoffByRound(fetchMatchMeta());
    boolean revealed = isRevealed(meta, Instant.now(), deadlines, firstKickoffByRound);
    if (!revealed) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "Match not revealed");
    }

    Integer myPointsObj =
        jdbc.query(
            "SELECT points FROM quiniela WHERE pool_id = ? AND user_id = ?",
            rs -> rs.next() ? rs.getInt("points") : null,
            POOL_ID,
            userId);
    int myPoints = myPointsObj == null ? 0 : myPointsObj;

    List<MatchPick> picks =
        jdbc.query(
            """
            WITH ranked AS (
              SELECT q.id AS quiniela_id, q.user_id, u.display_name, q.points, u.is_bot,
                     RANK() OVER (ORDER BY q.points DESC) AS rk
              FROM quiniela q JOIN users u ON u.id = q.user_id
              WHERE q.pool_id = ? AND u.role <> 'admin'
            )
            SELECT r.display_name, r.points, r.user_id, r.is_bot, r.rk,
                   b.score_t1, b.score_t2
            FROM ranked r
            JOIN bet b ON b.quiniela_id = r.quiniela_id AND b.match_id = ?
            ORDER BY r.rk ASC, r.display_name ASC
            """,
            (rs, n) -> {
              int s1 = rs.getInt("score_t1");
              int s2 = rs.getInt("score_t2");
              int pts = rs.getInt("points");
              long uid = rs.getLong("user_id");
              Integer earned =
                  meta.played() && meta.actualT1() != null && meta.actualT2() != null
                      ? scoreMatchForBet(
                          meta.pointsMultiplier(), s1, s2, meta.actualT1(), meta.actualT2())
                      : null;
              return new MatchPick(
                  rs.getString("display_name"),
                  rs.getInt("rk"),
                  pts,
                  userId != null && userId.equals(uid),
                  rs.getBoolean("is_bot"),
                  pts > myPoints,
                  s1,
                  s2,
                  earned);
            },
            POOL_ID,
            matchId);

    return new MatchPicksView(
        meta.id(), meta.actualT1(), meta.actualT2(), meta.played(), List.copyOf(picks));
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
    List<MatchMeta> allMatches = fetchMatchMeta();
    Map<String, Instant> firstKickoffByRound = firstKickoffByRound(allMatches);
    List<H2HMatch> matches = new ArrayList<>();
    for (MatchMeta m : allMatches) {
      boolean revealed = isRevealed(m, now, deadlines, firstKickoffByRound);
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
        if (mine != null) {
          myPoints +=
              scoreMatchForBet(m.pointsMultiplier(), mine[0], mine[1], m.actualT1(), m.actualT2());
        }
        if (theirs != null) {
          rivalPoints +=
              scoreMatchForBet(
                  m.pointsMultiplier(), theirs[0], theirs[1], m.actualT1(), m.actualT2());
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
    DayBounds bounds = dayBounds(userId);
    List<H2HMatch> past = new ArrayList<>();
    List<H2HMatch> today = new ArrayList<>();
    List<H2HMatch> upcoming = new ArrayList<>();
    for (H2HMatch hm : matches) {
      Instant k = Instant.parse(hm.kickoffAt());
      if (k.isBefore(bounds.startOfToday())) past.add(hm);
      else if (k.isBefore(bounds.startOfTomorrow())) today.add(hm);
      else upcoming.add(hm);
    }
    past.sort((a, b) -> Instant.parse(b.kickoffAt()).compareTo(Instant.parse(a.kickoffAt())));
    return new H2HView(
        rivalUserId,
        rivalName,
        agree,
        differ,
        myPoints,
        rivalPoints,
        Instant.now(),
        List.copyOf(past),
        List.copyOf(today),
        List.copyOf(upcoming));
  }
}

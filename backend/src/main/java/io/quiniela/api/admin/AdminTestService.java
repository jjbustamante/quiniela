package io.quiniela.api.admin;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
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
  private final JdbcTemplate jdbc;

  public AdminTestService(UserRepository users, DataSource ds) {
    this.users = users;
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

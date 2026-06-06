package io.quiniela.api.admin;

import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.team.Team;
import io.quiniela.api.team.TeamRepository;
import io.quiniela.api.user.AdminGuard;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Backs the admin-only "enter a match result" workflow.
 *
 * <p>Writing to {@code match.score_t1 / score_t2 / winner_id / played} fires the V005 PL/pgSQL
 * trigger which recomputes every player's {@code quinielas.points} for this match. The trigger
 * handles the correction case (subtract old contribution + add new), so re-entering a wrong score
 * just produces the right delta.
 *
 * <p>Role check is here rather than via Spring Security annotations because we want the same
 * controlled error envelope used elsewhere in the API and we don't have
 * {@code @EnableMethodSecurity} configured yet — keep the check explicit and local.
 */
@Service
public class AdminResultsService {

  private static final Long ACTIVE_TOURNAMENT_ID = 1L;

  private final AdminGuard adminGuard;
  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final TeamRepository teams;

  public AdminResultsService(
      AdminGuard adminGuard,
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams) {
    this.adminGuard = adminGuard;
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
  }

  public record RecordResultCommand(
      Long callerUserId, Long matchId, int scoreT1, int scoreT2, Long advancingTeamId) {}

  public record MatchResultView(
      Long matchId, int scoreT1, int scoreT2, Long winnerId, boolean played) {}

  public record TeamSummary(Long id, String code, String name, String flag) {}

  public record AdminMatchRow(
      Long matchId,
      String roundCode,
      String roundName,
      int roundSequence,
      String groupCode,
      Instant kickoffAt,
      TeamSummary team1,
      TeamSummary team2,
      Integer scoreT1,
      Integer scoreT2,
      Long winnerId,
      boolean played) {}

  @Transactional(readOnly = true)
  public List<AdminMatchRow> listAllMatches(Long callerUserId) {
    adminGuard.requireAdmin(callerUserId);

    Map<Long, Round> roundsById = new HashMap<>();
    for (Round r : rounds.findByTournamentIdOrderBySequenceAsc(ACTIVE_TOURNAMENT_ID)) {
      roundsById.put(r.getId(), r);
    }

    Map<Long, Team> teamsById = new HashMap<>();
    for (Team t : teams.findAll()) {
      teamsById.put(t.getId(), t);
    }

    return matches.findAll().stream()
        .filter(m -> ACTIVE_TOURNAMENT_ID.equals(m.getTournamentId()))
        .sorted(java.util.Comparator.comparing(Match::getKickoffAt))
        .map(m -> toRow(m, roundsById, teamsById))
        .toList();
  }

  private AdminMatchRow toRow(Match m, Map<Long, Round> roundsById, Map<Long, Team> teamsById) {
    Round r = roundsById.get(m.getRoundId());
    return new AdminMatchRow(
        m.getId(),
        r != null ? r.getCode() : null,
        r != null ? r.getName() : null,
        r != null ? r.getSequence() : 0,
        m.getGroupCode(),
        m.getKickoffAt(),
        teamSummary(m.getTeam1Id(), teamsById),
        teamSummary(m.getTeam2Id(), teamsById),
        m.getScoreT1(),
        m.getScoreT2(),
        m.getWinnerId(),
        Boolean.TRUE.equals(m.getPlayed()));
  }

  private static TeamSummary teamSummary(Long teamId, Map<Long, Team> teamsById) {
    if (teamId == null) return null;
    Team t = teamsById.get(teamId);
    if (t == null) return new TeamSummary(teamId, null, null, null);
    return new TeamSummary(t.getId(), t.getCode(), t.getName(), t.getFlagEmoji());
  }

  @Transactional
  public MatchResultView record(RecordResultCommand cmd) {
    if (cmd.scoreT1 < 0 || cmd.scoreT2 < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scores must be non-negative");
    }

    adminGuard.requireAdmin(cmd.callerUserId);

    Match m =
        matches
            .findById(cmd.matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown match"));

    m.setScoreT1(cmd.scoreT1);
    m.setScoreT2(cmd.scoreT2);
    m.setPlayed(true);
    m.setWinnerId(winnerOf(m, cmd.scoreT1, cmd.scoreT2));
    // On a draw the progressing team can't be derived from the score; trust the
    // admin's pick. On a decisive score the trigger overwrites this anyway.
    m.setAdvancedTeamId(
        cmd.scoreT1 == cmd.scoreT2 ? cmd.advancingTeamId : winnerOf(m, cmd.scoreT1, cmd.scoreT2));
    Match saved = matches.save(m);

    return new MatchResultView(
        saved.getId(),
        saved.getScoreT1(),
        saved.getScoreT2(),
        saved.getWinnerId(),
        Boolean.TRUE.equals(saved.getPlayed()));
  }

  /** team1 if t1 > t2, team2 if t2 > t1, null on draw (the V005 trigger treats null as draw). */
  private static Long winnerOf(Match m, int t1, int t2) {
    if (t1 > t2) return m.getTeam1Id();
    if (t2 > t1) return m.getTeam2Id();
    return null;
  }
}

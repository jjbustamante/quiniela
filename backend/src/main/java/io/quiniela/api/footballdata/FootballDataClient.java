package io.quiniela.api.footballdata;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FootballDataClient {

  public record TeamApi(Long id, String name, String tla, String crest) {}

  public record CompetitionTeamsResponse(List<TeamApi> teams) {}

  public record StandingsTeam(Long id, String name) {}

  public record StandingsRow(StandingsTeam team) {}

  public record StandingsGroup(String group, String type, List<StandingsRow> table) {}

  public record CompetitionStandingsResponse(List<StandingsGroup> standings) {}

  public record MatchScoreFull(Integer home, Integer away) {

    /** Goal-by-goal sum, treating null operands/components as zero (null + null = null). */
    static MatchScoreFull sum(MatchScoreFull a, MatchScoreFull b) {
      if (a == null && b == null) return null;
      int home =
          (a != null && a.home != null ? a.home : 0) + (b != null && b.home != null ? b.home : 0);
      int away =
          (a != null && a.away != null ? a.away : 0) + (b != null && b.away != null ? b.away : 0);
      return new MatchScoreFull(home, away);
    }
  }

  // winner ∈ {HOME_TEAM, AWAY_TEAM, DRAW}; duration ∈ {REGULAR, EXTRA_TIME, PENALTY_SHOOTOUT}.
  // IMPORTANT: when a knockout is decided on penalties (duration == PENALTY_SHOOTOUT),
  // football-data.org folds the shootout into `fullTime` (e.g. a 1-1 game won 5-3 on penalties is
  // reported as fullTime 6-4). The on-pitch 120' score lives in `regularTime` + `extraTime`, and
  // `penalties` holds the shootout tally. Use resultScore() to get the score that counts.
  public record MatchScore(
      String winner,
      String duration,
      MatchScoreFull fullTime,
      MatchScoreFull regularTime,
      MatchScoreFull extraTime,
      MatchScoreFull penalties) {

    /**
     * The on-pitch result that counts for scoring: the score after 120 minutes, EXCLUDING any
     * penalty shootout.
     *
     * <p>For a shootout (duration == PENALTY_SHOOTOUT) football-data.org folds the shootout tally
     * into {@code fullTime}, so the real score is reconstructed from {@code regularTime +
     * extraTime}. For every other duration (REGULAR group games, EXTRA_TIME knockouts) {@code
     * fullTime} already excludes a shootout and is returned as-is.
     */
    public MatchScoreFull resultScore() {
      if ("PENALTY_SHOOTOUT".equals(duration) && regularTime != null) {
        return MatchScoreFull.sum(regularTime, extraTime);
      }
      return fullTime;
    }
  }

  public record MatchTeam(Long id, String name) {}

  public record MatchApi(
      Long id,
      String utcDate,
      String status,
      String stage,
      String group,
      MatchTeam homeTeam,
      MatchTeam awayTeam,
      MatchScore score) {}

  public record CompetitionMatchesResponse(List<MatchApi> matches) {}

  private final RestClient client;

  public FootballDataClient(
      @Value("${app.football-data.base-url:https://api.football-data.org/v4}") String baseUrl,
      @Value("${app.football-data.api-key:}") String apiKey) {
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("X-Auth-Token", apiKey == null ? "" : apiKey)
            .build();
  }

  public CompetitionTeamsResponse getTeams(String competitionCode) {
    return client
        .get()
        .uri("/competitions/{code}/teams", competitionCode)
        .retrieve()
        .body(CompetitionTeamsResponse.class);
  }

  public CompetitionStandingsResponse getStandings(String competitionCode) {
    return client
        .get()
        .uri("/competitions/{code}/standings", competitionCode)
        .retrieve()
        .body(CompetitionStandingsResponse.class);
  }

  public CompetitionMatchesResponse getMatches(String competitionCode) {
    return client
        .get()
        .uri("/competitions/{code}/matches", competitionCode)
        .retrieve()
        .body(CompetitionMatchesResponse.class);
  }
}

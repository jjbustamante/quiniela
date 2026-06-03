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

  public record MatchScoreFull(Integer home, Integer away) {}

  // winner ∈ {HOME_TEAM, AWAY_TEAM, DRAW}; duration ∈ {REGULAR, EXTRA_TIME, PENALTY_SHOOTOUT}.
  // fullTime excludes shootout goals; penalties holds the shootout score (null when not
  // applicable).
  public record MatchScore(
      String winner, String duration, MatchScoreFull fullTime, MatchScoreFull penalties) {}

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

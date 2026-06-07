package io.quiniela.api.ranking;

import io.quiniela.api.scoring.ScoreBreakdown;
import java.util.List;

/** A player's points broken down by stage and, within a stage, by match. */
public record ScorecardView(
    long userId, String displayName, int totalPoints, List<StageScore> stages) {

  public record TeamRef(String code, String name, String flag) {}

  public record MatchScore(
      long matchId,
      TeamRef team1,
      TeamRef team2,
      String kickoffAt,
      Integer betScoreT1,
      Integer betScoreT2,
      Integer actualScoreT1,
      Integer actualScoreT2,
      ScoreBreakdown breakdown) {}

  public record StageScore(
      String roundCode, String roundName, int points, List<MatchScore> matches) {}
}

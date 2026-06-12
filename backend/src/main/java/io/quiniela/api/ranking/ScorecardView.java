package io.quiniela.api.ranking;

import io.quiniela.api.scoring.ScoreBreakdown;
import java.util.List;

/** A player's points broken down by stage and, within a stage, by match. */
public record ScorecardView(
    long userId,
    String displayName,
    int totalPoints,
    List<StageScore> stages,
    boolean liveScoring) {

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
      ScoreBreakdown breakdown,
      /** Frozen displayed points (from {@code bet.points}). */
      int points,
      /**
       * Non-null when frozen points differ from live recompute: {@code "PLACED_AFTER_KICKOFF"} if
       * the bet was created after the match kicked off; {@code "EDITED_AFTER_KICKOFF"} if it was
       * created before but edited after.
       */
      String note) {}

  public record StageScore(
      String roundCode, String roundName, int points, List<MatchScore> matches) {}
}

package io.quiniela.api.scoring;

/**
 * Additive point components for one bet-vs-result, matching the DB {@code score_match_for_bet}
 * (V016/V019) exactly:
 *
 * <ul>
 *   <li>{@code outcome}: 3 when the predicted winner/draw matches (with the
 *       knockout-regulation-draw refinement: a predicted draw scores via the advancing team, not
 *       the scoreline);
 *   <li>{@code team1Exact} / {@code team2Exact}: 2 each for an exact team score;
 *   <li>{@code goalDiff}: 1 for the right signed goal difference (suppressed on an exact score).
 * </ul>
 *
 * {@code total = (outcome + team1Exact + team2Exact + goalDiff) * multiplier}. {@code
 * ScoreBreakdownDivergenceIT} pins {@code total()} against the live DB function so the breakdown
 * always sums to a player's real points.
 */
public record ScoreBreakdown(
    int outcome, int team1Exact, int team2Exact, int goalDiff, int multiplier, int total) {

  public static ScoreBreakdown of(
      boolean knockout,
      int betT1,
      int betT2,
      Integer actualT1,
      Integer actualT2,
      Long predictedWinnerId,
      Long advancedTeamId,
      int multiplier) {
    if (actualT1 == null || actualT2 == null) {
      return new ScoreBreakdown(0, 0, 0, 0, multiplier, 0);
    }

    boolean exact = betT1 == actualT1 && betT2 == actualT2;
    int betWinner = Integer.compare(betT1, betT2);
    int actualWinner = Integer.compare(actualT1, actualT2);

    int outcome = 0;
    if (betWinner == actualWinner) {
      if (knockout
          && betWinner == 0
          && actualWinner == 0
          && predictedWinnerId != null
          && advancedTeamId != null) {
        outcome = predictedWinnerId.equals(advancedTeamId) ? 3 : 0;
      } else {
        outcome = 3;
      }
    }

    int team1Exact = betT1 == actualT1 ? 2 : 0;
    int team2Exact = betT2 == actualT2 ? 2 : 0;
    int goalDiff = (!exact && (betT1 - betT2) == (actualT1 - actualT2)) ? 1 : 0;

    int total = (outcome + team1Exact + team2Exact + goalDiff) * multiplier;
    return new ScoreBreakdown(outcome, team1Exact, team2Exact, goalDiff, multiplier, total);
  }
}

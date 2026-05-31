package io.quiniela.api.ranking;

import java.util.List;

public record RankingView(List<RankingEntry> entries, String updatedAt) {

  public record RankingEntry(
      int rank,
      Long userId,
      String displayName,
      int points,
      Integer delta,
      boolean isYou,
      boolean isBot) {}
}

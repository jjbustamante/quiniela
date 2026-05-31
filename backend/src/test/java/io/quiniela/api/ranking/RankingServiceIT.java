package io.quiniela.api.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RankingServiceIT extends AbstractIntegrationTest {

  @Autowired RankingService service;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  private void seedQuinielaWithPoints(Long userId, int points) {
    jdbc.update("INSERT INTO quiniela (pool_id, user_id, points) VALUES (1, ?, ?)", userId, points);
  }

  @Test
  void leaderboardHidesAdminAndPrizeQueryHidesBots() {
    var player =
        users.save(new User("g-rank-player", "p@example.com", "Player", null, UserRole.PLAYER));
    var admin =
        users.save(new User("g-rank-admin", "a@example.com", "Admin", null, UserRole.ADMIN));
    // Paul bot is re-seeded by the test cleanup; fetch him.
    var paul = users.findByGoogleSub("paul-bot-oracle").orElseThrow();

    seedQuinielaWithPoints(player.getId(), 30);
    seedQuinielaWithPoints(paul.getId(), 99); // top by points
    seedQuinielaWithPoints(admin.getId(), 50);

    var display = service.getRanking(player.getId()).entries();
    assertThat(display)
        .extracting(RankingView.RankingEntry::displayName)
        .contains("Pulpo Paul 🐙", "Player")
        .doesNotContain("Admin");
    var paulRow =
        display.stream().filter(e -> e.userId().equals(paul.getId())).findFirst().orElseThrow();
    assertThat(paulRow.isBot()).isTrue();

    var eligible = service.getPrizeEligible();
    assertThat(eligible)
        .extracting(RankingView.RankingEntry::displayName)
        .containsExactly("Player");
  }
}

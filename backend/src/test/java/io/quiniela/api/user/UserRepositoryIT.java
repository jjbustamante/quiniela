package io.quiniela.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryIT extends AbstractIntegrationTest {

  @Autowired UserRepository users;

  @Test
  void persistsUserWithRoleAndInvitePath() {
    var u = new User("google-sub-1", "a@example.com", "Alice", null, UserRole.CAPTAIN);
    u.setInvitePath("alice-x1y2z3");

    users.save(u);

    var found = users.findByInvitePath("alice-x1y2z3");
    assertThat(found).isPresent();
    assertThat(found.get().getRole()).isEqualTo(UserRole.CAPTAIN);
    assertThat(found.get().getDisplayName()).isEqualTo("Alice");
  }

  @Test
  void findByInvitePathReturnsEmptyForUnknown() {
    assertThat(users.findByInvitePath("does-not-exist")).isEmpty();
  }
}

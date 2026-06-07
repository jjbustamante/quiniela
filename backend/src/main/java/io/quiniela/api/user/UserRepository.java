package io.quiniela.api.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByGoogleSub(String googleSub);

  Optional<User> findByInvitePath(String invitePath);

  Optional<User> findByEmail(String email);

  List<User> findByInvitedByUserIdAndRoleOrderByDisplayNameAsc(Long inviterId, UserRole role);
}

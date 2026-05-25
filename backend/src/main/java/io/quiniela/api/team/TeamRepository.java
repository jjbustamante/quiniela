package io.quiniela.api.team;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

  Optional<Team> findByTournamentIdAndCode(Long tournamentId, String code);
}

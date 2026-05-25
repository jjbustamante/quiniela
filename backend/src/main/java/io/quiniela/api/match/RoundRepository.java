package io.quiniela.api.match;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRepository extends JpaRepository<Round, Long> {

  Optional<Round> findByTournamentIdAndCode(Long tournamentId, String code);
}

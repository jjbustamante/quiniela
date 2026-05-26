package io.quiniela.api.tournament;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

  Optional<Tournament> findBySlug(String slug);
}

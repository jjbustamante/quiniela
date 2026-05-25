package io.quiniela.api.match;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

  List<Match> findByTournamentIdAndGroupCodeOrderByKickoffAtAsc(
      Long tournamentId, String groupCode);

  List<Match> findByTournamentIdAndRoundIdOrderByKickoffAtAsc(Long tournamentId, Long roundId);
}

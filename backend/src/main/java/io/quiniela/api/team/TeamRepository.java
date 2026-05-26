package io.quiniela.api.team;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

  Optional<Team> findByTournamentIdAndCode(Long tournamentId, String code);

  @Query(
      "select count(distinct t.groupCode) from Team t "
          + "where t.tournamentId = :tournamentId and t.groupCode is not null")
  int countDistinctGroupCodeByTournamentId(@Param("tournamentId") Long tournamentId);
}

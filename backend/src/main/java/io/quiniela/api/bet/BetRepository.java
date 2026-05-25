package io.quiniela.api.bet;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetRepository extends JpaRepository<Bet, BetId> {

  Optional<Bet> findByQuinielaIdAndMatchId(Long quinielaId, Long matchId);

  List<Bet> findByQuinielaId(Long quinielaId);
}

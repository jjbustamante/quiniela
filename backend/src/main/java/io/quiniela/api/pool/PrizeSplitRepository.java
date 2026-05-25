package io.quiniela.api.pool;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrizeSplitRepository extends JpaRepository<PrizeSplit, PrizeSplitId> {

  List<PrizeSplit> findByPoolIdOrderByRankAsc(Long poolId);
}

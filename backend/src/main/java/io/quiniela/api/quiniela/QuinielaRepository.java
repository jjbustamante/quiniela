package io.quiniela.api.quiniela;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuinielaRepository extends JpaRepository<Quiniela, Long> {

  Optional<Quiniela> findByPoolIdAndUserId(Long poolId, Long userId);
}

package io.quiniela.api.paul;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaulPredictionRepository extends JpaRepository<PaulPrediction, Long> {

  List<PaulPrediction> findByMatchIdAndKind(Long matchId, String kind);

  List<PaulPrediction> findByKind(String kind);

  Optional<PaulPrediction> findByMatchIdAndModelAndKind(Long matchId, String model, String kind);
}

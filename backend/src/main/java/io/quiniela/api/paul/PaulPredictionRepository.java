package io.quiniela.api.paul;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaulPredictionRepository extends JpaRepository<PaulPrediction, Long> {

  List<PaulPrediction> findByMatchIdAndKind(Long matchId, String kind);

  List<PaulPrediction> findByKind(String kind);

  Optional<PaulPrediction> findByMatchIdAndModelAndKind(Long matchId, String model, String kind);

  List<PaulPrediction> findByOracleAndKind(String oracle, String kind);

  List<PaulPrediction> findByOracleAndMatchIdAndKind(String oracle, Long matchId, String kind);

  Optional<PaulPrediction> findByOracleAndMatchIdAndModelAndKind(
      String oracle, Long matchId, String model, String kind);
}

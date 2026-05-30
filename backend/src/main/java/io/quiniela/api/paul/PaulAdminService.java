package io.quiniela.api.paul;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaulAdminService {

  private final UserRepository users;
  private final PaulPredictionService predictionService;
  private final PaulEnsembleService ensembleService;
  private final PaulService paulService;

  public PaulAdminService(
      UserRepository users,
      PaulPredictionService predictionService,
      PaulEnsembleService ensembleService,
      PaulService paulService) {
    this.users = users;
    this.predictionService = predictionService;
    this.ensembleService = ensembleService;
    this.paulService = paulService;
  }

  public record GenerateResult(int candidatesCreated) {}

  public record SynthesizeResult(int officialsCreated) {}

  void requireAdmin(Long callerId) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  public GenerateResult generate(Long callerId) {
    requireAdmin(callerId);
    return new GenerateResult(predictionService.generateAllGroup());
  }

  public SynthesizeResult synthesize(Long callerId) {
    requireAdmin(callerId);
    return new SynthesizeResult(ensembleService.synthesizeAllGroup());
  }

  public PaulService.RevealResult reveal(Long callerId) {
    requireAdmin(callerId);
    return paulService.reveal();
  }
}

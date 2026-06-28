package io.quiniela.api.paul;

import java.util.List;

/**
 * A prediction-bot identity: which user it plays as, and which model(s) form its brain. Paul is an
 * ensemble oracle (multi-model roster + a judge); avatar oracles are single-model (a roster of one,
 * no judge — synthesis just promotes the lone candidate).
 */
public record Oracle(
    String key,
    String googleSub,
    String displayName,
    List<PaulProperties.ModelSpec> roster,
    PaulProperties.ModelSpec ensembleSpec) {

  public boolean isEnsemble() {
    return ensembleSpec != null;
  }
}

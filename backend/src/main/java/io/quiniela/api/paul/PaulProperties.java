package io.quiniela.api.paul;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.paul")
public record PaulProperties(
    String provider,
    List<String> models,
    String ensembleModel,
    String projectId,
    String location,
    List<OracleSpec> oracles) {

  public PaulProperties {
    if (provider == null) provider = "google";
    if (models == null || models.isEmpty()) models = List.of("gemini-2.5-flash");
    if (ensembleModel == null) ensembleModel = models.get(0);
    if (location == null) location = "us-central1";
    if (oracles == null) oracles = List.of();
  }

  /**
   * Config entry for an extra (non-Paul) oracle bot. {@code ensembleModel} null => single-model.
   */
  public record OracleSpec(
      String key,
      String googleSub,
      String displayName,
      List<String> models,
      String ensembleModel) {}

  /** One roster entry: which provider serves which model id. */
  public record ModelSpec(String provider, String model) {}

  /**
   * Parse {@link #models()} into (provider, model) specs. An entry is either "provider:modelId"
   * (explicit, e.g. {@code openai:openai/gpt-oss-120b-maas}) or a bare "modelId" that uses the
   * default {@link #provider()}. Split is on the FIRST colon only — model ids never contain a colon
   * (they use '/' or '@').
   */
  public List<ModelSpec> roster() {
    return models.stream().map(this::parseSpec).toList();
  }

  /** The (provider, model) used for the ensemble/judge pass. */
  public ModelSpec ensembleSpec() {
    return parseSpec(ensembleModel);
  }

  /**
   * The full oracle registry: Paul (built from the base fields) followed by each configured extra
   * oracle. An extra oracle with no ensemble-model is single-model (no judge).
   */
  public List<Oracle> allOracles() {
    List<Oracle> all = new ArrayList<>();
    all.add(new Oracle("paul", "paul-bot-oracle", "Pulpo Paul 🐙", roster(), ensembleSpec()));
    for (OracleSpec o : oracles) {
      List<ModelSpec> r = o.models().stream().map(this::parseSpec).toList();
      ModelSpec ens = o.ensembleModel() == null ? null : parseSpec(o.ensembleModel());
      all.add(new Oracle(o.key(), o.googleSub(), o.displayName(), r, ens));
    }
    return all;
  }

  private ModelSpec parseSpec(String s) {
    int i = s.indexOf(':');
    return i < 0
        ? new ModelSpec(provider, s)
        : new ModelSpec(s.substring(0, i), s.substring(i + 1));
  }
}

package io.quiniela.api.paul;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.paul")
public record PaulProperties(
    String provider, List<String> models, String ensembleModel, String projectId, String location) {

  public PaulProperties {
    if (provider == null) provider = "google";
    if (models == null || models.isEmpty()) models = List.of("gemini-2.5-flash");
    if (ensembleModel == null) ensembleModel = models.get(0);
    if (location == null) location = "us-central1";
  }

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

  private ModelSpec parseSpec(String s) {
    int i = s.indexOf(':');
    return i < 0
        ? new ModelSpec(provider, s)
        : new ModelSpec(s.substring(0, i), s.substring(i + 1));
  }
}

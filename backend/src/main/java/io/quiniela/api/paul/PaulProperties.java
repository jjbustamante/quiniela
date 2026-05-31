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
}

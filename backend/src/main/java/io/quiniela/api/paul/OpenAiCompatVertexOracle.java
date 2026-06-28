package io.quiniela.api.paul;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Calls Vertex AI Model Garden MaaS models (gpt-oss, Qwen, and — once enabled — Llama/Mistral/
 * DeepSeek) through Vertex's OpenAI-compatible chat-completions endpoint on the GLOBAL location.
 * Auth is ADC (the Cloud Run runtime SA, {@code roles/aiplatform.user}); billed to GCP credits,
 * same as Gemini.
 *
 * <p>Sits behind {@link RoutingPaulOracle} for non-Gemini providers; Gemini still goes through
 * {@link VertexPaulOracle} (genai SDK). The {@code provider} arg is ignored here — the provider
 * already selected this oracle. We talk raw JSON (serialize the request + parse the response with
 * our own Jackson 2 mapper) so we don't depend on any auto-configured HTTP message converter.
 */
public class OpenAiCompatVertexOracle implements PaulOracle {

  private static final String CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";

  private final String url;
  private final String projectId;
  private final GoogleCredentials credentials;
  private final RestClient http;
  private final ObjectMapper mapper;

  public OpenAiCompatVertexOracle(String projectId) {
    this.projectId = projectId;
    this.url =
        "https://aiplatform.googleapis.com/v1beta1/projects/"
            + projectId
            + "/locations/global/endpoints/openapi/chat/completions";
    try {
      this.credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM);
    } catch (Exception e) {
      throw new IllegalStateException(
          "No Application Default Credentials for the Vertex OpenAI-compat oracle: "
              + e.getMessage(),
          e);
    }
    HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(jdk);
    rf.setReadTimeout(Duration.ofSeconds(90));
    this.http = RestClient.builder().requestFactory(rf).build();
    this.mapper = new ObjectMapper();
  }

  @Override
  public PaulPredictionResult predict(
      String systemPrompt, String userPrompt, String provider, String model) {
    String jsonRules =
        "\n\nResponde SOLO con un objeto JSON (sin texto extra, sin markdown) con exactamente estas "
            + "claves: {\"scoreT1\": entero >= 0, \"scoreT2\": entero >= 0, "
            + "\"confidence\": número entre 0 y 1, \"reasoning\": texto en español, "
            + "\"advancing\": \"LOCAL\" | \"VISITANTE\" | null (solo eliminación directa)}.";
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "messages",
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt + jsonRules)),
            "temperature",
            0.8,
            "max_tokens",
            4096);
    try {
      String reqJson = mapper.writeValueAsString(body);
      credentials.refreshIfExpired();
      String token = credentials.getAccessToken().getTokenValue();
      String raw =
          http.post()
              .uri(url)
              .header("Authorization", "Bearer " + token)
              .header("X-Goog-User-Project", projectId)
              .header("Content-Type", "application/json")
              .body(reqJson)
              .retrieve()
              .body(String.class);
      JsonNode msg = mapper.readTree(raw).path("choices").path(0).path("message");
      String content =
          firstNonBlank(
              msg.path("content").asText(null), msg.path("reasoning_content").asText(null));
      if (content == null) {
        throw new IllegalStateException("empty completion content");
      }
      return mapper.readValue(extractJson(content), PaulPredictionResult.class);
    } catch (Exception e) {
      // Unchecked so the batch falls back to the deterministic stub for this one candidate.
      throw new IllegalStateException(
          "OpenAI-compat prediction failed for model " + model + ": " + e.getMessage(), e);
    }
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
  }

  /** Some models wrap JSON in markdown fences or prose — grab the outermost {...}. */
  private static String extractJson(String s) {
    int a = s.indexOf('{');
    int b = s.lastIndexOf('}');
    return (a >= 0 && b > a) ? s.substring(a, b + 1) : s;
  }
}

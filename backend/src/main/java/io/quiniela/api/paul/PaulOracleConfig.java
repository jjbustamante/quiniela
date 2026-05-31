package io.quiniela.api.paul;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaulOracleConfig {

  /**
   * Vertex routing oracle (the multi-vendor prod path). Active when {@code
   * app.paul.provider=vertex}. Dispatches each candidate by its per-model provider: Gemini via
   * {@link VertexPaulOracle} (genai SDK) and Model Garden MaaS models (gpt-oss, Qwen, …) via {@link
   * OpenAiCompatVertexOracle}. Both authenticate with the runtime SA's ADC and bill to GCP credits.
   * Declared first so it wins over the Gemini/stub beans via {@link ConditionalOnMissingBean}.
   */
  @Bean
  @ConditionalOnProperty(prefix = "app.paul", name = "provider", havingValue = "vertex")
  PaulOracle routingPaulOracle(PaulProperties props) {
    VertexPaulOracle gemini = new VertexPaulOracle(props.projectId(), props.location());
    OpenAiCompatVertexOracle openAiCompat = new OpenAiCompatVertexOracle(props.projectId());
    return new RoutingPaulOracle(gemini, openAiCompat);
  }

  /**
   * Real Gemini-backed oracle (AI Studio). Only registered when {@link ChatClient.Builder} is in
   * context, which requires {@code spring.ai.model.chat=google-genai} AND a non-empty {@code
   * GEMINI_API_KEY}. In tests (model.chat=none) and in keyless local runs, no ChatClient.Builder
   * bean exists, so this bean is skipped and {@link #stubPaulOracle()} takes over.
   */
  @Bean
  @ConditionalOnBean(ChatClient.Builder.class)
  @ConditionalOnMissingBean(PaulOracle.class)
  PaulOracle geminiPaulOracle(ChatClient.Builder builder) {
    return new GeminiPaulOracle(builder);
  }

  @Bean
  @ConditionalOnMissingBean(PaulOracle.class)
  PaulOracle stubPaulOracle() {
    return new StubPaulOracle();
  }
}

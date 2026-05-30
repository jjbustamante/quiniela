package io.quiniela.api.paul;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaulOracleConfig {

  /**
   * Real Gemini-backed oracle. Only registered when {@link ChatClient.Builder} is in context, which
   * requires {@code spring.ai.model.chat=google-genai} AND a non-empty {@code GEMINI_API_KEY}. In
   * tests (model.chat=none) and in keyless local runs, no ChatClient.Builder bean exists, so this
   * bean is skipped and {@link #stubPaulOracle()} takes over.
   */
  @Bean
  @ConditionalOnBean(ChatClient.Builder.class)
  PaulOracle geminiPaulOracle(ChatClient.Builder builder) {
    return new GeminiPaulOracle(builder);
  }

  @Bean
  @ConditionalOnMissingBean(PaulOracle.class)
  PaulOracle stubPaulOracle() {
    return new StubPaulOracle();
  }
}

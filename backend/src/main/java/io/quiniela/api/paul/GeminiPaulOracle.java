package io.quiniela.api.paul;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

public class GeminiPaulOracle implements PaulOracle {

  private final ChatClient chatClient;

  public GeminiPaulOracle(ChatClient.Builder builder) {
    this.chatClient = builder.build();
  }

  @Override
  public PaulPredictionResult predict(
      String systemPrompt, String userPrompt, String provider, String model) {
    // M8 note: ChatClientRequestSpec.options() takes a ChatOptions.Builder<?>, not a built
    // ChatOptions. GoogleGenAiChatOptions.Builder extends DefaultToolCallingChatOptions$Builder
    // which implements ChatOptions.Builder, so pass the builder directly (do NOT call .build()).
    // model(String) and temperature(Double) are both inherited from the parent builder chain.
    return chatClient
        .prompt()
        .options(GoogleGenAiChatOptions.builder().model(model).temperature(0.8))
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .entity(PaulPredictionResult.class);
  }
}

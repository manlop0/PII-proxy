package com.project.piiproxy.pipeline.core;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizer {

  private final TextAnalyzer analyzer;
  private final String gatewaySystemPrompt;

  public RequestAnonymizer(TextAnalyzer analyzer, String gatewaySystemPrompt) {
    this.analyzer = analyzer;
    this.gatewaySystemPrompt = gatewaySystemPrompt;
  }

  public Future<Void> redactRequest(JsonObject requestBody, String sessionId, LlmJsonAdapter adapter) {
    return adapter.redactRequest(requestBody, sessionId, analyzer)
      .map(v -> {
        if (gatewaySystemPrompt != null && !gatewaySystemPrompt.isBlank()) {
          adapter.injectGatewaySystemPrompt(requestBody, gatewaySystemPrompt);
        }
        return null;
      });
  }
}

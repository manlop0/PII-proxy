package com.project.piiproxy.pipeline.core;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizer {

  private final TextAnalyzer analyzer;
  private final String gatewaySystemPrompt;

  public RequestAnonymizer(TextAnalyzer analyzer, String gatewaySystemPrompt) {
    this.analyzer = analyzer;
    this.gatewaySystemPrompt = gatewaySystemPrompt;
  }

  public void redactRequest(JsonObject requestBody, String sessionId, LlmJsonAdapter adapter) {
    adapter.redactRequest(requestBody, sessionId, analyzer);

    if (gatewaySystemPrompt != null && !gatewaySystemPrompt.isBlank()) {
      adapter.injectGatewaySystemPrompt(requestBody, gatewaySystemPrompt);
    }
  }
}

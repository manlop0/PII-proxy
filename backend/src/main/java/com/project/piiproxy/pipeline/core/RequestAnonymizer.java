package com.project.piiproxy.pipeline.core;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizer {

  private final TextAnalyzer analyzer;

  public RequestAnonymizer(TextAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public void redactRequest(JsonObject requestBody, String sessionId, LlmJsonAdapter adapter) {
    adapter.redactRequest(requestBody, sessionId, analyzer);
  }
}

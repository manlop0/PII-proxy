package com.project.piiproxy.pipeline.core;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class UnaryResponseRestorer {

  private final TextAnalyzer analyzer;

  public UnaryResponseRestorer(TextAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public void restoreResponse(JsonObject responseBody, String sessionId, LlmJsonAdapter adapter) {
    adapter.restoreUnaryResponse(responseBody, sessionId, analyzer);
  }
}

package com.project.piiproxy.pipeline.core;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class UnaryResponseRestorer {

  private final TextAnalyzer analyzer;

  public UnaryResponseRestorer(TextAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public void restoreResponse(JsonObject responseBody, String sessionId) {
    JsonArray choices = responseBody.getJsonArray("choices");
    if (choices != null && !choices.isEmpty()) {
      JsonObject message = choices.getJsonObject(0).getJsonObject("message");
      if (message != null) {
        String content = message.getString("content");
        if (content != null) {
          String restoredContent = analyzer.restoreText(content, sessionId);
          message.put("content", restoredContent);
        }
      }
    }
  }
}

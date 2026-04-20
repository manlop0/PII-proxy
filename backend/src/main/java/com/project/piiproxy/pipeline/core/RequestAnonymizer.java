package com.project.piiproxy.pipeline.core;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizer {

  private final TextAnalyzer analyzer;

  public RequestAnonymizer(TextAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public void redactRequest(JsonObject requestBody, String sessionId) {
    JsonArray messages = requestBody.getJsonArray("messages");
    if (messages != null) {
      for (int i = 0; i < messages.size(); i++) {
        JsonObject message = messages.getJsonObject(i);
        String content = message.getString("content");

        if (content != null) {
          String safeContent = analyzer.anonymizeText(content, sessionId);
          message.put("content", safeContent);
        }
      }
    }
  }
}

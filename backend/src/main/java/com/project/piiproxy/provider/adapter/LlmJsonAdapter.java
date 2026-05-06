package com.project.piiproxy.provider.adapter;

import com.project.piiproxy.pipeline.core.SessionStreamProcessor;
import com.project.piiproxy.pipeline.core.TextAnalyzer;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

public interface LlmJsonAdapter {
  void redactRequest(JsonObject requestBody, String sessionId, TextAnalyzer analyzer);

  void restoreUnaryResponse(JsonObject responseBody, String sessionId, TextAnalyzer analyzer);

  List<String> getStreamProcessorKeys();

  void restoreStreamChunk(JsonObject jsonChunk, Map<String, SessionStreamProcessor> processors);

  void injectGatewaySystemPrompt(JsonObject requestBody, String gatewayPrompt);
}

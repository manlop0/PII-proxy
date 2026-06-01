package com.project.piiproxy.provider.adapter;

import com.project.piiproxy.pipeline.anonymize.TextAnalyzer;
import com.project.piiproxy.pipeline.stream.SessionStreamProcessor;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Provider-specific JSON contract. Encapsulates redaction of outgoing requests,
 * restoration of incoming responses (both unary and streaming), and gateway prompt injection.
 */
public interface LlmJsonCodec {
  Future<Void> redactRequest(JsonObject requestBody, String sessionId, TextAnalyzer analyzer);

  Future<Void> restoreUnaryResponse(JsonObject responseBody, String sessionId, TextAnalyzer analyzer);

  List<String> getStreamProcessorKeys();

  Future<Void> restoreStreamChunk(JsonObject jsonChunk, Map<String, SessionStreamProcessor> processors);

  void injectGatewaySystemPrompt(JsonObject requestBody, String gatewayPrompt);
}

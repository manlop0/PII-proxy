package com.project.piiproxy.provider.codec;

import com.project.piiproxy.pipeline.anonymize.TextAnalyzer;
import com.project.piiproxy.pipeline.stream.SessionStreamProcessor;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link LlmJsonCodec} implementation for the OpenAI chat completions schema, including SSE streaming.
 */
public class OpenAiCodec implements LlmJsonCodec {

  @Override
  public Future<Void> redactRequest(JsonObject requestBody, String sessionId, TextAnalyzer analyzer) {
    JsonArray messages = requestBody.getJsonArray("messages");
    if (messages == null || messages.isEmpty()) {
      return Future.succeededFuture();
    }

    List<Future<Void>> futures = new ArrayList<>();

    for (int i = 0; i < messages.size(); i++) {
      JsonObject message = messages.getJsonObject(i);
      futures.add(redactField(message, "content", sessionId, analyzer));
      futures.add(redactField(message, "reasoning", sessionId, analyzer));
      futures.add(redactField(message, "thought", sessionId, analyzer));
    }

    return Future.all(futures).mapEmpty();
  }

  @Override
  public Future<Void> restoreUnaryResponse(JsonObject responseBody, String sessionId, TextAnalyzer analyzer) {
    JsonArray choices = responseBody.getJsonArray("choices");
    if (choices == null || choices.isEmpty()) return Future.succeededFuture();

    JsonObject message = choices.getJsonObject(0).getJsonObject("message");
    if (message == null) return Future.succeededFuture();

    List<Future<Void>> futures = new ArrayList<>();
    futures.add(restoreFieldAsync(message, "content", sessionId, analyzer));
    futures.add(restoreFieldAsync(message, "reasoning", sessionId, analyzer));

    JsonArray reasoningDetails = message.getJsonArray("reasoning_details");
    if (reasoningDetails != null && !reasoningDetails.isEmpty()) {
      JsonObject reasoning_details = reasoningDetails.getJsonObject(0);
      if (reasoning_details != null) {
        futures.add(restoreFieldAsync(reasoning_details, "text", sessionId, analyzer));
      }
    }
    return Future.all(futures).mapEmpty();
  }

  @Override
  public List<String> getStreamProcessorKeys() {
    return List.of("content", "reasoning", "reasoning_details");
  }

  @Override
  public Future<Void> restoreStreamChunk(JsonObject jsonChunk, Map<String, SessionStreamProcessor> processors) {
    JsonArray choices = jsonChunk.getJsonArray("choices");
    if (choices == null || choices.isEmpty()) return Future.succeededFuture();

    JsonObject delta = choices.getJsonObject(0).getJsonObject("delta");
    if (delta == null) return Future.succeededFuture();

    List<Future<Void>> futures = new ArrayList<>();
    if (delta.containsKey("content")) {
      futures.add(processChunkAsync(processors.get("content"), delta, "content"));
    }
    if (delta.containsKey("reasoning")) {
      futures.add(processChunkAsync(processors.get("reasoning"), delta, "reasoning"));
    }
    if (delta.containsKey("reasoning_details")) {
      JsonArray details = delta.getJsonArray("reasoning_details");
      for (int i = 0; i < details.size(); i++) {
        JsonObject detail = details.getJsonObject(i);
        if (detail.containsKey("text")) {
          futures.add(processChunkAsync(processors.get("reasoning_details"), detail, "text"));
        }
      }
    }
    return Future.all(futures).mapEmpty();
  }

  private Future<Void> processChunkAsync(SessionStreamProcessor processor, JsonObject target, String field) {
    return processor.processChunkAsync(target.getString(field))
      .onSuccess(safe -> target.put(field, safe))
      .mapEmpty();
  }

  @Override
  public void injectGatewaySystemPrompt(JsonObject requestBody, String gatewayPrompt) {
    if (gatewayPrompt == null || gatewayPrompt.isBlank()) return;

    JsonArray messages = requestBody.getJsonArray("messages");
    if (messages == null || messages.isEmpty()) return;

    JsonObject firstMessage = messages.getJsonObject(0);
    String role = firstMessage.getString("role");

    if ("system".equals(role) || "developer".equals(role)) {
      String oldContent = firstMessage.getString("content", "");
      firstMessage.put("content", oldContent + "\n\n[Gateway Note]: " + gatewayPrompt);
    } else {
      JsonObject sysMsg = new JsonObject()
        .put("role", "system")
        .put("content", "[Gateway Note]: " + gatewayPrompt);
      messages.add(0, sysMsg);
    }
  }

  private Future<Void> redactField(JsonObject obj, String field, String sessionId, TextAnalyzer analyzer) {
    String text = obj.getString(field);
    if (text != null && !text.isBlank()) {
      return analyzer.anonymizeText(text, sessionId)
        .onSuccess(safeText -> obj.put(field, safeText))
        .mapEmpty();
    }
    return Future.succeededFuture();
  }

  private void restoreField(JsonObject obj, String field, String sessionId, TextAnalyzer analyzer) {
    String text = obj.getString(field);
    if (text != null && !text.isBlank()) {
      obj.put(field, analyzer.restoreText(text, sessionId, field));
    }
  }

  private Future<Void> restoreFieldAsync(JsonObject obj, String field, String sessionId, TextAnalyzer analyzer) {
    String text = obj.getString(field);
    if (text == null || text.isBlank()) return Future.succeededFuture();
    return analyzer.restoreTextAsync(text, sessionId, field)
      .onSuccess(restored -> obj.put(field, restored))
      .mapEmpty();
  }
}

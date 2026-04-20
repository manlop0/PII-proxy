package com.project.piiproxy.server.handler;

import com.project.piiproxy.provider.LlmProvider;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.core.buffer.Buffer;

public interface LlmRequestHandler {
  /**
   * @param ctx - Context of the client's initial request
   * @param httpRequest - A prepared request to the LLM API (with the necessary headers)
   * @param requestBody - JSON request body
   * @param sessionId - Session ID (transmitted or generated)
   * @param provider - Target provider (OpenAI, OpenRouter, etc.)
   * @param targetPath - API path (e.g., /v1/chat/completions)
   */
  void handle(RoutingContext ctx,
              JsonObject requestBody,
              String sessionId,
              boolean isEphemeral,
              LlmProvider provider,
              String targetPath);
}

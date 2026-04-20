package com.project.piiproxy.server.handler;

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
   * @param isEphemeral - Session flag (should the cache be cleared at the end)
   */
  void handle(RoutingContext ctx,
              HttpRequest<Buffer> httpRequest,
              JsonObject requestBody,
              String sessionId,
              boolean isEphemeral);
}

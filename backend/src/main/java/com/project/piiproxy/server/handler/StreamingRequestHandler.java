package com.project.piiproxy.server.handler;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.codec.BodyCodec;

public class StreamingRequestHandler implements LlmRequestHandler {

  @Override
  public void handle(RoutingContext ctx, HttpRequest<Buffer> httpRequest, JsonObject requestBody, String sessionId, boolean isEphemeral) {

    // TODO: PiiRedactor.redact(requestBody, sessionId)

    ctx.response().setChunked(true);

    httpRequest.as(BodyCodec.pipe(ctx.response()))
      .sendJsonObject(requestBody)
      .onSuccess(response -> {
        System.out.println("Streaming finished. Status: " + response.statusCode());
        cleanupIfEphemeral(sessionId, isEphemeral);
      })
      .onFailure(err -> {
        System.err.println("Streaming error: " + err.getMessage());
        if (!ctx.response().ended()) {
          ctx.response().end();
        }
      });
  }

  private void cleanupIfEphemeral(String sessionId, boolean isEphemeral) {
    if (isEphemeral) {
      System.out.println("Cleaning up ephemeral session: " + sessionId);
      // TODO: Remove sessionId from MapDB
    }
  }
}

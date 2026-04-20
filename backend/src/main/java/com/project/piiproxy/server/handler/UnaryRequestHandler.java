package com.project.piiproxy.server.handler;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;

public class UnaryRequestHandler implements LlmRequestHandler {

  @Override
  public void handle(RoutingContext ctx, HttpRequest<Buffer> httpRequest, JsonObject requestBody, String sessionId, boolean isEphemeral) {

    // TODO: PiiRedactor.redact(requestBody, sessionId)

    httpRequest.sendJsonObject(requestBody).onSuccess(response -> {
      MultiMap responseHeaders = response.headers();
      responseHeaders.remove("Transfer-Encoding"); // Защита протокола

      // TODO: PiiRedactor.restore(response.bodyAsJsonObject(), sessionId)

      ctx.response().headers().addAll(responseHeaders);
      ctx.response()
        .setStatusCode(response.statusCode())
        .end(response.bodyAsBuffer());

      cleanupIfEphemeral(sessionId, isEphemeral);

    }).onFailure(err -> failRequest(ctx, err));
  }

  private void cleanupIfEphemeral(String sessionId, boolean isEphemeral) {
    if (isEphemeral) {
      System.out.println("Cleaning up ephemeral session: " + sessionId);
      // TODO: remove sessionId from MapDB
    }
  }

  private void failRequest(RoutingContext ctx, Throwable err) {
    System.err.println("Unary upstream error: " + err.getMessage());
    if (!ctx.response().ended()) {
      ctx.response().setStatusCode(502).end("{\"error\": \"Bad Gateway\"}");
    }
  }
}

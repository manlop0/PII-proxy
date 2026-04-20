package com.project.piiproxy.server.handler;

import com.project.piiproxy.pipeline.core.RequestAnonymizer;
import com.project.piiproxy.pipeline.core.UnaryResponseRestorer;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;

public class UnaryRequestHandler implements LlmRequestHandler {

  private final RequestAnonymizer anonymizer;
  private final UnaryResponseRestorer restorer;

  public UnaryRequestHandler(RequestAnonymizer anonymizer, UnaryResponseRestorer restorer) {
    this.anonymizer = anonymizer;
    this.restorer = restorer;
  }

  @Override
  public void handle(RoutingContext ctx, HttpRequest<Buffer> httpRequest, JsonObject requestBody, String sessionId, boolean isEphemeral) {

    anonymizer.redactRequest(requestBody, sessionId);

    httpRequest.sendJsonObject(requestBody).onSuccess(response -> {
      MultiMap responseHeaders = response.headers();
      responseHeaders.remove("Transfer-Encoding");

      JsonObject responseJson = response.bodyAsJsonObject();
      restorer.restoreResponse(responseJson, sessionId);

      ctx.response().headers().addAll(responseHeaders);
      ctx.response()
        .setStatusCode(response.statusCode())
        .end(responseJson.toBuffer());

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

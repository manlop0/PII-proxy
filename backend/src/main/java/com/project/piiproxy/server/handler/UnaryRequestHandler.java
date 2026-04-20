package com.project.piiproxy.server.handler;

import com.project.piiproxy.pipeline.core.RequestAnonymizer;
import com.project.piiproxy.pipeline.core.UnaryResponseRestorer;
import com.project.piiproxy.pipeline.state.SessionCleaner;
import com.project.piiproxy.provider.LlmProvider;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;


public class UnaryRequestHandler implements LlmRequestHandler {

  private final RequestAnonymizer anonymizer;
  private final UnaryResponseRestorer restorer;
  private final HttpClient httpClient;
  private final SessionCleaner sessionCleaner;

  public UnaryRequestHandler(RequestAnonymizer anonymizer, UnaryResponseRestorer restorer, HttpClient httpClient, SessionCleaner sessionCleaner) {
    this.anonymizer = anonymizer;
    this.restorer = restorer;
    this.httpClient = httpClient;
    this.sessionCleaner = sessionCleaner;
  }

  @Override
  public void handle(RoutingContext ctx, JsonObject requestBody, String sessionId, boolean isEphemeral, LlmProvider provider, String targetPath) {

    anonymizer.redactRequest(requestBody, sessionId);

    MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
      .addAll(ctx.request().headers())
      .remove("Host")
      .remove("Content-Length")
      .remove("Accept-Encoding");

    RequestOptions options = new RequestOptions()
      .setMethod(HttpMethod.POST)
      .setHost(provider.getHost())
      .setPort(provider.getPort())
      .setURI(targetPath)
      .setSsl(provider.getPort() == 443);

    httpClient.request(options).compose(request -> {
        request.headers().addAll(requestHeaders);
        return request.send(requestBody.toBuffer());
      })
      .onSuccess(response -> {
      MultiMap responseHeaders = response.headers();
      responseHeaders
        .remove("Transfer-Encoding")
        .remove("Content-Length")
        .remove("Accept-Encoding");

        response.body().onComplete(ar -> {
          if (ar.succeeded()) {
            try {
              JsonObject responseJson = ar.result().toJsonObject();
              restorer.restoreResponse(responseJson, sessionId);

              ctx.response().headers().addAll(responseHeaders);
              ctx.response()
                .setStatusCode(response.statusCode())
                .end(responseJson.toBuffer());

            } catch (Exception e) {
              System.err.println("JSON Parse Error: " + e.getMessage());
              if (!ctx.response().ended()) {
                ctx.response().setStatusCode(502).end("{\"error\": \"Bad Gateway Response (Parse Error)\"}");
              }
            }
          } else {
            System.err.println("Failed to read full response body: " + ar.cause().getMessage());
            if (!ctx.response().ended()) {
              ctx.response().setStatusCode(502).end("{\"error\": \"Bad Gateway Response (Read Error)\"}");
            }
          }

          cleanupIfEphemeral(sessionId, isEphemeral);
        });
    }).onFailure(err -> failRequest(ctx, err));
  }

  private void cleanupIfEphemeral(String sessionId, boolean isEphemeral) {
    if (isEphemeral) {
      System.out.println("Cleaning up ephemeral session: " + sessionId);
      sessionCleaner.clearSession(sessionId);
    }
  }

  private void failRequest(RoutingContext ctx, Throwable err) {
    System.err.println("Unary upstream error: " + err.getMessage());
    if (!ctx.response().ended()) {
      ctx.response().setStatusCode(502).end("{\"error\": \"Bad Gateway\"}");
    }
  }
}

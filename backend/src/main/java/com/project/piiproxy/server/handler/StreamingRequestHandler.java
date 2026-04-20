package com.project.piiproxy.server.handler;

import com.project.piiproxy.pipeline.core.RequestAnonymizer;
import com.project.piiproxy.pipeline.core.StreamingResponseRestorer;
import com.project.piiproxy.pipeline.state.SessionCleaner;
import com.project.piiproxy.provider.LlmProvider;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class StreamingRequestHandler implements LlmRequestHandler {

  private final RequestAnonymizer anonymizer;
  private final StreamingResponseRestorer restorer;
  private final HttpClient httpClient;
  private final SessionCleaner sessionCleaner;

  public StreamingRequestHandler(RequestAnonymizer anonymizer, StreamingResponseRestorer restorer, HttpClient httpClient, SessionCleaner sessionCleaner) {
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

    ctx.response().setChunked(true);
    ctx.response().putHeader("Content-Type", "text/event-stream");
    ctx.response().putHeader("Cache-Control", "no-cache");

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
        Handler<Buffer> streamHandler = restorer.createStreamHandler(ctx, sessionId);
        response.handler(streamHandler);

        response.endHandler(v -> {
          ctx.response().end();
          cleanupIfEphemeral(sessionId, isEphemeral);
        });

        response.exceptionHandler(err -> {
          System.err.println("Stream interrupted: " + err.getMessage());
          if (!ctx.response().ended()) ctx.response().end();
          cleanupIfEphemeral(sessionId, isEphemeral);
        });
      })
      .onFailure(err -> {
        System.err.println("Streaming error: " + err.getMessage());
        if (!ctx.response().ended()) {
          ctx.response().end();
        }
        cleanupIfEphemeral(sessionId, isEphemeral);
      });
  }

  private void cleanupIfEphemeral(String sessionId, boolean isEphemeral) {
    if (isEphemeral) {
      System.out.println("Cleaning up ephemeral session: " + sessionId);
      sessionCleaner.clearSession(sessionId);
    }
  }
}

package com.project.piiproxy.server.handler;

import com.project.piiproxy.pipeline.anonymize.TextAnalyzer;
import com.project.piiproxy.pipeline.restore.StreamingResponseRestorer;
import com.project.piiproxy.pipeline.state.SessionCleaner;
import com.project.piiproxy.provider.LlmEndpoint;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles SSE streaming requests: anonymizes the body, opens an upstream SSE channel, and restores chunks as they arrive. */
public class StreamingRequestHandler implements LlmRequestHandler {

  private static final Logger log = LoggerFactory.getLogger(StreamingRequestHandler.class);

  private final TextAnalyzer analyzer;
  private final String gatewaySystemPrompt;
  private final StreamingResponseRestorer restorer;
  private final HttpClient httpClient;
  private final SessionCleaner sessionCleaner;

  public StreamingRequestHandler(TextAnalyzer analyzer, String gatewaySystemPrompt, StreamingResponseRestorer restorer, HttpClient httpClient, SessionCleaner sessionCleaner) {
    this.analyzer = analyzer;
    this.gatewaySystemPrompt = gatewaySystemPrompt;
    this.restorer = restorer;
    this.httpClient = httpClient;
    this.sessionCleaner = sessionCleaner;
  }

  @Override
  public void handle(RoutingContext ctx, JsonObject requestBody, String sessionId, boolean isEphemeral,               LlmEndpoint provider, String targetPath) {

    log.debug("Incoming streaming request to provider '{}', session '{}', ephemeral: {}", provider.getId(), sessionId, isEphemeral);

    provider.getCodec().redactRequest(requestBody, sessionId, analyzer)
      .map(v -> {
        if (gatewaySystemPrompt != null && !gatewaySystemPrompt.isBlank()) {
          provider.getCodec().injectGatewaySystemPrompt(requestBody, gatewaySystemPrompt);
        }
        return null;
      }).compose(v -> {
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

        return httpClient.request(options).compose(request -> {
          request.headers().addAll(requestHeaders);
          return request.send(requestBody.toBuffer());
        });
      })
      .onSuccess(response -> {
        Handler<Buffer> streamHandler = restorer.createStreamHandler(ctx, sessionId, provider.getCodec());
        response.handler(streamHandler);

        response.endHandler(v -> {
          ctx.response().end();
          cleanupIfEphemeral(sessionId, isEphemeral);
        });

        response.exceptionHandler(err -> {
          log.warn("Stream interrupted: {}", err.getMessage());
          if (!ctx.response().ended()) ctx.response().end();
          cleanupIfEphemeral(sessionId, isEphemeral);
        });
      })
      .onFailure(err -> {
        log.error("Streaming error: {}", err.getMessage());
        if (!ctx.response().ended()) {
          ctx.response().end();
        }
        cleanupIfEphemeral(sessionId, isEphemeral);
      });
  }

  private void cleanupIfEphemeral(String sessionId, boolean isEphemeral) {
    if (isEphemeral) {
      log.debug("Cleaning up ephemeral session: {}", sessionId);
      sessionCleaner.clearSession(sessionId);
    }
  }
}

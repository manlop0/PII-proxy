package com.project.piiproxy.config;

import com.project.piiproxy.provider.LlmEndpoint;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.handler.LlmRequestHandler;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Builds the HTTP router for the PII proxy. Routes {@code POST /:provider/*} requests to the
 * appropriate {@link LlmRequestHandler} (unary or streaming) based on the {@code stream} flag in the body.
 * Resolves the session id from {@code X-Conversation-Id} header, then {@code user} field, then a random ephemeral UUID.
 */
public class ProviderRouter {

  private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

  private final Vertx vertx;
  private final ProviderRegistry registry;
  private final LlmRequestHandler unaryHandler;
  private final LlmRequestHandler streamingHandler;

  public ProviderRouter(Vertx vertx,
                        ProviderRegistry registry,
                        LlmRequestHandler unaryHandler,
                        LlmRequestHandler streamingHandler) {
    this.vertx = vertx;
    this.registry = registry;
    this.unaryHandler = unaryHandler;
    this.streamingHandler = streamingHandler;
  }

  public Router build() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.get("/health").handler(ctx ->
      ctx.response().setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end("{\"status\": \"UP\"}"));

    router.get("/ready").handler(ctx ->
      ctx.response().setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end("{\"status\": \"READY\"}"));

    router.post("/:provider/*").handler(this::handle);
    return router;
  }

  private void handle(RoutingContext ctx) {
    LlmEndpoint provider = registry.getProvider(ctx.pathParam("provider"));

    if (provider == null) {
      ctx.response().setStatusCode(400).putHeader("Content-Type", "application/json")
        .end("{\"error\": \"Unknown LLM provider. Check the URL.\"}");
      return;
    }

    JsonObject requestBody;
    try {
      requestBody = ctx.body().asJsonObject();
    } catch (DecodeException e) {
      ctx.response().setStatusCode(400).end("{\"error\": \"Invalid JSON\"}");
      return;
    }

    String sessionId = ctx.request().getHeader("X-Conversation-Id");
    boolean isEphemeral = false;

    if (sessionId == null || sessionId.isBlank()) {
      sessionId = requestBody.getString("user");
    }

    if (sessionId == null || sessionId.isBlank()) {
      sessionId = UUID.randomUUID().toString();
      isEphemeral = true;
      log.warn("No Session ID found in Headers or JSON. Using Ephemeral ID: {}", sessionId);
    }

    String targetPath = ctx.request().path().substring(("/" + provider.getId()).length());
    boolean isStream = requestBody.getBoolean("stream", false);

    if (isStream) {
      streamingHandler.handle(ctx, requestBody, sessionId, isEphemeral, provider, targetPath);
    } else {
      unaryHandler.handle(ctx, requestBody, sessionId, isEphemeral, provider, targetPath);
    }
  }
}

package com.project.piiproxy.server;

import com.project.piiproxy.provider.LlmProvider;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.handler.LlmRequestHandler;
import com.project.piiproxy.server.handler.StreamingRequestHandler;
import com.project.piiproxy.server.handler.UnaryRequestHandler;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.UUID;

public class ProxyServerVerticle extends VerticleBase {
  private final ProviderRegistry registry;
  private WebClient webClient;

  private final LlmRequestHandler unaryHandler = new UnaryRequestHandler();
  private final LlmRequestHandler streamingHandler = new StreamingRequestHandler();

  public ProxyServerVerticle(ProviderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Future<?> start() {
    this.webClient = WebClient.create(vertx, new WebClientOptions().setKeepAlive(true));

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.post("/:provider/*").handler(ctx -> {
      LlmProvider provider = registry.getProvider(ctx.pathParam("provider"));

      if (provider == null) {
        ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
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
        sessionId = UUID.randomUUID().toString();
        isEphemeral = true;
        System.out.println("WARN: Missing X-Conversation-Id. Generated ephemeral ID: " + sessionId);
      }

      String targetPath = ctx.request().path().substring(("/" + provider.getId()).length());
      MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
        .addAll(ctx.request().headers())
        .remove("Host");

      var httpRequest = webClient.post(provider.getPort(), provider.getHost(), targetPath)
        .ssl(provider.getPort() == 443)
        .putHeaders(requestHeaders);

      boolean isStream = requestBody.getBoolean("stream", false);
      if (isStream) {
        streamingHandler.handle(ctx, httpRequest, requestBody, sessionId, isEphemeral);
      } else {
        unaryHandler.handle(ctx, httpRequest, requestBody, sessionId, isEphemeral);
      }
    });

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080)
      .onSuccess(server -> System.out.println("Proxy running on port: " + server.actualPort()));
  };
}

package com.project.piiproxy.server;

import com.project.piiproxy.pipeline.core.RequestAnonymizer;
import com.project.piiproxy.pipeline.core.StreamingResponseRestorer;
import com.project.piiproxy.pipeline.core.TextAnalyzer;
import com.project.piiproxy.pipeline.core.UnaryResponseRestorer;
import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.filter.regex.CreditCardFilter;
import com.project.piiproxy.pipeline.filter.regex.EmailFilter;
import com.project.piiproxy.pipeline.filter.regex.IpAddressFilter;
import com.project.piiproxy.pipeline.filter.regex.PhoneFilter;
import com.project.piiproxy.pipeline.state.MapDbStorage;
import com.project.piiproxy.pipeline.state.PiiStorage;
import com.project.piiproxy.provider.LlmProvider;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.handler.LlmRequestHandler;
import com.project.piiproxy.server.handler.StreamingRequestHandler;
import com.project.piiproxy.server.handler.UnaryRequestHandler;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.UUID;

public class ProxyServerVerticle extends VerticleBase {
  private final ProviderRegistry registry;

  public ProxyServerVerticle(ProviderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Future<?> start() {

    HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true));

    PiiStorage storage = new MapDbStorage();

    List<TextFilter> filters = List.of(
      new EmailFilter(),
      new PhoneFilter(),
      new CreditCardFilter(),
      new IpAddressFilter()
    );

    TextAnalyzer analyzer = new TextAnalyzer(storage, filters);

    RequestAnonymizer requestAnonymizer = new RequestAnonymizer(analyzer);
    UnaryResponseRestorer unaryRestorer = new UnaryResponseRestorer(analyzer);
    StreamingResponseRestorer streamingRestorer = new StreamingResponseRestorer(analyzer);

    LlmRequestHandler unaryHandler = new UnaryRequestHandler(requestAnonymizer, unaryRestorer, httpClient);
    LlmRequestHandler streamingHandler = new StreamingRequestHandler(requestAnonymizer, streamingRestorer, httpClient);


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

      boolean isStream = requestBody.getBoolean("stream", false);
      if (isStream) {
        streamingHandler.handle(ctx, requestBody, sessionId, isEphemeral, provider, targetPath);
      } else {
        unaryHandler.handle(ctx, requestBody, sessionId, isEphemeral, provider, targetPath);
      }
    });

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080)
      .onSuccess(server -> System.out.println("Proxy running on port: " + server.actualPort()));
  };
}

package com.project.piiproxy.config;

import com.project.piiproxy.pipeline.core.RequestAnonymizer;
import com.project.piiproxy.pipeline.core.StreamingResponseRestorer;
import com.project.piiproxy.pipeline.core.TextAnalyzer;
import com.project.piiproxy.pipeline.core.UnaryResponseRestorer;
import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.filter.regex.CreditCardFilter;
import com.project.piiproxy.pipeline.filter.regex.EmailFilter;
import com.project.piiproxy.pipeline.filter.regex.IpAddressFilter;
import com.project.piiproxy.pipeline.filter.regex.PhoneFilter;
import com.project.piiproxy.pipeline.state.LoggingStorageDecorator;
import com.project.piiproxy.pipeline.state.MapDbStorage;
import com.project.piiproxy.pipeline.state.PiiStorage;
import com.project.piiproxy.pipeline.state.SessionCleaner;
import com.project.piiproxy.provider.LlmProvider;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.handler.LlmRequestHandler;
import com.project.piiproxy.server.handler.StreamingRequestHandler;
import com.project.piiproxy.server.handler.UnaryRequestHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AppConfigurator {
  public static Router configureRouter(Vertx vertx, JsonObject config, ProviderRegistry registry) {

    HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true));

    JsonObject storageConfig = config.getJsonObject("storage", new JsonObject());
    String dbPath = storageConfig.getString("path", "./data/pii-cache.db");
    MapDbStorage baseStorage = new MapDbStorage(dbPath);

    JsonObject debugConfig = config.getJsonObject("debug", new JsonObject());
    boolean logMappings = debugConfig.getBoolean("log_mappings", false);

    JsonObject pipelineConfig = config.getJsonObject("pipeline", new JsonObject());
    String systemPrompt = pipelineConfig.getString("gateway_system_prompt", "");

    List<TextFilter> filters = new ArrayList<>();
    JsonObject filtersConfig = pipelineConfig.getJsonObject("filters", new JsonObject());

    if (filtersConfig.getBoolean("email", true)) filters.add(new EmailFilter());
    if (filtersConfig.getBoolean("phone", true)) filters.add(new PhoneFilter());
    if (filtersConfig.getBoolean("credit_card", true)) filters.add(new CreditCardFilter());
    if (filtersConfig.getBoolean("ip_address", true)) filters.add(new IpAddressFilter());

    PiiStorage storage = logMappings ? new LoggingStorageDecorator(baseStorage) : baseStorage;
    SessionCleaner sessionCleaner = baseStorage;

    TextAnalyzer analyzer = new TextAnalyzer(storage, filters);

    RequestAnonymizer anonymizer = new RequestAnonymizer(analyzer, systemPrompt);
    UnaryResponseRestorer unaryRestorer = new UnaryResponseRestorer(analyzer);
    StreamingResponseRestorer streamingRestorer = new StreamingResponseRestorer(analyzer);

    LlmRequestHandler unaryHandler = new UnaryRequestHandler(anonymizer, unaryRestorer, httpClient, sessionCleaner);
    LlmRequestHandler streamingHandler = new StreamingRequestHandler(anonymizer, streamingRestorer, httpClient, sessionCleaner);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.post("/:provider/*").handler(ctx -> {
      LlmProvider provider = registry.getProvider(ctx.pathParam("provider"));

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
        System.out.println("WARN: No Session ID found in Headers or JSON. Using Ephemeral ID: " + sessionId);
      }

      String targetPath = ctx.request().path().substring(("/" + provider.getId()).length());
      boolean isStream = requestBody.getBoolean("stream", false);

      if (isStream) {
        streamingHandler.handle(ctx, requestBody, sessionId, isEphemeral, provider, targetPath);
      } else {
        unaryHandler.handle(ctx, requestBody, sessionId, isEphemeral, provider, targetPath);
      }
    });

    return router;
  }
}

package com.project.piiproxy.config;

import com.project.piiproxy.pipeline.core.RequestAnonymizer;
import com.project.piiproxy.pipeline.core.StreamingResponseRestorer;
import com.project.piiproxy.pipeline.core.TextAnalyzer;
import com.project.piiproxy.pipeline.core.UnaryResponseRestorer;
import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.filter.ml.NerModelFilter;
import com.project.piiproxy.pipeline.filter.regex.CreditCardFilter;
import com.project.piiproxy.pipeline.filter.regex.EmailFilter;
import com.project.piiproxy.pipeline.filter.regex.IpAddressFilter;
import com.project.piiproxy.pipeline.filter.regex.PhoneFilter;
import com.project.piiproxy.pipeline.model.ConflictStrategy;
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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.*;

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
    String strategyString = pipelineConfig.getString("conflict_strategy", "LONGEST_WINS");

    ConflictStrategy strategy;
    try {
      strategy = ConflictStrategy.valueOf(strategyString.toUpperCase());
    } catch (IllegalArgumentException e) {
      System.err.println("WARN: Invalid conflict_strategy '" + strategyString + "' in config. Defaulting to LONGEST_WINS.");
      strategy = ConflictStrategy.LONGEST_WINS;
    }

    List<TextFilter> regexFilters = new ArrayList<>();
    JsonObject filtersConfig = pipelineConfig.getJsonObject("filters", new JsonObject());

    if (filtersConfig.getBoolean("email", true)) regexFilters.add(new EmailFilter());
    if (filtersConfig.getBoolean("phone", true)) regexFilters.add(new PhoneFilter());
    if (filtersConfig.getBoolean("credit_card", true)) regexFilters.add(new CreditCardFilter());
    if (filtersConfig.getBoolean("ip_address", true)) regexFilters.add(new IpAddressFilter());


    PiiStorage storage = logMappings ? new LoggingStorageDecorator(baseStorage) : baseStorage;
    SessionCleaner sessionCleaner = baseStorage;

    TextAnalyzer analyzer = new TextAnalyzer(vertx, storage, regexFilters, strategy);

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

  public static NerModelFilter createMlFilter(JsonObject config) throws Exception {
    JsonObject mlConfig = config.getJsonObject("ml", new JsonObject());
    String modelDir = mlConfig.getString("model_directory");
    int mlThreads = mlConfig.getInteger("intra_op_threads", 1);

    List<String> ignoredTagsList = mlConfig.getJsonArray("ignored_tags", new JsonArray()).getList();
    Set<String> ignoredTags = new HashSet<>(ignoredTagsList);

    try {
      return new NerModelFilter(modelDir, mlThreads, ignoredTags);
    } catch (Exception e) {
      throw new RuntimeException("CRITICAL: Failed to load ML Pipeline from " + modelDir, e);
    }
  }
}

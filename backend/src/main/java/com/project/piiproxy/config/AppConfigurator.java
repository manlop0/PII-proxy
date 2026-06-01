package com.project.piiproxy.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.project.piiproxy.pipeline.anonymize.TextAnalyzer;
import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.filter.ml.NerModelFilter;
import com.project.piiproxy.pipeline.model.ConflictStrategy;
import com.project.piiproxy.pipeline.restore.StreamingResponseRestorer;
import com.project.piiproxy.pipeline.state.MapDbStorage;
import com.project.piiproxy.pipeline.state.PiiStorage;
import com.project.piiproxy.pipeline.state.SessionCleaner;
import com.project.piiproxy.pipeline.state.resolution.EntityResolutionStrategy;
import com.project.piiproxy.pipeline.state.resolution.ExactMatchResolutionStrategy;
import com.project.piiproxy.pipeline.state.resolution.ResolutionStrategyFactory;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.handler.LlmRequestHandler;
import com.project.piiproxy.server.handler.StreamingRequestHandler;
import com.project.piiproxy.server.handler.UnaryRequestHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Application bootstrap and dependency-injection container.
 * Wires together the HTTP server, ML filter, storage, and provider registry from {@code config.yaml}.
 */
public class AppConfigurator {

  private static final Logger log = LoggerFactory.getLogger(AppConfigurator.class);

  public static Router configureRouter(Vertx vertx, JsonObject config, ProviderRegistry registry) {
    return configureRouter(vertx, config, registry, null);
  }

  public static Router configureRouter(Vertx vertx, JsonObject config, ProviderRegistry registry, Consumer<PiiStorage> storageSink) {

    HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true));

    JsonObject pipelineConfig = config.getJsonObject("pipeline", new JsonObject());

    EntityResolutionStrategy resolutionStrategy = buildResolutionStrategy(pipelineConfig);
    configureLogLevel(config);

    String systemPrompt = pipelineConfig.getString("gateway_system_prompt", "");
    ConflictStrategy strategy = parseConflictStrategy(pipelineConfig);

    List<TextFilter> regexFilters = new FilterChainFactory().build(pipelineConfig);

    MapDbStorage baseStorage = buildStorage(config, resolutionStrategy);
    if (storageSink != null) {
      storageSink.accept(baseStorage);
    }
    PiiStorage storage = baseStorage;
    SessionCleaner sessionCleaner = baseStorage;

    TextAnalyzer analyzer = new TextAnalyzer(vertx, storage, regexFilters, strategy);
    StreamingResponseRestorer streamingRestorer = new StreamingResponseRestorer(analyzer);

    LlmRequestHandler unaryHandler = new UnaryRequestHandler(analyzer, systemPrompt, httpClient, sessionCleaner);
    LlmRequestHandler streamingHandler = new StreamingRequestHandler(analyzer, systemPrompt, streamingRestorer, httpClient, sessionCleaner);

    return new ProviderRouter(vertx, registry, unaryHandler, streamingHandler).build();
  }

  private static EntityResolutionStrategy buildResolutionStrategy(JsonObject pipelineConfig) {
    JsonObject resolutionConfig = pipelineConfig.getJsonObject("entity_resolution", new JsonObject());
    boolean resolutionEnabled = resolutionConfig.getBoolean("enabled", true);

    if (!resolutionEnabled) {
      log.info("Entity Resolution Strategy: Exact Match Only (Fuzzy matching disabled)");
      return new ExactMatchResolutionStrategy();
    }

    String algo = resolutionConfig.getString("algorithm", "jaro-winkler");
    double threshold = resolutionConfig.getDouble("threshold", 0.88);
    List<String> typesList = resolutionConfig.getJsonArray("fuzzy_types", new JsonArray()).getList();
    Set<String> fuzzyTypes = new HashSet<>(typesList);

    log.info("Entity Resolution Strategy: {} (threshold: {}, fuzzy_types: {})", algo, threshold, fuzzyTypes);
    return ResolutionStrategyFactory.create(algo, threshold, fuzzyTypes);
  }

  private static void configureLogLevel(JsonObject config) {
    JsonObject loggingConfig = config.getJsonObject("logging", new JsonObject());
    String logLevel = loggingConfig.getString("level", "INFO");
    try {
      LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
      ch.qos.logback.classic.Logger appLogger = loggerContext.getLogger("com.project.piiproxy");
      appLogger.setLevel(Level.valueOf(logLevel.toUpperCase()));
    } catch (Exception e) {
      log.warn("Could not set log level programmatically. Ensure logback-classic is used.", e);
    }
  }

  private static ConflictStrategy parseConflictStrategy(JsonObject pipelineConfig) {
    String strategyString = pipelineConfig.getString("conflict_strategy", "LONGEST_WINS");
    try {
      return ConflictStrategy.valueOf(strategyString.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid conflict_strategy '{}' in config. Defaulting to LONGEST_WINS.", strategyString);
      return ConflictStrategy.LONGEST_WINS;
    }
  }

  private static MapDbStorage buildStorage(JsonObject config, EntityResolutionStrategy resolutionStrategy) {
    JsonObject storageConfig = config.getJsonObject("storage", new JsonObject());
    String dbPath = storageConfig.getString("path", "./data/pii-cache.db");
    return new MapDbStorage(dbPath, resolutionStrategy);
  }

  public static NerModelFilter createMlFilter(JsonObject config) throws Exception {
    JsonObject mlConfig = config.getJsonObject("ml", new JsonObject());
    String modelDir = mlConfig.getString("model_directory");
    int mlThreads = mlConfig.getInteger("intra_op_threads", 1);
    String adapterType = mlConfig.getString("output_adapter", "BIO");

    List<String> ignoredTagsList = mlConfig.getJsonArray("ignored_tags", new JsonArray()).getList();
    Set<String> ignoredTags = new HashSet<>(ignoredTagsList);

    Map<String, String> tagMapping = new HashMap<>();
    JsonObject mappingJson = mlConfig.getJsonObject("tag_mapping");
    if (mappingJson != null) {
      for (String key : mappingJson.fieldNames()) {
        tagMapping.put(key, mappingJson.getString(key));
      }
    }

    try {
      return new NerModelFilter(modelDir, mlThreads, ignoredTags, tagMapping, adapterType);
    } catch (Exception e) {
      throw new RuntimeException("CRITICAL: Failed to load ML Pipeline from " + modelDir, e);
    }
  }
}

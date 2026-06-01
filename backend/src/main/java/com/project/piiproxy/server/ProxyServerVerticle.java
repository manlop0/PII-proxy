package com.project.piiproxy.server;

import com.project.piiproxy.config.AppConfigurator;
import com.project.piiproxy.pipeline.state.PiiStorage;
import com.project.piiproxy.pipeline.worker.MlBatchAggregatorVerticle;
import com.project.piiproxy.provider.ProviderRegistry;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HTTP entry point. Deploys the batch aggregator, configures the router, and dispatches to unary or streaming handlers. */
public class ProxyServerVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(ProxyServerVerticle.class);

  private final ProviderRegistry registry;
  private HttpServer server;
  private PiiStorage storage;

  public ProxyServerVerticle(ProviderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Future<?> start() {

    ConfigStoreOptions fileStore = new ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(new JsonObject().put("path", "config.yaml"));

    ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(fileStore);
    ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

    return retriever.getConfig()
      .compose(config -> vertx.executeBlocking(() -> {
          log.info("Loading ONNX Model into Native Memory...");
          return AppConfigurator.createMlFilter(config);
        })

        .compose(globalMlFilter -> {
          JsonObject mlConfig = config.getJsonObject("ml", new JsonObject());
          int batchSize = mlConfig.getInteger("batch_size", 16);
          int batchTimeout = mlConfig.getInteger("batch_timeout_ms", 10);
          int maxQueueSize = mlConfig.getInteger("max_queue_size", 1000);

          log.info("Deploying ML Aggregator Verticle (Batch Size: {}, Timeout: {}ms, Max Queue: {})...",
            batchSize, batchTimeout, maxQueueSize);

          return vertx.deployVerticle(new MlBatchAggregatorVerticle(globalMlFilter, batchSize, batchTimeout, maxQueueSize));
        })

        .compose(deploymentId -> {
          log.info("Configuring HTTP Router...");
          Router router = AppConfigurator.configureRouter(vertx, config, registry, s -> this.storage = s);

          int port = config.getJsonObject("server", new JsonObject()).getInteger("port", 8080);

          return vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onSuccess(s -> {
              this.server = s;
              log.info("Gateway running on port: {}", s.actualPort());
            });
        }));
  }

  @Override
  public Future<?> stop() {
    Future<?> chain = server != null
      ? server.close()
      : Future.succeededFuture();

    if (storage != null) {
      chain = chain.onComplete(v -> {
        try {
          storage.close();
        } catch (Exception e) {
          log.error("Error closing PII storage", e);
        }
      });
    }

    return chain;
  }
}

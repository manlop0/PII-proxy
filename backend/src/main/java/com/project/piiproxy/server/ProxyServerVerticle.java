package com.project.piiproxy.server;

import com.project.piiproxy.config.AppConfigurator;
import com.project.piiproxy.pipeline.worker.MlWorkerVerticle;
import com.project.piiproxy.provider.ProviderRegistry;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServerVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(ProxyServerVerticle.class);

  private final ProviderRegistry registry;

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
          int workersCount = config.getJsonObject("ml", new JsonObject()).getInteger("worker_pool_size", 4);

          log.info("Deploying {} ML Worker Verticles...", workersCount);

          DeploymentOptions workerOpts = new DeploymentOptions()
            .setThreadingModel(ThreadingModel.WORKER)
            .setInstances(workersCount);

          return vertx.deployVerticle(() -> new MlWorkerVerticle(globalMlFilter), workerOpts);
        })

        .compose(deploymentId -> {
          log.info("Configuring HTTP Router...");
          Router router = AppConfigurator.configureRouter(vertx, config, registry);

          int port = config.getJsonObject("server", new JsonObject()).getInteger("port", 8080);

          return vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onSuccess(server -> log.info("Gateway running on port: {}", server.actualPort()));
        }));
  }
}

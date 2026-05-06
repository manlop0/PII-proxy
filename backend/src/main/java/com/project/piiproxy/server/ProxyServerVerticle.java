package com.project.piiproxy.server;

import com.project.piiproxy.config.AppConfigurator;
import com.project.piiproxy.provider.ProviderRegistry;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class ProxyServerVerticle extends VerticleBase {

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
      .compose(config -> {
        Router router = AppConfigurator.configureRouter(vertx, config, registry);

        int port = config.getJsonObject("server", new JsonObject()).getInteger("port", 8080);

        return vertx.createHttpServer()
          .requestHandler(router)
          .listen(port)
          .onSuccess(server -> System.out.println("Gateway running on port: " + server.actualPort()));
      });
  }

}

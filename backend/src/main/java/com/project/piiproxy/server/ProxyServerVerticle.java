package com.project.piiproxy.server;

import com.project.piiproxy.config.AppConfigurator;
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
import com.project.piiproxy.provider.LlmProvider;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.handler.LlmRequestHandler;
import com.project.piiproxy.server.handler.StreamingRequestHandler;
import com.project.piiproxy.server.handler.UnaryRequestHandler;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
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

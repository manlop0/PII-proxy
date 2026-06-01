package com.project.piiproxy;

import com.project.piiproxy.provider.OpenAiProvider;
import com.project.piiproxy.provider.OpenRouterProvider;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.ProxyServerVerticle;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry-point verticle: registers providers and deploys {@link ProxyServerVerticle}. */
public class MainVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    ProviderRegistry registry = new ProviderRegistry();

    registry.register(new OpenAiProvider());
    registry.register(new OpenRouterProvider());

    return vertx.deployVerticle(new ProxyServerVerticle(registry))
      .onSuccess(id -> log.info("System initialized. Deployment ID: {}", id));
  }
}

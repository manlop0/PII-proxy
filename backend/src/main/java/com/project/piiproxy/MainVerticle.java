package com.project.piiproxy;

import com.project.piiproxy.provider.OpenAiProvider;
import com.project.piiproxy.provider.OpenRouterProvider;
import com.project.piiproxy.provider.ProviderRegistry;
import com.project.piiproxy.server.ProxyServerVerticle;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;

public class MainVerticle extends VerticleBase {

  @Override
  public Future<?> start() {
    ProviderRegistry registry = new ProviderRegistry();

    registry.register(new OpenAiProvider());
    registry.register(new OpenRouterProvider());

    return vertx.deployVerticle(new ProxyServerVerticle(registry))
      .onSuccess(id -> System.out.println("System initialized. Deployment ID: " + id));
  }
}

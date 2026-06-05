package com.project.piiproxy;

import com.project.piiproxy.server.ProxyServerVerticle;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry-point verticle: deploys {@link ProxyServerVerticle}. */
public class MainVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    return vertx.deployVerticle(new ProxyServerVerticle())
      .onSuccess(id -> log.info("System initialized. Deployment ID: {}", id));
  }
}

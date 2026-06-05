package com.project.piiproxy.config;

import com.project.piiproxy.provider.LlmEndpoint;
import com.project.piiproxy.provider.codec.LlmJsonCodec;
import io.vertx.core.json.JsonObject;

/**
 * {@link LlmEndpoint} descriptor created from config.yaml provider entries.
 * The codec is resolved via {@link CodecFactory} — supports shortnames ("openai") and FQCN.
 */
public class ConfigurableProvider implements LlmEndpoint {

  private final String id;
  private final String host;
  private final int port;
  private final LlmJsonCodec codec;

  public ConfigurableProvider(String id, JsonObject config) {
    this.id = id;
    this.host = config.getString("host");
    this.port = config.getInteger("port", 443);
    this.codec = CodecFactory.create(config.getString("codec", "openai"));
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public LlmJsonCodec getCodec() {
    return codec;
  }
}

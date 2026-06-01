package com.project.piiproxy.provider;

import com.project.piiproxy.provider.adapter.LlmJsonCodec;
import com.project.piiproxy.provider.adapter.OpenAiAdapter;

/** {@link LlmEndpoint} descriptor for openrouter.ai. Reuses the OpenAI-compatible JSON codec. */
public class OpenRouterProvider implements LlmEndpoint {

  private final LlmJsonCodec codec = new OpenAiAdapter();

  @Override
  public String getId() {
    return "openrouter";
  }

  @Override
  public String getHost() {
    return "openrouter.ai";
  }

  @Override
  public int getPort() {
    return 443;
  }

  @Override
  public LlmJsonCodec getCodec() {
    return codec;
  }
}

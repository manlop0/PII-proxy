package com.project.piiproxy.provider;

import com.project.piiproxy.provider.adapter.LlmJsonCodec;
import com.project.piiproxy.provider.adapter.OpenAiAdapter;

/** {@link LlmEndpoint} descriptor for api.openai.com (HTTPS, port 443, OpenAI JSON format). */
public class OpenAiProvider implements LlmEndpoint {
  private final LlmJsonCodec codec = new OpenAiAdapter();

  @Override
  public String getId() {
    return "openai";
  }

  @Override
  public String getHost() {
    return "api.openai.com";
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

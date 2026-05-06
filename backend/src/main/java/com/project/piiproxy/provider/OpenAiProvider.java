package com.project.piiproxy.provider;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;
import com.project.piiproxy.provider.adapter.OpenAiAdapter;

public class OpenAiProvider implements LlmProvider {
  private final LlmJsonAdapter adapter = new OpenAiAdapter();

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
  public LlmJsonAdapter getAdapter() {
    return adapter;
  }
}

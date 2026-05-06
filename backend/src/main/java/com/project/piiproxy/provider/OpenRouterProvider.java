package com.project.piiproxy.provider;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;
import com.project.piiproxy.provider.adapter.OpenAiAdapter;

public class OpenRouterProvider implements LlmProvider {

  private final LlmJsonAdapter adapter = new OpenAiAdapter();

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
  public LlmJsonAdapter getAdapter() {
    return adapter;
  }
}

package com.project.piiproxy.provider;

public class OpenRouterProvider implements LlmProvider {
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
}

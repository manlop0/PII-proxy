package com.project.piiproxy.provider;

public class OpenAiProvider implements LlmProvider {
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
}

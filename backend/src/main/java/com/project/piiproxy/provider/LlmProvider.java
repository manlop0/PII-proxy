package com.project.piiproxy.provider;

public interface LlmProvider {
  String getId();

  String getHost();

  int getPort();
}

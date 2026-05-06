package com.project.piiproxy.provider;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;

public interface LlmProvider {

  String getId();

  String getHost();

  int getPort();

  LlmJsonAdapter getAdapter();
}

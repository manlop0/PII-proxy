package com.project.piiproxy.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProviderRegistry {
  private final Map<String, LlmProvider> providers = new ConcurrentHashMap<>();

  public void register(LlmProvider provider) {
    providers.put(provider.getId(), provider);
  }

  public LlmProvider getProvider(String id) {
    if (id == null || id.isBlank()) return null;
    return providers.get(id.toLowerCase());
  }
}

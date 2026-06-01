package com.project.piiproxy.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry mapping provider ids to {@link LlmEndpoint} instances (case-insensitive lookup). */
public class ProviderRegistry {
  private final Map<String, LlmEndpoint> providers = new ConcurrentHashMap<>();

  public void register(LlmEndpoint provider) {
    providers.put(provider.getId().toLowerCase(), provider);
  }

  public LlmEndpoint getProvider(String id) {
    if (id == null || id.isBlank()) return null;
    return providers.get(id.toLowerCase());
  }
}

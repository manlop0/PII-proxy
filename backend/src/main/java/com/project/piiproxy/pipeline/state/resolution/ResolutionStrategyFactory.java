package com.project.piiproxy.pipeline.state.resolution;

import java.util.Set;

/**
 * Factory for creating EntityResolutionStrategy instances based on configuration.
 * Uses a registry pattern to allow easy addition of new algorithms in the future
 * without modifying the core configurator.
 */
public class ResolutionStrategyFactory {

  public static EntityResolutionStrategy create(String algorithm, double threshold, Set<String> fuzzyTypes) {
    if (algorithm == null) {
      return new ExactMatchResolutionStrategy();
    }

    EntityResolutionStrategy baseStrategy;
    switch (algorithm.toLowerCase()) {
      case "jaro-winkler":
        baseStrategy = new JaroWinklerResolutionStrategy(threshold);
        break;
      case "exact":
      default:
        return new ExactMatchResolutionStrategy();
    }

    if (fuzzyTypes == null || fuzzyTypes.isEmpty()) {
      return baseStrategy;
    }

    return new TypeAwareResolutionStrategy(fuzzyTypes, baseStrategy);
  }
}

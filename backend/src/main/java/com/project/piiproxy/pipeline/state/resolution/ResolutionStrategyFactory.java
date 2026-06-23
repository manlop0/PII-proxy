package com.project.piiproxy.pipeline.state.resolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Creates {@link EntityResolutionStrategy} instances from config values.
 * Supports shortnames for built-in strategies and Fully Qualified Class Names (FQCN) for custom ones.
 *
 * <p>Built-in shortnames:</p>
 * <ul>
 *   <li>{@code "exact"} — {@link ExactMatchResolutionStrategy} (also the default when {@code null})</li>
 *   <li>{@code "jaro-winkler"} — {@link JaroWinklerResolutionStrategy}</li>
 * </ul>
 *
 * <p>Custom strategies: pass the FQCN of a class implementing {@link EntityResolutionStrategy}.
 * The class must have a public no-arg constructor, or a single-{@code double} constructor
 * (used as the similarity threshold).</p>
 * <pre>{@code
 * pipeline:
 *   entity_resolution:
 *     algorithm: "com.project.piiproxy.pipeline.state.resolution.MyCustomStrategy"
 * }</pre>
 */
public class ResolutionStrategyFactory {

  private static final Logger log = LoggerFactory.getLogger(ResolutionStrategyFactory.class);

  public static EntityResolutionStrategy create(String algorithm, double threshold, Set<String> fuzzyTypes) {
    EntityResolutionStrategy baseStrategy = resolveBase(algorithm, threshold);

    if (fuzzyTypes == null || fuzzyTypes.isEmpty()) {
      return baseStrategy;
    }

    return new TypeAwareResolutionStrategy(fuzzyTypes, baseStrategy);
  }

  private static EntityResolutionStrategy resolveBase(String algorithm, double threshold) {
    if (algorithm == null || algorithm.isBlank() || "exact".equalsIgnoreCase(algorithm)) {
      return new ExactMatchResolutionStrategy();
    }

    if ("jaro-winkler".equalsIgnoreCase(algorithm)) {
      return new JaroWinklerResolutionStrategy(threshold);
    }

    return loadCustom(algorithm, threshold);
  }

  private static EntityResolutionStrategy loadCustom(String fqcn, double threshold) {
    try {
      Class<?> clazz = Class.forName(fqcn);
      if (!EntityResolutionStrategy.class.isAssignableFrom(clazz)) {
        log.error("Class {} does not implement EntityResolutionStrategy", fqcn);
        throw new IllegalArgumentException("Class " + fqcn + " does not implement EntityResolutionStrategy");
      }

      // Try (double) constructor first (Jaro-Winkler-style).
      try {
        return (EntityResolutionStrategy) clazz
            .getConstructor(double.class)
            .newInstance(threshold);
      } catch (NoSuchMethodException ignored) {
        // Fall through to no-arg constructor.
      }

      return (EntityResolutionStrategy) clazz.getDeclaredConstructor().newInstance();

    } catch (ClassNotFoundException e) {
      log.error("Resolution strategy class not found: {}", fqcn);
      throw new IllegalArgumentException("Resolution strategy class not found: " + fqcn, e);
    } catch (ClassCastException e) {
      log.error("Class {} does not implement EntityResolutionStrategy", fqcn);
      throw new IllegalArgumentException("Class " + fqcn + " does not implement EntityResolutionStrategy", e);
    } catch (Exception e) {
      log.error("Failed to instantiate custom resolution strategy: {}", fqcn, e);
      throw new IllegalArgumentException("Failed to instantiate resolution strategy: " + fqcn
        + " (must have a public no-arg or (double) constructor)", e);
    }
  }
}
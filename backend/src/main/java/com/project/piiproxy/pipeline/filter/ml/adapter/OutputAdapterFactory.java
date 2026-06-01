package com.project.piiproxy.pipeline.filter.ml.adapter;

import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link ModelOutputAdapter} by alias ({@code BIO}, {@code SIMPLE}) or by fully-qualified class name,
 * enabling new model architectures to be plugged in without code changes.
 */
public class OutputAdapterFactory {

  public static ModelOutputAdapter create(
      String adapterType,
      Map<Integer, String> id2label,
      Set<String> ignoredTags,
      Map<String, String> tagMapping) {

    if ("BIO".equalsIgnoreCase(adapterType)) {
      return new BioOutputAdapter(id2label, ignoredTags, tagMapping);
    }
    if ("SIMPLE".equalsIgnoreCase(adapterType)) {
      return new SimpleOutputAdapter(id2label, ignoredTags, tagMapping);
    }

    try {
      Class<?> clazz = Class.forName(adapterType);
      if (!ModelOutputAdapter.class.isAssignableFrom(clazz)) {
        throw new IllegalArgumentException("Class " + adapterType + " does not implement ModelOutputAdapter");
      }

      return (ModelOutputAdapter) clazz
          .getConstructor(Map.class, Set.class, Map.class)
          .newInstance(id2label, ignoredTags, tagMapping);

    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Adapter class not found: " + adapterType, e);
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate custom adapter: " + adapterType, e);
    }
  }
}

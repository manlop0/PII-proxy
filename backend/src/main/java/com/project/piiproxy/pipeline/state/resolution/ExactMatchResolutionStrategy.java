package com.project.piiproxy.pipeline.state.resolution;

/**
 * A pass-through strategy that only resolves entities if they match exactly.
 * This is effectively a no-op since exact matching is usually handled before calling the strategy.
 */
public class ExactMatchResolutionStrategy implements EntityResolutionStrategy {

  @Override
  public String resolve(String entityType, String newEntity, Iterable<String> existingEntities) {
    if (newEntity == null) return null;
    
    for (String existing : existingEntities) {
      if (newEntity.equals(existing)) {
        return existing;
      }
    }
    return null;
  }
}

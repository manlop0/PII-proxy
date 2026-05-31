package com.project.piiproxy.pipeline.state.resolution;

import java.util.Set;

/**
 * A composite strategy that routes resolution to a fuzzy matching strategy (like Jaro-Winkler)
 * only for specific entity types (e.g., PERSON, LOCATION). For all other types, it enforces
 * exact matching to prevent accidental merging of distinct sensitive identifiers (e.g., phone numbers).
 */
public class TypeAwareResolutionStrategy implements EntityResolutionStrategy {

  private final Set<String> fuzzyTypes;
  private final EntityResolutionStrategy fuzzyStrategy;
  private final EntityResolutionStrategy exactStrategy;

  public TypeAwareResolutionStrategy(Set<String> fuzzyTypes, EntityResolutionStrategy fuzzyStrategy) {
    this.fuzzyTypes = fuzzyTypes;
    this.fuzzyStrategy = fuzzyStrategy;
    this.exactStrategy = new ExactMatchResolutionStrategy();
  }

  @Override
  public String resolve(String entityType, String newEntity, Iterable<String> existingEntities) {
    if (fuzzyTypes.contains(entityType)) {
      return fuzzyStrategy.resolve(entityType, newEntity, existingEntities);
    }
    return exactStrategy.resolve(entityType, newEntity, existingEntities);
  }
}

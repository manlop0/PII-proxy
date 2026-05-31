package com.project.piiproxy.pipeline.state.resolution;

/**
 * Strategy interface for resolving entity coreference (e.g. matching "John" and "John's").
 * Implementations determine whether a newly found entity should be mapped to an existing tag.
 */
public interface EntityResolutionStrategy {

  /**
   * Attempts to match a newly found entity against a collection of previously seen entities.
   *
   * @param entityType The type of the entity (e.g., "PERSON", "PHONE", "LOCATION").
   * @param newEntity The newly discovered entity string (e.g., "John's").
   * @param existingEntities The collection of previously saved entities of the same type in the current session.
   * @return The exact string of the existing entity if a match is found, or null if no match is found.
   */
  String resolve(String entityType, String newEntity, Iterable<String> existingEntities);
}

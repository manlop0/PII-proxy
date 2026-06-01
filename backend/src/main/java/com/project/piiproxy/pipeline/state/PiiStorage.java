package com.project.piiproxy.pipeline.state;

/** Contract for session-scoped PII storage: save originals, look up tags, and cache hashed anonymized text. */
public interface PiiStorage {
  String saveOriginal(String sessionId, String type, String originalValue);

  String getOriginal(String sessionId, String tag);

  void cacheAnonymizedText(String sessionId, String textHash, String anonymizedText);

  String getCachedAnonymizedText(String sessionId, String textHash);
}

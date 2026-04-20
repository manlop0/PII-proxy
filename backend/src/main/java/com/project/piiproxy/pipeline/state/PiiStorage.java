package com.project.piiproxy.pipeline.state;

import com.project.piiproxy.pipeline.model.PiiType;

public interface PiiStorage {
  String saveOriginal(String sessionId, PiiType type, String originalValue);
  String getOriginal(String sessionId, String tag);

  void cacheAnonymizedText(String sessionId, String textHash, String anonymizedText);
  String getCachedAnonymizedText(String sessionId, String textHash);
}

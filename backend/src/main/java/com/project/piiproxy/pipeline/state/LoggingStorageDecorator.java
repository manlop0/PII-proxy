package com.project.piiproxy.pipeline.state;

import com.project.piiproxy.pipeline.model.PiiType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingStorageDecorator implements PiiStorage, SessionCleaner {

  private static final Logger log = LoggerFactory.getLogger(LoggingStorageDecorator.class);
  private final MapDbStorage delegate;

  public LoggingStorageDecorator(MapDbStorage delegate) {
    this.delegate = delegate;
  }

  @Override
  public String saveOriginal(String sessionId, PiiType type, String originalValue) {
    String tag = delegate.saveOriginal(sessionId, type, originalValue);

    log.debug("[{}] MAPPED: '{}' -> {}", sessionId, originalValue, tag);
    return tag;
  }

  @Override
  public String getOriginal(String sessionId, String tag) {
    return delegate.getOriginal(sessionId, tag);
  }

  @Override
  public void cacheAnonymizedText(String sessionId, String textHash, String anonymizedText) {
    delegate.cacheAnonymizedText(sessionId, textHash, anonymizedText);
  }

  @Override
  public String getCachedAnonymizedText(String sessionId, String textHash) {
    return delegate.getCachedAnonymizedText(sessionId, textHash);
  }

  @Override
  public void clearSession(String sessionId) {
    delegate.clearSession(sessionId);
  }
}

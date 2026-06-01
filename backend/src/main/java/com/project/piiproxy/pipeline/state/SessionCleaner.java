package com.project.piiproxy.pipeline.state;

/** Contract for clearing all state associated with a session. */
public interface SessionCleaner {
  void clearSession(String sessionId);
}

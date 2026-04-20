package com.project.piiproxy.pipeline.core;

public class SessionStreamProcessor {

  private final String sessionId;
  private final TextAnalyzer analyzer;

  private final StringBuilder tagBuffer = new StringBuilder();
  private boolean isBuffering = false;

  public SessionStreamProcessor(String sessionId, TextAnalyzer analyzer) {
    this.sessionId = sessionId;
    this.analyzer = analyzer;
  }

  public String processChunk(String chunkText) {
    if (chunkText == null || chunkText.isEmpty()) return chunkText;

    StringBuilder readyToSend = new StringBuilder();

    for (char c : chunkText.toCharArray()) {
      if (isBuffering) {
        tagBuffer.append(c);
        if (c == '>') {
          String potentialTag = tagBuffer.toString();
          if (potentialTag.matches("<[A-Z_]+_\\d+>")) {
            String restored = analyzer.restoreText(potentialTag, sessionId);
            readyToSend.append(restored);
          } else {
            readyToSend.append(potentialTag);
          }
          tagBuffer.setLength(0);
          isBuffering = false;

        } else if (tagBuffer.length() > 50) {
          readyToSend.append(tagBuffer);
          tagBuffer.setLength(0);
          isBuffering = false;
        }
      } else {
        if (c == '<') {
          isBuffering = true;
          tagBuffer.append(c);
        } else {
          readyToSend.append(c);
        }
      }
    }
    return readyToSend.toString();
  }
}

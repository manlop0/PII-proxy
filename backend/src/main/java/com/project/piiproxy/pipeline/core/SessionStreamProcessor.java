package com.project.piiproxy.pipeline.core;

import java.util.regex.Pattern;

public class SessionStreamProcessor {

  private final String sessionId;
  private final TextAnalyzer analyzer;

  private static final Pattern TAG_PATTERN = Pattern.compile("^<[A-Z_]+_\\d+>$");

  private final StringBuilder tagBuffer = new StringBuilder(64);
  private boolean isBuffering = false;

  private final StringBuilder fullRawText = new StringBuilder(4096);
  private final StringBuilder fullRestoredText = new StringBuilder(4096);

  private final StringBuilder readyToSend = new StringBuilder(128);

  public SessionStreamProcessor(String sessionId, TextAnalyzer analyzer) {
    this.sessionId = sessionId;
    this.analyzer = analyzer;
  }

  public String processChunk(String chunkText) {
    if (chunkText == null || chunkText.isEmpty()) return chunkText;

    fullRawText.append(chunkText);

    readyToSend.setLength(0);

    int len = chunkText.length();
    for (int i = 0; i < len; i++) {
      char c = chunkText.charAt(i);

      if (isBuffering) {
        tagBuffer.append(c);
        if (c == '>') {
          String potentialTag = tagBuffer.toString();

          if (TAG_PATTERN.matcher(potentialTag).matches()) {
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

    String restoredChunk = readyToSend.toString();
    fullRestoredText.append(restoredChunk);

    return restoredChunk;
  }

  public void flushCache() {
    if (!fullRestoredText.isEmpty() && !fullRawText.isEmpty()) {
      analyzer.cacheRestoredText(sessionId, fullRestoredText.toString(), fullRawText.toString());
    }
  }
}

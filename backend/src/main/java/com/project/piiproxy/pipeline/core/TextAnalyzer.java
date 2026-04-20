package com.project.piiproxy.pipeline.core;

import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.model.Span;
import com.project.piiproxy.pipeline.state.PiiStorage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextAnalyzer {

  private final PiiStorage storage;
  private final List<TextFilter> filters;
  private static final Pattern TAG_PATTERN = Pattern.compile("<[A-Z_]+_\\d+>");

  public TextAnalyzer(PiiStorage storage, List<TextFilter> filters) {
    this.storage = storage;
    this.filters = filters;
  }

  public String anonymizeText(String text, String sessionId) {
    if (text == null || text.isBlank()) return text;

    String hash = computeHash(text);
    String cached = storage.getCachedAnonymizedText(sessionId, hash);
    if (cached != null) {
      return cached;
    }

    List<Span> allSpans = new ArrayList<>();
    for (TextFilter filter : filters) {
      allSpans.addAll(filter.find(text));
    }

    if (allSpans.isEmpty()) {
      storage.cacheAnonymizedText(sessionId, hash, text);
      return text;
    }

    allSpans.sort(Comparator.comparingInt(Span::start));
    List<Span> resolvedSpans = resolveConflicts(allSpans);

    StringBuilder sb = new StringBuilder(text);
    for (int i = resolvedSpans.size() - 1; i >= 0; i--) {
      Span span = resolvedSpans.get(i);
      String tag = storage.saveOriginal(sessionId, span.type(), span.value());
      sb.replace(span.start(), span.end(), tag);
    }

    String result = sb.toString();

    storage.cacheAnonymizedText(sessionId, hash, result);
    return result;
  }

  public String restoreText(String text, String sessionId) {
    if (text == null || text.isBlank()) return text;

    Matcher matcher = TAG_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      String tag = matcher.group();
      String original = storage.getOriginal(sessionId, tag);
      matcher.appendReplacement(result, original != null ? original : tag);
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private List<Span> resolveConflicts(List<Span> sortedSpans) {
    List<Span> resolved = new ArrayList<>();
    int lastEnd = -1;
    for (Span span : sortedSpans) {
      if (span.start() >= lastEnd) {
        resolved.add(span);
        lastEnd = span.end();
      }
    }
    return resolved;
  }

  private String computeHash(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}

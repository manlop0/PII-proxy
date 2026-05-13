package com.project.piiproxy.pipeline.core;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.model.ConflictStrategy;
import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import com.project.piiproxy.pipeline.state.PiiStorage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TextAnalyzer {

  private final Vertx vertx;
  private final PiiStorage storage;
  private final List<TextFilter> filters;
  private final ConflictStrategy conflictStrategy;
  private static final Pattern TAG_PATTERN = Pattern.compile("<[A-Z_]+_\\d+>");

  public TextAnalyzer(Vertx vertx, PiiStorage storage, List<TextFilter> filters, ConflictStrategy conflictStrategy) {
    this.vertx = vertx;
    this.storage = storage;
    this.filters = filters;
    this.conflictStrategy = conflictStrategy;
  }

  public Future<String> anonymizeText(String text, String sessionId) {
    if (text == null || text.isBlank()) return Future.succeededFuture(text);

    String hash = computeHash(text);
    String cached = storage.getCachedAnonymizedText(sessionId, hash);
    if (cached != null) {
      return Future.succeededFuture(cached);
    }

    Future<List<Span>> mlFuture = vertx.eventBus().<JsonArray>request("ml.ner.analyze", text)
      .map(reply -> {
        JsonArray array = reply.body();
        return array.stream()
          .map(obj -> ((JsonObject) obj).mapTo(Span.class))
          .collect(Collectors.toList());
      })
      .recover(err -> {
        System.err.println("ML skipped due to error: " + err.getMessage());
        return Future.succeededFuture(new ArrayList<>());
      });

    List<Span> regexSpans = new ArrayList<>();
    for (TextFilter filter : filters) {
      regexSpans.addAll(filter.find(text));
    }

    return mlFuture.map(mlSpans -> {

      List<Span> resolvedSpans = resolveConflicts(regexSpans, mlSpans, conflictStrategy);

      if (resolvedSpans.isEmpty()) {
        storage.cacheAnonymizedText(sessionId, hash, text);
        return text;
      }

      resolvedSpans.sort((s1, s2) -> Integer.compare(s2.start(), s1.start()));
      StringBuilder sb = new StringBuilder(text);

      for (Span span : resolvedSpans) {
        String tagType = span.type() == PiiType.MODEL ? span.rawType() : span.type().name();

        String tag = storage.saveOriginal(sessionId, tagType, span.value());
        sb.replace(span.start(), span.end(), tag);
      }

      String result = sb.toString();
      storage.cacheAnonymizedText(sessionId, hash, result);
      return result;
    });
  }

  public String restoreText(String text, String sessionId) {
    if (text == null || text.isBlank()) return text;

    Matcher matcher = TAG_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      String tag = matcher.group();
      String original = storage.getOriginal(sessionId, tag);

      String safeReplacement = Matcher.quoteReplacement(original != null ? original : tag);
      matcher.appendReplacement(result, safeReplacement);
    }
    matcher.appendTail(result);
    String restoredText = result.toString();

    cacheRestoredText(sessionId, restoredText, text);

    return restoredText;
  }

  public void cacheRestoredText(String sessionId, String restoredText, String rawAnonymizedText) {
    if (restoredText == null || restoredText.isBlank()) return;
    String hash = computeHash(restoredText);
    storage.cacheAnonymizedText(sessionId, hash, rawAnonymizedText);
  }

  private List<Span> resolveConflicts(List<Span> regexSpans, List<Span> mlSpans, ConflictStrategy strategy) {
    List<Span> all = new ArrayList<>();
    all.addAll(regexSpans);
    all.addAll(mlSpans);

    all.sort((s1, s2) -> {
      int cmp = Integer.compare(s1.start(), s2.start());
      if (cmp == 0) {
        if (strategy == ConflictStrategy.REGEX_PRIORITY) {
          boolean s1IsRegex = regexSpans.contains(s1);
          boolean s2IsRegex = regexSpans.contains(s2);
          if (s1IsRegex && !s2IsRegex) return -1;
          if (!s1IsRegex && s2IsRegex) return 1;
        }
        return Integer.compare(s2.end() - s2.start(), s1.end() - s1.start());
      }
      return cmp;
    });

    List<Span> resolved = new ArrayList<>();
    int currentEnd = -1;

    for (Span span : all) {
      if (span.start() >= currentEnd) {
        resolved.add(span);
        currentEnd = span.end();
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

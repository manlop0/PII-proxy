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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TextAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(TextAnalyzer.class);

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
      log.debug("Cache hit for anonymization in session {}", sessionId);
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
        log.warn("ML skipped due to error: {}", err.getMessage());
        return Future.succeededFuture(new ArrayList<>());
      });

    List<Span> regexSpans = new ArrayList<>();
    for (TextFilter filter : filters) {
      regexSpans.addAll(filter.find(text));
    }
    if (log.isDebugEnabled()) {
      log.debug("Found Regex spans:{}", regexSpans.isEmpty() ? " none" : regexSpans.stream().map(Span::toString).collect(Collectors.joining("\n  - ", "\n  - ", "")));
    }

    return mlFuture.map(mlSpans -> {

      if (log.isDebugEnabled()) {
        log.debug("Received from ML:{}", mlSpans.isEmpty() ? " none" : mlSpans.stream().map(Span::toString).collect(Collectors.joining("\n  - ", "\n  - ", "")));
      }

      List<Span> resolvedSpans = resolveConflicts(regexSpans, mlSpans, conflictStrategy);

      if (log.isDebugEnabled()) {
        log.debug("Final spans for replacement:{}", resolvedSpans.isEmpty() ? " none" : resolvedSpans.stream().map(Span::toString).collect(Collectors.joining("\n  - ", "\n  - ", "")));
      }

      if (resolvedSpans.isEmpty()) {
        storage.cacheAnonymizedText(sessionId, hash, text);
        return text;
      }

      resolvedSpans.sort(Comparator.comparingInt(Span::start));
      List<String> tags = new ArrayList<>(resolvedSpans.size());
      for (Span span : resolvedSpans) {
        String tagType = span.type() == PiiType.MODEL ? span.rawType() : span.type().name();
        tags.add(storage.saveOriginal(sessionId, tagType, span.value()));
      }

      resolvedSpans.sort((s1, s2) -> Integer.compare(s2.start(), s1.start()));
      StringBuilder sb = new StringBuilder(text);

      for (int i = 0; i < resolvedSpans.size(); i++) {
        Span span = resolvedSpans.get(i);
        sb.replace(span.start(), span.end(), tags.get(i));
      }

      String result = sb.toString();
      storage.cacheAnonymizedText(sessionId, hash, result);
      return result;
    });
  }

  public String restoreText(String text, String sessionId) {
    return restoreText(text, sessionId, "unknown");
  }

  public String restoreText(String text, String sessionId, String context) {
    if (text == null || text.isBlank()) return text;

    Matcher matcher = TAG_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder();

    int count = 0;
    while (matcher.find()) {
      count++;
      String tag = matcher.group();
      String original = storage.getOriginal(sessionId, tag);

      String safeReplacement = Matcher.quoteReplacement(original != null ? original : tag);
      matcher.appendReplacement(result, safeReplacement);
    }
    matcher.appendTail(result);
    String restoredText = result.toString();

    if (count > 0) {
      log.debug("Restored {} tags in '{}' for session {}", count, context, sessionId);
    }

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
    Span lastAdded = null;

    for (Span span : all) {
      if (span.start() >= currentEnd) {
        resolved.add(span);
        currentEnd = span.end();
        lastAdded = span;
      } else if (log.isDebugEnabled()) {
        log.debug("Conflict resolved (Strategy: {}): Kept {} | Dropped overlapping {}", strategy, lastAdded, span);
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

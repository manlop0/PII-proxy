package com.project.piiproxy.pipeline.anonymize;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.project.piiproxy.pipeline.BusAddresses;
import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.model.ConflictStrategy;
import com.project.piiproxy.pipeline.model.Span;
import com.project.piiproxy.pipeline.state.PiiStorage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core PII detection engine. Combines regex and ML model results, resolves conflicts,
 * and performs a two-pass tag assignment so that the first occurrence of an entity in text
 * becomes the canonical form referenced by the generated tag.
 */
public class TextAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(TextAnalyzer.class);
  private static final Pattern TAG_PATTERN = Pattern.compile("<[A-Z_]+_\\d+>");

  private final Vertx vertx;
  private final PiiStorage storage;
  private final List<TextFilter> filters;
  private final ConflictStrategy conflictStrategy;
  private final SpanConflictResolver conflictResolver;
  private final PiiSubstitutor substitutor;
  private final TextHasher hasher;

  public TextAnalyzer(Vertx vertx,
                      PiiStorage storage,
                      List<TextFilter> filters,
                      ConflictStrategy conflictStrategy) {
    this(vertx, storage, filters, conflictStrategy,
      new SpanConflictResolver(), new PiiSubstitutor(storage), new TextHasher());
  }

  public TextAnalyzer(Vertx vertx,
                      PiiStorage storage,
                      List<TextFilter> filters,
                      ConflictStrategy conflictStrategy,
                      SpanConflictResolver conflictResolver,
                      PiiSubstitutor substitutor,
                      TextHasher hasher) {
    this.vertx = vertx;
    this.storage = storage;
    this.filters = filters;
    this.conflictStrategy = conflictStrategy;
    this.conflictResolver = conflictResolver;
    this.substitutor = substitutor;
    this.hasher = hasher;
  }

  public Future<String> anonymizeText(String text, String sessionId) {
    if (text == null || text.isBlank()) return Future.succeededFuture(text);

    String hash = hasher.computeHash(text);
    return vertx.executeBlocking(() -> {
      String cached = storage.getCachedAnonymizedText(sessionId, hash);
      if (cached != null) {
        log.debug("Cache hit for anonymization in session {}", sessionId);
        return cached;
      }
      return null;
    }, false).compose(cached -> {
      if (cached != null) return Future.succeededFuture(cached);

      Future<List<Span>> mlFuture = vertx.eventBus().<JsonArray>request(BusAddresses.ML_NER_ANALYZE, text)
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

      return mlFuture.compose(mlSpans -> vertx.executeBlocking(() -> {

        if (log.isDebugEnabled()) {
          log.debug("Received from ML:{}", mlSpans.isEmpty() ? " none" : mlSpans.stream().map(Span::toString).collect(Collectors.joining("\n  - ", "\n  - ", "")));
        }

        List<Span> resolvedSpans = conflictResolver.resolve(regexSpans, mlSpans, conflictStrategy);

        if (log.isDebugEnabled()) {
          log.debug("Final spans for replacement:{}", resolvedSpans.isEmpty() ? " none" : resolvedSpans.stream().map(Span::toString).collect(Collectors.joining("\n  - ", "\n  - ", "")));
        }

        String result = substitutor.substitute(text, sessionId, resolvedSpans);
        storage.cacheAnonymizedText(sessionId, hash, result);
        return result;
      }, false));
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

  public Future<String> restoreTextAsync(String text, String sessionId, String context) {
    if (text == null || text.isBlank()) return Future.succeededFuture(text);
    return vertx.executeBlocking(() -> restoreText(text, sessionId, context), false);
  }

  public void cacheRestoredText(String sessionId, String restoredText, String rawAnonymizedText) {
    if (restoredText == null || restoredText.isBlank()) return;
    String hash = hasher.computeHash(restoredText);
    storage.cacheAnonymizedText(sessionId, hash, rawAnonymizedText);
  }

  public Future<Void> cacheRestoredTextAsync(String sessionId, String restoredText, String rawAnonymizedText) {
    if (restoredText == null || restoredText.isBlank()) return Future.succeededFuture();
    return vertx.executeBlocking(() -> {
      cacheRestoredText(sessionId, restoredText, rawAnonymizedText);
      return null;
    }, false);
  }
}

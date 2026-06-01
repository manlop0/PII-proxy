package com.project.piiproxy.pipeline.anonymize;

import com.project.piiproxy.pipeline.model.ConflictStrategy;
import com.project.piiproxy.pipeline.model.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure function that merges overlapping regex and ML {@link Span}s into a single, non-overlapping list.
 * Sort priority: by start position; on ties, the configured {@link ConflictStrategy} decides (longer-first by default,
 * regex-first when {@code REGEX_PRIORITY} is set).
 */
public class SpanConflictResolver {

  private static final Logger log = LoggerFactory.getLogger(SpanConflictResolver.class);

  public List<Span> resolve(List<Span> regexSpans, List<Span> mlSpans, ConflictStrategy strategy) {
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
}

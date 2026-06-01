package com.project.piiproxy.pipeline.anonymize;

import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import com.project.piiproxy.pipeline.state.PiiStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Replaces detected {@link Span}s in source text with their PII tags using a two-pass strategy.
 * Forward pass: persists each entity to {@link PiiStorage} so the first-seen form becomes the canonical one.
 * Backward pass: replaces spans in the StringBuilder from end to start, keeping earlier indices valid.
 */
public class PiiSubstitutor {

  private final PiiStorage storage;

  public PiiSubstitutor(PiiStorage storage) {
    this.storage = storage;
  }

  public String substitute(String text, String sessionId, List<Span> resolvedSpans) {
    if (resolvedSpans.isEmpty()) {
      return text;
    }

    // First pass (forward): save originals to storage so the first-seen form becomes canonical.
    resolvedSpans.sort(Comparator.comparingInt(Span::start));
    List<String> tags = new ArrayList<>(resolvedSpans.size());
    for (Span span : resolvedSpans) {
      String tagType = span.type() == PiiType.MODEL ? span.rawType() : span.type().name();
      tags.add(storage.saveOriginal(sessionId, tagType, span.value()));
    }

    // Second pass (backward): replacing from end to start keeps earlier indices valid.
    resolvedSpans.sort((s1, s2) -> Integer.compare(s2.start(), s1.start()));
    StringBuilder sb = new StringBuilder(text);

    for (int i = 0; i < resolvedSpans.size(); i++) {
      Span span = resolvedSpans.get(i);
      sb.replace(span.start(), span.end(), tags.get(i));
    }

    return sb.toString();
  }
}

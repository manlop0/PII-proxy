package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;

import java.util.ArrayList;
import java.util.List;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

public abstract class BaseRegexFilter implements TextFilter {
  private final Pattern pattern;
  private final PiiType type;

  protected BaseRegexFilter(String regex, PiiType type) {
    this.pattern = Pattern.compile(regex);
    this.type = type;
  }

  @Override
  public List<Span> find(String text) {
    if (text == null || text.isBlank()) return List.of();

    List<Span> spans = new ArrayList<>();
    Matcher matcher = pattern.matcher(text);

    while (matcher.find()) {
      String value = matcher.group();
      int start = matcher.start();
      int end = matcher.end();

      while (!value.isEmpty() && Character.isWhitespace(value.charAt(0))) {
        value = value.substring(1);
        start++;
      }

      while (!value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1))) {
        value = value.substring(0, value.length() - 1);
        end--;
      }

      if (!value.isEmpty()) {
        spans.add(new Span(start, end, type, type.toString(), value));
      }
    }
    return spans;
  }
}

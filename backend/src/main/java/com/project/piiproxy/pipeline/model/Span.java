package com.project.piiproxy.pipeline.model;

public record Span(int start, int end, PiiType type, String rawType, String value) {

  @Override
  public String toString() {
    String displayType = (type == PiiType.MODEL) ? rawType : type.name();
    return String.format("'%s' [%s] (%d..%d)", value, displayType, start, end);
  }
}


package com.project.piiproxy.pipeline.filter;

import com.project.piiproxy.pipeline.model.Span;
import java.util.List;

/** Common contract for PII detectors: produce a list of {@link Span}s from an input string. */
public interface TextFilter {
  List<Span> find(String text);
}

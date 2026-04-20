package com.project.piiproxy.pipeline.filter;

import com.project.piiproxy.pipeline.model.Span;
import java.util.List;

public interface TextFilter {
  List<Span> find(String text);
}

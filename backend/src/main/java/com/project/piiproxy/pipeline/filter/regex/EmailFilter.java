package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;

/** Detects email addresses (RFC 5322 simplified pattern) using a regex. */
public class EmailFilter extends BaseRegexFilter {
  public EmailFilter() {
    super("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", PiiType.EMAIL);
  }
}

package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;


// TODO: use library
public class PhoneFilter extends BaseRegexFilter {
  public PhoneFilter() {
    super("(?:\\+?\\d{1,3}[\\s-]?)?(?:\\(\\d{1,4}\\)[\\s-]?)?[\\d\\s-]{7,15}\\b", PiiType.PHONE);
  }
}

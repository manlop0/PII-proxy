package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;

public class IpAddressFilter extends BaseRegexFilter {
  public IpAddressFilter() {
    super("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b", PiiType.IP_ADDRESS);
  }
}

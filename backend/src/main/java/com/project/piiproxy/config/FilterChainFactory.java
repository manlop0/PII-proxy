package com.project.piiproxy.config;

import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.filter.regex.CreditCardFilter;
import com.project.piiproxy.pipeline.filter.regex.EmailFilter;
import com.project.piiproxy.pipeline.filter.regex.IpAddressFilter;
import com.project.piiproxy.pipeline.filter.regex.PhoneFilter;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the regex-based PII filter chain from {@code pipeline.filters.*} config.
 * Each filter can be individually enabled/disabled in config; missing flags default to enabled.
 */
public class FilterChainFactory {

  private static final Logger log = LoggerFactory.getLogger(FilterChainFactory.class);

  public List<TextFilter> build(JsonObject pipelineConfig) {
    List<TextFilter> regexFilters = new ArrayList<>();
    JsonObject filtersConfig = pipelineConfig.getJsonObject("filters", new JsonObject());

    List<String> enabledFilters = new ArrayList<>();
    List<String> disabledFilters = new ArrayList<>();

    if (filtersConfig.getBoolean("email", true)) {
      regexFilters.add(new EmailFilter());
      enabledFilters.add("email");
    } else {
      disabledFilters.add("email");
    }
    if (filtersConfig.getBoolean("phone", true)) {
      regexFilters.add(new PhoneFilter());
      enabledFilters.add("phone");
    } else {
      disabledFilters.add("phone");
    }
    if (filtersConfig.getBoolean("credit_card", true)) {
      regexFilters.add(new CreditCardFilter());
      enabledFilters.add("credit_card");
    } else {
      disabledFilters.add("credit_card");
    }
    if (filtersConfig.getBoolean("ip_address", true)) {
      regexFilters.add(new IpAddressFilter());
      enabledFilters.add("ip_address");
    } else {
      disabledFilters.add("ip_address");
    }

    log.info("Regex Filters -> Enabled: {}, Disabled: {}", enabledFilters, disabledFilters);
    return regexFilters;
  }
}

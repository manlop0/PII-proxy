package com.project.piiproxy.config;

import com.project.piiproxy.pipeline.filter.TextFilter;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the regex-based PII filter chain from {@code pipeline.filters} config.
 * Each entry is either a built-in shortname (e.g. {@code "email"}) or a Fully Qualified Class Name
 * pointing to a custom {@link TextFilter} implementation.
 */
public class FilterChainFactory {

  private static final Logger log = LoggerFactory.getLogger(FilterChainFactory.class);

  public List<TextFilter> build(JsonObject pipelineConfig) {
    JsonObject filtersConfig = pipelineConfig.getJsonObject("filters", new JsonObject());

    JsonArray filtersArray = filtersConfig.getJsonArray("names");

    List<TextFilter> regexFilters = new ArrayList<>();
    List<String> enabledFilters = new ArrayList<>();
    List<String> failedFilters = new ArrayList<>();

    if (filtersArray != null) {
      for (Object entry : filtersArray) {
        String filterId = String.valueOf(entry);
        try {
          TextFilter filter = FilterFactory.create(filterId);
          regexFilters.add(filter);
          enabledFilters.add(filterId);
        } catch (Exception e) {
          failedFilters.add(filterId + " (" + e.getMessage() + ")");
        }
      }
    } else {
      // Legacy map format: pipeline.filters.<name>: true/false — defaults to all built-in filters enabled.
      log.warn("Legacy pipeline.filters map format detected. Migrate to the new list format (see config.yaml).");
      if (filtersConfig.getBoolean("email", true))        enableLegacy("email",        regexFilters, enabledFilters, failedFilters);
      if (filtersConfig.getBoolean("phone", true))        enableLegacy("phone",        regexFilters, enabledFilters, failedFilters);
      if (filtersConfig.getBoolean("credit_card", true))  enableLegacy("credit_card",  regexFilters, enabledFilters, failedFilters);
      if (filtersConfig.getBoolean("ip_address", true))   enableLegacy("ip_address",   regexFilters, enabledFilters, failedFilters);
    }

    log.info("Regex Filters -> Enabled: {}, Failed: {}", enabledFilters, failedFilters);
    return regexFilters;
  }

  private void enableLegacy(String id, List<TextFilter> filters, List<String> enabled, List<String> failed) {
    try {
      filters.add(FilterFactory.create(id));
      enabled.add(id);
    } catch (Exception e) {
      failed.add(id + " (" + e.getMessage() + ")");
    }
  }
}

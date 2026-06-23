package com.project.piiproxy.config;

import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.filter.regex.CreditCardFilter;
import com.project.piiproxy.pipeline.filter.regex.EmailFilter;
import com.project.piiproxy.pipeline.filter.regex.IpAddressFilter;
import com.project.piiproxy.pipeline.filter.regex.PhoneFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates {@link TextFilter} instances from config values.
 * Supports shortnames for built-in filters and Fully Qualified Class Names (FQCN) for custom ones.
 *
 * <p>Built-in shortnames (all live in {@code com.project.piiproxy.pipeline.filter.regex}):</p>
 * <ul>
 *   <li>{@code "email"} — {@link EmailFilter}</li>
 *   <li>{@code "phone"} — {@link PhoneFilter}</li>
 *   <li>{@code "credit_card"} — {@link CreditCardFilter}</li>
 *   <li>{@code "ip_address"} — {@link IpAddressFilter}</li>
 * </ul>
 *
 * <p>Custom filters: pass the FQCN of a class implementing {@link TextFilter} with a public no-arg constructor.</p>
 * <pre>{@code
 * pipeline:
 *   filters:
 *     - "email"
 *     - "com.project.piiproxy.pipeline.filter.regex.MyCustomRegexFilter"
 * }</pre>
 */
public class FilterFactory {

  private static final Logger log = LoggerFactory.getLogger(FilterFactory.class);

  public static TextFilter create(String filterId) {
    if (filterId == null || filterId.isBlank()) {
      throw new IllegalArgumentException("Filter id must not be null or blank");
    }

    String key = filterId.trim();
    switch (key.toLowerCase()) {
      case "email":
        return new EmailFilter();
      case "phone":
        return new PhoneFilter();
      case "credit_card":
        return new CreditCardFilter();
      case "ip_address":
        return new IpAddressFilter();
      default:
        return loadCustom(filterId);
    }
  }

  private static TextFilter loadCustom(String fqcn) {
    try {
      Class<?> clazz = Class.forName(fqcn);
      if (!TextFilter.class.isAssignableFrom(clazz)) {
        log.error("Class {} does not implement TextFilter", fqcn);
        throw new IllegalArgumentException("Class " + fqcn + " does not implement TextFilter");
      }
      TextFilter filter = (TextFilter) clazz.getDeclaredConstructor().newInstance();
      log.info("Loaded custom filter: {}", fqcn);
      return filter;
    } catch (ClassNotFoundException e) {
      log.error("Filter class not found: {}", fqcn);
      throw new IllegalArgumentException("Filter class not found: " + fqcn, e);
    } catch (ClassCastException e) {
      log.error("Class {} does not implement TextFilter", fqcn);
      throw new IllegalArgumentException("Class " + fqcn + " does not implement TextFilter", e);
    } catch (Exception e) {
      log.error("Failed to instantiate custom filter: {}", fqcn, e);
      throw new IllegalArgumentException("Failed to instantiate filter: " + fqcn
        + " (must have a public no-arg constructor)", e);
    }
  }
}
package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;

import java.util.List;
import java.util.stream.Collectors;

public class CreditCardFilter extends BaseRegexFilter {

  public CreditCardFilter() {
    super("\\b(?:\\d[ -]*?){13,19}\\b", PiiType.CREDIT_CARD);
  }

  @Override
  public List<Span> find(String text) {
    List<Span> rawSpans = super.find(text);

    return rawSpans.stream()
      .filter(span -> isValidLuhn(span.value()))
      .collect(Collectors.toList());
  }

  /**
   * Luhn Algorithm
   */
  private boolean isValidLuhn(String cardNumber) {
    String digitsOnly = cardNumber.replaceAll("[\\s-]", "");

    if (digitsOnly.length() < 13 || digitsOnly.length() > 19) return false;

    int sum = 0;
    boolean alternate = false;

    for (int i = digitsOnly.length() - 1; i >= 0; i--) {
      int n = Character.getNumericValue(digitsOnly.charAt(i));

      if (alternate) {
        n *= 2;
        if (n > 9) {
          n = (n % 10) + 1;
        }
      }
      sum += n;
      alternate = !alternate;
    }
    return (sum % 10 == 0);
  }
}

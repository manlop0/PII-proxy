package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditCardFilterTest {

  private CreditCardFilter filter;

  @BeforeEach
  void setUp() {
    filter = new CreditCardFilter();
  }

  @Test
  void validVisa_detected() {
    List<Span> spans = filter.find("card: 4111111111111111");

    assertEquals(1, spans.size());
    assertEquals("4111111111111111", spans.get(0).value());
    assertEquals(PiiType.CREDIT_CARD, spans.get(0).type());
  }

  @Test
  void validMastercard_detected() {
    List<Span> spans = filter.find("card: 5500000000000004");

    assertEquals(1, spans.size());
    assertEquals("5500000000000004", spans.get(0).value());
  }

  @Test
  void invalidLuhn_notDetected() {
    List<Span> spans = filter.find("card: 1234567890123456");

    assertTrue(spans.isEmpty());
  }

  @Test
  void tooShort_notDetected() {
    List<Span> spans = filter.find("card: 123456789012");

    assertTrue(spans.isEmpty());
  }

  @Test
  void withDashes_detected() {
    List<Span> spans = filter.find("card: 4111-1111-1111-1111");

    assertEquals(1, spans.size());
    assertTrue(spans.get(0).value().contains("4111"));
  }

  @Test
  void withSpaces_detected() {
    List<Span> spans = filter.find("card: 4111 1111 1111 1111");

    assertEquals(1, spans.size());
    assertTrue(spans.get(0).value().contains("4111"));
  }
}

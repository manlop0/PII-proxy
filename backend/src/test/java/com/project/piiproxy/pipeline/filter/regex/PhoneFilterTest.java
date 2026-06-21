package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhoneFilterTest {

  private PhoneFilter filter;

  @BeforeEach
  void setUp() {
    filter = new PhoneFilter();
  }

  @Test
  void internationalFormat_detected() {
    List<Span> spans = filter.find("call +1-555-123-4567 now");

    assertEquals(1, spans.size());
    assertEquals("+1-555-123-4567", spans.get(0).value());
  }

  @Test
  void parenthesesFormat_detected() {
    List<Span> spans = filter.find("phone: (555) 123-4567 now");

    assertEquals(1, spans.size());
    assertEquals("(555) 123-4567", spans.get(0).value());
  }

  @Test
  void dashedFormat_detected() {
    List<Span> spans = filter.find("tel: 555-123-4567 now");

    assertEquals(1, spans.size());
    assertEquals("555-123-4567", spans.get(0).value());
  }

  @Test
  void tooShort_notDetected() {
    List<Span> spans = filter.find("number 12345");

    assertTrue(spans.isEmpty());
  }
}

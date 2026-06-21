package com.project.piiproxy.pipeline.filter.regex;

import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailFilterTest {

  private EmailFilter filter;

  @BeforeEach
  void setUp() {
    filter = new EmailFilter();
  }

  @Test
  void standardEmail_detected() {
    List<Span> spans = filter.find("contact me at john@example.com please");

    assertEquals(1, spans.size());
    assertEquals("john@example.com", spans.get(0).value());
    assertEquals(PiiType.EMAIL, spans.get(0).type());
  }

  @Test
  void emailWithSubdomain_detected() {
    List<Span> spans = filter.find("send to user@mail.server.example.com");

    assertEquals(1, spans.size());
    assertEquals("user@mail.server.example.com", spans.get(0).value());
  }

  @Test
  void emailWithPlus_detected() {
    List<Span> spans = filter.find("john+tag@gmail.com");

    assertEquals(1, spans.size());
    assertEquals("john+tag@gmail.com", spans.get(0).value());
  }

  @Test
  void noAtSymbol_notDetected() {
    List<Span> spans = filter.find("this is not an email");

    assertTrue(spans.isEmpty());
  }

  @Test
  void multipleEmails_allDetected() {
    List<Span> spans = filter.find("write to a@b.com or c@d.com");

    assertEquals(2, spans.size());
    assertEquals("a@b.com", spans.get(0).value());
    assertEquals("c@d.com", spans.get(1).value());
  }
}

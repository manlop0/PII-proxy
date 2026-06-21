package com.project.piiproxy.pipeline.anonymize;

import com.project.piiproxy.pipeline.model.ConflictStrategy;
import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpanConflictResolverTest {

  private SpanConflictResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new SpanConflictResolver();
  }

  @Test
  void regexPriority_regexWinsOverMlAtSamePosition() {
    Span regex = new Span(0, 10, PiiType.EMAIL, "EMAIL", "test@mail.com");
    Span ml = new Span(0, 10, PiiType.MODEL, "PERSON", "test@mail");

    List<Span> result = resolver.resolve(List.of(regex), List.of(ml), ConflictStrategy.REGEX_PRIORITY);

    assertEquals(1, result.size());
    assertEquals(PiiType.EMAIL, result.get(0).type());
  }

  @Test
  void longestWins_longerSpanWins() {
    Span shortSpan = new Span(0, 5, PiiType.MODEL, "PERSON", "John");
    Span longSpan = new Span(0, 10, PiiType.EMAIL, "EMAIL", "john@test.com");

    List<Span> result = resolver.resolve(List.of(shortSpan), List.of(longSpan), ConflictStrategy.LONGEST_WINS);

    assertEquals(1, result.size());
    assertEquals(10, result.get(0).end());
  }

  @Test
  void noOverlap_bothKept() {
    Span first = new Span(0, 5, PiiType.MODEL, "PERSON", "John");
    Span second = new Span(10, 20, PiiType.EMAIL, "EMAIL", "a@b.com");

    List<Span> result = resolver.resolve(List.of(first), List.of(second), ConflictStrategy.LONGEST_WINS);

    assertEquals(2, result.size());
    assertEquals("John", result.get(0).value());
    assertEquals("a@b.com", result.get(1).value());
  }

  @Test
  void emptyRegexSpans_mlSpansKept() {
    Span ml = new Span(0, 5, PiiType.MODEL, "PERSON", "John");

    List<Span> result = resolver.resolve(List.of(), List.of(ml), ConflictStrategy.LONGEST_WINS);

    assertEquals(1, result.size());
    assertEquals(PiiType.MODEL, result.get(0).type());
  }

  @Test
  void emptyMlSpans_regexSpansKept() {
    Span regex = new Span(0, 10, PiiType.EMAIL, "EMAIL", "a@b.com");

    List<Span> result = resolver.resolve(List.of(regex), List.of(), ConflictStrategy.REGEX_PRIORITY);

    assertEquals(1, result.size());
    assertEquals(PiiType.EMAIL, result.get(0).type());
  }

  @Test
  void multipleOverlapping_firstSpanWins() {
    Span a = new Span(0, 10, PiiType.EMAIL, "EMAIL", "first@email.com");
    Span b = new Span(5, 15, PiiType.MODEL, "PERSON", "overlap");
    Span c = new Span(20, 30, PiiType.PHONE, "PHONE", "1234567890");

    List<Span> result = resolver.resolve(List.of(a), List.of(b, c), ConflictStrategy.LONGEST_WINS);

    assertEquals(2, result.size());
    assertEquals(0, result.get(0).start());
    assertEquals(20, result.get(1).start());
  }

  @Test
  void bothEmpty_returnsEmpty() {
    List<Span> result = resolver.resolve(List.of(), List.of(), ConflictStrategy.LONGEST_WINS);

    assertTrue(result.isEmpty());
  }

  @Test
  void sameStart_longestWinsWithLongestStrategy() {
    Span shortSpan = new Span(5, 8, PiiType.MODEL, "PERSON", "Joe");
    Span longSpan = new Span(5, 20, PiiType.EMAIL, "EMAIL", "joe@example.com");

    List<Span> result = resolver.resolve(List.of(shortSpan), List.of(longSpan), ConflictStrategy.LONGEST_WINS);

    assertEquals(1, result.size());
    assertEquals("joe@example.com", result.get(0).value());
  }
}

package com.project.piiproxy.pipeline.state.resolution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JaroWinklerResolutionStrategyTest {

  private JaroWinklerResolutionStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new JaroWinklerResolutionStrategy(0.88);
  }

  @Test
  void exactMatch_returnsMatch() {
    String result = strategy.resolve("PERSON", "John", List.of("John", "Jane"));

    assertEquals("John", result);
  }

  @Test
  void similarNames_returnsMatch() {
    String result = strategy.resolve("PERSON", "Johnson", List.of("John", "Jane"));

    assertEquals("John", result);
  }

  @Test
  void belowThreshold_returnsNull() {
    String result = strategy.resolve("PERSON", "Alexander", List.of("John", "Jane"));

    assertNull(result);
  }

  @Test
  void thresholdBoundary_exactlyAtThreshold() {
    // "Martha" vs "Marhta" — Jaro-Winkler ~0.96, well above 0.88
    String result = strategy.resolve("PERSON", "Martha", List.of("Marhta"));

    assertEquals("Marhta", result);
  }

  @Test
  void longStringOver64Chars_usesArrayFallback() {
    String long1 = "A".repeat(50) + "B".repeat(20); // 70 chars
    String long2 = "A".repeat(50) + "C".repeat(20); // 70 chars

    String result = strategy.resolve("PERSON", long1, List.of(long2));

    assertNotNull(result); // Should match — 50/70 shared prefix
  }

  @Test
  void emptyExisting_returnsNull() {
    String result = strategy.resolve("PERSON", "John", List.of());

    assertNull(result);
  }

  @Test
  void multipleExisting_bestMatchReturned() {
    String result = strategy.resolve("PERSON", "Johnathan", List.of("John", "Jonathan", "Jane"));

    // "Johnathan" is closer to "Jonathan" than to "John"
    assertEquals("Jonathan", result);
  }

  @Test
  void nullNewEntity_returnsNull() {
    String result = strategy.resolve("PERSON", null, List.of("John"));

    assertNull(result);
  }

  @Test
  void blankNewEntity_returnsNull() {
    String result = strategy.resolve("PERSON", "  ", List.of("John"));

    assertNull(result);
  }
}

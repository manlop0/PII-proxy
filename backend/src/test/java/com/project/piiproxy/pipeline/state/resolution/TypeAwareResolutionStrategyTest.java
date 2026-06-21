package com.project.piiproxy.pipeline.state.resolution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeAwareResolutionStrategyTest {

  private TypeAwareResolutionStrategy strategy;

  @BeforeEach
  void setUp() {
    JaroWinklerResolutionStrategy fuzzy = new JaroWinklerResolutionStrategy(0.88);
    strategy = new TypeAwareResolutionStrategy(Set.of("PERSON", "LOCATION", "ORGANIZATION"), fuzzy);
  }

  @Test
  void fuzzyType_delegatesToFuzzyStrategy() {
    String result = strategy.resolve("PERSON", "Johnson", List.of("John", "Jane"));

    assertEquals("John", result);
  }

  @Test
  void nonFuzzyType_delegatesToExact() {
    // PHONE is not in fuzzyTypes — should use exact match only
    String result = strategy.resolve("PHONE", "555-1234", List.of("555-1235", "555-1234"));

    assertEquals("555-1234", result);
  }

  @Test
  void nonFuzzyType_noMatchExactFallback() {
    String result = strategy.resolve("PHONE", "555-1234", List.of("555-1235"));

    assertNull(result);
  }

  @Test
  void fuzzyType_noMatch_returnsNull() {
    String result = strategy.resolve("PERSON", "Alexander", List.of("John", "Jane"));

    assertNull(result);
  }
}

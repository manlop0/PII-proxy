package com.project.piiproxy.pipeline.state.resolution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExactMatchResolutionStrategyTest {

  private ExactMatchResolutionStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new ExactMatchResolutionStrategy();
  }

  @Test
  void exactMatch_returnsMatch() {
    String result = strategy.resolve("EMAIL", "test@mail.com", List.of("test@mail.com", "other@mail.com"));

    assertEquals("test@mail.com", result);
  }

  @Test
  void noMatch_returnsNull() {
    String result = strategy.resolve("EMAIL", "test@mail.com", List.of("other@mail.com"));

    assertNull(result);
  }

  @Test
  void nullNewEntity_returnsNull() {
    String result = strategy.resolve("EMAIL", null, List.of("test@mail.com"));

    assertNull(result);
  }
}

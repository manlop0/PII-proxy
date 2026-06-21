package com.project.piiproxy.pipeline.state.resolution;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionStrategyFactoryTest {

  @Test
  void exact_createsExactStrategy() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create("exact", 0.88, Set.of("PERSON"));

    assertInstanceOf(ExactMatchResolutionStrategy.class, strategy);
  }

  @Test
  void jaroWinkler_createsTypeAware() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create("jaro-winkler", 0.88, Set.of("PERSON"));

    assertInstanceOf(TypeAwareResolutionStrategy.class, strategy);
  }

  @Test
  void nullAlgorithm_createsExact() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create(null, 0.88, Set.of("PERSON"));

    assertInstanceOf(ExactMatchResolutionStrategy.class, strategy);
  }

  @Test
  void jaroWinklerWithEmptyFuzzyTypes_createsBaseOnly() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create("jaro-winkler", 0.88, Set.of());

    assertInstanceOf(JaroWinklerResolutionStrategy.class, strategy);
  }
}

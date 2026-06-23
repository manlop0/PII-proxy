package com.project.piiproxy.pipeline.state.resolution;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionStrategyFactoryTest {

  @Test
  void exact_createsTypeAware() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create("exact", 0.88, Set.of("PERSON"));

    assertInstanceOf(TypeAwareResolutionStrategy.class, strategy);
  }

  @Test
  void jaroWinkler_createsTypeAware() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create("jaro-winkler", 0.88, Set.of("PERSON"));

    assertInstanceOf(TypeAwareResolutionStrategy.class, strategy);
  }

  @Test
  void nullAlgorithm_createsTypeAware() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create(null, 0.88, Set.of("PERSON"));

    assertInstanceOf(TypeAwareResolutionStrategy.class, strategy);
  }

  @Test
  void exactWithEmptyFuzzyTypes_createsExactOnly() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create("exact", 0.88, Set.of());

    assertInstanceOf(ExactMatchResolutionStrategy.class, strategy);
  }

  @Test
  void jaroWinklerWithEmptyFuzzyTypes_createsBaseOnly() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create("jaro-winkler", 0.88, Set.of());

    assertInstanceOf(JaroWinklerResolutionStrategy.class, strategy);
  }

  @Test
  void customFqcn_loadedViaReflection() {
    EntityResolutionStrategy strategy = ResolutionStrategyFactory.create(
        "com.project.piiproxy.pipeline.state.resolution.ExactMatchResolutionStrategy",
        0.88, Set.of());

    assertInstanceOf(ExactMatchResolutionStrategy.class, strategy);
  }

  @Test
  void invalidFqcn_throwsException() {
    assertThrows(IllegalArgumentException.class, () ->
        ResolutionStrategyFactory.create("com.nonexistent.NotAClass", 0.88, Set.of()));
  }

  @Test
  void wrongInterfaceFqcn_throwsException() {
    assertThrows(IllegalArgumentException.class, () ->
        ResolutionStrategyFactory.create("java.lang.String", 0.88, Set.of()));
  }
}

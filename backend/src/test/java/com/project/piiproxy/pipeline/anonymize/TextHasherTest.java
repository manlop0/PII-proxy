package com.project.piiproxy.pipeline.anonymize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextHasherTest {

  private TextHasher hasher;

  @BeforeEach
  void setUp() {
    hasher = new TextHasher();
  }

  @Test
  void deterministic_sameInputSameHash() {
    String hash1 = hasher.computeHash("hello world");
    String hash2 = hasher.computeHash("hello world");

    assertEquals(hash1, hash2);
  }

  @Test
  void differentInputs_differentHashes() {
    String hash1 = hasher.computeHash("hello");
    String hash2 = hasher.computeHash("world");

    assertNotEquals(hash1, hash2);
  }

  @Test
  void format_is64CharHex() {
    String hash = hasher.computeHash("test");

    assertEquals(64, hash.length());
    assertTrue(hash.matches("[0-9a-f]{64}"), "Hash should be lowercase hex");
  }
}

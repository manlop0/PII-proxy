package com.project.piiproxy.pipeline;

/**
 * Central registry of Vert.x Event Bus addresses used across the application.
 * Keeps the bus topology in one place and prevents string drift between senders and consumers.
 */
public final class BusAddresses {

  public static final String ML_NER_ANALYZE = "ml.ner.analyze";

  private BusAddresses() {
  }
}

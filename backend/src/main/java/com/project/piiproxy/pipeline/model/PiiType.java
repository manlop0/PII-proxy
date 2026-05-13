package com.project.piiproxy.pipeline.model;

public enum PiiType {
  // Deterministic (Regex tags)
  EMAIL,
  PHONE,
  CREDIT_CARD,
  IP_ADDRESS,

  // Model-dependent (Model tags)
  MODEL
}

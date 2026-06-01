package com.project.piiproxy.pipeline.model;

/** PII taxonomy used by regex filters. Model-detected tags flow through {@link PiiType#MODEL} with the raw label carried separately. */
public enum PiiType {
  // Deterministic (Regex tags)
  EMAIL,
  PHONE,
  CREDIT_CARD,
  IP_ADDRESS,

  // Model-dependent (Model tags)
  MODEL
}

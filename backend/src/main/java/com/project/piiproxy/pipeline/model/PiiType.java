package com.project.piiproxy.pipeline.model;

public enum PiiType {
  // Deterministic
  EMAIL,
  PHONE,
  CREDIT_CARD,
  IP_ADDRESS,

  // Context-dependent
  PERSON,
  LOCATION,
  ORGANIZATION
}

package com.project.piiproxy.pipeline.model;

/** Strategy for resolving overlaps between regex and ML spans ({@code LONGEST_WINS}, {@code REGEX_PRIORITY}). */
public enum ConflictStrategy {
  LONGEST_WINS,
  REGEX_PRIORITY
}

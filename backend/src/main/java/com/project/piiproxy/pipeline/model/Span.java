package com.project.piiproxy.pipeline.model;

public record Span(int start, int end, PiiType type, String value) { }

package com.project.piiproxy.provider;

import com.project.piiproxy.provider.adapter.LlmJsonCodec;

/** Provider descriptor: identity, upstream host/port, and the matching JSON codec. */
public interface LlmEndpoint {

  String getId();

  String getHost();

  int getPort();

  LlmJsonCodec getCodec();
}

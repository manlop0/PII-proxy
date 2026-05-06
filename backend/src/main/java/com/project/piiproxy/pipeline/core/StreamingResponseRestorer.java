package com.project.piiproxy.pipeline.core;

import com.project.piiproxy.provider.adapter.LlmJsonAdapter;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

public class StreamingResponseRestorer {

  private final TextAnalyzer analyzer;

  public StreamingResponseRestorer(TextAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public Handler<Buffer> createStreamHandler(RoutingContext ctx, String sessionId, LlmJsonAdapter adapter) {
    Map<String, SessionStreamProcessor> processors = new HashMap<>();
    for (String key : adapter.getStreamProcessorKeys()) {
      processors.put(key, new SessionStreamProcessor(sessionId, analyzer));
    }

    return RecordParser.newDelimited("\n\n", eventBuffer -> {
      String eventStr = eventBuffer.toString("UTF-8").trim();

      if (eventStr.isEmpty()) return;

      if (eventStr.equals("data: [DONE]")) {
        processors.values().forEach(SessionStreamProcessor::flushCache);
        ctx.response().write("data: [DONE]\n\n");
        return;
      }

      if (eventStr.startsWith("data: ")) {
        String jsonStr = eventStr.substring(6);
        try {
          JsonObject json = new JsonObject(jsonStr);

          adapter.restoreStreamChunk(json, processors);

          ctx.response().write("data: " + json.encode() + "\n\n");

        } catch (DecodeException e) {
          ctx.response().write(eventBuffer.appendString("\n\n"));
        }
      } else {
        ctx.response().write(eventBuffer.appendString("\n\n"));
      }
    });
  }
}

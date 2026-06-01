package com.project.piiproxy.pipeline.restore;

import com.project.piiproxy.pipeline.anonymize.TextAnalyzer;
import com.project.piiproxy.pipeline.stream.SessionStreamProcessor;
import com.project.piiproxy.provider.adapter.LlmJsonCodec;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a streaming SSE response handler that re-inserts PII tags on the fly.
 * Uses Vert.x {@link RecordParser} to split frames and one {@link SessionStreamProcessor} per streamable key.
 */
public class StreamingResponseRestorer {

  private final TextAnalyzer analyzer;

  public StreamingResponseRestorer(TextAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public Handler<Buffer> createStreamHandler(RoutingContext ctx, String sessionId, LlmJsonCodec codec) {
    Map<String, SessionStreamProcessor> processors = new HashMap<>();
    for (String key : codec.getStreamProcessorKeys()) {
      processors.put(key, new SessionStreamProcessor(sessionId, key, analyzer));
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

          codec.restoreStreamChunk(json, processors)
            .onComplete(ar -> {
              if (ar.succeeded()) {
                ctx.response().write("data: " + json.encode() + "\n\n");
              } else {
                ctx.response().write(eventBuffer.appendString("\n\n"));
              }
            });

        } catch (DecodeException e) {
          ctx.response().write(eventBuffer.appendString("\n\n"));
        }
      } else {
        ctx.response().write(eventBuffer.appendString("\n\n"));
      }
    });
  }
}

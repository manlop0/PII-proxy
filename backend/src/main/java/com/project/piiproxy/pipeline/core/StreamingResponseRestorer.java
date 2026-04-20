package com.project.piiproxy.pipeline.core;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.ext.web.RoutingContext;

public class StreamingResponseRestorer {

  private final TextAnalyzer analyzer;

  public StreamingResponseRestorer(TextAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public Handler<Buffer> createStreamHandler(RoutingContext ctx, String sessionId) {
    SessionStreamProcessor processor = new SessionStreamProcessor(sessionId, analyzer);

    return RecordParser.newDelimited("\n\n", eventBuffer -> {
      String eventStr = eventBuffer.toString("UTF-8").trim();

      if (eventStr.isEmpty()) return;
      if (eventStr.equals("data: [DONE]")) {
        ctx.response().write("data: [DONE]\n\n");
        return;
      }

      if (eventStr.startsWith("data: ")) {
        String jsonStr = eventStr.substring(("data: ").length());
        try {
          JsonObject json = new JsonObject(jsonStr);
          JsonArray choices = json.getJsonArray("choices");

          if (choices != null && !choices.isEmpty()) {
            JsonObject delta = choices.getJsonObject(0).getJsonObject("delta");
            if (delta != null && delta.containsKey("content")) {

              String originalContent = delta.getString("content");
              String safeContent = processor.processChunk(originalContent);

              delta.put("content", safeContent);
            }
          }
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

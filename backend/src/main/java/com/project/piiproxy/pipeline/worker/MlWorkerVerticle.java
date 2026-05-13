package com.project.piiproxy.pipeline.worker;

import com.project.piiproxy.pipeline.filter.ml.NerModelFilter;
import com.project.piiproxy.pipeline.model.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MlWorkerVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(MlWorkerVerticle.class);

  private final NerModelFilter mlFilter;

  public MlWorkerVerticle(NerModelFilter mlFilter) {
    this.mlFilter = mlFilter;
  }

  @Override
  public void start() {
    vertx.eventBus().<String>consumer("ml.ner.analyze", msg -> {
      String text = msg.body();

      if (text == null || text.isBlank()) {
        msg.reply(new JsonArray());
        return;
      }

      try {
        List<Span> spans = mlFilter.find(text);

        JsonArray result = new JsonArray();
        for (Span s : spans) {
          result.add(JsonObject.mapFrom(s));
        }

        msg.reply(result);

      } catch (Exception e) {
        msg.fail(500, "ML Inference failed: " + e.getMessage());
      }
    });

    log.info("ML Worker Verticle started on thread: {}", Thread.currentThread().getName());
  }
}

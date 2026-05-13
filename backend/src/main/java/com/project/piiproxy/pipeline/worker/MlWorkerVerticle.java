package com.project.piiproxy.pipeline.worker;

import com.project.piiproxy.pipeline.filter.ml.NerModelFilter;
import com.project.piiproxy.pipeline.model.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class MlWorkerVerticle extends AbstractVerticle {

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

        // === ДЕБАГ: Смотрим, что воркер отправляет обратно в главный поток ===
        System.out.println("[DEBUG Worker] Отправка спанов: " + result.encodePrettily());
        //

        msg.reply(result);

      } catch (Exception e) {
        msg.fail(500, "ML Inference failed: " + e.getMessage());
      }
    });

    System.out.println("ML Worker Verticle started on thread: " + Thread.currentThread().getName());
  }
}

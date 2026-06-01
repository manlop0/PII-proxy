package com.project.piiproxy.pipeline.worker;

import com.project.piiproxy.pipeline.BusAddresses;
import com.project.piiproxy.pipeline.filter.ml.NerModelFilter;
import com.project.piiproxy.pipeline.model.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates concurrent requests into ML inference batches and forwards them to {@link NerModelFilter}.
 * Deployed as a single Event Loop instance so that Vert.x's Round-Robin dispatch cannot fragment batches.
 */
public class MlBatchAggregatorVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(MlBatchAggregatorVerticle.class);

  private final NerModelFilter mlFilter;
  private final int batchSize;
  private final int batchTimeoutMs;

  private List<Message<String>> currentBatch = new ArrayList<>();
  private long timerId = -1;
  private Promise<Void> currentFlush;

  public MlBatchAggregatorVerticle(NerModelFilter mlFilter, int batchSize, int batchTimeoutMs) {
    this.mlFilter = mlFilter;
    this.batchSize = batchSize;
    this.batchTimeoutMs = batchTimeoutMs;
  }

  @Override
  public void start() {
    vertx.eventBus().<String>consumer(BusAddresses.ML_NER_ANALYZE, msg -> {
      String text = msg.body();

      if (text == null || text.isBlank()) {
        msg.reply(new JsonArray());
        return;
      }

      currentBatch.add(msg);

      if (currentBatch.size() == 1) {
        timerId = vertx.setTimer(batchTimeoutMs, id -> flushBatch());
      }

      if (currentBatch.size() >= batchSize) {
        if (timerId != -1) {
          vertx.cancelTimer(timerId);
          timerId = -1;
        }
        flushBatch();
      }
    });

    log.info("ML Aggregator Verticle started on Event Loop thread: {}", Thread.currentThread().getName());
  }

  private void flushBatch() {
    if (currentBatch.isEmpty()) {
      return;
    }

    timerId = -1;
    List<Message<String>> batchToProcess = new ArrayList<>(currentBatch);
    currentBatch.clear();

    Promise<Void> flushPromise = Promise.promise();
    currentFlush = flushPromise;

    vertx.executeBlocking(() -> {
      List<String> texts = new ArrayList<>(batchToProcess.size());
      for (Message<String> msg : batchToProcess) {
        texts.add(msg.body());
      }
      return mlFilter.findBatch(texts);
    }, false).onComplete(ar -> {
      if (ar.succeeded()) {
        List<List<Span>> batchResults = ar.result();
        for (int i = 0; i < batchToProcess.size(); i++) {
          List<Span> spans = batchResults.get(i);
          JsonArray result = new JsonArray();
          for (Span s : spans) {
            result.add(JsonObject.mapFrom(s));
          }
          batchToProcess.get(i).reply(result);
        }
      } else {
        log.error("ML Inference batch failed", ar.cause());
        for (Message<String> msg : batchToProcess) {
          msg.fail(500, "ML Inference failed: " + ar.cause().getMessage());
        }
      }
      currentFlush = null;
      flushPromise.complete();
    });
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    log.info("ML Aggregator stopping. Cancelling timer and flushing pending batch ({} messages)...", currentBatch.size());

    if (timerId != -1) {
      vertx.cancelTimer(timerId);
      timerId = -1;
    }

    Runnable closeMlFilter = () -> {
      try {
        mlFilter.close();
        log.info("ML filter resources released.");
        stopPromise.complete();
      } catch (Exception e) {
        log.error("Error closing ML filter", e);
        stopPromise.fail(e);
      }
    };

    if (!currentBatch.isEmpty()) {
      flushBatch();
    }

    if (currentFlush != null) {
      currentFlush.future().onComplete(v -> closeMlFilter.run());
    } else {
      closeMlFilter.run();
    }
  }
}

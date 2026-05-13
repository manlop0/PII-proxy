package com.project.piiproxy.pipeline.filter.ml.adapter;

import ai.djl.huggingface.tokenizers.jni.CharSpan;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;

import java.util.*;

/**
 * Universal adapter for models using the BIO (Begin, Inside, Outside) tagging scheme.
 */
public class BioOutputAdapter implements ModelOutputAdapter {

  private final Map<Integer, String> id2label;
  private final Set<String> ignoredTags;

  public BioOutputAdapter(Map<Integer, String> id2label, Set<String> ignoredTags) {
    this.id2label = id2label;
    this.ignoredTags = ignoredTags;
  }

  @Override
  public List<Span> extractSpans(OrtSession.Result result, CharSpan[] charSpans, String originalText) throws OrtException {
    float[][][] logits = (float[][][]) result.get(0).getValue();
    float[][] sequenceLogits = logits[0];

    List<Span> spans = new ArrayList<>();
    String currentEntity = null;
    int currentStart = -1;
    int currentEnd = -1;

    for (int i = 0; i < sequenceLogits.length; i++) {
      if (charSpans[i] == null) continue;

      int classIdx = argmax(sequenceLogits[i]);
      String label = id2label.getOrDefault(classIdx, "O");

      if (label.startsWith("B-")) {
        flush(spans, originalText, currentStart, currentEnd, currentEntity);

        currentEntity = label.substring(2);
        currentStart = charSpans[i].getStart();
        currentEnd = charSpans[i].getEnd();
      } else if (label.startsWith("I-") && currentEntity != null && label.endsWith(currentEntity)) {
        currentEnd = charSpans[i].getEnd();
      } else {
        flush(spans, originalText, currentStart, currentEnd, currentEntity);
        currentEntity = null;
      }
    }
    flush(spans, originalText, currentStart, currentEnd, currentEntity);
    return spans;
  }

  private void flush(List<Span> spans, String text, int start, int end, String entity) {
    if (entity != null && !ignoredTags.contains(entity)) {
      spans.add(new Span(start, end, PiiType.MODEL, entity, text.substring(start, end)));
    }
  }

  private int argmax(float[] array) {
    int maxIdx = 0;
    for (int i = 1; i < array.length; i++) {
      if (array[i] > array[maxIdx]) maxIdx = i;
    }
    return maxIdx;
  }
}

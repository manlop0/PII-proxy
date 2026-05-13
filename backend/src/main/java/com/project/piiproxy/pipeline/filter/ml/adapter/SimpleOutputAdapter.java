package com.project.piiproxy.pipeline.filter.ml.adapter;

import ai.djl.huggingface.tokenizers.jni.CharSpan;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Universal adapter for models that output simple tags without BIO scheme.
 * 1 token = 1 entity span. Consecutive tokens with the same tag are NOT merged.
 */
public class SimpleOutputAdapter implements ModelOutputAdapter {

  private static final Logger log = LoggerFactory.getLogger(SimpleOutputAdapter.class);

  private final Map<Integer, String> id2label;
  private final Set<String> ignoredTags;
  private final Map<String, String> tagMapping;

  private final List<String> activeTags = new ArrayList<>();
  private final List<String> disabledTags = new ArrayList<>();

  public SimpleOutputAdapter(Map<Integer, String> id2label, Set<String> ignoredTags, Map<String, String> tagMapping) {
    this.id2label = id2label;
    this.ignoredTags = ignoredTags != null ? ignoredTags : Collections.emptySet();
    this.tagMapping = tagMapping != null ? tagMapping : Collections.emptyMap();

    Set<String> allTags = new HashSet<>();
    for (String label : id2label.values()) {
      if (!"O".equals(label)) {
        allTags.add(label);
      }
    }

    for (String tag : allTags) {
      if (this.ignoredTags.contains(tag)) {
        disabledTags.add(tag);
      } else {
        activeTags.add(tag);
      }
    }
  }

  @Override
  public List<Span> extractSpans(OrtSession.Result result, CharSpan[] charSpans, String originalText) throws OrtException {
    float[][][] logits = (float[][][]) result.get(0).getValue();
    float[][] sequenceLogits = logits[0];

    List<Span> spans = new ArrayList<>();

    for (int i = 0; i < sequenceLogits.length; i++) {
      if (charSpans[i] == null) continue;

      int classIdx = argmax(sequenceLogits[i]);
      String label = id2label.getOrDefault(classIdx, "O");

      if (!"O".equals(label)) {
        String tokenStr = originalText.substring(charSpans[i].getStart(), charSpans[i].getEnd());
        log.trace("ML-Adapter Token: '{}' -> Tag: {}", tokenStr, label);

        if (!ignoredTags.contains(label)) {
          String mappedEntity = tagMapping.getOrDefault(label, label);
          spans.add(new Span(charSpans[i].getStart(), charSpans[i].getEnd(), PiiType.MODEL, mappedEntity, tokenStr));
        }
      }
    }
    return spans;
  }

  private int argmax(float[] array) {
    int maxIdx = 0;
    for (int i = 1; i < array.length; i++) {
      if (array[i] > array[maxIdx]) maxIdx = i;
    }
    return maxIdx;
  }

  @Override
  public List<String> getActiveTags() {
    return activeTags;
  }

  @Override
  public List<String> getIgnoredTags() {
    return disabledTags;
  }
}

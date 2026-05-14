package com.project.piiproxy.pipeline.filter.ml.adapter;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.jni.CharSpan;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.project.piiproxy.pipeline.model.PiiType;
import com.project.piiproxy.pipeline.model.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
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
  public List<List<Span>> extractBatchSpans(OrtSession.Result result, Encoding[] encodings, List<String> originalTexts) throws OrtException {
    OnnxTensor tensor = (OnnxTensor) result.get(0);
    FloatBuffer buffer = tensor.getFloatBuffer();
    long[] shape = tensor.getInfo().getShape();

    int batchSize = (int) shape[0];
    int seqLen = (int) shape[1];
    int numLabels = (int) shape[2];

    List<List<Span>> batchSpans = new ArrayList<>(batchSize);

    for (int b = 0; b < batchSize; b++) {
      CharSpan[] charSpans = encodings[b].getCharTokenSpans();
      String originalText = originalTexts.get(b);
      batchSpans.add(processSequence(buffer, b, seqLen, numLabels, charSpans, originalText));
    }

    return batchSpans;
  }

  private List<Span> processSequence(FloatBuffer buffer, int batchIdx, int seqLen, int numLabels, CharSpan[] charSpans, String originalText) {
    List<Span> spans = new ArrayList<>();

    int batchOffset = batchIdx * seqLen * numLabels;

    for (int i = 0; i < seqLen; i++) {
      if (i >= charSpans.length || charSpans[i] == null) continue;

      int tokenOffset = batchOffset + (i * numLabels);
      int classIdx = argmax(buffer, tokenOffset, numLabels);
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

  private int argmax(FloatBuffer buffer, int tokenOffset, int numLabels) {
    int maxIdx = 0;
    float maxVal = buffer.get(tokenOffset);
    for (int i = 1; i < numLabels; i++) {
      float val = buffer.get(tokenOffset + i);
      if (val > maxVal) {
        maxIdx = i;
        maxVal = val;
      }
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

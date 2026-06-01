package com.project.piiproxy.pipeline.filter.ml.adapter;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.project.piiproxy.pipeline.model.Span;

import java.util.List;

/** Contract for adapters that turn a raw ONNX inference result into a list of detected {@link Span}s per request. */
public interface ModelOutputAdapter {

  List<List<Span>> extractBatchSpans(OrtSession.Result result, Encoding[] encodings, List<String> originalTexts) throws OrtException;

  List<String> getActiveTags();

  List<String> getIgnoredTags();

}

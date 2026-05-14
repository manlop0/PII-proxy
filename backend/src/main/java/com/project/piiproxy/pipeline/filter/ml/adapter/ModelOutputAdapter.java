package com.project.piiproxy.pipeline.filter.ml.adapter;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.project.piiproxy.pipeline.model.Span;

import java.util.List;

public interface ModelOutputAdapter {

  List<List<Span>> extractBatchSpans(OrtSession.Result result, Encoding[] encodings, List<String> originalTexts) throws OrtException;

  List<String> getActiveTags();

  List<String> getIgnoredTags();

}

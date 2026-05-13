package com.project.piiproxy.pipeline.filter.ml.adapter;

import ai.djl.huggingface.tokenizers.jni.CharSpan;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.project.piiproxy.pipeline.model.Span;

import java.util.List;

public interface ModelOutputAdapter {

  List<Span> extractSpans(OrtSession.Result result, CharSpan[] charSpans, String originalText) throws OrtException;

}

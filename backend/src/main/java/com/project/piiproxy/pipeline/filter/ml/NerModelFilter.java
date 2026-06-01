package com.project.piiproxy.pipeline.filter.ml;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.project.piiproxy.pipeline.filter.ml.adapter.ModelOutputAdapter;
import com.project.piiproxy.pipeline.filter.ml.adapter.OutputAdapterFactory;
import com.project.piiproxy.pipeline.model.Span;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.util.*;

/**
 * ML-based PII detector backed by an ONNX model and a HuggingFace tokenizer.
 * Token IDs are written directly into an off-heap {@link ByteBuffer} (zero-GC) and inference
 * is dispatched to a worker pool via {@code executeBlocking}.
 */
public class NerModelFilter implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(NerModelFilter.class);

  private final HuggingFaceTokenizer tokenizer;
  private final OrtEnvironment env;
  private final OrtSession session;
  private final ModelOutputAdapter outputAdapter;

  private final Set<String> expectedInputs;

  public NerModelFilter(String modelDir, int intraThreads, Set<String> ignoredTags, Map<String, String> tagMapping, String adapterType) throws Exception {
    File dir = new File(modelDir);
    if (!dir.exists() || !dir.isDirectory()) {
      throw new IllegalArgumentException("Model directory not found: " + modelDir);
    }

    File onnxFile = findFile(dir, ".onnx");
    File configFile = findFile(dir, "config.json");
    File tokenizerFile = findFile(dir, "tokenizer.json");

    this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath());

    JsonObject configJson = new JsonObject(Files.readString(configFile.toPath()));
    Map<Integer, String> id2label = new HashMap<>();
    JsonObject labels = configJson.getJsonObject("id2label");
    for (String key : labels.fieldNames()) {
      id2label.put(Integer.parseInt(key), labels.getString(key));
    }

    this.outputAdapter = OutputAdapterFactory.create(adapterType, id2label, ignoredTags, tagMapping);

    log.info("ML Model Tags -> Active: {}, Ignored: {}", outputAdapter.getActiveTags(), outputAdapter.getIgnoredTags());

    this.env = OrtEnvironment.getEnvironment();
    OrtSession.SessionOptions options = new OrtSession.SessionOptions();
    options.setIntraOpNumThreads(intraThreads);
    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

    this.session = env.createSession(onnxFile.getAbsolutePath(), options);
    this.expectedInputs = session.getInputNames();
  }

  private File findFile(File dir, String suffix) {
    return Arrays.stream(dir.listFiles())
      .filter(f -> f.getName().endsWith(suffix))
      .findFirst()
      .orElseThrow(() -> new RuntimeException("Missing required ML file: *" + suffix + " in " + dir.getPath()));
  }

  public List<List<Span>> findBatch(List<String> texts) {
    if (texts == null || texts.isEmpty()) return Collections.emptyList();

    Encoding[] encodings = tokenizer.batchEncode(texts);
    int batchSize = encodings.length;
    int maxLen = 0;

    for (Encoding e : encodings) {
      if (e.getIds().length > maxLen) {
        maxLen = e.getIds().length;
      }
    }

    long[] shape = {batchSize, maxLen};
    int totalElements = batchSize * maxLen;
    int byteSize = totalElements * 8;

    ByteBuffer idBytes = null;
    ByteBuffer maskBytes = null;
    ByteBuffer typeBytes = null;

    if (expectedInputs.contains("input_ids")) {
      idBytes = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder());
    }
    if (expectedInputs.contains("attention_mask")) {
      maskBytes = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder());
    }
    if (expectedInputs.contains("token_type_ids")) {
      typeBytes = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder());
    }

    LongBuffer idBuffer = idBytes != null ? idBytes.asLongBuffer() : null;
    LongBuffer maskBuffer = maskBytes != null ? maskBytes.asLongBuffer() : null;
    LongBuffer typeBuffer = typeBytes != null ? typeBytes.asLongBuffer() : null;

    for (int i = 0; i < batchSize; i++) {
      Encoding e = encodings[i];
      int offset = i * maxLen;

      // Zero-GC: write token ids, masks and type ids directly into the off-heap LongBuffer views.
      if (idBuffer != null) {
        idBuffer.position(offset).put(e.getIds());
      }
      if (maskBuffer != null) {
        maskBuffer.position(offset).put(e.getAttentionMask());
      }
      if (typeBuffer != null) {
        typeBuffer.position(offset).put(e.getTypeIds());
      }
    }

    Map<String, OnnxTensor> tensorMap = new HashMap<>();

    try {
      if (idBuffer != null) {
        idBuffer.rewind();
        tensorMap.put("input_ids", OnnxTensor.createTensor(env, idBuffer, shape));
      }
      if (maskBuffer != null) {
        maskBuffer.rewind();
        tensorMap.put("attention_mask", OnnxTensor.createTensor(env, maskBuffer, shape));
      }
      if (typeBuffer != null) {
        typeBuffer.rewind();
        tensorMap.put("token_type_ids", OnnxTensor.createTensor(env, typeBuffer, shape));
      }

      try (OrtSession.Result result = session.run(tensorMap)) {
        return outputAdapter.extractBatchSpans(result, encodings, texts);
      }

    } catch (Exception e) {
      throw new RuntimeException("ML Inference batch failed", e);
    } finally {
      for (OnnxTensor tensor : tensorMap.values()) {
        if (tensor != null) {
          tensor.close();
        }
      }
    }
  }

  @Override
  public void close() throws Exception {
    if (session != null) session.close();
    if (env != null) env.close();
    if (tokenizer != null) tokenizer.close();
  }
}

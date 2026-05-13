package com.project.piiproxy.pipeline.filter.ml;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import com.project.piiproxy.pipeline.filter.TextFilter;
import com.project.piiproxy.pipeline.filter.ml.adapter.ModelOutputAdapter;
import com.project.piiproxy.pipeline.filter.ml.adapter.OutputAdapterFactory;
import com.project.piiproxy.pipeline.model.Span;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.*;

public class NerModelFilter implements TextFilter, AutoCloseable {

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

  @Override
  public List<Span> find(String text) {
    if (text == null || text.isBlank()) return Collections.emptyList();

    Encoding encoding = tokenizer.encode(text);
    long[] ids = encoding.getIds();
    long[] shape = {1, ids.length};

    Map<String, OnnxTensor> tensorMap = new HashMap<>();

    // Input tensors
    try {
      // INPUT_IDS
      if (expectedInputs.contains("input_ids")) {
        ByteBuffer idBytes = ByteBuffer.allocateDirect(ids.length * 8).order(ByteOrder.nativeOrder());
        idBytes.asLongBuffer().put(ids).rewind();
        tensorMap.put("input_ids", OnnxTensor.createTensor(env, idBytes.asLongBuffer(), shape));
      }

      // ATTENTION_MASK
      if (expectedInputs.contains("attention_mask")) {
        long[] mask = encoding.getAttentionMask();
        ByteBuffer maskBytes = ByteBuffer.allocateDirect(mask.length * 8).order(ByteOrder.nativeOrder());
        maskBytes.asLongBuffer().put(mask).rewind();
        tensorMap.put("attention_mask", OnnxTensor.createTensor(env, maskBytes.asLongBuffer(), shape));
      }

      // TOKEN_TYPE_IDS
      if (expectedInputs.contains("token_type_ids")) {
        long[] typeIds = encoding.getTypeIds();
        ByteBuffer typeBytes = ByteBuffer.allocateDirect(typeIds.length * 8).order(ByteOrder.nativeOrder());
        typeBytes.asLongBuffer().put(typeIds).rewind();
        tensorMap.put("token_type_ids", OnnxTensor.createTensor(env, typeBytes.asLongBuffer(), shape));
      }

      try (OrtSession.Result result = session.run(tensorMap)) {
        return outputAdapter.extractSpans(result, encoding.getCharTokenSpans(), text);
      }

    } catch (Exception e) {
      throw new RuntimeException("ML Inference failed", e);
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

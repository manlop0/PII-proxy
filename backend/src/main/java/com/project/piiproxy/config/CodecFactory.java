package com.project.piiproxy.config;

import com.project.piiproxy.provider.codec.LlmJsonCodec;
import com.project.piiproxy.provider.codec.OpenAiCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates {@link LlmJsonCodec} instances from config values.
 * Supports shortnames for built-in codecs and Fully Qualified Class Names (FQCN) for custom ones.
 *
 * <p>Built-in shortnames:</p>
 * <ul>
 *   <li>{@code "openai"} — {@link OpenAiCodec} (also the default)</li>
 * </ul>
 *
 * <p>Custom codecs: pass the FQCN of a class implementing {@link LlmJsonCodec} with a no-arg constructor.</p>
 * <pre>{@code
 * providers:
 *   custom_provider:
 *     host: api.example.com
 *     port: 443
 *     codec: "com.project.piiproxy.provider.codec.MyCustomCodec"
 * }</pre>
 */
public class CodecFactory {

  private static final Logger log = LoggerFactory.getLogger(CodecFactory.class);

  public static LlmJsonCodec create(String codecId) {
    if (codecId == null || codecId.isBlank() || "openai".equalsIgnoreCase(codecId)) {
      return new OpenAiCodec();
    }

    try {
      Class<?> clazz = Class.forName(codecId);
      LlmJsonCodec codec = (LlmJsonCodec) clazz.getDeclaredConstructor().newInstance();
      log.info("Loaded custom codec: {}", codecId);
      return codec;
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Codec class not found: " + codecId, e);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Codec class does not implement LlmJsonCodec: " + codecId, e);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to instantiate codec: " + codecId
        + " (must have a public no-arg constructor)", e);
    }
  }
}

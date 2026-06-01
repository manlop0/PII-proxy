package com.project.piiproxy.pipeline.state;

import com.project.piiproxy.pipeline.state.resolution.EntityResolutionStrategy;
import com.project.piiproxy.pipeline.state.resolution.ExactMatchResolutionStrategy;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Off-heap PII storage backed by MapDB. Persists original-to-tag and tag-to-original mappings
 * and delegates entity coreference (e.g. matching "John" to "John's") to a pluggable
 * {@link EntityResolutionStrategy}.
 */
public class MapDbStorage implements PiiStorage, SessionCleaner {

  private static final Logger log = LoggerFactory.getLogger(MapDbStorage.class);

  private final DB db;
  private final HTreeMap<String, String> piiMap;
  private final HTreeMap<String, String> reversePiiMap;
  private final HTreeMap<String, Integer> counters;
  private final HTreeMap<String, String> messageCache;
  private final EntityResolutionStrategy resolutionStrategy;

  public MapDbStorage(String dbPath, EntityResolutionStrategy resolutionStrategy) {
    this.resolutionStrategy = resolutionStrategy != null ? resolutionStrategy : new ExactMatchResolutionStrategy();
    File dbFile = new File(dbPath);

    File parentDir = dbFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      parentDir.mkdirs();
    }

    this.db = DBMaker.fileDB(dbFile)
      .fileMmapEnableIfSupported()
      .cleanerHackEnable()
      .transactionEnable()
      .closeOnJvmShutdown()
      .make();

    this.piiMap = db.hashMap("piiMap", Serializer.STRING, Serializer.STRING).createOrOpen();
    this.reversePiiMap = db.hashMap("reversePiiMap", Serializer.STRING, Serializer.STRING).createOrOpen();
    this.counters = db.hashMap("counters", Serializer.STRING, Serializer.INTEGER).createOrOpen();
    this.messageCache = db.hashMap("messageCache", Serializer.STRING, Serializer.STRING).createOrOpen();
  }

  @Override
  public String saveOriginal(String sessionId, String type, String originalValue) {
    String exactReverseKey = sessionId + "_" + type + "_" + originalValue;
    String exactExistingTag = reversePiiMap.get(exactReverseKey);
    if (exactExistingTag != null) {
      return exactExistingTag;
    }

    List<String> existingEntities = getExistingEntitiesForType(sessionId, type);
    if (!existingEntities.isEmpty()) {
      String resolvedEntity = resolutionStrategy.resolve(type, originalValue, existingEntities);
      if (resolvedEntity != null) {
        String resolvedReverseKey = sessionId + "_" + type + "_" + resolvedEntity;
        String resolvedTag = reversePiiMap.get(resolvedReverseKey);

        if (resolvedTag != null) {
          log.debug("[{}] FUZZY MATCH: '{}' resolved to existing '{}' -> {}", sessionId, originalValue, resolvedEntity, resolvedTag);
          reversePiiMap.put(exactReverseKey, resolvedTag);
          return resolvedTag;
        }
      }
    }

    String counterKey = sessionId + "_" + type;
    Integer currentCount = counters.compute(counterKey, (k, oldValue) ->
      (oldValue == null) ? 1 : oldValue + 1
    );

    String tag = "<" + type + "_" + currentCount + ">";
    String storageKey = sessionId + "_" + tag;

    piiMap.put(storageKey, originalValue);
    reversePiiMap.put(exactReverseKey, tag);

    log.debug("[{}] MAPPED: '{}' -> {}", sessionId, originalValue, tag);

    return tag;
  }

  private List<String> getExistingEntitiesForType(String sessionId, String type) {
    String prefix = sessionId + "_" + type + "_";
    List<String> entities = new ArrayList<>();

    for (String key : reversePiiMap.keySet()) {
      if (key.startsWith(prefix)) {
        entities.add(key.substring(prefix.length()));
      }
    }
    return entities;
  }

  @Override
  public String getOriginal(String sessionId, String tag) {
    return piiMap.get(sessionId + "_" + tag);
  }

  @Override
  public void cacheAnonymizedText(String sessionId, String textHash, String anonymizedText) {
    messageCache.put(sessionId + "_" + textHash, anonymizedText);
  }

  @Override
  public String getCachedAnonymizedText(String sessionId, String textHash) {
    return messageCache.get(sessionId + "_" + textHash);
  }

  @Override
  public void clearSession(String sessionId) {
    String prefix = sessionId + "_";
    piiMap.keySet().removeIf(key -> key.startsWith(prefix));
    counters.keySet().removeIf(key -> key.startsWith(prefix));
    messageCache.keySet().removeIf(key -> key.startsWith(prefix));
    reversePiiMap.keySet().removeIf(key -> key.startsWith(prefix));
  }

  @Override
  public void close() {
    if (!db.isClosed()) {
      db.close();
      log.info("MapDB storage closed.");
    }
  }
}

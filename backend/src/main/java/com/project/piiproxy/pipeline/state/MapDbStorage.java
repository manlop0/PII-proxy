package com.project.piiproxy.pipeline.state;

import com.project.piiproxy.pipeline.model.PiiType;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapDbStorage implements PiiStorage, SessionCleaner {

  private static final Logger log = LoggerFactory.getLogger(MapDbStorage.class);

  private final DB db;
  private final HTreeMap<String, String> piiMap;
  private final HTreeMap<String, String> reversePiiMap;
  private final HTreeMap<String, Integer> counters;
  private final HTreeMap<String, String> messageCache;

  public MapDbStorage(String dbPath) {
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

    String reverseKey = sessionId + "_" + type + "_" + originalValue;
    String existingTag = reversePiiMap.get(reverseKey);
    if (existingTag != null) {
      return existingTag;
    }

    String counterKey = sessionId + "_" + type;
    Integer currentCount = counters.compute(counterKey, (k, oldValue) ->
      (oldValue == null) ? 1 : oldValue + 1
    );

    String tag = "<" + type + "_" + currentCount + ">";
    String storageKey = sessionId + "_" + tag;

    piiMap.put(storageKey, originalValue);
    reversePiiMap.put(reverseKey, tag);

    log.debug("[{}] MAPPED: '{}' -> {}", sessionId, originalValue, tag);

    return tag;
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
}

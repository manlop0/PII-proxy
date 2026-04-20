package com.project.piiproxy.pipeline.state;

import com.project.piiproxy.pipeline.model.PiiType;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

public class MapDbStorage implements PiiStorage {

  private final DB db;
  private final HTreeMap<String, String> piiMap;
  private final HTreeMap<String, Integer> counters;
  private final HTreeMap<String, String> messageCache;

  public MapDbStorage() {
    this.db = DBMaker.memoryDB().closeOnJvmShutdown().make();
    this.piiMap = db.hashMap("piiMap", Serializer.STRING, Serializer.STRING).createOrOpen();
    this.counters = db.hashMap("counters", Serializer.STRING, Serializer.INTEGER).createOrOpen();
    this.messageCache = db.hashMap("messageCache", Serializer.STRING, Serializer.STRING).createOrOpen();
  }

  @Override
  public String saveOriginal(String sessionId, PiiType type, String originalValue) {
    String counterKey = sessionId + "_" + type.name();

    Integer currentCount = counters.compute(counterKey, (k, oldValue) ->
      (oldValue == null) ? 1 : oldValue + 1
    );

    String tag = "<" + type.name() + "_" + currentCount + ">";
    String storageKey = sessionId + "_" + tag;

    piiMap.put(storageKey, originalValue);

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
}

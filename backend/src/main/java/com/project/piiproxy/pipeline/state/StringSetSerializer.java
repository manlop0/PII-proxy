package com.project.piiproxy.pipeline.state;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Fast MapDB serializer for {@code Set<String>} using {@link DataInput2#readUTF()}
 */
public final class StringSetSerializer implements Serializer<Set<String>> {

  public static final StringSetSerializer INSTANCE = new StringSetSerializer();

  @Override
  public void serialize(DataOutput2 out, Set<String> value) throws IOException {
    out.writeInt(value.size());
    for (String s : value) {
      out.writeUTF(s);
    }
  }

  @Override
  public Set<String> deserialize(DataInput2 in, int available) throws IOException {
    int size = in.readInt();
    Set<String> set = new HashSet<>(Math.max(16, size * 2));
    for (int i = 0; i < size; i++) {
      set.add(in.readUTF());
    }
    return set;
  }
}

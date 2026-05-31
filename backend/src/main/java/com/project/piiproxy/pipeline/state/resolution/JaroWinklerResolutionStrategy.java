package com.project.piiproxy.pipeline.state.resolution;

/**
 * Strategy that uses the Jaro-Winkler distance algorithm to find fuzzy matches between entities.
 * The Winkler modification gives a higher score to strings that share a prefix.
 * This implementation is optimized for Zero-GC by using bitmasks (long) for strings up to 64 characters.
 */
public class JaroWinklerResolutionStrategy implements EntityResolutionStrategy {

  private static final int MAX_BITMASK_LENGTH = 64;
  private final double threshold;

  public JaroWinklerResolutionStrategy(double threshold) {
    this.threshold = threshold;
  }

  @Override
  public String resolve(String entityType, String newEntity, Iterable<String> existingEntities) {
    if (newEntity == null || newEntity.isBlank()) return null;

    String bestMatch = null;
    double highestScore = 0.0;

    for (String existing : existingEntities) {
      if (existing == null || existing.isBlank()) continue;

      double score = calculateSimilarity(newEntity, existing);
      if (score >= threshold && score > highestScore) {
        highestScore = score;
        bestMatch = existing;
      }
    }

    return bestMatch;
  }

  private double calculateSimilarity(String s1, String s2) {
    if (s1.equals(s2)) {
      return 1.0;
    }

    int len1 = s1.length();
    int len2 = s2.length();

    if (len1 == 0 || len2 == 0) {
      return 0.0;
    }

    // Fallback to array allocation for extremely long strings
    if (len1 > MAX_BITMASK_LENGTH || len2 > MAX_BITMASK_LENGTH) {
      return calculateSimilarityWithArrays(s1, s2, len1, len2);
    }

    int matchDistance = Math.max(len1, len2) / 2 - 1;

    long s1Matches = 0L;
    long s2Matches = 0L;

    int matches = 0;
    for (int i = 0; i < len1; i++) {
      int start = Math.max(0, i - matchDistance);
      int end = Math.min(i + matchDistance + 1, len2);

      for (int j = start; j < end; j++) {
        if ((s2Matches & (1L << j)) != 0) {
          continue;
        }
        if (s1.charAt(i) == s2.charAt(j)) {
          s1Matches |= (1L << i);
          s2Matches |= (1L << j);
          matches++;
          break;
        }
      }
    }

    if (matches == 0) {
      return 0.0;
    }

    int transpositions = 0;
    int k = 0;
    for (int i = 0; i < len1; i++) {
      if ((s1Matches & (1L << i)) != 0) {
        while ((s2Matches & (1L << k)) == 0) {
          k++;
        }
        if (s1.charAt(i) != s2.charAt(k)) {
          transpositions++;
        }
        k++;
      }
    }

    double m = matches;
    double jaro = ((m / len1) + (m / len2) + ((m - (transpositions / 2.0)) / m)) / 3.0;

    int prefix = 0;
    int maxPrefix = Math.min(4, Math.min(len1, len2));
    for (int i = 0; i < maxPrefix; i++) {
      if (s1.charAt(i) == s2.charAt(i)) {
        prefix++;
      } else {
        break;
      }
    }

    return jaro + (prefix * 0.1 * (1.0 - jaro));
  }

  private double calculateSimilarityWithArrays(String s1, String s2, int len1, int len2) {
    int matchDistance = Math.max(len1, len2) / 2 - 1;

    boolean[] s1Matches = new boolean[len1];
    boolean[] s2Matches = new boolean[len2];

    int matches = 0;
    for (int i = 0; i < len1; i++) {
      int start = Math.max(0, i - matchDistance);
      int end = Math.min(i + matchDistance + 1, len2);

      for (int j = start; j < end; j++) {
        if (s2Matches[j]) continue;
        if (s1.charAt(i) == s2.charAt(j)) {
          s1Matches[i] = true;
          s2Matches[j] = true;
          matches++;
          break;
        }
      }
    }

    if (matches == 0) return 0.0;

    int transpositions = 0;
    int k = 0;
    for (int i = 0; i < len1; i++) {
      if (s1Matches[i]) {
        while (!s2Matches[k]) k++;
        if (s1.charAt(i) != s2.charAt(k)) transpositions++;
        k++;
      }
    }

    double m = matches;
    double jaro = ((m / len1) + (m / len2) + ((m - (transpositions / 2.0)) / m)) / 3.0;

    int prefix = 0;
    int maxPrefix = Math.min(4, Math.min(len1, len2));
    for (int i = 0; i < maxPrefix; i++) {
      if (s1.charAt(i) == s2.charAt(i)) prefix++;
      else break;
    }

    return jaro + (prefix * 0.1 * (1.0 - jaro));
  }
}

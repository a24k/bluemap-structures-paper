package dev.a24k.bluemapstructures.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;

/**
 * Cache validity key: scan output is deterministic given (seed, radius, layer set,
 * format version), so a stored scan is reusable iff this key matches. The seed goes in
 * hashed — the cache file sits in the server directory and needn't be a plaintext copy
 * of the seed.
 */
public final class ScanCacheKey {

  /** Bump when the scan algorithm or cache payload format changes. */
  private static final String CACHE_VERSION = "1";

  private ScanCacheKey() {}

  public static String compute(long seed, int radiusBlocks, Collection<String> enabledLayerIds) {
    String canonical =
        CACHE_VERSION
            + "|"
            + Long.toHexString(seed)
            + "|"
            + radiusBlocks
            + "|"
            + String.join(",", enabledLayerIds.stream().sorted().toList());
    return "v" + CACHE_VERSION + ":" + sha256Hex(canonical);
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM without SHA-256", e);
    }
  }
}

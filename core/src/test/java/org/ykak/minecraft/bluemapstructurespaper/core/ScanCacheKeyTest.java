package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScanCacheKeyTest {

  @Test
  void sameInputsSameKey() {
    assertEquals(
        ScanCacheKey.compute(42L, 5000, List.of("village", "monument")),
        ScanCacheKey.compute(42L, 5000, List.of("village", "monument")));
  }

  @Test
  void layerOrderDoesNotMatter() {
    assertEquals(
        ScanCacheKey.compute(42L, 5000, List.of("monument", "village")),
        ScanCacheKey.compute(42L, 5000, List.of("village", "monument")));
  }

  @Test
  void anyChangedInputChangesTheKey() {
    String base = ScanCacheKey.compute(42L, 5000, List.of("village"));
    assertNotEquals(base, ScanCacheKey.compute(43L, 5000, List.of("village")));
    assertNotEquals(base, ScanCacheKey.compute(42L, 6000, List.of("village")));
    assertNotEquals(base, ScanCacheKey.compute(42L, 5000, List.of("monument")));
  }

  @Test
  void keyDoesNotLeakTheSeed() {
    String key = ScanCacheKey.compute(123456789L, 5000, List.of("village"));
    assertFalse(key.contains("123456789"));
  }
}

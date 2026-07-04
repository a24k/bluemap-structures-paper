package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Overlapping sample queries find the same structure repeatedly; identity is the whole
 * (key, x, z) record. First-seen order is preserved so cache files and marker ids stay
 * stable across runs.
 */
public final class Deduplicator {

  private Deduplicator() {}

  public static List<FoundStructure> dedupe(List<FoundStructure> found) {
    return List.copyOf(new LinkedHashSet<>(found));
  }
}

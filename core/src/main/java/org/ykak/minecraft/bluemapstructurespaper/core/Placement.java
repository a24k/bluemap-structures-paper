package org.ykak.minecraft.bluemapstructurespaper.core;

/** How vanilla places a structure, which decides our sampling strategy. */
public sealed interface Placement {

  /**
   * Random-spread placement: at most one attempt per {@code spacingChunks}-sided cell,
   * cells anchored at chunk (0,0).
   */
  record Grid(int spacingChunks) implements Placement {}

  /** Concentric-rings placement (strongholds). */
  record ConcentricRings() implements Placement {}
}

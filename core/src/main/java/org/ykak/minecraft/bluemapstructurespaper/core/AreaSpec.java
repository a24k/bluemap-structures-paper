package org.ykak.minecraft.bluemapstructurespaper.core;

/**
 * One configured search area, before per-world resolution. A {@code spawn} center can
 * only be resolved by the plugin (main thread, {@code World#getSpawnLocation}), so the
 * parsed config keeps centers symbolic; the plugin turns each spec into a
 * {@link SearchArea} at scan setup.
 */
public record AreaSpec(Center center, int radiusBlocks) {

  /** Where the area is centered. */
  public sealed interface Center {

    Origin ORIGIN = new Origin();
    Spawn SPAWN = new Spawn();

    /** World origin (0,0). */
    record Origin() implements Center {}

    /** The world's spawn point, resolved per world at scan setup. */
    record Spawn() implements Center {}

    /** Explicit block coordinates. */
    record Fixed(int x, int z) implements Center {}
  }
}

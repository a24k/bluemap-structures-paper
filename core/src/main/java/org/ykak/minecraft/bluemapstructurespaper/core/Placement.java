package org.ykak.minecraft.bluemapstructurespaper.core;

/**
 * How vanilla places a structure, which decides the seed-math used by
 * {@link SeedStructureLocator}. Constants (spacing/separation/salt) are cross-checked
 * against cubiomes ({@code finders.c}, MIT) — see {@link StructureCatalog}.
 */
public sealed interface Placement {

  /** How offsets within a placement cell are rolled from the region's {@link java.util.Random}. */
  enum Spread {
    /** One {@code nextInt} call per axis (uniform across the cell). */
    LINEAR,
    /** Two {@code nextInt} calls per axis, averaged (biases toward the cell center). */
    TRIANGULAR
  }

  /**
   * Random-spread placement: at most one attempt per {@code spacingChunks}-sided cell,
   * cells anchored at chunk (0,0). The attempt's offset within the cell is confined to
   * {@code spacingChunks - separationChunks} chunks per axis.
   */
  record Grid(int spacingChunks, int separationChunks, long salt, Spread spread) implements Placement {}

  /** Concentric-rings placement (strongholds). */
  record ConcentricRings() implements Placement {}

  /** Which of the two nether-complex structures a shared-grid cell resolves to. */
  enum NetherRole {
    FORTRESS,
    BASTION
  }

  /**
   * Fortress and bastion share one random-spread grid (spacing 27 / separation 4 / salt
   * 30084232); a secondary weighted roll on the resulting chunk position decides which
   * structure the cell gets. See {@link SeedStructureLocator} for the roll itself.
   */
  record NetherComplex(NetherRole role) implements Placement {
    public static final int SPACING_CHUNKS = 27;
    public static final int SEPARATION_CHUNKS = 4;
    public static final long SALT = 30084232L;
  }

  /**
   * Per-chunk placement (buried treasure): every chunk independently rolls a
   * {@code nextFloat() < probability} check seeded from its own chunk coordinates.
   */
  record PerChunkProbability(long salt, double probability) implements Placement {}
}

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
   *
   * @param frequency post-placement rarity reduction applied to the cell's candidate; {@code
   *     1.0f} means no reduction. Values below 1.0 model vanilla's data-driven {@code
   *     frequency_reduction_method: legacy_type_1} (used by {@code pillager_outpost} since
   *     1.14): {@code new Random((chunkX>>4 ^ (chunkZ>>4)<<4) ^ worldSeed)}, discard one {@code
   *     nextInt()} call, then the candidate survives iff {@code nextInt(round(1/frequency)) ==
   *     0}. Note the coarse {@code >> 4} (16-chunk) granularity and that the structure's own
   *     salt is <em>not</em> mixed in — this is a vanilla quirk, not an approximation.
   * @param exclusionZone if non-null, suppresses the cell's candidate when it falls near
   *     another grid's placement candidates; see {@link ExclusionZone}. Null means vanilla
   *     defines no exclusion zone for this structure set.
   */
  record Grid(
      int spacingChunks,
      int separationChunks,
      long salt,
      Spread spread,
      float frequency,
      ExclusionZone exclusionZone)
      implements Placement {
    /** Convenience for grids with no rarity reduction and no exclusion zone. */
    public Grid(int spacingChunks, int separationChunks, long salt, Spread spread) {
      this(spacingChunks, separationChunks, salt, spread, 1.0f, null);
    }
  }

  /**
   * Vanilla suppresses a structure-set placement candidate if it falls within {@code
   * chunkCount} chunks (Chebyshev distance) of any placement candidate of {@code otherGrid}'s
   * structure set. Only {@code otherGrid}'s <em>placement candidates</em> are checked — whether
   * that other structure actually generates there (biome, its own rarity gate, etc.) is
   * irrelevant. Vanilla's {@code pillager_outpost} declares this against the villages set
   * (10 chunks) in its structure-set JSON.
   */
  record ExclusionZone(Grid otherGrid, int chunkCount) {}

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

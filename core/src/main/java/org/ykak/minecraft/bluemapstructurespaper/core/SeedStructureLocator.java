package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Computes structure candidate positions from the world seed alone (Chunkbase-style seed
 * math), replacing the previous {@code locateNearestStructure}-based scanner which sync-loads
 * chunks and stalls the server (issue #3). No world/chunk access happens here; biome
 * validation is delegated to the plugin layer through {@link BiomeCheck}.
 *
 * <p><b>BiomeCheck contract:</b> {@link #locate} invokes {@code check.isValid(...)} for
 * <em>every</em> candidate position, including layers whose {@link StructureLayer#biomeTagIds()}
 * is empty (ruined portals, stronghold). The locator does not special-case empty tag lists —
 * it is the plugin's {@link BiomeCheck} implementation's responsibility to treat "no tags"
 * as "always valid". This keeps the locator a pure geometry/placement engine and puts biome
 * semantics in one place (the plugin, which actually has biome data).
 *
 * <p>Multi-key layers (e.g. {@code village}, which aggregates five registry structures) report
 * found instances under {@code layer.structureKeys().get(0)} — seed math alone can't tell which
 * variant landed at a given cell without a biome/noise sample, and the marker layer only cares
 * about the layer id, not the specific variant key. If per-variant keys are ever needed, resolve
 * them via the same biome sample the plugin already takes for {@link BiomeCheck}.
 */
public final class SeedStructureLocator {

  private static final long REGION_X_MULTIPLIER = 341873128712L;
  private static final long REGION_Z_MULTIPLIER = 132897987541L;

  private static final int STRONGHOLD_COUNT = 128;
  private static final double STRONGHOLD_DISTANCE = 32.0;
  private static final int STRONGHOLD_INITIAL_RING_SIZE = 3;

  private SeedStructureLocator() {}

  /**
   * Biome validity check, implemented by the plugin (which has access to the world's biome
   * source). Core tests use stubs. See the class javadoc for the empty-tags contract.
   */
  @FunctionalInterface
  public interface BiomeCheck {
    boolean isValid(StructureLayer layer, int blockX, int blockZ);
  }

  /**
   * Finds all instances of {@code layer} within the square {@code |x|,|z| <= radiusBlocks},
   * validated via {@code check}. Positions are chunk centers ({@code chunkX*16+8, chunkZ*16+8})
   * except buried treasure, whose vanilla placement offset is {@code chunkX*16+9, chunkZ*16+9}.
   */
  public static List<FoundStructure> locate(
      StructureLayer layer, long worldSeed, int radiusBlocks, BiomeCheck check) {
    Placement placement = layer.placement();
    if (placement instanceof Placement.ConcentricRings) {
      return locateStronghold(layer, worldSeed, radiusBlocks, check);
    }
    if (placement instanceof Placement.PerChunkProbability perChunk) {
      return locatePerChunk(layer, perChunk, worldSeed, radiusBlocks, check);
    }
    if (placement instanceof Placement.NetherComplex netherComplex) {
      return locateNetherComplex(layer, netherComplex, worldSeed, radiusBlocks, check);
    }
    if (placement instanceof Placement.Grid grid) {
      return locateGrid(layer, grid, worldSeed, radiusBlocks, check);
    }
    throw new IllegalStateException("unhandled placement type: " + placement);
  }

  // ---- Grid (random-spread) placements ------------------------------------------------

  private static List<FoundStructure> locateGrid(
      StructureLayer layer, Placement.Grid grid, long worldSeed, int radiusBlocks, BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    String key = layer.structureKeys().get(0);
    int spacing = grid.spacingChunks();
    int radiusChunks = radiusBlocks / 16;
    int regionMin = Math.floorDiv(-radiusChunks, spacing) - 1;
    int regionMax = Math.floorDiv(radiusChunks, spacing) + 1;

    for (int regionX = regionMin; regionX <= regionMax; regionX++) {
      for (int regionZ = regionMin; regionZ <= regionMax; regionZ++) {
        int[] chunk =
            cellChunk(
                regionX, regionZ, spacing, grid.separationChunks(), grid.salt(), grid.spread(), worldSeed);
        addIfWithinRadius(results, key, chunk[0], chunk[1], radiusBlocks, layer, check);
      }
    }
    return results;
  }

  /** Random-spread core: one placement attempt per {@code spacing}-chunk-sided region cell. */
  private static int[] cellChunk(
      int regionX,
      int regionZ,
      int spacing,
      int separation,
      long salt,
      Placement.Spread spread,
      long worldSeed) {
    long regionSeed =
        (long) regionX * REGION_X_MULTIPLIER + (long) regionZ * REGION_Z_MULTIPLIER + worldSeed + salt;
    Random rand = new Random(regionSeed);
    int range = spacing - separation;

    int chunkX;
    int chunkZ;
    if (spread == Placement.Spread.TRIANGULAR) {
      chunkX = regionX * spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2;
      chunkZ = regionZ * spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2;
    } else {
      chunkX = regionX * spacing + rand.nextInt(range);
      chunkZ = regionZ * spacing + rand.nextInt(range);
    }
    return new int[] {chunkX, chunkZ};
  }

  // ---- Nether complex (fortress/bastion shared grid) ----------------------------------

  private static List<FoundStructure> locateNetherComplex(
      StructureLayer layer,
      Placement.NetherComplex complex,
      long worldSeed,
      int radiusBlocks,
      BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    String key = layer.structureKeys().get(0);
    int spacing = Placement.NetherComplex.SPACING_CHUNKS;
    int separation = Placement.NetherComplex.SEPARATION_CHUNKS;
    long salt = Placement.NetherComplex.SALT;
    int radiusChunks = radiusBlocks / 16;
    int regionMin = Math.floorDiv(-radiusChunks, spacing) - 1;
    int regionMax = Math.floorDiv(radiusChunks, spacing) + 1;

    // setCarverSeed's two multipliers depend only on the world seed.
    Random carverInit = new Random(worldSeed);
    long multA = carverInit.nextLong();
    long multB = carverInit.nextLong();

    for (int regionX = regionMin; regionX <= regionMax; regionX++) {
      for (int regionZ = regionMin; regionZ <= regionMax; regionZ++) {
        int[] chunk =
            cellChunk(regionX, regionZ, spacing, separation, salt, Placement.Spread.LINEAR, worldSeed);
        int chunkX = chunk[0];
        int chunkZ = chunk[1];

        long carverSeed = (multA * (long) chunkX) ^ (multB * (long) chunkZ) ^ worldSeed;
        boolean isFortress = new Random(carverSeed).nextInt(5) < 2;
        Placement.NetherRole actual =
            isFortress ? Placement.NetherRole.FORTRESS : Placement.NetherRole.BASTION;
        if (actual != complex.role()) {
          continue;
        }
        addIfWithinRadius(results, key, chunkX, chunkZ, radiusBlocks, layer, check);
      }
    }
    return results;
  }

  // ---- Per-chunk probability (buried treasure) ----------------------------------------

  private static List<FoundStructure> locatePerChunk(
      StructureLayer layer,
      Placement.PerChunkProbability perChunk,
      long worldSeed,
      int radiusBlocks,
      BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    String key = layer.structureKeys().get(0);
    int radiusChunks = radiusBlocks / 16;

    for (int chunkX = -radiusChunks; chunkX <= radiusChunks; chunkX++) {
      for (int chunkZ = -radiusChunks; chunkZ <= radiusChunks; chunkZ++) {
        long seed =
            (long) chunkX * REGION_X_MULTIPLIER
                + (long) chunkZ * REGION_Z_MULTIPLIER
                + worldSeed
                + perChunk.salt();
        if (new Random(seed).nextFloat() < perChunk.probability()) {
          int blockX = chunkX * 16 + 9;
          int blockZ = chunkZ * 16 + 9;
          if (Math.abs(blockX) <= radiusBlocks
              && Math.abs(blockZ) <= radiusBlocks
              && check.isValid(layer, blockX, blockZ)) {
            results.add(new FoundStructure(key, blockX, blockZ));
          }
        }
      }
    }
    return results;
  }

  // ---- Concentric rings (stronghold) ---------------------------------------------------

  /**
   * Ports vanilla's {@code ConcentricRingsStructurePlacement} for strongholds (128 count,
   * distance 32, spread 3, expanding count-per-ring, angle math seeded from {@code
   * Random(worldSeed)}).
   *
   * <p><b>Accuracy caveat:</b> vanilla additionally biome-snaps each ring position (nudging it
   * within +/-112 blocks to the nearest valid stronghold biome), which requires world/chunk
   * access this module deliberately doesn't have. This returns the un-nudged geometric ring
   * position; the true in-game position can differ by up to ~112 blocks. There is no
   * plugin-side correction step for this today — only {@link BiomeCheck} rejection, which for
   * strongholds (empty {@link StructureLayer#biomeTagIds()}) a real plugin implementation should
   * treat as always-valid, per the class javadoc's contract.
   */
  private static List<FoundStructure> locateStronghold(
      StructureLayer layer, long worldSeed, int radiusBlocks, BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    String key = layer.structureKeys().get(0);
    Random rand = new Random(worldSeed);

    int ringSize = STRONGHOLD_INITIAL_RING_SIZE;
    double angle = rand.nextDouble() * Math.PI * 2.0;
    int placedInRing = 0;
    int ring = 0;

    for (int i = 0; i < STRONGHOLD_COUNT; i++) {
      double dist =
          (4.0 * STRONGHOLD_DISTANCE + STRONGHOLD_DISTANCE * ring * 6.0)
              + (rand.nextDouble() - 0.5) * STRONGHOLD_DISTANCE * 2.5;
      int chunkX = (int) Math.round(Math.cos(angle) * dist);
      int chunkZ = (int) Math.round(Math.sin(angle) * dist);

      addIfWithinRadius(results, key, chunkX, chunkZ, radiusBlocks, layer, check);

      angle += Math.PI * 2.0 / ringSize;
      placedInRing++;

      if (placedInRing == ringSize) {
        ring++;
        placedInRing = 0;
        ringSize += 2 * ringSize / (ring + 1);
        if (ringSize > STRONGHOLD_COUNT - i - 1) {
          ringSize = STRONGHOLD_COUNT - i - 1;
        }
        angle += rand.nextDouble() * Math.PI * 2.0;
      }
    }
    return results;
  }

  // ---- shared helpers -------------------------------------------------------------------

  private static void addIfWithinRadius(
      List<FoundStructure> results,
      String key,
      int chunkX,
      int chunkZ,
      int radiusBlocks,
      StructureLayer layer,
      BiomeCheck check) {
    int blockX = chunkX * 16 + 8;
    int blockZ = chunkZ * 16 + 8;
    if (Math.abs(blockX) <= radiusBlocks
        && Math.abs(blockZ) <= radiusBlocks
        && check.isValid(layer, blockX, blockZ)) {
      results.add(new FoundStructure(key, blockX, blockZ));
    }
  }
}

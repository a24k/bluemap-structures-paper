package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
 * <p><b>Search areas (issue #4):</b> the search space is a union of square
 * {@link SearchArea}s. Each area is swept independently (work stays proportional to the
 * areas' sizes, not to the bounding box of possibly far-apart areas) and candidates are
 * deduplicated by position before membership/biome checks, so overlapping areas never
 * produce duplicate results and evaluate each candidate exactly once.
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
   * Convenience for a single origin-centered square {@code |x|,|z| <= radiusBlocks}.
   */
  public static List<FoundStructure> locate(
      StructureLayer layer, long worldSeed, int radiusBlocks, BiomeCheck check) {
    return locate(layer, worldSeed, List.of(new SearchArea(0, 0, radiusBlocks)), check);
  }

  /**
   * Finds all instances of {@code layer} within the union of {@code areas}, validated via
   * {@code check}. Positions are chunk centers ({@code chunkX*16+8, chunkZ*16+8}) except
   * buried treasure, whose vanilla placement offset is {@code chunkX*16+9, chunkZ*16+9}.
   * Results are duplicate-free even when areas overlap. Stronghold rings stay anchored at
   * (0,0) by vanilla definition — areas only filter which ring positions are reported.
   */
  public static List<FoundStructure> locate(
      StructureLayer layer, long worldSeed, List<SearchArea> areas, BiomeCheck check) {
    if (areas.isEmpty()) {
      return List.of();
    }
    Placement placement = layer.placement();
    if (placement instanceof Placement.ConcentricRings) {
      return locateStronghold(layer, worldSeed, areas, check);
    }
    if (placement instanceof Placement.PerChunkProbability perChunk) {
      return locatePerChunk(layer, perChunk, worldSeed, areas, check);
    }
    if (placement instanceof Placement.NetherComplex netherComplex) {
      return locateNetherComplex(layer, netherComplex, worldSeed, areas, check);
    }
    if (placement instanceof Placement.Grid grid) {
      return locateGrid(layer, grid, worldSeed, areas, check);
    }
    throw new IllegalStateException("unhandled placement type: " + placement);
  }

  // ---- Grid (random-spread) placements ------------------------------------------------

  private static List<FoundStructure> locateGrid(
      StructureLayer layer, Placement.Grid grid, long worldSeed, List<SearchArea> areas, BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    String key = layer.structureKeys().get(0);
    int spacing = grid.spacingChunks();

    for (SearchArea area : areas) {
      int regionMinX = regionFloor(area.minBlockX(), spacing) - 1;
      int regionMaxX = regionFloor(area.maxBlockX(), spacing) + 1;
      int regionMinZ = regionFloor(area.minBlockZ(), spacing) - 1;
      int regionMaxZ = regionFloor(area.maxBlockZ(), spacing) + 1;
      for (int regionX = regionMinX; regionX <= regionMaxX; regionX++) {
        for (int regionZ = regionMinZ; regionZ <= regionMaxZ; regionZ++) {
          int[] chunk =
              cellChunk(
                  regionX, regionZ, spacing, grid.separationChunks(), grid.salt(), grid.spread(), worldSeed);
          addCandidate(results, seen, key, chunk[0], chunk[1], 8, areas, layer, check);
        }
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
      List<SearchArea> areas,
      BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    String key = layer.structureKeys().get(0);
    int spacing = Placement.NetherComplex.SPACING_CHUNKS;
    int separation = Placement.NetherComplex.SEPARATION_CHUNKS;
    long salt = Placement.NetherComplex.SALT;

    // setCarverSeed's two multipliers depend only on the world seed.
    Random carverInit = new Random(worldSeed);
    long multA = carverInit.nextLong();
    long multB = carverInit.nextLong();

    for (SearchArea area : areas) {
      int regionMinX = regionFloor(area.minBlockX(), spacing) - 1;
      int regionMaxX = regionFloor(area.maxBlockX(), spacing) + 1;
      int regionMinZ = regionFloor(area.minBlockZ(), spacing) - 1;
      int regionMaxZ = regionFloor(area.maxBlockZ(), spacing) + 1;
      for (int regionX = regionMinX; regionX <= regionMaxX; regionX++) {
        for (int regionZ = regionMinZ; regionZ <= regionMaxZ; regionZ++) {
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
          addCandidate(results, seen, key, chunkX, chunkZ, 8, areas, layer, check);
        }
      }
    }
    return results;
  }

  // ---- Per-chunk probability (buried treasure) ----------------------------------------

  private static List<FoundStructure> locatePerChunk(
      StructureLayer layer,
      Placement.PerChunkProbability perChunk,
      long worldSeed,
      List<SearchArea> areas,
      BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    String key = layer.structureKeys().get(0);

    for (SearchArea area : areas) {
      int chunkMinX = Math.floorDiv(area.minBlockX(), 16);
      int chunkMaxX = Math.floorDiv(area.maxBlockX(), 16);
      int chunkMinZ = Math.floorDiv(area.minBlockZ(), 16);
      int chunkMaxZ = Math.floorDiv(area.maxBlockZ(), 16);
      for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
        for (int chunkZ = chunkMinZ; chunkZ <= chunkMaxZ; chunkZ++) {
          long seed =
              (long) chunkX * REGION_X_MULTIPLIER
                  + (long) chunkZ * REGION_Z_MULTIPLIER
                  + worldSeed
                  + perChunk.salt();
          if (new Random(seed).nextFloat() < perChunk.probability()) {
            addCandidate(results, seen, key, chunkX, chunkZ, 9, areas, layer, check);
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
      StructureLayer layer, long worldSeed, List<SearchArea> areas, BiomeCheck check) {
    List<FoundStructure> results = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
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

      addCandidate(results, seen, key, chunkX, chunkZ, 8, areas, layer, check);

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

  /** Placement-region index containing the given block coordinate. */
  private static int regionFloor(int block, int spacingChunks) {
    return Math.floorDiv(Math.floorDiv(block, 16), spacingChunks);
  }

  /**
   * Adds the candidate if it wasn't already evaluated (overlapping areas revisit region
   * cells), lies inside the area union, and passes the biome check — in that order, so
   * {@code check} runs at most once per unique in-union candidate.
   */
  private static void addCandidate(
      List<FoundStructure> results,
      Set<Long> seen,
      String key,
      int chunkX,
      int chunkZ,
      int blockOffset,
      List<SearchArea> areas,
      StructureLayer layer,
      BiomeCheck check) {
    int blockX = chunkX * 16 + blockOffset;
    int blockZ = chunkZ * 16 + blockOffset;
    long packed = (((long) blockX) << 32) ^ (blockZ & 0xFFFFFFFFL);
    if (!seen.add(packed)) {
      return;
    }
    if (SearchArea.anyContains(areas, blockX, blockZ) && check.isValid(layer, blockX, blockZ)) {
      results.add(new FoundStructure(key, blockX, blockZ));
    }
  }
}

package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Property tests for {@link SeedStructureLocator}. Golden-vector tests against the
 * reference-mod-derived generator live in {@link SeedStructureLocatorGoldenVectorTest}.
 */
class SeedStructureLocatorTest {

  private static final SeedStructureLocator.BiomeCheck ALWAYS_VALID = (layer, x, z) -> true;
  private static final SeedStructureLocator.BiomeCheck NEVER_VALID = (layer, x, z) -> false;
  private static final long[] SEEDS = {42L, 69420L, -3849722879L};

  // ---- determinism ----------------------------------------------------------------------

  @Test
  void sameSeedProducesSameResults() {
    StructureLayer village = StructureCatalog.byId("village").orElseThrow();
    List<FoundStructure> first = SeedStructureLocator.locate(village, 42L, 3000, ALWAYS_VALID);
    List<FoundStructure> second = SeedStructureLocator.locate(village, 42L, 3000, ALWAYS_VALID);
    assertEquals(toSet(first), toSet(second));
    assertFalse(first.isEmpty());
  }

  @Test
  void differentSeedsProduceDifferentResults() {
    StructureLayer village = StructureCatalog.byId("village").orElseThrow();
    List<FoundStructure> a = SeedStructureLocator.locate(village, 42L, 3000, ALWAYS_VALID);
    List<FoundStructure> b = SeedStructureLocator.locate(village, 69420L, 3000, ALWAYS_VALID);
    assertFalse(toSet(a).equals(toSet(b)));
  }

  // ---- radius filtering -------------------------------------------------------------------

  @Test
  void allPositionsLieWithinTheRequestedRadius() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      int radius = layer.id().equals("buried_treasure") ? 512 : 2000;
      List<FoundStructure> found = SeedStructureLocator.locate(layer, 42L, radius, ALWAYS_VALID);
      for (FoundStructure f : found) {
        assertTrue(Math.abs(f.x()) <= radius, layer.id() + " x out of radius: " + f.x());
        assertTrue(Math.abs(f.z()) <= radius, layer.id() + " z out of radius: " + f.z());
      }
    }
  }

  @Test
  void smallerRadiusYieldsSubsetOfLargerRadius() {
    StructureLayer village = StructureCatalog.byId("village").orElseThrow();
    Set<Long> small = toSet(SeedStructureLocator.locate(village, 42L, 500, ALWAYS_VALID));
    Set<Long> large = toSet(SeedStructureLocator.locate(village, 42L, 3000, ALWAYS_VALID));
    assertTrue(large.containsAll(small));
    assertTrue(large.size() > small.size());
  }

  // ---- grid cell containment --------------------------------------------------------------

  @Test
  void gridPositionsLieWithinTheirPlacementCell() {
    StructureLayer village = StructureCatalog.byId("village").orElseThrow();
    Placement.Grid grid = (Placement.Grid) village.placement();
    int range = grid.spacingChunks() - grid.separationChunks();

    List<FoundStructure> found = SeedStructureLocator.locate(village, 42L, 3000, ALWAYS_VALID);
    assertFalse(found.isEmpty());
    for (FoundStructure f : found) {
      int chunkX = Math.floorDiv(f.x() - 8, 16);
      int chunkZ = Math.floorDiv(f.z() - 8, 16);
      int regionX = Math.floorDiv(chunkX, grid.spacingChunks());
      int regionZ = Math.floorDiv(chunkZ, grid.spacingChunks());
      int offsetX = chunkX - regionX * grid.spacingChunks();
      int offsetZ = chunkZ - regionZ * grid.spacingChunks();
      assertTrue(offsetX >= 0 && offsetX < range, "offsetX " + offsetX + " out of [0," + range + ")");
      assertTrue(offsetZ >= 0 && offsetZ < range, "offsetZ " + offsetZ + " out of [0," + range + ")");
    }
  }

  // ---- nether complex ---------------------------------------------------------------------

  @Test
  void netherComplexFortressAndBastionPartitionTheSharedGrid() {
    StructureLayer fortress = StructureCatalog.byId("fortress").orElseThrow();
    StructureLayer bastion = StructureCatalog.byId("bastion").orElseThrow();

    for (long seed : SEEDS) {
      Set<Long> fortressCells =
          toSet(SeedStructureLocator.locate(fortress, seed, 4000, ALWAYS_VALID));
      Set<Long> bastionCells =
          toSet(SeedStructureLocator.locate(bastion, seed, 4000, ALWAYS_VALID));

      assertFalse(fortressCells.isEmpty());
      assertFalse(bastionCells.isEmpty());
      assertTrue(
          Collections.disjoint(fortressCells, bastionCells),
          "a shared-grid cell resolved to both fortress and bastion for seed " + seed);
    }
  }

  // ---- spread distribution ------------------------------------------------------------------

  @Test
  void triangularSpreadClustersMoreTightlyThanLinearSpread() {
    // village: LINEAR, spacing 34, separation 8, range 26.
    // mansion: TRIANGULAR, spacing 80, separation 20, range 60.
    // Compare offset variance normalized by range^2: a uniform (LINEAR) draw has
    // variance range^2/12; the average-of-two-uniforms (TRIANGULAR) draw has half that,
    // range^2/24 - triangular should be visibly tighter regardless of the raw range size.
    StructureLayer village = StructureCatalog.byId("village").orElseThrow();
    StructureLayer mansion = StructureCatalog.byId("mansion").orElseThrow();
    Placement.Grid villageGrid = (Placement.Grid) village.placement();
    Placement.Grid mansionGrid = (Placement.Grid) mansion.placement();

    double linearRatio = normalizedOffsetVariance(village, villageGrid);
    double triangularRatio = normalizedOffsetVariance(mansion, mansionGrid);

    assertTrue(
        triangularRatio < linearRatio * 0.75,
        "expected triangular spread (" + triangularRatio + ") to be tighter than linear (" + linearRatio + ")");
  }

  private static double normalizedOffsetVariance(StructureLayer layer, Placement.Grid grid) {
    List<Integer> offsets = new ArrayList<>();
    for (long seed : SEEDS) {
      for (FoundStructure f : SeedStructureLocator.locate(layer, seed, 6000, ALWAYS_VALID)) {
        int chunkX = Math.floorDiv(f.x() - 8, 16);
        int regionX = Math.floorDiv(chunkX, grid.spacingChunks());
        offsets.add(chunkX - regionX * grid.spacingChunks());
      }
    }
    assertFalse(offsets.isEmpty());
    double mean = offsets.stream().mapToInt(Integer::intValue).average().orElseThrow();
    double variance =
        offsets.stream().mapToDouble(o -> (o - mean) * (o - mean)).average().orElseThrow();
    int range = grid.spacingChunks() - grid.separationChunks();
    return variance / ((double) range * range);
  }

  // ---- stronghold ---------------------------------------------------------------------------

  @Test
  void strongholdPositionsAreDeterministicAndRadiusFiltered() {
    StructureLayer stronghold = StructureCatalog.byId("stronghold").orElseThrow();
    List<FoundStructure> found = SeedStructureLocator.locate(stronghold, 42L, 50_000, ALWAYS_VALID);
    List<FoundStructure> again = SeedStructureLocator.locate(stronghold, 42L, 50_000, ALWAYS_VALID);
    assertEquals(toSet(found), toSet(again));
    assertFalse(found.isEmpty());
    assertTrue(found.size() <= 128);
    for (FoundStructure f : found) {
      assertTrue(Math.abs(f.x()) <= 50_000);
      assertTrue(Math.abs(f.z()) <= 50_000);
    }
  }

  // ---- BiomeCheck contract --------------------------------------------------------------------

  @Test
  void rejectingBiomeCheckFiltersAllPositions() {
    StructureLayer village = StructureCatalog.byId("village").orElseThrow();
    List<FoundStructure> found = SeedStructureLocator.locate(village, 42L, 3000, NEVER_VALID);
    assertTrue(found.isEmpty());
  }

  @Test
  void biomeCheckIsInvokedEvenForEmptyTagLayers() {
    // Contract: the locator always calls BiomeCheck; it is the plugin's implementation that
    // decides "no biome tags" means "always valid". Prove the locator doesn't special-case it
    // by having the stub itself reject everything for a layer whose biomeTagIds() is empty.
    StructureLayer ruinedPortalNether = StructureCatalog.byId("ruined_portal_nether").orElseThrow();
    assertTrue(ruinedPortalNether.biomeTagIds().isEmpty());

    int[] invocationCount = {0};
    SeedStructureLocator.BiomeCheck counting =
        (layer, x, z) -> {
          invocationCount[0]++;
          return true;
        };
    List<FoundStructure> found =
        SeedStructureLocator.locate(ruinedPortalNether, 42L, 2000, counting);
    assertFalse(found.isEmpty());
    assertEquals(found.size(), invocationCount[0]);

    List<FoundStructure> rejected =
        SeedStructureLocator.locate(ruinedPortalNether, 42L, 2000, NEVER_VALID);
    assertTrue(rejected.isEmpty(), "locator must not bypass BiomeCheck for empty-tag layers");
  }

  @Test
  void biomeCheckReceivesTheRequestingLayer() {
    StructureLayer monument = StructureCatalog.byId("monument").orElseThrow();
    List<StructureLayer> seenLayers = new ArrayList<>();
    SeedStructureLocator.BiomeCheck recording =
        (layer, x, z) -> {
          seenLayers.add(layer);
          return true;
        };
    List<FoundStructure> found = SeedStructureLocator.locate(monument, 42L, 2000, recording);
    assertFalse(found.isEmpty());
    for (StructureLayer seen : seenLayers) {
      assertEquals(monument, seen);
    }
  }

  private static Set<Long> toSet(List<FoundStructure> found) {
    Set<Long> set = new HashSet<>();
    for (FoundStructure f : found) {
      set.add((((long) f.x()) << 32) ^ (f.z() & 0xFFFFFFFFL));
    }
    return set;
  }
}

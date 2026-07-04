package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SamplePlannerTest {

  // --- grid placements ------------------------------------------------------

  @Test
  void gridCoversEveryPlacementCellIntersectingTheSearchSquare() {
    int radius = 5000;
    int spacing = 34; // villages
    int cellBlocks = spacing * 16; // 544

    List<SampleQuery> samples = SamplePlanner.forGrid(radius, spacing);

    // cells k with k*cell <= radius and (k+1)*cell > -radius: k in [-10, 9] → 20 per axis
    assertEquals(20 * 20, samples.size());

    Set<Long> cells = new HashSet<>();
    for (SampleQuery s : samples) {
      // every sample sits at its cell's block center
      assertEquals(Math.floorMod(s.blockX(), cellBlocks), cellBlocks / 2, s.toString());
      assertEquals(Math.floorMod(s.blockZ(), cellBlocks), cellBlocks / 2, s.toString());
      // covering search radius: from the cell center, `spacing` chunks reaches any corner
      assertEquals(spacing, s.radiusChunks());
      long cellX = Math.floorDiv(s.blockX(), cellBlocks);
      long cellZ = Math.floorDiv(s.blockZ(), cellBlocks);
      assertTrue(cells.add(cellX << 32 | (cellZ & 0xffffffffL)), "duplicate cell " + s);
    }
  }

  @Test
  void gridWithTinyRadiusStillSamplesTheCellsAroundOrigin() {
    // radius smaller than one cell: the 4 cells touching the search square remain
    List<SampleQuery> samples = SamplePlanner.forGrid(256, 34);
    assertEquals(4, samples.size());
  }

  @Test
  void gridSampleCountGrowsQuadratically() {
    assertEquals(
        SamplePlanner.forGrid(5000, 80).size(), // mansions: cell 1280 → k in [-4,3] → 8²
        64);
  }

  // --- stronghold rings ------------------------------------------------------

  @Test
  void ringsWithinRadiusAreSampledWithTwoXOversampling() {
    // ring 1 (3 strongholds, inner 1280) and ring 2 (6, inner 4352) are inside 5000;
    // ring 3 (inner 7424) is not.
    List<SampleQuery> samples = SamplePlanner.forRings(5000);
    assertEquals(2 * 3 + 2 * 6, samples.size());
  }

  @Test
  void ringSamplesSitOnTheRingMidRadius() {
    List<SampleQuery> samples = SamplePlanner.forRings(3000); // ring 1 only → 6 samples
    assertEquals(6, samples.size());
    for (SampleQuery s : samples) {
      double dist = Math.hypot(s.blockX(), s.blockZ());
      // ring 1 spans 1280..2816, mid radius 2048
      assertEquals(2048.0, dist, 1.5, s.toString());
      // search radius must cover the ring's radial half-width (768 blocks = 48 chunks)
      assertTrue(s.radiusChunks() >= 48, s.toString());
    }
  }

  @Test
  void noRingSamplesWhenRadiusIsInsideTheFirstRing() {
    assertTrue(SamplePlanner.forRings(1000).isEmpty());
  }

  // --- dispatch --------------------------------------------------------------

  @Test
  void forLayerDispatchesOnPlacement() {
    StructureLayer village = StructureCatalog.byId("village").orElseThrow();
    StructureLayer stronghold = StructureCatalog.byId("stronghold").orElseThrow();

    assertEquals(
        SamplePlanner.forGrid(5000, 34), SamplePlanner.forLayer(village, 5000));
    assertEquals(SamplePlanner.forRings(5000), SamplePlanner.forLayer(stronghold, 5000));
  }
}

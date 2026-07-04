package dev.a24k.bluemapstructures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns "everything within radius R" into a set of {@code locateNearestStructure}
 * queries whose nearest-hit regions jointly cover the search area (results are then
 * de-duplicated).
 */
public final class SamplePlanner {

  /** Vanilla ConcentricRingsStructurePlacement: stronghold count per ring. */
  private static final int[] RING_COUNTS = {3, 6, 10, 15, 21, 28, 36, 9};

  /** Ring i spans RING_INNER + 3072·i … RING_OUTER + 3072·i blocks from origin. */
  private static final int RING_INNER = 1280;
  private static final int RING_OUTER = 2816;
  private static final int RING_STEP = 3072;

  private SamplePlanner() {}

  public static List<SampleQuery> forLayer(StructureLayer layer, int radiusBlocks) {
    return switch (layer.placement()) {
      case Placement.Grid grid -> forGrid(radiusBlocks, grid.spacingChunks());
      case Placement.ConcentricRings ignored -> forRings(radiusBlocks);
    };
  }

  /**
   * One sample at the block center of every placement cell (side = spacing chunks,
   * anchored at chunk 0,0) that intersects the search square. From the center, a search
   * radius of {@code spacing} chunks covers the whole cell, wherever the cell's single
   * placement attempt landed.
   */
  public static List<SampleQuery> forGrid(int radiusBlocks, int spacingChunks) {
    int cellBlocks = spacingChunks * 16;
    int kMin = Math.floorDiv(-radiusBlocks, cellBlocks);
    int kMax = Math.floorDiv(radiusBlocks, cellBlocks);

    List<SampleQuery> samples = new ArrayList<>((kMax - kMin + 1) * (kMax - kMin + 1));
    for (int kx = kMin; kx <= kMax; kx++) {
      for (int kz = kMin; kz <= kMax; kz++) {
        samples.add(
            new SampleQuery(
                kx * cellBlocks + cellBlocks / 2, kz * cellBlocks + cellBlocks / 2,
                spacingChunks));
      }
    }
    return samples;
  }

  /**
   * Stronghold rings: for every vanilla ring whose inner edge lies within the search
   * radius, sample 2×count points evenly around the ring's mid radius. The 2×
   * oversampling makes every stronghold the nearest hit of at least one sample; the
   * per-sample search radius covers the ring's radial half-width plus the worst-case
   * angular gap.
   */
  public static List<SampleQuery> forRings(int radiusBlocks) {
    List<SampleQuery> samples = new ArrayList<>();
    for (int ring = 0; ring < RING_COUNTS.length; ring++) {
      int inner = RING_INNER + RING_STEP * ring;
      if (inner > radiusBlocks) {
        break;
      }
      int outer = RING_OUTER + RING_STEP * ring;
      int mid = (inner + outer) / 2;
      int count = RING_COUNTS[ring];
      int sampleCount = 2 * count;

      double radialHalfWidth = (outer - inner) / 2.0;
      double halfGapArc = outer * Math.PI / sampleCount;
      int radiusChunks = (int) Math.ceil((radialHalfWidth + halfGapArc) / 16.0);

      for (int j = 0; j < sampleCount; j++) {
        double angle = 2.0 * Math.PI * j / sampleCount;
        samples.add(
            new SampleQuery(
                (int) Math.round(mid * Math.cos(angle)),
                (int) Math.round(mid * Math.sin(angle)),
                radiusChunks));
      }
    }
    return samples;
  }
}

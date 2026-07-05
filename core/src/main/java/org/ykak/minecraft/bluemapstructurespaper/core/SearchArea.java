package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.List;

/**
 * A resolved square search area: blocks with {@code |x - centerX| <= radiusBlocks} and
 * {@code |z - centerZ| <= radiusBlocks}. The plugin builds these from {@link AreaSpec}s
 * once symbolic centers (spawn) are resolved per world.
 */
public record SearchArea(int centerX, int centerZ, int radiusBlocks) {

  public boolean contains(int blockX, int blockZ) {
    return Math.abs(blockX - centerX) <= radiusBlocks && Math.abs(blockZ - centerZ) <= radiusBlocks;
  }

  public int minBlockX() {
    return centerX - radiusBlocks;
  }

  public int maxBlockX() {
    return centerX + radiusBlocks;
  }

  public int minBlockZ() {
    return centerZ - radiusBlocks;
  }

  public int maxBlockZ() {
    return centerZ + radiusBlocks;
  }

  /** Union membership: true if any area contains the block position. */
  public static boolean anyContains(List<SearchArea> areas, int blockX, int blockZ) {
    for (SearchArea area : areas) {
      if (area.contains(blockX, blockZ)) {
        return true;
      }
    }
    return false;
  }
}

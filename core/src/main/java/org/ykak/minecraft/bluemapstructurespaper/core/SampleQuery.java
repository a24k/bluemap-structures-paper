package org.ykak.minecraft.bluemapstructurespaper.core;

/**
 * One {@code locateNearestStructure} invocation: origin block position plus the search
 * radius to pass (sized in chunks — see docs/DESIGN.md §2.5 on the parameter's
 * blocks-vs-chunks ambiguity).
 */
public record SampleQuery(int blockX, int blockZ, int radiusChunks) {}

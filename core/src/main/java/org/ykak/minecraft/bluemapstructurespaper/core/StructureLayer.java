package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.List;

/**
 * One toggleable marker layer on the map. A layer may aggregate several registry
 * structures (e.g. all five village variants).
 *
 * @param id config key and marker-set id suffix, e.g. {@code village}
 * @param displayName sidebar label, e.g. {@code Villages}
 * @param dimension dimension whose maps carry this layer
 * @param placement vanilla placement, decides the sampling strategy
 * @param structureKeys registry keys ({@code minecraft:…}) aggregated into this layer
 * @param zoomMaxDistance BlueMap POI {@code maxDistance}; 1000 = only visible zoomed-in
 * @param iconFile file name under {@code icons/} on the plugin classpath
 * @param defaultEnabled whether the layer scans without explicit config
 */
public record StructureLayer(
    String id,
    String displayName,
    Dimension dimension,
    Placement placement,
    List<String> structureKeys,
    int zoomMaxDistance,
    String iconFile,
    boolean defaultEnabled) {}

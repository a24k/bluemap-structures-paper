package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.List;

/**
 * One toggleable marker layer on the map. A layer may aggregate several registry
 * structures (e.g. all five village variants).
 *
 * @param id config key and marker-set id suffix, e.g. {@code village}
 * @param displayName sidebar label, e.g. {@code Villages}
 * @param dimension dimension whose maps carry this layer
 * @param placement vanilla placement, decides the seed-math strategy
 * @param structureKeys registry keys ({@code minecraft:…}) aggregated into this layer;
 *     {@link SeedStructureLocator} reports found instances under {@code structureKeys().get(0)}
 * @param biomeTagIds biome tag ids (under {@code minecraft:has_structure/…}) the plugin should
 *     validate found positions against; an empty list means no biome restriction (e.g. ruined
 *     portals, stronghold) — see {@link SeedStructureLocator.BiomeCheck} for the contract
 * @param zoomMaxDistance BlueMap POI {@code maxDistance}; 1000 = only visible zoomed-in
 * @param defaultEnabled whether the layer scans without explicit config
 */
public record StructureLayer(
    String id,
    String displayName,
    Dimension dimension,
    Placement placement,
    List<String> structureKeys,
    List<String> biomeTagIds,
    int zoomMaxDistance,
    boolean defaultEnabled) {}

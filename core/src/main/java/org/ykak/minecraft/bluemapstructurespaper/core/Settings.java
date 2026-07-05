package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed plugin configuration. Built from the plain nested-map shape a YAML parser
 * produces, so this stays free of Bukkit types. Invalid values fall back to defaults
 * and are reported via {@link #warnings()} for the plugin to log.
 *
 * <p>Search areas are a list of {@link AreaSpec}s (union semantics), with optional
 * per-world overrides under {@code worlds.<name>.areas} that replace the default list
 * entirely for that world. The legacy top-level {@code radius-blocks} is sugar for a
 * single origin-centered area and is ignored (with a warning) when {@code areas} is
 * also present. World names are not validated here — the plugin warns at scan time
 * about overrides that match no loaded world.
 */
public record Settings(
    List<AreaSpec> defaultAreas,
    Map<String, List<AreaSpec>> worldAreas,
    Map<String, Boolean> layerEnabled,
    List<String> enabledLayerIds,
    List<String> warnings) {

  public static final int DEFAULT_RADIUS_BLOCKS = 5000;
  public static final int MIN_RADIUS_BLOCKS = 256;
  public static final int MAX_RADIUS_BLOCKS = 1_000_000;
  /** Bound on explicit center coordinates (vanilla world border sits at ±29,999,984). */
  public static final int MAX_CENTER_BLOCKS = 30_000_000;

  private static final List<AreaSpec> DEFAULT_AREAS =
      List.of(new AreaSpec(AreaSpec.Center.ORIGIN, DEFAULT_RADIUS_BLOCKS));

  public boolean isLayerEnabled(String layerId) {
    return layerEnabled.getOrDefault(layerId, false);
  }

  /** The area list to scan for {@code worldName}: its override if present, else the default. */
  public List<AreaSpec> areasForWorld(String worldName) {
    return worldAreas.getOrDefault(worldName, defaultAreas);
  }

  public static Settings fromMap(Map<String, ?> config, Collection<StructureLayer> catalog) {
    List<String> warnings = new ArrayList<>();

    List<AreaSpec> defaultAreas =
        readAreaList(config.get("areas"), config.get("radius-blocks"), "", warnings);
    if (defaultAreas == null) {
      defaultAreas = DEFAULT_AREAS;
    }

    Map<String, List<AreaSpec>> worldAreas = new LinkedHashMap<>();
    Map<String, ?> worldsSection = readSection(config.get("worlds"), "worlds", warnings);
    for (Map.Entry<String, ?> entry : worldsSection.entrySet()) {
      String prefix = "worlds." + entry.getKey();
      if (!(entry.getValue() instanceof Map<?, ?>)) {
        warnings.add(prefix + ": expected a section, got '" + entry.getValue() + "', ignored");
        continue;
      }
      Map<String, ?> worldSection = readSection(entry.getValue(), prefix, warnings);
      List<AreaSpec> areas =
          readAreaList(
              worldSection.get("areas"), worldSection.get("radius-blocks"), prefix + ".", warnings);
      if (areas == null) {
        warnings.add(prefix + ": no usable 'areas' or 'radius-blocks', override ignored");
        continue;
      }
      worldAreas.put(entry.getKey(), areas);
    }

    Map<String, ?> layerSection = readSection(config.get("layers"), "layers", warnings);
    Map<String, Boolean> layerEnabled = new LinkedHashMap<>();
    for (StructureLayer layer : catalog) {
      Object value = layerSection.get(layer.id());
      layerEnabled.put(
          layer.id(),
          value == null
              ? layer.defaultEnabled()
              : readBoolean(value, "layers." + layer.id(), layer.defaultEnabled(), warnings));
    }
    for (String key : layerSection.keySet()) {
      if (!layerEnabled.containsKey(key)) {
        warnings.add("layers." + key + ": unknown layer id, ignored");
      }
    }

    List<String> enabledIds =
        layerEnabled.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();

    return new Settings(
        defaultAreas,
        Map.copyOf(worldAreas),
        Map.copyOf(layerEnabled),
        List.copyOf(enabledIds),
        List.copyOf(warnings));
  }

  // ---- area parsing -----------------------------------------------------------------

  /**
   * Parses {@code areas} (a list) with {@code radius-blocks} as legacy sugar for a single
   * origin-centered area. Returns {@code null} when neither key yields a usable list —
   * the caller decides whether that means "use defaults" (top level) or "no override"
   * (world section). {@code keyPrefix} is {@code ""} or {@code "worlds.<name>."}.
   */
  private static List<AreaSpec> readAreaList(
      Object areasValue, Object radiusValue, String keyPrefix, List<String> warnings) {
    List<AreaSpec> areas = areasValue == null ? null : readAreas(areasValue, keyPrefix, warnings);
    if (areas != null) {
      if (radiusValue != null) {
        warnings.add(
            keyPrefix + "radius-blocks: ignored because " + keyPrefix + "areas is also set");
      }
      return areas;
    }
    if (radiusValue == null) {
      return null;
    }
    int radius =
        readInt(
            radiusValue,
            keyPrefix + "radius-blocks",
            DEFAULT_RADIUS_BLOCKS,
            MIN_RADIUS_BLOCKS,
            MAX_RADIUS_BLOCKS,
            warnings);
    return List.of(new AreaSpec(AreaSpec.Center.ORIGIN, radius));
  }

  /** Parses the {@code areas} list itself; {@code null} when nothing in it is usable. */
  private static List<AreaSpec> readAreas(Object value, String keyPrefix, List<String> warnings) {
    if (!(value instanceof List<?> list)) {
      warnings.add(keyPrefix + "areas: expected a list, got '" + value + "', ignored");
      return null;
    }
    List<AreaSpec> areas = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      AreaSpec area = readArea(list.get(i), keyPrefix + "areas[" + i + "]", warnings);
      if (area != null) {
        areas.add(area);
      }
    }
    if (areas.isEmpty()) {
      warnings.add(keyPrefix + "areas: no valid entries, ignored");
      return null;
    }
    return List.copyOf(areas);
  }

  private static AreaSpec readArea(Object value, String key, List<String> warnings) {
    if (!(value instanceof Map<?, ?>)) {
      warnings.add(
          key + ": expected a mapping with 'center'/'radius-blocks', got '" + value + "', ignored");
      return null;
    }
    Map<String, ?> entry = readSection(value, key, warnings);
    AreaSpec.Center center = readCenter(entry.get("center"), key + ".center", warnings);
    int radius =
        readInt(
            entry.get("radius-blocks"),
            key + ".radius-blocks",
            DEFAULT_RADIUS_BLOCKS,
            MIN_RADIUS_BLOCKS,
            MAX_RADIUS_BLOCKS,
            warnings);
    return new AreaSpec(center, radius);
  }

  /** {@code origin} | {@code spawn} | {@code {x: <block>, z: <block>}}; missing = origin. */
  private static AreaSpec.Center readCenter(Object value, String key, List<String> warnings) {
    if (value == null) {
      return AreaSpec.Center.ORIGIN;
    }
    if (value instanceof String name) {
      switch (name.toLowerCase(Locale.ROOT)) {
        case "origin":
          return AreaSpec.Center.ORIGIN;
        case "spawn":
          return AreaSpec.Center.SPAWN;
        default:
          warnings.add(
              key + ": expected origin, spawn or {x, z}, got '" + name + "', using origin");
          return AreaSpec.Center.ORIGIN;
      }
    }
    if (value instanceof Map<?, ?>) {
      Map<String, ?> coords = readSection(value, key, warnings);
      if (coords.get("x") == null || coords.get("z") == null) {
        warnings.add(key + ": needs both x and z, missing one defaults to 0");
      }
      int x =
          readInt(coords.get("x"), key + ".x", 0, -MAX_CENTER_BLOCKS, MAX_CENTER_BLOCKS, warnings);
      int z =
          readInt(coords.get("z"), key + ".z", 0, -MAX_CENTER_BLOCKS, MAX_CENTER_BLOCKS, warnings);
      return new AreaSpec.Center.Fixed(x, z);
    }
    warnings.add(key + ": expected origin, spawn or {x, z}, got '" + value + "', using origin");
    return AreaSpec.Center.ORIGIN;
  }

  // ---- primitive readers --------------------------------------------------------------

  private static Map<String, ?> readSection(Object value, String key, List<String> warnings) {
    if (value == null) {
      return Map.of();
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> section = new LinkedHashMap<>();
      map.forEach((k, v) -> section.put(String.valueOf(k), v));
      return section;
    }
    warnings.add(key + ": expected a section, got '" + value + "', using defaults");
    return Map.of();
  }

  private static int readInt(
      Object value, String key, int def, int min, int max, List<String> warnings) {
    if (value == null) {
      return def;
    }
    if (value instanceof Number number) {
      int intValue = number.intValue();
      if (intValue < min || intValue > max) {
        int clamped = Math.clamp(intValue, min, max);
        warnings.add(key + ": " + intValue + " out of range [" + min + ", " + max
            + "], clamped to " + clamped);
        return clamped;
      }
      return intValue;
    }
    warnings.add(key + ": expected a number, got '" + value + "', using default " + def);
    return def;
  }

  private static boolean readBoolean(
      Object value, String key, boolean def, List<String> warnings) {
    if (value == null) {
      return def;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    warnings.add(key + ": expected true/false, got '" + value + "', using default " + def);
    return def;
  }
}

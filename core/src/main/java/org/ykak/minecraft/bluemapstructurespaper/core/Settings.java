package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed plugin configuration. Built from the plain nested-map shape a YAML parser
 * produces, so this stays free of Bukkit types. Invalid values fall back to defaults
 * and are reported via {@link #warnings()} for the plugin to log.
 */
public record Settings(
    int radiusBlocks,
    int budgetMsPerTick,
    boolean cacheEnabled,
    Map<String, Boolean> layerEnabled,
    List<String> enabledLayerIds,
    List<String> warnings) {

  public static final int DEFAULT_RADIUS_BLOCKS = 5000;
  public static final int MIN_RADIUS_BLOCKS = 256;
  public static final int MAX_RADIUS_BLOCKS = 1_000_000;
  public static final int DEFAULT_BUDGET_MS = 20;
  public static final int MIN_BUDGET_MS = 1;
  public static final int MAX_BUDGET_MS = 45;

  public boolean isLayerEnabled(String layerId) {
    return layerEnabled.getOrDefault(layerId, false);
  }

  public static Settings fromMap(Map<String, ?> config, Collection<StructureLayer> catalog) {
    List<String> warnings = new ArrayList<>();

    int radius =
        readInt(
            config.get("radius-blocks"),
            "radius-blocks",
            DEFAULT_RADIUS_BLOCKS,
            MIN_RADIUS_BLOCKS,
            MAX_RADIUS_BLOCKS,
            warnings);

    Map<String, ?> scan = readSection(config.get("scan"), "scan", warnings);
    int budget =
        readInt(
            scan.get("budget-ms-per-tick"),
            "scan.budget-ms-per-tick",
            DEFAULT_BUDGET_MS,
            MIN_BUDGET_MS,
            MAX_BUDGET_MS,
            warnings);
    boolean cache =
        readBoolean(scan.get("cache-enabled"), "scan.cache-enabled", true, warnings);

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
        radius,
        budget,
        cache,
        Map.copyOf(layerEnabled),
        List.copyOf(enabledIds),
        List.copyOf(warnings));
  }

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

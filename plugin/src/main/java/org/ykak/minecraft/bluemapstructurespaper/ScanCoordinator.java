package org.ykak.minecraft.bluemapstructurespaper;

import de.bluecolored.bluemap.api.BlueMapAPI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.ykak.minecraft.bluemapstructurespaper.core.AreaSpec;
import org.ykak.minecraft.bluemapstructurespaper.core.Dimension;
import org.ykak.minecraft.bluemapstructurespaper.core.FoundStructure;
import org.ykak.minecraft.bluemapstructurespaper.core.SearchArea;
import org.ykak.minecraft.bluemapstructurespaper.core.SeedStructureLocator;
import org.ykak.minecraft.bluemapstructurespaper.core.Settings;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureCatalog;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureLayer;

/**
 * Per Bukkit world, plans and runs a seed-math structure scan (async) and hands the results
 * to the {@link MarkerPublisher} back on the main thread. The scan is pure computation over
 * the world seed plus biome validation, so it costs milliseconds rather than the sync-loading
 * {@code locateNearestStructure} calls this replaced (issue #3).
 */
final class ScanCoordinator {

  private static final Logger LOGGER = Logger.getLogger("BlueMapStructuresPaper");

  /**
   * Past this many markers in one world the BlueMap web app tends to get sluggish; zoom
   * gating helps but the practical limit is total marker count (issue #4 measurements).
   */
  private static final int MARKER_COUNT_WARN_THRESHOLD = 5000;

  private final BlueMapStructuresPlugin plugin;
  private final Settings settings;
  private final MarkerPublisher publisher;
  private final List<BukkitTask> activeTasks = new ArrayList<>();

  ScanCoordinator(BlueMapStructuresPlugin plugin, Settings settings) {
    this.plugin = plugin;
    this.settings = settings;
    this.publisher = new MarkerPublisher(plugin, settings);
  }

  /** Main thread only. */
  void start(BlueMapAPI api) {
    stopTasks();
    warnUnknownWorldOverrides();
    for (World world : plugin.getServer().getWorlds()) {
      Dimension dimension = dimensionOf(world);
      if (dimension == null || api.getWorld(world).isEmpty()) {
        continue; // custom dimension, or no BlueMap map configured for this world
      }
      List<StructureLayer> layers =
          StructureCatalog.layers().stream()
              .filter(layer -> layer.dimension() == dimension)
              .filter(layer -> settings.isLayerEnabled(layer.id()))
              .toList();
      if (layers.isEmpty()) {
        continue;
      }

      // Everything the async task needs must be captured here, on the main thread:
      // the biome provider/tag lookups, the seed, and the resolved search areas
      // (spawn centers require World#getSpawnLocation).
      BiomeTagCheck check = new BiomeTagCheck(world, layers);
      long seed = world.getSeed();
      List<SearchArea> areas = resolveAreas(settings.areasForWorld(world.getName()), world);
      String worldName = world.getName();

      BukkitTask task =
          Bukkit.getScheduler()
              .runTaskAsynchronously(
                  plugin, () -> runScan(api, world, worldName, layers, seed, areas, check));
      activeTasks.add(task);
    }
  }

  /** Configured per-world overrides that match no loaded world (scan-time check, issue #4). */
  private void warnUnknownWorldOverrides() {
    Set<String> loaded =
        plugin.getServer().getWorlds().stream().map(World::getName).collect(Collectors.toSet());
    for (String name : settings.worldAreas().keySet()) {
      if (!loaded.contains(name)) {
        LOGGER.warning(
            "config.yml: worlds." + name + ": no loaded world has this name, override unused.");
      }
    }
  }

  /** Main thread only: turns symbolic centers into block coordinates for this world. */
  private static List<SearchArea> resolveAreas(List<AreaSpec> specs, World world) {
    List<SearchArea> areas = new ArrayList<>(specs.size());
    for (AreaSpec spec : specs) {
      areas.add(
          switch (spec.center()) {
            case AreaSpec.Center.Origin ignored -> new SearchArea(0, 0, spec.radiusBlocks());
            case AreaSpec.Center.Spawn ignored -> {
              Location spawn = world.getSpawnLocation();
              yield new SearchArea(spawn.getBlockX(), spawn.getBlockZ(), spec.radiusBlocks());
            }
            case AreaSpec.Center.Fixed fixed ->
                new SearchArea(fixed.x(), fixed.z(), spec.radiusBlocks());
          });
    }
    return List.copyOf(areas);
  }

  /** Async thread. */
  private void runScan(
      BlueMapAPI api,
      World world,
      String worldName,
      List<StructureLayer> layers,
      long seed,
      List<SearchArea> areas,
      BiomeTagCheck check) {
    long startedNanos = System.nanoTime();
    Map<String, List<FoundStructure>> results = new LinkedHashMap<>();
    int total = 0;
    for (StructureLayer layer : layers) {
      List<FoundStructure> found = SeedStructureLocator.locate(layer, seed, areas, check);
      results.put(layer.id(), found);
      total += found.size();
    }
    long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
    LOGGER.info(
        "World '" + worldName + "': found " + total + " structure(s) across " + layers.size()
            + " layer(s) in " + areas.size() + " area(s) in " + elapsedMs + " ms.");
    if (total > MARKER_COUNT_WARN_THRESHOLD) {
      LOGGER.warning(
          "World '" + worldName + "': " + total + " markers exceeds ~"
              + MARKER_COUNT_WARN_THRESHOLD + "; the BlueMap web app may become sluggish."
              + " Consider smaller search areas or disabling dense layers (zoom gating"
              + " reduces drawing, not the marker payload).");
    }

    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!plugin.isEnabled()) {
                return; // plugin was disabled while the async scan was in flight
              }
              publisher.publish(api, world, results);
            });
  }

  /** Callable from any thread (BlueMap's onDisable). */
  void stop(BlueMapAPI api) {
    stopTasks();
    publisher.removeAll(api);
  }

  private void stopTasks() {
    activeTasks.forEach(BukkitTask::cancel);
    activeTasks.clear();
  }

  private static Dimension dimensionOf(World world) {
    return switch (world.getEnvironment()) {
      case NORMAL -> Dimension.OVERWORLD;
      case NETHER -> Dimension.NETHER;
      case THE_END -> Dimension.END;
      default -> null;
    };
  }
}

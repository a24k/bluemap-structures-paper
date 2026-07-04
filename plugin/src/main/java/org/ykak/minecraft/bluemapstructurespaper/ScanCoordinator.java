package org.ykak.minecraft.bluemapstructurespaper;

import de.bluecolored.bluemap.api.BlueMapAPI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.ykak.minecraft.bluemapstructurespaper.core.Dimension;
import org.ykak.minecraft.bluemapstructurespaper.core.FoundStructure;
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
      // the biome provider/tag lookups, the seed, and the radius.
      BiomeTagCheck check = new BiomeTagCheck(world, layers);
      long seed = world.getSeed();
      int radius = settings.radiusBlocks();
      String worldName = world.getName();

      BukkitTask task =
          Bukkit.getScheduler()
              .runTaskAsynchronously(
                  plugin, () -> runScan(api, world, worldName, layers, seed, radius, check));
      activeTasks.add(task);
    }
  }

  /** Async thread. */
  private void runScan(
      BlueMapAPI api,
      World world,
      String worldName,
      List<StructureLayer> layers,
      long seed,
      int radius,
      BiomeTagCheck check) {
    long startedNanos = System.nanoTime();
    Map<String, List<FoundStructure>> results = new LinkedHashMap<>();
    int total = 0;
    for (StructureLayer layer : layers) {
      List<FoundStructure> found = SeedStructureLocator.locate(layer, seed, radius, check);
      results.put(layer.id(), found);
      total += found.size();
    }
    long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
    LOGGER.info(
        "World '" + worldName + "': found " + total + " structure(s) across " + layers.size()
            + " layer(s) in " + elapsedMs + " ms.");

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

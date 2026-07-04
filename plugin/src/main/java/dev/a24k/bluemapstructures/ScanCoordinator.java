package dev.a24k.bluemapstructures;

import de.bluecolored.bluemap.api.BlueMapAPI;
import dev.a24k.bluemapstructures.core.Dimension;
import dev.a24k.bluemapstructures.core.FoundStructure;
import dev.a24k.bluemapstructures.core.ScanCacheKey;
import dev.a24k.bluemapstructures.core.Settings;
import dev.a24k.bluemapstructures.core.StructureCatalog;
import dev.a24k.bluemapstructures.core.StructureLayer;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;

/**
 * Decides, per Bukkit world, whether markers can come from the cache or a fresh scan is
 * needed, runs the scans, and hands results to the {@link MarkerPublisher}.
 */
final class ScanCoordinator {

  private final BlueMapStructuresPlugin plugin;
  private final Settings settings;
  private final ScanResultCache cache;
  private final MarkerPublisher publisher;
  private final List<StructureScanner> activeScanners = new ArrayList<>();

  ScanCoordinator(BlueMapStructuresPlugin plugin, Settings settings) {
    this.plugin = plugin;
    this.settings = settings;
    this.cache = new ScanResultCache(plugin.getDataFolder().toPath().resolve("cache"));
    this.publisher = new MarkerPublisher(plugin, settings);
  }

  /** Main thread only. */
  void start(BlueMapAPI api) {
    stopScanners();
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

      String cacheKey =
          ScanCacheKey.compute(
              world.getSeed(),
              settings.radiusBlocks(),
              layers.stream().map(StructureLayer::id).toList());

      if (settings.cacheEnabled()) {
        Optional<Map<String, List<FoundStructure>>> cached = cache.load(world.getUID(), cacheKey);
        if (cached.isPresent()) {
          plugin
              .getLogger()
              .info("World '" + world.getName() + "': using cached scan results.");
          publisher.publish(api, world, cached.get());
          continue;
        }
      }

      StructureScanner scanner =
          new StructureScanner(
              plugin,
              world,
              buildQueries(world, layers),
              settings.radiusBlocks(),
              settings.budgetMsPerTick(),
              results -> onScanComplete(api, world, cacheKey, results));
      activeScanners.add(scanner);
      scanner.start();
    }
  }

  /** Callable from any thread (BlueMap's onDisable). */
  void stop(BlueMapAPI api) {
    stopScanners();
    publisher.removeAll(api);
  }

  private void stopScanners() {
    activeScanners.forEach(StructureScanner::stop);
    activeScanners.clear();
  }

  private void onScanComplete(
      BlueMapAPI api, World world, String cacheKey, Map<String, List<FoundStructure>> results) {
    if (settings.cacheEnabled()) {
      cache.store(world.getUID(), cacheKey, results);
    }
    publisher.publish(api, world, results);
  }

  private List<StructureScanner.LayerQuery> buildQueries(World world, List<StructureLayer> layers) {
    Registry<Structure> registry =
        RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
    List<StructureScanner.LayerQuery> queries = new ArrayList<>();
    for (StructureLayer layer : layers) {
      for (String key : layer.structureKeys()) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        Structure structure = namespacedKey == null ? null : registry.get(namespacedKey);
        if (structure == null) {
          plugin
              .getLogger()
              .warning(
                  "Structure '" + key + "' (layer " + layer.id()
                      + ") not present in this server's registry, skipping.");
          continue;
        }
        queries.add(new StructureScanner.LayerQuery(layer, key, structure));
      }
    }
    plugin
        .getLogger()
        .info(
            "World '" + world.getName() + "': scanning " + queries.size()
                + " structure(s) across " + layers.size() + " layer(s), radius "
                + settings.radiusBlocks() + " blocks.");
    return queries;
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

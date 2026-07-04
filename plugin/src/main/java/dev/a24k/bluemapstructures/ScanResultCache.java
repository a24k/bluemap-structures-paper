package dev.a24k.bluemapstructures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.a24k.bluemapstructures.core.FoundStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Persists scan results per world (JSON in the plugin data folder), keyed by
 * {@link dev.a24k.bluemapstructures.core.ScanCacheKey} so any change to seed, radius or
 * layer set invalidates the file. Corrupt/missing files simply mean "re-scan".
 */
final class ScanResultCache {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Logger LOGGER = Logger.getLogger("BlueMapStructuresPaper");

  /** Gson-friendly file shape. */
  private static final class CacheFile {
    String key;
    Map<String, List<Entry>> layers;
  }

  private static final class Entry {
    String k;
    int x;
    int z;
  }

  private final Path cacheDir;

  ScanResultCache(Path cacheDir) {
    this.cacheDir = cacheDir;
  }

  Optional<Map<String, List<FoundStructure>>> load(UUID worldId, String expectedKey) {
    Path file = fileFor(worldId);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      CacheFile cached = GSON.fromJson(Files.readString(file), CacheFile.class);
      if (cached == null || cached.layers == null || !expectedKey.equals(cached.key)) {
        return Optional.empty();
      }
      Map<String, List<FoundStructure>> results = new LinkedHashMap<>();
      cached.layers.forEach(
          (layerId, entries) -> {
            List<FoundStructure> found = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
              found.add(new FoundStructure(entry.k, entry.x, entry.z));
            }
            results.put(layerId, List.copyOf(found));
          });
      return Optional.of(results);
    } catch (IOException | JsonSyntaxException e) {
      LOGGER.warning("Unreadable scan cache " + file + " (" + e.getMessage() + "), re-scanning.");
      return Optional.empty();
    }
  }

  void store(UUID worldId, String key, Map<String, List<FoundStructure>> results) {
    CacheFile cached = new CacheFile();
    cached.key = key;
    cached.layers = new LinkedHashMap<>();
    results.forEach(
        (layerId, found) -> {
          List<Entry> entries = new ArrayList<>(found.size());
          for (FoundStructure structure : found) {
            Entry entry = new Entry();
            entry.k = structure.structureKey();
            entry.x = structure.x();
            entry.z = structure.z();
            entries.add(entry);
          }
          cached.layers.put(layerId, entries);
        });
    try {
      Files.createDirectories(cacheDir);
      Files.writeString(fileFor(worldId), GSON.toJson(cached));
    } catch (IOException e) {
      LOGGER.warning("Could not write scan cache for " + worldId + ": " + e.getMessage());
    }
  }

  private Path fileFor(UUID worldId) {
    return cacheDir.resolve(worldId + ".json");
  }
}

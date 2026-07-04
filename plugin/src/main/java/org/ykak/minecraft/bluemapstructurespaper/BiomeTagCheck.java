package org.ykak.minecraft.bluemapstructurespaper;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.TagKey;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.ykak.minecraft.bluemapstructurespaper.core.SeedStructureLocator;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureLayer;

/**
 * Validates seed-math candidates against the world's vanilla biome layout, for one Bukkit
 * world. Tag sets are resolved once at construction (main thread); {@link #isValid} is called
 * repeatedly from the async scan task.
 */
final class BiomeTagCheck implements SeedStructureLocator.BiomeCheck {

  private static final Logger LOGGER = Logger.getLogger("BlueMapStructuresPaper");
  private static final int SAMPLE_Y = 64;

  private final World world;
  private final BiomeProvider provider;
  private final Map<String, Set<Biome>> allowedBiomesByLayer;

  /** Main thread only: touches the world's biome provider and the biome registry. */
  BiomeTagCheck(World world, List<StructureLayer> layers) {
    this.world = world;

    BiomeProvider providerAttempt;
    try {
      providerAttempt = world.vanillaBiomeProvider();
    } catch (RuntimeException e) {
      // Paper #9394: some exotic/custom worlds throw instead of returning a provider.
      providerAttempt = null;
      LOGGER.warning(
          "World '" + world.getName() + "': vanilla biome provider unavailable ("
              + e + "), biome-based structure filtering disabled for this world.");
    }
    this.provider = providerAttempt;

    Registry<Biome> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
    Map<String, Set<Biome>> resolved = new LinkedHashMap<>();
    for (StructureLayer layer : layers) {
      if (layer.biomeTagIds().isEmpty()) {
        continue; // no restriction: isValid treats a missing entry as always-valid
      }
      Set<Biome> allowed = new HashSet<>();
      for (String tagId : layer.biomeTagIds()) {
        TagKey<Biome> tagKey = TagKey.create(RegistryKey.BIOME, NamespacedKey.minecraft(tagId));
        if (!biomes.hasTag(tagKey)) {
          LOGGER.warning(
              "Biome tag '" + tagId + "' (layer " + layer.id()
                  + ") not present in this server's biome registry, skipping.");
          continue;
        }
        allowed.addAll(biomes.getTag(tagKey).resolve(biomes));
      }
      resolved.put(layer.id(), allowed);
    }
    this.allowedBiomesByLayer = Map.copyOf(resolved);
  }

  /** Called from the async scan task; {@code BiomeProvider.getBiome} is documented thread-safe. */
  @Override
  public boolean isValid(StructureLayer layer, int blockX, int blockZ) {
    Set<Biome> allowed = allowedBiomesByLayer.get(layer.id());
    if (allowed == null) {
      return true; // layer has no biome restriction
    }
    if (provider == null) {
      return true; // no provider available for this world; can't filter, so accept all
    }
    Biome biome = provider.getBiome(world, blockX, SAMPLE_Y, blockZ);
    return allowed.contains(biome);
  }
}

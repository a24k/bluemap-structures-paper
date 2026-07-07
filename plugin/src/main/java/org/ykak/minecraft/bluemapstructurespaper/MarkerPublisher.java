package org.ykak.minecraft.bluemapstructurespaper;

import de.bluecolored.bluemap.api.AssetStorage;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.ykak.minecraft.bluemapstructurespaper.core.FoundStructure;
import org.ykak.minecraft.bluemapstructurespaper.core.MarkerData;
import org.ykak.minecraft.bluemapstructurespaper.core.Settings;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureCatalog;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureLayer;

/**
 * Builds BlueMap marker sets (one toggleable set per layer per map) from scan results.
 * Icons are generated at runtime from the Minecraft client jar (see
 * {@link ClientAssetIcons}) and stored per map through {@link AssetStorage} (the
 * non-deprecated replacement for {@code WebApp#createImage}); existing assets are reused
 * across restarts.
 */
final class MarkerPublisher {

  private static final int ICON_ANCHOR = 11; // center of the 22×22 icons

  private final BlueMapStructuresPlugin plugin;
  private final Settings settings;
  private final Set<String> publishedSetIds = ConcurrentHashMap.newKeySet();

  MarkerPublisher(BlueMapStructuresPlugin plugin, Settings settings) {
    this.plugin = plugin;
    this.settings = settings;
  }

  /** Main thread only. */
  void publish(
      BlueMapAPI api,
      World world,
      Map<String, List<FoundStructure>> resultsByLayer,
      Map<String, Path> iconFiles) {
    api.getWorld(world)
        .ifPresent(
            blueMapWorld -> {
              for (BlueMapMap map : blueMapWorld.getMaps()) {
                publishToMap(map, resultsByLayer, iconFiles);
              }
            });
  }

  /** Callable from any thread; BlueMap's marker-set map is concurrent. */
  void removeAll(BlueMapAPI api) {
    for (BlueMapMap map : api.getMaps()) {
      map.getMarkerSets().keySet().removeAll(publishedSetIds);
    }
    publishedSetIds.clear();
  }

  private void publishToMap(
      BlueMapMap map,
      Map<String, List<FoundStructure>> resultsByLayer,
      Map<String, Path> iconFiles) {
    Map<String, String> iconAddresses = storeIcons(map, iconFiles);

    for (Map.Entry<String, List<FoundStructure>> entry : resultsByLayer.entrySet()) {
      StructureLayer layer = StructureCatalog.byId(entry.getKey()).orElse(null);
      if (layer == null || entry.getValue().isEmpty()) {
        continue;
      }

      MarkerSet markerSet =
          MarkerSet.builder().label(layer.displayName()).defaultHidden(false).build();

      int y = MarkerData.defaultY(layer.dimension());
      for (FoundStructure found : entry.getValue()) {
        POIMarker.Builder builder =
            POIMarker.builder()
                .label(MarkerData.label(layer.displayName(), found.x(), found.z()))
                .detail(MarkerData.popupHtml(layer.displayName(), found.x(), y, found.z()))
                .position(found.x() + 0.5, y, found.z() + 0.5)
                .maxDistance(layer.zoomMaxDistance());
        String icon = iconAddresses.get(layer.id());
        if (icon != null) {
          builder.icon(icon, ICON_ANCHOR, ICON_ANCHOR);
        }
        markerSet
            .getMarkers()
            .put(MarkerData.markerId(layer.id(), found.x(), found.z()), builder.build());
      }

      String setId = MarkerData.markerSetId(layer.id());
      map.getMarkerSets().put(setId, markerSet);
      publishedSetIds.add(setId);
    }
  }

  /**
   * Writes generated icons (see {@link ClientAssetIcons}) into the map's asset storage
   * (skipping ones already there). Asset names are keyed by the running Minecraft version
   * and the plugin's own version, so an icon set generated for one combination is not
   * reused after a server upgrade or a plugin update that changes the icon table (the new
   * asset name also busts browser caches of the old URL).
   */
  private Map<String, String> storeIcons(BlueMapMap map, Map<String, Path> iconFiles) {
    Map<String, String> addresses = new HashMap<>();
    AssetStorage storage = map.getAssetStorage();
    String mcVersion = Bukkit.getMinecraftVersion();
    String pluginVersion = plugin.getPluginMeta().getVersion();
    for (StructureLayer layer : StructureCatalog.layers()) {
      if (!settings.isLayerEnabled(layer.id())) {
        continue;
      }
      Path iconFile = iconFiles.get(layer.id());
      if (iconFile == null) {
        continue;
      }
      String assetName = "bmsp-" + mcVersion + "-v" + pluginVersion + "-" + layer.id() + ".png";
      try {
        if (!storage.assetExists(assetName)) {
          BufferedImage image;
          try (InputStream in = Files.newInputStream(iconFile)) {
            image = ImageIO.read(in);
          }
          try (OutputStream out = storage.writeAsset(assetName)) {
            ImageIO.write(image, "png", out);
          }
        }
        addresses.put(layer.id(), storage.getAssetUrl(assetName));
      } catch (IOException e) {
        plugin
            .getLogger()
            .warning(
                "Could not store icon for layer '" + layer.id() + "' on map '" + map.getId()
                    + "': " + e.getMessage());
      }
    }
    return addresses;
  }
}

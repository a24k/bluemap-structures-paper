package org.ykak.minecraft.bluemapstructurespaper;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.ykak.minecraft.bluemapstructurespaper.core.FoundStructure;
import org.ykak.minecraft.bluemapstructurespaper.core.MarkerData;
import org.ykak.minecraft.bluemapstructurespaper.core.Settings;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureCatalog;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureLayer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.bukkit.World;

/**
 * Builds BlueMap marker sets (one toggleable set per layer per map) from scan results.
 * Icons are uploaded once per BlueMap enable via the web app.
 */
final class MarkerPublisher {

  private static final int ICON_ANCHOR = 11; // center of the 22×22 icons

  private final BlueMapStructuresPlugin plugin;
  private final Settings settings;
  private final Map<String, String> iconAddresses = new HashMap<>();
  private final Set<String> publishedSetIds = ConcurrentHashMap.newKeySet();
  private boolean iconsUploaded;

  MarkerPublisher(BlueMapStructuresPlugin plugin, Settings settings) {
    this.plugin = plugin;
    this.settings = settings;
  }

  /** Main thread only. */
  void publish(BlueMapAPI api, World world, Map<String, List<FoundStructure>> resultsByLayer) {
    uploadIconsOnce(api);
    api.getWorld(world)
        .ifPresent(
            blueMapWorld -> {
              for (BlueMapMap map : blueMapWorld.getMaps()) {
                publishToMap(map, resultsByLayer);
              }
            });
  }

  /** Callable from any thread; BlueMap's marker-set map is concurrent. */
  void removeAll(BlueMapAPI api) {
    for (BlueMapMap map : api.getMaps()) {
      map.getMarkerSets().keySet().removeAll(publishedSetIds);
    }
    publishedSetIds.clear();
    iconsUploaded = false;
    iconAddresses.clear();
  }

  private void publishToMap(BlueMapMap map, Map<String, List<FoundStructure>> resultsByLayer) {
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

  private void uploadIconsOnce(BlueMapAPI api) {
    if (iconsUploaded) {
      return;
    }
    iconsUploaded = true;
    for (StructureLayer layer : StructureCatalog.layers()) {
      if (!settings.isLayerEnabled(layer.id())) {
        continue;
      }
      try (InputStream stream =
          plugin.getResource("icons/" + layer.iconFile())) {
        if (stream == null) {
          plugin.getLogger().warning("Missing bundled icon: " + layer.iconFile());
          continue;
        }
        BufferedImage image = ImageIO.read(stream);
        String address =
            api.getWebApp().createImage(image, "structures/" + layer.iconFile());
        iconAddresses.put(layer.id(), address);
      } catch (IOException e) {
        plugin
            .getLogger()
            .warning("Could not upload icon " + layer.iconFile() + ": " + e.getMessage());
      }
    }
  }
}

package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.Map;
import java.util.Optional;

/**
 * Maps each {@link StructureLayer} id to the client-jar texture path that composes its marker
 * icon. Used at startup to look up which texture to extract from the downloaded Mojang client
 * jar before handing it to {@link IconComposer}.
 */
public final class IconSources {

  private static final String TEXTURES_ROOT = "assets/minecraft/textures/";

  private static final Map<String, String> PATHS =
      Map.ofEntries(
          Map.entry("village", TEXTURES_ROOT + "item/bell.png"),
          Map.entry("desert_pyramid", TEXTURES_ROOT + "block/chiseled_sandstone.png"),
          Map.entry("jungle_temple", TEXTURES_ROOT + "block/mossy_cobblestone.png"),
          Map.entry("swamp_hut", TEXTURES_ROOT + "item/cauldron.png"),
          Map.entry("igloo", TEXTURES_ROOT + "item/snowball.png"),
          Map.entry("pillager_outpost", TEXTURES_ROOT + "item/crossbow_standby.png"),
          Map.entry("ancient_city", TEXTURES_ROOT + "item/echo_shard.png"),
          Map.entry("trail_ruins", TEXTURES_ROOT + "item/brush.png"),
          Map.entry("trial_chambers", TEXTURES_ROOT + "item/trial_key.png"),
          Map.entry("ocean_ruin", TEXTURES_ROOT + "item/trident.png"),
          Map.entry("shipwreck", TEXTURES_ROOT + "item/oak_boat.png"),
          Map.entry("ruined_portal", TEXTURES_ROOT + "block/obsidian.png"),
          Map.entry("monument", TEXTURES_ROOT + "block/prismarine_bricks.png"),
          Map.entry("mansion", TEXTURES_ROOT + "item/totem_of_undying.png"),
          Map.entry("fortress", TEXTURES_ROOT + "item/blaze_rod.png"),
          Map.entry("bastion", TEXTURES_ROOT + "item/gold_ingot.png"),
          Map.entry("ruined_portal_nether", TEXTURES_ROOT + "block/crying_obsidian.png"),
          Map.entry("end_city", TEXTURES_ROOT + "item/shulker_shell.png"),
          Map.entry("buried_treasure", TEXTURES_ROOT + "item/heart_of_the_sea.png"),
          Map.entry("stronghold", TEXTURES_ROOT + "item/ender_eye.png"));

  private IconSources() {}

  /**
   * Client-jar entry path of the texture composing this layer's icon, e.g. {@code
   * "assets/minecraft/textures/item/ender_eye.png"}; empty for unknown layer ids.
   */
  public static Optional<String> texturePath(String layerId) {
    return Optional.ofNullable(PATHS.get(layerId));
  }
}

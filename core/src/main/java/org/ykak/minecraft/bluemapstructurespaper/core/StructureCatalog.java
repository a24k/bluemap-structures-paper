package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.List;
import java.util.Optional;

/**
 * The 20 supported structure layers. Spacing/separation values mirror vanilla 1.21
 * {@code StructureSet} constants (cross-checked against mc-bluemap-structures, MIT —
 * see THIRD_PARTY_NOTICES.md). Zoom tiers: 5000 = always visible, 1000 = dense layer,
 * only shown zoomed-in.
 */
public final class StructureCatalog {

  private static final int WIDE = 5000;
  private static final int ZOOMED_IN = 1000;

  private static final List<StructureLayer> LAYERS =
      List.of(
          overworld(
              "village",
              "Villages",
              34,
              List.of(
                  "minecraft:village_plains",
                  "minecraft:village_desert",
                  "minecraft:village_savanna",
                  "minecraft:village_snowy",
                  "minecraft:village_taiga"),
              WIDE,
              "village.png"),
          overworld(
              "desert_pyramid",
              "Desert Pyramids",
              32,
              List.of("minecraft:desert_pyramid"),
              WIDE,
              "desert_temple.png"),
          overworld(
              "jungle_temple",
              "Jungle Temples",
              32,
              // vanilla registry name differs from the colloquial one
              List.of("minecraft:jungle_pyramid"),
              WIDE,
              "jungle_temple.png"),
          overworld(
              "swamp_hut",
              "Swamp Huts",
              32,
              List.of("minecraft:swamp_hut"),
              WIDE,
              "witch_hut.png"),
          overworld("igloo", "Igloos", 32, List.of("minecraft:igloo"), WIDE, "igloo.png"),
          overworld(
              "pillager_outpost",
              "Pillager Outposts",
              32,
              List.of("minecraft:pillager_outpost"),
              WIDE,
              "outpost.png"),
          overworld(
              "ancient_city",
              "Ancient Cities",
              24,
              List.of("minecraft:ancient_city"),
              WIDE,
              "ancient_city.png"),
          overworld(
              "trail_ruins",
              "Trail Ruins",
              34,
              List.of("minecraft:trail_ruins"),
              WIDE,
              "trail_ruins.png"),
          overworld(
              "trial_chambers",
              "Trial Chambers",
              34,
              List.of("minecraft:trial_chambers"),
              ZOOMED_IN,
              "trial_chamber.png"),
          overworld(
              "ocean_ruin",
              "Ocean Ruins",
              20,
              List.of("minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm"),
              ZOOMED_IN,
              "ocean_ruins.png"),
          overworld(
              "shipwreck",
              "Shipwrecks",
              24,
              List.of("minecraft:shipwreck", "minecraft:shipwreck_beached"),
              ZOOMED_IN,
              "shipwreck.png"),
          overworld(
              "ruined_portal",
              "Ruined Portals (Overworld)",
              40,
              List.of(
                  "minecraft:ruined_portal",
                  "minecraft:ruined_portal_desert",
                  "minecraft:ruined_portal_jungle",
                  "minecraft:ruined_portal_swamp",
                  "minecraft:ruined_portal_mountain",
                  "minecraft:ruined_portal_ocean"),
              ZOOMED_IN,
              "ruined_portal_ow.png"),
          overworld(
              "monument",
              "Ocean Monuments",
              32,
              List.of("minecraft:monument"),
              WIDE,
              "monument.png"),
          overworld(
              "mansion",
              "Woodland Mansions",
              80,
              List.of("minecraft:mansion"),
              WIDE,
              "mansion.png"),
          new StructureLayer(
              "fortress",
              "Nether Fortresses",
              Dimension.NETHER,
              new Placement.Grid(27),
              List.of("minecraft:fortress"),
              WIDE,
              "nether_fortress.png",
              true),
          new StructureLayer(
              "bastion",
              "Bastion Remnants",
              Dimension.NETHER,
              new Placement.Grid(27),
              List.of("minecraft:bastion_remnant"),
              WIDE,
              "bastion.png",
              true),
          new StructureLayer(
              "ruined_portal_nether",
              "Ruined Portals (Nether)",
              Dimension.NETHER,
              // vanilla nether ruined portals use spacing 25 (not 40 like overworld)
              new Placement.Grid(25),
              List.of("minecraft:ruined_portal_nether"),
              WIDE,
              "ruined_portal_nether.png",
              true),
          new StructureLayer(
              "end_city",
              "End Cities",
              Dimension.END,
              new Placement.Grid(20),
              List.of("minecraft:end_city"),
              WIDE,
              "end_city.png",
              true),
          new StructureLayer(
              "buried_treasure",
              "Buried Treasure",
              Dimension.OVERWORLD,
              // per-chunk placement: exhaustive locate() enumeration is very expensive,
              // hence opt-in (see docs/REQUIREMENTS.md FR-1)
              new Placement.Grid(1),
              List.of("minecraft:buried_treasure"),
              ZOOMED_IN,
              "treasure.png",
              false),
          new StructureLayer(
              "stronghold",
              "Strongholds",
              Dimension.OVERWORLD,
              new Placement.ConcentricRings(),
              List.of("minecraft:stronghold"),
              WIDE,
              "stronghold.png",
              true));

  private StructureCatalog() {}

  /** All layers, in stable catalog order (drives config listing and scan order). */
  public static List<StructureLayer> layers() {
    return LAYERS;
  }

  public static Optional<StructureLayer> byId(String id) {
    return LAYERS.stream().filter(layer -> layer.id().equals(id)).findFirst();
  }

  private static StructureLayer overworld(
      String id, String name, int spacing, List<String> keys, int zoom, String icon) {
    return new StructureLayer(
        id, name, Dimension.OVERWORLD, new Placement.Grid(spacing), keys, zoom, icon, true);
  }
}

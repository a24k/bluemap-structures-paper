package org.ykak.minecraft.bluemapstructurespaper.core;

import java.util.List;
import java.util.Optional;

/**
 * The 20 supported structure layers. Spacing/separation/salt/spread values are cross-checked
 * against cubiomes ({@code finders.c}, MIT — {@code getStructureConfig}) as of the 1.21+ line;
 * re-verify here if a Minecraft update touches structure placement. cubiomes stores {@code
 * chunkRange = spacing - separation} directly, so separation below is derived as {@code
 * spacing - chunkRange}.
 *
 * <p><b>Known deliberate deviation from the reference mod (mc-bluemap-structures, MIT):</b>
 * {@code ruined_portal_nether} uses spacing 25 / separation 10 (cubiomes {@code
 * s_ruined_portal_n_117}, the 1.17+ config) — the reference mod's 40 / 15 is cubiomes'
 * pre-1.17 {@code s_ruined_portal_n} config and would leave real placement cells unsampled
 * on modern versions.
 *
 * <p>Zoom tiers: 5000 = always visible, 1000 = dense layer, only shown zoomed-in.
 *
 * <p>{@code pillager_outpost}'s rarity gate ({@code frequency} 0.2) and its exclusion zone
 * against the villages structure set come from vanilla's structure-set data (and the 1.14
 * {@code legacy_type_1} code it preserves), which cubiomes models in {@code
 * isViableStructurePos}, not {@code getStructureConfig}.
 */
public final class StructureCatalog {

  private static final int WIDE = 5000;
  private static final int ZOOMED_IN = 1000;

  /**
   * Shared random-spread grid for the villages structure set (spacing 34 / separation 8 / salt
   * 10387312, LINEAR spread) — also used to model {@code pillager_outpost}'s vanilla
   * exclusion zone against villages (see {@link Placement.ExclusionZone}).
   */
  private static final Placement.Grid VILLAGE_GRID =
      new Placement.Grid(34, 8, 10387312L, Placement.Spread.LINEAR);

  private static final List<StructureLayer> LAYERS =
      List.of(
          new StructureLayer(
              "village",
              "Villages",
              Dimension.OVERWORLD,
              VILLAGE_GRID,
              List.of(
                  "minecraft:village_plains",
                  "minecraft:village_desert",
                  "minecraft:village_savanna",
                  "minecraft:village_snowy",
                  "minecraft:village_taiga"),
              List.of(
                  "has_structure/village_plains",
                  "has_structure/village_desert",
                  "has_structure/village_savanna",
                  "has_structure/village_snowy",
                  "has_structure/village_taiga"),
              WIDE,
              true),
          linear(
              "desert_pyramid",
              "Desert Pyramids",
              32,
              8,
              14357617,
              List.of("minecraft:desert_pyramid"),
              List.of("has_structure/desert_pyramid"),
              WIDE),
          linear(
              "jungle_temple",
              "Jungle Temples",
              32,
              8,
              14357619,
              // vanilla registry name differs from the colloquial one
              List.of("minecraft:jungle_pyramid"),
              List.of("has_structure/jungle_temple"),
              WIDE),
          linear(
              "swamp_hut",
              "Swamp Huts",
              32,
              8,
              14357620,
              List.of("minecraft:swamp_hut"),
              List.of("has_structure/swamp_hut"),
              WIDE),
          linear(
              "igloo",
              "Igloos",
              32,
              8,
              14357618,
              List.of("minecraft:igloo"),
              List.of("has_structure/igloo"),
              WIDE),
          new StructureLayer(
              "pillager_outpost",
              "Pillager Outposts",
              Dimension.OVERWORLD,
              new Placement.Grid(
                  32,
                  8,
                  165745296L,
                  Placement.Spread.LINEAR,
                  0.2f,
                  new Placement.ExclusionZone(VILLAGE_GRID, 10)),
              List.of("minecraft:pillager_outpost"),
              List.of("has_structure/pillager_outpost"),
              WIDE,
              true),
          linear(
              "ancient_city",
              "Ancient Cities",
              24,
              8,
              20083232,
              List.of("minecraft:ancient_city"),
              List.of("has_structure/ancient_city"),
              WIDE),
          linear(
              "trail_ruins",
              "Trail Ruins",
              34,
              8,
              83469867,
              List.of("minecraft:trail_ruins"),
              List.of("has_structure/trail_ruins"),
              WIDE),
          linear(
              "trial_chambers",
              "Trial Chambers",
              34,
              12,
              94251327,
              List.of("minecraft:trial_chambers"),
              List.of("has_structure/trial_chambers"),
              ZOOMED_IN),
          linear(
              "ocean_ruin",
              "Ocean Ruins",
              20,
              8,
              14357621,
              List.of("minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm"),
              List.of("has_structure/ocean_ruin_cold", "has_structure/ocean_ruin_warm"),
              ZOOMED_IN),
          linear(
              "shipwreck",
              "Shipwrecks",
              24,
              4,
              165745295,
              List.of("minecraft:shipwreck", "minecraft:shipwreck_beached"),
              List.of("has_structure/shipwreck", "has_structure/shipwreck_beached"),
              ZOOMED_IN),
          linear(
              "ruined_portal",
              "Ruined Portals (Overworld)",
              40,
              15,
              34222645,
              List.of(
                  "minecraft:ruined_portal",
                  "minecraft:ruined_portal_desert",
                  "minecraft:ruined_portal_jungle",
                  "minecraft:ruined_portal_swamp",
                  "minecraft:ruined_portal_mountain",
                  "minecraft:ruined_portal_ocean"),
              List.of(),
              ZOOMED_IN),
          triangular(
              "monument",
              "Ocean Monuments",
              32,
              5,
              10387313,
              List.of("minecraft:monument"),
              List.of("has_structure/ocean_monument"),
              WIDE),
          triangular(
              "mansion",
              "Woodland Mansions",
              80,
              20,
              10387319,
              List.of("minecraft:mansion"),
              List.of("has_structure/woodland_mansion"),
              WIDE),
          new StructureLayer(
              "fortress",
              "Nether Fortresses",
              Dimension.NETHER,
              new Placement.NetherComplex(Placement.NetherRole.FORTRESS),
              List.of("minecraft:fortress"),
              List.of("has_structure/nether_fortress"),
              WIDE,
              true),
          new StructureLayer(
              "bastion",
              "Bastion Remnants",
              Dimension.NETHER,
              new Placement.NetherComplex(Placement.NetherRole.BASTION),
              List.of("minecraft:bastion_remnant"),
              List.of("has_structure/bastion_remnant"),
              WIDE,
              true),
          new StructureLayer(
              "ruined_portal_nether",
              "Ruined Portals (Nether)",
              Dimension.NETHER,
              // cubiomes 1.17+ config (s_ruined_portal_n_117) — see class javadoc
              new Placement.Grid(25, 10, 34222645L, Placement.Spread.LINEAR),
              List.of("minecraft:ruined_portal_nether"),
              List.of(),
              WIDE,
              true),
          new StructureLayer(
              "end_city",
              "End Cities",
              Dimension.END,
              new Placement.Grid(20, 11, 10387313L, Placement.Spread.TRIANGULAR),
              List.of("minecraft:end_city"),
              List.of("has_structure/end_city"),
              WIDE,
              true),
          new StructureLayer(
              "buried_treasure",
              "Buried Treasure",
              Dimension.OVERWORLD,
              new Placement.PerChunkProbability(10387320L, 0.01),
              List.of("minecraft:buried_treasure"),
              List.of("has_structure/buried_treasure"),
              ZOOMED_IN,
              false),
          new StructureLayer(
              "stronghold",
              "Strongholds",
              Dimension.OVERWORLD,
              new Placement.ConcentricRings(),
              List.of("minecraft:stronghold"),
              List.of(),
              WIDE,
              true));

  private StructureCatalog() {}

  /** All layers, in stable catalog order (drives config listing and scan order). */
  public static List<StructureLayer> layers() {
    return LAYERS;
  }

  public static Optional<StructureLayer> byId(String id) {
    return LAYERS.stream().filter(layer -> layer.id().equals(id)).findFirst();
  }

  private static StructureLayer linear(
      String id,
      String name,
      int spacing,
      int separation,
      long salt,
      List<String> keys,
      List<String> biomeTagIds,
      int zoom) {
    return new StructureLayer(
        id,
        name,
        Dimension.OVERWORLD,
        new Placement.Grid(spacing, separation, salt, Placement.Spread.LINEAR),
        keys,
        biomeTagIds,
        zoom,
        true);
  }

  private static StructureLayer triangular(
      String id,
      String name,
      int spacing,
      int separation,
      long salt,
      List<String> keys,
      List<String> biomeTagIds,
      int zoom) {
    return new StructureLayer(
        id,
        name,
        Dimension.OVERWORLD,
        new Placement.Grid(spacing, separation, salt, Placement.Spread.TRIANGULAR),
        keys,
        biomeTagIds,
        zoom,
        true);
  }
}

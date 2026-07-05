package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructureCatalogTest {

  @Test
  void hasTwentyLayersWithUniqueIds() {
    List<StructureLayer> layers = StructureCatalog.layers();
    assertEquals(20, layers.size());

    Set<String> ids = new HashSet<>();
    for (StructureLayer layer : layers) {
      assertTrue(ids.add(layer.id()), "duplicate layer id: " + layer.id());
    }
  }

  @Test
  void byIdResolvesKnownAndRejectsUnknown() {
    assertTrue(StructureCatalog.byId("village").isPresent());
    assertTrue(StructureCatalog.byId("no_such_layer").isEmpty());
  }

  @Test
  void onlyBuriedTreasureIsDisabledByDefault() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      if (layer.id().equals("buried_treasure")) {
        assertFalse(layer.defaultEnabled());
      } else {
        assertTrue(layer.defaultEnabled(), layer.id() + " should default to enabled");
      }
    }
  }

  @Test
  void aggregatedLayersCarryAllRegistryKeys() {
    assertEquals(
        List.of(
            "minecraft:village_plains",
            "minecraft:village_desert",
            "minecraft:village_savanna",
            "minecraft:village_snowy",
            "minecraft:village_taiga"),
        StructureCatalog.byId("village").orElseThrow().structureKeys());
    assertEquals(6, StructureCatalog.byId("ruined_portal").orElseThrow().structureKeys().size());
    assertEquals(2, StructureCatalog.byId("ocean_ruin").orElseThrow().structureKeys().size());
    assertEquals(2, StructureCatalog.byId("shipwreck").orElseThrow().structureKeys().size());
    // vanilla registry name differs from the colloquial one
    assertEquals(
        List.of("minecraft:jungle_pyramid"),
        StructureCatalog.byId("jungle_temple").orElseThrow().structureKeys());
  }

  @Test
  void allStructureKeysAreNamespaced() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      assertFalse(layer.structureKeys().isEmpty(), layer.id());
      for (String key : layer.structureKeys()) {
        assertTrue(key.matches("minecraft:[a-z0-9_]+"), layer.id() + " has bad key " + key);
      }
    }
  }

  @Test
  void dimensionsMatchVanilla() {
    assertEquals(Dimension.NETHER, StructureCatalog.byId("fortress").orElseThrow().dimension());
    assertEquals(Dimension.NETHER, StructureCatalog.byId("bastion").orElseThrow().dimension());
    assertEquals(
        Dimension.NETHER, StructureCatalog.byId("ruined_portal_nether").orElseThrow().dimension());
    assertEquals(Dimension.END, StructureCatalog.byId("end_city").orElseThrow().dimension());
    long overworld =
        StructureCatalog.layers().stream()
            .filter(l -> l.dimension() == Dimension.OVERWORLD)
            .count();
    assertEquals(16, overworld);
  }

  @Test
  void placementKindsMatchExpectedStrategies() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      switch (layer.id()) {
        case "stronghold" -> assertInstanceOf(Placement.ConcentricRings.class, layer.placement());
        case "buried_treasure" ->
            assertInstanceOf(Placement.PerChunkProbability.class, layer.placement());
        case "fortress", "bastion" ->
            assertInstanceOf(Placement.NetherComplex.class, layer.placement());
        default -> {
          Placement.Grid grid = assertInstanceOf(Placement.Grid.class, layer.placement());
          assertTrue(grid.spacingChunks() > 0, layer.id());
          assertTrue(grid.separationChunks() >= 0, layer.id());
          assertTrue(grid.separationChunks() < grid.spacingChunks(), layer.id());
        }
      }
    }
  }

  @Test
  void netherComplexRolesAreDistinctAndShareTheGrid() {
    Placement.NetherComplex fortress =
        (Placement.NetherComplex) StructureCatalog.byId("fortress").orElseThrow().placement();
    Placement.NetherComplex bastion =
        (Placement.NetherComplex) StructureCatalog.byId("bastion").orElseThrow().placement();
    assertEquals(Placement.NetherRole.FORTRESS, fortress.role());
    assertEquals(Placement.NetherRole.BASTION, bastion.role());
    assertEquals(27, Placement.NetherComplex.SPACING_CHUNKS);
    assertEquals(4, Placement.NetherComplex.SEPARATION_CHUNKS);
    assertEquals(30084232L, Placement.NetherComplex.SALT);
  }

  @Test
  void gridSpacingsSeparationsSaltsAndSpreadsMatchCubiomes() {
    // cubiomes finders.c getStructureConfig: chunkRange = spacing - separation.
    assertGrid("village", 34, 8, 10387312L, Placement.Spread.LINEAR);
    assertGrid("desert_pyramid", 32, 8, 14357617L, Placement.Spread.LINEAR);
    assertGrid("ancient_city", 24, 8, 20083232L, Placement.Spread.LINEAR);
    assertGrid("mansion", 80, 20, 10387319L, Placement.Spread.TRIANGULAR);
    assertGrid("monument", 32, 5, 10387313L, Placement.Spread.TRIANGULAR);
    assertGrid("end_city", 20, 11, 10387313L, Placement.Spread.TRIANGULAR);
    assertGrid("ruined_portal", 40, 15, 34222645L, Placement.Spread.LINEAR);
    assertGrid("shipwreck", 24, 4, 165745295L, Placement.Spread.LINEAR);
    assertGrid("trial_chambers", 34, 12, 94251327L, Placement.Spread.LINEAR);

    // deliberate deviation from the reference mod (which uses the pre-1.17 config 40/15):
    // cubiomes' 1.17+ config s_ruined_portal_n_117 is spacing 25 / separation 10.
    assertGrid("ruined_portal_nether", 25, 10, 34222645L, Placement.Spread.LINEAR);
  }

  @Test
  void perChunkProbabilityMatchesVanillaTreasureFormula() {
    Placement.PerChunkProbability treasure =
        (Placement.PerChunkProbability)
            StructureCatalog.byId("buried_treasure").orElseThrow().placement();
    assertEquals(10387320L, treasure.salt());
    assertEquals(0.01, treasure.probability(), 1e-9);
  }

  @Test
  void biomeTagIdsArePresentExceptForUnrestrictedLayers() {
    assertEquals(
        List.of(
            "has_structure/village_plains",
            "has_structure/village_desert",
            "has_structure/village_savanna",
            "has_structure/village_snowy",
            "has_structure/village_taiga"),
        StructureCatalog.byId("village").orElseThrow().biomeTagIds());
    assertEquals(
        List.of("has_structure/ocean_monument"),
        StructureCatalog.byId("monument").orElseThrow().biomeTagIds());

    // empty = no biome restriction
    assertTrue(StructureCatalog.byId("ruined_portal").orElseThrow().biomeTagIds().isEmpty());
    assertTrue(
        StructureCatalog.byId("ruined_portal_nether").orElseThrow().biomeTagIds().isEmpty());
    assertTrue(StructureCatalog.byId("stronghold").orElseThrow().biomeTagIds().isEmpty());
  }

  @Test
  void zoomTiersAndIconsArePresent() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      assertTrue(
          layer.zoomMaxDistance() == 1000 || layer.zoomMaxDistance() == 5000,
          layer.id() + " zoom " + layer.zoomMaxDistance());
      assertTrue(IconSources.texturePath(layer.id()).isPresent(), layer.id());
      assertFalse(layer.displayName().isBlank(), layer.id());
    }
    // dense layers are zoom-gated
    assertEquals(1000, StructureCatalog.byId("shipwreck").orElseThrow().zoomMaxDistance());
    assertEquals(1000, StructureCatalog.byId("ocean_ruin").orElseThrow().zoomMaxDistance());
  }

  private static void assertGrid(
      String id, int spacing, int separation, long salt, Placement.Spread spread) {
    Placement.Grid grid = (Placement.Grid) StructureCatalog.byId(id).orElseThrow().placement();
    assertEquals(spacing, grid.spacingChunks(), id + " spacing");
    assertEquals(separation, grid.separationChunks(), id + " separation");
    assertEquals(salt, grid.salt(), id + " salt");
    assertEquals(spread, grid.spread(), id + " spread");
  }
}

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
  void strongholdUsesRingsEverythingElseGrids() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      if (layer.id().equals("stronghold")) {
        assertInstanceOf(Placement.ConcentricRings.class, layer.placement());
      } else {
        Placement.Grid grid = assertInstanceOf(Placement.Grid.class, layer.placement());
        assertTrue(grid.spacingChunks() > 0, layer.id());
      }
    }
  }

  @Test
  void gridSpacingsMatchVanillaStructureSets() {
    assertEquals(34, gridSpacing("village"));
    assertEquals(32, gridSpacing("desert_pyramid"));
    assertEquals(24, gridSpacing("ancient_city"));
    assertEquals(80, gridSpacing("mansion"));
    assertEquals(27, gridSpacing("fortress"));
    assertEquals(20, gridSpacing("end_city"));
    assertEquals(40, gridSpacing("ruined_portal"));
    assertEquals(1, gridSpacing("buried_treasure"));
  }

  @Test
  void zoomTiersAndIconsArePresent() {
    for (StructureLayer layer : StructureCatalog.layers()) {
      assertTrue(
          layer.zoomMaxDistance() == 1000 || layer.zoomMaxDistance() == 5000,
          layer.id() + " zoom " + layer.zoomMaxDistance());
      assertTrue(layer.iconFile().endsWith(".png"), layer.id());
      assertFalse(layer.displayName().isBlank(), layer.id());
    }
    // dense layers are zoom-gated
    assertEquals(1000, StructureCatalog.byId("shipwreck").orElseThrow().zoomMaxDistance());
    assertEquals(1000, StructureCatalog.byId("ocean_ruin").orElseThrow().zoomMaxDistance());
  }

  private static int gridSpacing(String id) {
    return ((Placement.Grid) StructureCatalog.byId(id).orElseThrow().placement()).spacingChunks();
  }
}

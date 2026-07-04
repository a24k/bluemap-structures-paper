package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsTest {

  @Test
  void emptyConfigYieldsDocumentedDefaults() {
    Settings settings = Settings.fromMap(Map.of(), StructureCatalog.layers());

    assertEquals(5000, settings.radiusBlocks());
    assertTrue(settings.isLayerEnabled("village"));
    assertFalse(settings.isLayerEnabled("buried_treasure"), "opt-in layer");
    assertTrue(settings.warnings().isEmpty());
  }

  @Test
  void valuesAreReadFromNestedYamlShape() {
    Settings settings =
        Settings.fromMap(
            Map.of(
                "radius-blocks", 10000,
                "layers", Map.of("village", false, "buried_treasure", true)),
            StructureCatalog.layers());

    assertEquals(10000, settings.radiusBlocks());
    assertFalse(settings.isLayerEnabled("village"));
    assertTrue(settings.isLayerEnabled("buried_treasure"));
    assertTrue(settings.isLayerEnabled("mansion"), "untouched layers keep their default");
  }

  @Test
  void outOfRangeNumbersAreClampedWithWarnings() {
    Settings low = Settings.fromMap(Map.of("radius-blocks", 50), StructureCatalog.layers());
    assertEquals(256, low.radiusBlocks());
    assertEquals(1, low.warnings().size());

    Settings high =
        Settings.fromMap(Map.of("radius-blocks", 99_999_999), StructureCatalog.layers());
    assertEquals(1_000_000, high.radiusBlocks());
  }

  @Test
  void malformedValuesFallBackToDefaultsWithWarnings() {
    Settings settings =
        Settings.fromMap(Map.of("radius-blocks", "very far"), StructureCatalog.layers());

    assertEquals(5000, settings.radiusBlocks());
    assertFalse(settings.warnings().isEmpty());
  }

  @Test
  void unknownLayerIdsAreReportedNotFatal() {
    Settings settings =
        Settings.fromMap(
            Map.of("layers", Map.of("vilage", false)), StructureCatalog.layers());

    assertTrue(settings.isLayerEnabled("village"));
    assertEquals(1, settings.warnings().size());
    assertTrue(settings.warnings().get(0).contains("vilage"));
  }

  @Test
  void enabledLayerIdsListsOnlyEnabledOnesInCatalogOrder() {
    Settings settings =
        Settings.fromMap(
            Map.of("layers", Map.of("village", false)), StructureCatalog.layers());

    assertFalse(settings.enabledLayerIds().contains("village"));
    assertFalse(settings.enabledLayerIds().contains("buried_treasure"));
    assertEquals(18, settings.enabledLayerIds().size());
    assertEquals("desert_pyramid", settings.enabledLayerIds().get(0));
  }
}

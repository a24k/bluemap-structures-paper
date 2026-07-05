package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsTest {

  private static final AreaSpec DEFAULT_AREA =
      new AreaSpec(AreaSpec.Center.ORIGIN, Settings.DEFAULT_RADIUS_BLOCKS);

  @Test
  void emptyConfigYieldsDocumentedDefaults() {
    Settings settings = Settings.fromMap(Map.of(), StructureCatalog.layers());

    assertEquals(List.of(DEFAULT_AREA), settings.defaultAreas());
    assertTrue(settings.worldAreas().isEmpty());
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

    assertEquals(
        List.of(new AreaSpec(AreaSpec.Center.ORIGIN, 10000)),
        settings.defaultAreas(),
        "legacy radius-blocks is sugar for one origin-centered area");
    assertFalse(settings.isLayerEnabled("village"));
    assertTrue(settings.isLayerEnabled("buried_treasure"));
    assertTrue(settings.isLayerEnabled("mansion"), "untouched layers keep their default");
    assertTrue(settings.warnings().isEmpty());
  }

  @Test
  void outOfRangeNumbersAreClampedWithWarnings() {
    Settings low = Settings.fromMap(Map.of("radius-blocks", 50), StructureCatalog.layers());
    assertEquals(List.of(new AreaSpec(AreaSpec.Center.ORIGIN, 256)), low.defaultAreas());
    assertEquals(1, low.warnings().size());

    Settings high =
        Settings.fromMap(Map.of("radius-blocks", 99_999_999), StructureCatalog.layers());
    assertEquals(List.of(new AreaSpec(AreaSpec.Center.ORIGIN, 1_000_000)), high.defaultAreas());
  }

  @Test
  void malformedValuesFallBackToDefaultsWithWarnings() {
    Settings settings =
        Settings.fromMap(Map.of("radius-blocks", "very far"), StructureCatalog.layers());

    assertEquals(List.of(DEFAULT_AREA), settings.defaultAreas());
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

  // ---- areas ---------------------------------------------------------------------------

  @Test
  void areaListParsesAllCenterForms() {
    Settings settings =
        Settings.fromMap(
            Map.of(
                "areas",
                List.of(
                    Map.of("center", "origin", "radius-blocks", 5000),
                    Map.of("center", "spawn", "radius-blocks", 3000),
                    Map.of("center", Map.of("x", -12000, "z", 8000), "radius-blocks", 2000))),
            StructureCatalog.layers());

    assertEquals(
        List.of(
            new AreaSpec(AreaSpec.Center.ORIGIN, 5000),
            new AreaSpec(AreaSpec.Center.SPAWN, 3000),
            new AreaSpec(new AreaSpec.Center.Fixed(-12000, 8000), 2000)),
        settings.defaultAreas());
    assertTrue(settings.warnings().isEmpty());
  }

  @Test
  void areaWithoutCenterDefaultsToOriginAndMissingRadiusToDefault() {
    Settings settings =
        Settings.fromMap(
            Map.of("areas", List.of(Map.of("radius-blocks", 700), Map.of("center", "spawn"))),
            StructureCatalog.layers());

    assertEquals(
        List.of(
            new AreaSpec(AreaSpec.Center.ORIGIN, 700),
            new AreaSpec(AreaSpec.Center.SPAWN, Settings.DEFAULT_RADIUS_BLOCKS)),
        settings.defaultAreas());
    assertTrue(settings.warnings().isEmpty());
  }

  @Test
  void areasAndLegacyRadiusTogetherWarnAndAreasWins() {
    Settings settings =
        Settings.fromMap(
            Map.of(
                "radius-blocks", 9000,
                "areas", List.of(Map.of("center", "origin", "radius-blocks", 1000))),
            StructureCatalog.layers());

    assertEquals(List.of(new AreaSpec(AreaSpec.Center.ORIGIN, 1000)), settings.defaultAreas());
    assertEquals(1, settings.warnings().size());
    assertTrue(settings.warnings().get(0).contains("radius-blocks"));
  }

  @Test
  void malformedAreaEntriesAreSkippedWithWarnings() {
    Settings settings =
        Settings.fromMap(
            Map.of(
                "areas",
                List.of(
                    "not a map",
                    Map.of("center", "moon", "radius-blocks", 1000),
                    Map.of("center", Map.of("x", 5), "radius-blocks", 1000))),
            StructureCatalog.layers());

    // "not a map" dropped; unknown center string -> origin; missing z -> 0 (both warned).
    assertEquals(
        List.of(
            new AreaSpec(AreaSpec.Center.ORIGIN, 1000),
            new AreaSpec(new AreaSpec.Center.Fixed(5, 0), 1000)),
        settings.defaultAreas());
    assertEquals(3, settings.warnings().size());
  }

  @Test
  void unusableAreasListFallsBackToLegacyRadiusWhenPresent() {
    Settings settings =
        Settings.fromMap(
            Map.of("areas", "everywhere", "radius-blocks", 8000), StructureCatalog.layers());

    assertEquals(List.of(new AreaSpec(AreaSpec.Center.ORIGIN, 8000)), settings.defaultAreas());
    assertEquals(1, settings.warnings().size());
    assertTrue(settings.warnings().get(0).contains("areas"));
  }

  @Test
  void entirelyInvalidAreaListFallsBackToDefault() {
    Settings settings =
        Settings.fromMap(Map.of("areas", "everywhere"), StructureCatalog.layers());

    assertEquals(List.of(DEFAULT_AREA), settings.defaultAreas());
    assertFalse(settings.warnings().isEmpty());
  }

  @Test
  void perWorldOverrideReplacesTheDefaultListEntirely() {
    Settings settings =
        Settings.fromMap(
            Map.of(
                "areas", List.of(Map.of("center", "origin", "radius-blocks", 5000)),
                "worlds",
                    Map.of(
                        "20260314",
                        Map.of("areas", List.of(Map.of("center", "spawn", "radius-blocks", 3000))))),
            StructureCatalog.layers());

    assertEquals(
        List.of(new AreaSpec(AreaSpec.Center.SPAWN, 3000)), settings.areasForWorld("20260314"));
    assertEquals(
        List.of(new AreaSpec(AreaSpec.Center.ORIGIN, 5000)),
        settings.areasForWorld("other_world"),
        "worlds without an override use the default list");
    assertTrue(settings.warnings().isEmpty());
  }

  @Test
  void perWorldOverrideAcceptsLegacyRadiusSugar() {
    Settings settings =
        Settings.fromMap(
            Map.of("worlds", Map.of("nether", Map.of("radius-blocks", 2000))),
            StructureCatalog.layers());

    assertEquals(
        List.of(new AreaSpec(AreaSpec.Center.ORIGIN, 2000)), settings.areasForWorld("nether"));
    assertTrue(settings.warnings().isEmpty());
  }

  @Test
  void emptyWorldOverrideIsIgnoredWithWarning() {
    Settings settings =
        Settings.fromMap(
            Map.of("worlds", Map.of("lobby", Map.of("something-else", 1))),
            StructureCatalog.layers());

    assertEquals(List.of(DEFAULT_AREA), settings.areasForWorld("lobby"));
    assertTrue(settings.worldAreas().isEmpty());
    assertEquals(1, settings.warnings().size());
    assertTrue(settings.warnings().get(0).contains("lobby"));
  }

  @Test
  void fixedCenterCoordinatesAreClampedToWorldBorderScale() {
    Settings settings =
        Settings.fromMap(
            Map.of(
                "areas",
                List.of(
                    Map.of(
                        "center", Map.of("x", 999_999_999, "z", -999_999_999),
                        "radius-blocks", 1000))),
            StructureCatalog.layers());

    assertEquals(
        List.of(
            new AreaSpec(
                new AreaSpec.Center.Fixed(Settings.MAX_CENTER_BLOCKS, -Settings.MAX_CENTER_BLOCKS),
                1000)),
        settings.defaultAreas());
    assertEquals(2, settings.warnings().size());
  }
}

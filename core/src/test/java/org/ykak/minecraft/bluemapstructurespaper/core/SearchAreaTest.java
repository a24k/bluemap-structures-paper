package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchAreaTest {

  @Test
  void containsIsInclusiveAtTheBoundary() {
    SearchArea area = new SearchArea(100, -200, 50);

    assertTrue(area.contains(100, -200));
    assertTrue(area.contains(150, -150), "corner is inside");
    assertTrue(area.contains(50, -250), "opposite corner is inside");
    assertFalse(area.contains(151, -200));
    assertFalse(area.contains(100, -251));
  }

  @Test
  void blockBoundsMatchCenterPlusMinusRadius() {
    SearchArea area = new SearchArea(-12000, 8000, 2000);

    assertEquals(-14000, area.minBlockX());
    assertEquals(-10000, area.maxBlockX());
    assertEquals(6000, area.minBlockZ());
    assertEquals(10000, area.maxBlockZ());
  }

  @Test
  void anyContainsIsTheUnionOfAllAreas() {
    List<SearchArea> areas = List.of(new SearchArea(0, 0, 100), new SearchArea(1000, 0, 100));

    assertTrue(SearchArea.anyContains(areas, 50, 50));
    assertTrue(SearchArea.anyContains(areas, 950, 0));
    assertFalse(SearchArea.anyContains(areas, 500, 0), "gap between the areas");
    assertFalse(SearchArea.anyContains(List.of(), 0, 0));
  }
}

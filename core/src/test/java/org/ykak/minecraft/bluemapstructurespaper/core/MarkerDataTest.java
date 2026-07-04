package org.ykak.minecraft.bluemapstructurespaper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkerDataTest {

  @Test
  void idsAreStableAndUniquePerPosition() {
    assertEquals("structures-village", MarkerData.markerSetId("village"));
    assertEquals("bmsp-village-160--320", MarkerData.markerId("village", 160, -320));
  }

  @Test
  void labelCombinesDisplayNameAndCoordinates() {
    assertEquals("Villages (160, -320)", MarkerData.label("Villages", 160, -320));
  }

  @Test
  void popupContainsCopyableTpCommand() {
    String html = MarkerData.popupHtml("Villages", 160, 63, -320);
    assertTrue(html.contains("/tp @s 160 63 -320"), html);
    assertTrue(html.contains("Villages"), html);
  }

  @Test
  void popupEscapesHtmlInDisplayNames() {
    String html = MarkerData.popupHtml("<script>alert(1)</script>", 0, 63, 0);
    assertFalse(html.contains("<script>"), html);
    assertTrue(html.contains("&lt;script&gt;"), html);
  }

  @Test
  void defaultMarkerYPerDimension() {
    assertEquals(63, MarkerData.defaultY(Dimension.OVERWORLD));
    assertEquals(64, MarkerData.defaultY(Dimension.NETHER));
    assertEquals(64, MarkerData.defaultY(Dimension.END));
  }
}

package org.ykak.minecraft.bluemapstructurespaper.core;

/** Pure formatting for BlueMap marker ids, labels and popup content. */
public final class MarkerData {

  private MarkerData() {}

  public static String markerSetId(String layerId) {
    return "structures-" + layerId;
  }

  public static String markerId(String layerId, int x, int z) {
    return "bmsp-" + layerId + "-" + x + "-" + z;
  }

  public static String label(String displayName, int x, int z) {
    return displayName + " (" + x + ", " + z + ")";
  }

  /** Popup body: name, coordinates, and a copyable {@code /tp} command. */
  public static String popupHtml(String displayName, int x, int y, int z) {
    String tp = "/tp @s " + x + " " + y + " " + z;
    return "<div class=\"bmsp-popup\">"
        + "<strong>" + escapeHtml(displayName) + "</strong><br>"
        + "(" + x + ", " + z + ")<br>"
        + "<code>" + tp + "</code>"
        + "</div>";
  }

  /**
   * Marker/teleport height when the locate result carries no Y: sea level in the
   * overworld; 64 in the nether (safely below the roof) and the end.
   */
  public static int defaultY(Dimension dimension) {
    return switch (dimension) {
      case OVERWORLD -> 63;
      case NETHER, END -> 64;
    };
  }

  static String escapeHtml(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}

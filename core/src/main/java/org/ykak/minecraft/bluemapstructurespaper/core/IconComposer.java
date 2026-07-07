package org.ykak.minecraft.bluemapstructurespaper.core;

import java.awt.image.BufferedImage;

/**
 * Composes marker icons from raw client-jar textures. Vanilla animated block textures (e.g.
 * flowing water/lava) ship as tall vertical strips of stacked frames; this only ever needs the
 * first frame. Textures larger than the icon size are downscaled with nearest-neighbor sampling
 * so pixel art stays crisp (no smoothing/interpolation).
 */
public final class IconComposer {

  public static final int ICON_SIZE = 22;

  private IconComposer() {}

  /**
   * Composes a 22×22 ARGB icon: animation strips (height &gt; width) are first cropped to their
   * top width×width frame; frames larger than 22 in either dimension are scaled down
   * (nearest-neighbor, aspect preserved) to fit; the result is centered on a fully transparent
   * canvas.
   */
  public static BufferedImage compose(BufferedImage source) {
    BufferedImage frame = cropToTopFrame(source);
    frame = scaleDownToFit(frame);

    BufferedImage canvas = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
    int offsetX = (ICON_SIZE - frame.getWidth()) / 2;
    int offsetY = (ICON_SIZE - frame.getHeight()) / 2;
    for (int y = 0; y < frame.getHeight(); y++) {
      for (int x = 0; x < frame.getWidth(); x++) {
        canvas.setRGB(offsetX + x, offsetY + y, frame.getRGB(x, y));
      }
    }
    return canvas;
  }

  /** Animated block textures are vertical strips of stacked width×width frames; keep the first. */
  private static BufferedImage cropToTopFrame(BufferedImage source) {
    int width = source.getWidth();
    int height = source.getHeight();
    if (height <= width) {
      return source;
    }
    return source.getSubimage(0, 0, width, width);
  }

  /** Scales down (never up) preserving aspect ratio so both dimensions fit within ICON_SIZE. */
  private static BufferedImage scaleDownToFit(BufferedImage frame) {
    int width = frame.getWidth();
    int height = frame.getHeight();
    if (width <= ICON_SIZE && height <= ICON_SIZE) {
      return frame;
    }

    double scale = Math.min((double) ICON_SIZE / width, (double) ICON_SIZE / height);
    int newWidth = clamp((int) Math.floor(width * scale));
    int newHeight = clamp((int) Math.floor(height * scale));

    BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < newHeight; y++) {
      int srcY = Math.min(height - 1, (int) (y / scale));
      for (int x = 0; x < newWidth; x++) {
        int srcX = Math.min(width - 1, (int) (x / scale));
        scaled.setRGB(x, y, frame.getRGB(srcX, srcY));
      }
    }
    return scaled;
  }

  private static int clamp(int dimension) {
    return Math.max(1, Math.min(ICON_SIZE, dimension));
  }
}

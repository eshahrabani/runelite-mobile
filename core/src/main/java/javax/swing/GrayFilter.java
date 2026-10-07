package javax.swing;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RGBImageFilter;

/**
 * {@code javax.swing.GrayFilter}: the one member RuneLite uses is the static
 * {@link #createDisabledImage(Image)}, which {@code ImageUtil.grayscaleImage} calls to
 * dim icons. Implemented for real (luma-weighted desaturation) because returning null
 * there would NPE inside {@code ImageUtil}.
 */
public abstract class GrayFilter extends RGBImageFilter {

    protected GrayFilter() {
    }

    /** @return a grayscale copy of {@code src} (same size, alpha preserved). */
    public static Image createDisabledImage(Image src) {
        if (src == null) {
            return null;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getPixels();
        if (w <= 0 || h <= 0 || px == null) {
            return new BufferedImage(new int[0], 0, 0);
        }
        int[] out = new int[w * h];
        int limit = Math.min(out.length, px.length);
        for (int i = 0; i < limit; i++) {
            int argb = px[i];
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            int luma = (r * 299 + g * 587 + b * 114) / 1000;
            // the desktop filter brightens the result so disabled icons stay legible
            luma = Math.min(255, luma + 40);
            out[i] = (a << 24) | (luma << 16) | (luma << 8) | luma;
        }
        return new BufferedImage(out, w, h);
    }
}

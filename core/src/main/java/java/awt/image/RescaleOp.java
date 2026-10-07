package java.awt.image;

import java.awt.RenderingHints;

/**
 * AWT RescaleOp compatibility stub for Android runtime.
 *
 * <p>Applies per-band {@code scale * value + offset} to the four ARGB bands of
 * a {@code BufferedImage}. Used by the RuneLite image utilities for
 * brightening/darkening sprite sets.
 */
public class RescaleOp {
    private final float[] scaleFactors;
    private final float[] offsets;

    public RescaleOp(float[] scaleFactors, float[] offsets, RenderingHints hints) {
        this.scaleFactors = scaleFactors.clone();
        this.offsets = offsets.clone();
    }

    public int getNumFactors() {
        return scaleFactors.length;
    }

    public float[] getScaleFactors(float[] scaleFactors) {
        float[] out = scaleFactors == null ? new float[this.scaleFactors.length] : scaleFactors;
        System.arraycopy(this.scaleFactors, 0, out, 0, this.scaleFactors.length);
        return out;
    }

    public BufferedImage filter(BufferedImage src, BufferedImage dst) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (dst == null) {
            dst = new BufferedImage(w, h,
                    src.hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        }
        boolean perBand = scaleFactors.length >= 4;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = p & 0xFF;
                a = rescale(a, perBand ? 0 : 0, perBand);
                r = rescale(r, perBand ? 1 : 0, perBand);
                g = rescale(g, perBand ? 2 : 0, perBand);
                b = rescale(b, perBand ? 3 : 0, perBand);
                dst.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return dst;
    }

    private int rescale(int value, int band, boolean perBand) {
        int idx = perBand && band < scaleFactors.length ? band : 0;
        float scale = scaleFactors[idx];
        float offset = idx < offsets.length ? offsets[idx] : 0.0f;
        int v = Math.round(value * scale + offset);
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}

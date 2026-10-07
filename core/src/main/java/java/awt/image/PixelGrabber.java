package java.awt.image;

import java.awt.Image;

/**
 * AWT image.PixelGrabber compatibility stub for Android runtime.
 */
public class PixelGrabber {
    private final Image src;
    private final int srcX;
    private final int srcY;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final int offset;
    private final int scansize;
    private ColorModel colorModel;
    private int status;

    public PixelGrabber(Image src, int x, int y, int w, int h, int[] pixels, int offset, int scansize) {
        this.src = src;
        this.srcX = x;
        this.srcY = y;
        this.width = w;
        this.height = h;
        this.pixels = pixels;
        this.offset = offset;
        this.scansize = scansize;
    }

    public void setColorModel(ColorModel model) {
        this.colorModel = model;
    }

    public ColorModel getColorModel() {
        return colorModel;
    }

    public boolean grabPixels() {
        return grabPixels(0L);
    }

    public boolean grabPixels(long ms) {
        if (src == null || pixels == null || src.getPixels() == null) {
            status = 0;
            return false;
        }
        int[] srcPixels = src.getPixels();
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        for (int y = 0; y < height; y++) {
            int sy = srcY + y;
            for (int x = 0; x < width; x++) {
                int sx = srcX + x;
                int p = (sx < 0 || sy < 0 || sx >= srcWidth || sy >= srcHeight) ? 0 : srcPixels[sy * srcWidth + sx];
                pixels[offset + y * scansize + x] = colorModel != null ? colorModel.getRGB(p) : p;
            }
        }
        status = 1;
        return true;
    }

    public int getStatus() {
        return status;
    }

    public int[] getPixels() {
        return pixels;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

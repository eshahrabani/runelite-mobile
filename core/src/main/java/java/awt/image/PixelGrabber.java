package java.awt.image;

import java.awt.Image;

/**
 * AWT image.PixelGrabber compatibility stub for Android runtime.
 */
public class PixelGrabber {
    private final int[] pixels;
    private final int offset;
    private final int scansize;
    private int status;

    public PixelGrabber(Image src, int x, int y, int w, int h, int[] pixels, int offset, int scansize) {
        this.pixels = pixels;
        this.offset = offset;
        this.scansize = scansize;
    }

    public boolean grabPixels() {
        status = 1;
        return true;
    }

    public boolean grabPixels(long ms) {
        status = 1;
        return true;
    }

    public int getStatus() {
        return status;
    }

    public int[] getPixels() {
        return pixels;
    }
}

package java.awt;

import java.awt.image.ImageObserver;

/**
 * AWT Image compatibility stub for Android runtime.
 *
 * <p>A plain image is a non-premultiplied ARGB {@code int[]} plus a size. The
 * {@code hasAlpha} flag distinguishes opaque sources (the game frame) from
 * sources that need blending ({@link java.awt.image.BufferedImage} sprites);
 * it is set from the colour model of a {@code BufferedImage} and defaults to
 * false here.
 */
public class Image {
    protected int[] pixels;
    protected int width;
    protected int height;

    /** True when {@link Graphics#drawImage} must blend this source per pixel. */
    public boolean hasAlpha;

    public Image() {}

    public Image(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth(ImageObserver observer) {
        return width;
    }

    public int getHeight(ImageObserver observer) {
        return height;
    }

    public int[] getPixels() {
        return pixels;
    }

    /**
     * Returns a graphics context bound to this image's pixels. A
     * {@link Graphics2D} is returned because
     * {@code net.runelite.client.callback.Hooks} casts the result to
     * {@code Graphics2D} to render overlays into the game frame.
     */
    public Graphics getGraphics() {
        return new Graphics2D(this);
    }

    public void flush() {
    }
}

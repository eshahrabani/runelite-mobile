package java.awt.image;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;

/**
 * AWT VolatileImage compatibility stub for Android runtime.
 *
 * <p>There is no accelerated off-screen memory on the mobile surface, so a
 * volatile image is just an ARGB {@code int[]}; {@link #validate} always reports
 * {@link #IMAGE_OK}. {@code Hooks} uses one as the stretched intermediate and
 * only needs it to be writable and readable.
 */
public class VolatileImage extends java.awt.Image {
    public static final int IMAGE_OK = 0;
    public static final int IMAGE_RESTORED = 1;
    public static final int IMAGE_INCOMPATIBLE = 2;

    public VolatileImage(int[] pixels, int width, int height) {
        super(pixels, width, height);
        this.hasAlpha = true;
    }

    @Override
    public Graphics getGraphics() {
        return new Graphics2D(this);
    }

    public int validate(GraphicsConfiguration gc) {
        return IMAGE_OK;
    }

    public boolean contentsLost() {
        return false;
    }

    @Override
    public void flush() {
    }
}

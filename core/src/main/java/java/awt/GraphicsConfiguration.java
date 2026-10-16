package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.image.VolatileImage;

/**
 * AWT GraphicsConfiguration compatibility stub for Android runtime.
 *
 * <p>Describes the single synthetic screen. The default transform is identity
 * (there is no device scaling applied here; the app's render thread does the
 * surface scaling) and volatile images are plain ARGB images.
 */
public abstract class GraphicsConfiguration {
    protected GraphicsConfiguration() {
    }

    public abstract GraphicsDevice getDevice();

    public abstract Rectangle getBounds();

    public AffineTransform getDefaultTransform() {
        return new AffineTransform();
    }

    public AffineTransform getNormalizingTransform() {
        return new AffineTransform();
    }

    public java.awt.image.ColorModel getColorModel() {
        return java.awt.image.ColorModel.getRGBdefault();
    }

    public java.awt.image.ColorModel getColorModel(int transparency) {
        return java.awt.image.ColorModel.getRGBdefault();
    }

    public java.awt.image.BufferedImage createCompatibleImage(int width, int height) {
        return new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    }

    public java.awt.image.BufferedImage createCompatibleImage(int width, int height, int transparency) {
        return createCompatibleImage(width, height);
    }

    public VolatileImage createCompatibleVolatileImage(int width, int height) {
        return new VolatileImage(new int[Math.max(0, width * height)], width, height);
    }

    public VolatileImage createCompatibleVolatileImage(int width, int height, int transparency) {
        return createCompatibleVolatileImage(width, height);
    }
}

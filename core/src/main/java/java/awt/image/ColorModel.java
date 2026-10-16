package java.awt.image;

/**
 * The port's {@code java.awt.image.ColorModel}.
 *
 * <p>Hand-written because the client asks a BufferedImage's model for its component
 * count and alpha, and because {@code BufferedImage} reports pixel bits through it.
 *
 * <p>Only the number of components and whether an alpha component is present
 * matter here: {@link BufferedImage} uses the latter to decide whether
 * {@code Graphics.drawImage} must blend the source. {@link DirectColorModel} is
 * the only concrete model.
 */
public abstract class ColorModel {
    protected int pixel_bits;
    protected int numComponents;
    protected boolean hasAlpha;
    protected boolean isAlphaPremultiplied;

    protected ColorModel(int bits) {
        this(bits, 3, false, false);
    }

    protected ColorModel(int bits, int numComponents, boolean hasAlpha, boolean isAlphaPremultiplied) {
        this.pixel_bits = bits;
        this.numComponents = numComponents;
        this.hasAlpha = hasAlpha;
        this.isAlphaPremultiplied = isAlphaPremultiplied;
    }

    /** The JDK's shared 32-bit ARGB model. */
    public static java.awt.image.ColorModel getRGBdefault() {
        return RGB_DEFAULT;
    }

    private static final java.awt.image.ColorModel RGB_DEFAULT =
            new java.awt.image.DirectColorModel(32, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000);

    public int getPixelSize() {
        return pixel_bits;
    }

    public int getNumComponents() {
        return numComponents;
    }

    public boolean hasAlpha() {
        return hasAlpha;
    }

    public boolean isAlphaPremultiplied() {
        return isAlphaPremultiplied;
    }

    public int getTransferType() {
        return DataBuffer.TYPE_INT;
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        return null;
    }

    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        return null;
    }

    public int getRGB(int pixel) {
        return pixel;
    }
}

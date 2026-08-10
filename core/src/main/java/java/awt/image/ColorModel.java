package java.awt.image;

/**
 * AWT ColorModel compatibility stub for Android runtime.
 */
public abstract class ColorModel {
    protected int pixel_bits;

    protected ColorModel(int bits) {
        this.pixel_bits = bits;
    }

    public int getPixelSize() {
        return pixel_bits;
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        return null;
    }

    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        return null;
    }
}

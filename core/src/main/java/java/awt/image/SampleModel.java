package java.awt.image;

/**
 * AWT SampleModel compatibility stub for Android runtime.
 *
 * <p>Kept abstract: the only concrete subclass is
 * {@link SinglePixelPackedSampleModel}, which records the band masks.
 */
public abstract class SampleModel {
    protected int dataType;
    protected int width;
    protected int height;
    protected int numBands;

    public SampleModel(int dataType, int w, int h, int numBands) {
        this.dataType = dataType;
        this.width = w;
        this.height = h;
        this.numBands = numBands;
    }

    public final int getWidth() {
        return width;
    }

    public final int getHeight() {
        return height;
    }

    public final int getNumBands() {
        return numBands;
    }

    public final int getDataType() {
        return dataType;
    }

    public abstract SampleModel createCompatibleSampleModel(int w, int h);
}

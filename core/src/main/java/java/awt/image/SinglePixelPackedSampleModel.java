package java.awt.image;

/**
 * AWT SinglePixelPackedSampleModel compatibility stub for Android runtime.
 *
 * <p>Records the per-band bit masks used by {@link DirectColorModel}; all bands
 * live in one 32-bit pixel, which is the only layout the mobile surface needs.
 */
public class SinglePixelPackedSampleModel extends SampleModel {
    private final int[] bitMasks;

    public SinglePixelPackedSampleModel(int dataType, int w, int h, int[] bitMasks) {
        super(dataType, w, h, bitMasks.length);
        this.bitMasks = bitMasks;
    }

    public SinglePixelPackedSampleModel(int w, int h, int[] bitMasks) {
        this(DataBuffer.TYPE_INT, w, h, bitMasks);
    }

    public int[] getBitMasks() {
        return bitMasks;
    }

    public int getBitMask(int band) {
        return bitMasks[band];
    }

    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        return new SinglePixelPackedSampleModel(dataType, w, h, bitMasks);
    }
}

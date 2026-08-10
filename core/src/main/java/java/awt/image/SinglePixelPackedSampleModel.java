package java.awt.image;

/**
 * AWT SinglePixelPackedSampleModel compatibility stub for Android runtime.
 */
public class SinglePixelPackedSampleModel extends SampleModel {
    public SinglePixelPackedSampleModel(int w, int h, int[] bandMasks) {
        super(3, w, h, bandMasks.length);
    }
}

package java.awt.image;

/**
 * AWT DirectColorModel compatibility stub for Android runtime.
 */
public class DirectColorModel extends PackedColorModel {
    private final int redMask;
    private final int greenMask;
    private final int blueMask;
    private final int alphaMask;

    public DirectColorModel(int bits, int rmask, int gmask, int bmask) {
        this(bits, rmask, gmask, bmask, 0);
    }

    public DirectColorModel(int bits, int rmask, int gmask, int bmask, int amask) {
        super(bits);
        this.redMask = rmask;
        this.greenMask = gmask;
        this.blueMask = bmask;
        this.alphaMask = amask;
    }

    public final int getRedMask() {
        return redMask;
    }

    public final int getGreenMask() {
        return greenMask;
    }

    public final int getBlueMask() {
        return blueMask;
    }

    public final int getAlphaMask() {
        return alphaMask;
    }

    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        int[] bandMasks = new int[3];
        bandMasks[0] = redMask;
        bandMasks[1] = greenMask;
        bandMasks[2] = blueMask;
        if (alphaMask != 0) {
            bandMasks = new int[4];
            bandMasks[0] = redMask;
            bandMasks[1] = greenMask;
            bandMasks[2] = blueMask;
            bandMasks[3] = alphaMask;
        }
        return new SinglePixelPackedSampleModel(w, h, bandMasks);
    }

    @Override
    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        SampleModel sm = createCompatibleSampleModel(w, h);
        DataBuffer db = new DataBufferInt(w * h);
        return Raster.createWritableRaster(sm, db, new java.awt.Point(0, 0));
    }
}

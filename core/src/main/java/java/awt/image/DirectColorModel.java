package java.awt.image;

import java.awt.Point;
import java.awt.color.ColorSpace;

/**
 * AWT DirectColorModel compatibility stub for Android runtime.
 *
 * <p>The injected client builds its frame buffer with this model over a
 * {@link DataBufferInt}; the masks are recorded so
 * {@link #createCompatibleSampleModel(int, int)} can describe the raster and so
 * {@link BufferedImage} knows whether the pixels carry alpha.
 */
public class DirectColorModel extends PackedColorModel {
    private final int redMask;
    private final int greenMask;
    private final int blueMask;
    private final int alphaMask;
    private final ColorSpace colorSpace;
    private final int transferType;

    public DirectColorModel(int bits, int rmask, int gmask, int bmask) {
        this(ColorSpace.getInstance(ColorSpace.CS_sRGB), bits, rmask, gmask, bmask, 0, false, DataBuffer.TYPE_INT);
    }

    public DirectColorModel(int bits, int rmask, int gmask, int bmask, int amask) {
        this(ColorSpace.getInstance(ColorSpace.CS_sRGB), bits, rmask, gmask, bmask, amask, false, DataBuffer.TYPE_INT);
    }

    public DirectColorModel(ColorSpace space, int bits, int rmask, int gmask, int bmask,
                            int amask, boolean isAlphaPremultiplied, int transferType) {
        super(bits, masks(rmask, gmask, bmask, amask), amask != 0, isAlphaPremultiplied);
        this.colorSpace = space;
        this.redMask = rmask;
        this.greenMask = gmask;
        this.blueMask = bmask;
        this.alphaMask = amask;
        this.transferType = transferType;
    }

    private static int[] masks(int r, int g, int b, int a) {
        if (a != 0) {
            return new int[] {r, g, b, a};
        }
        return new int[] {r, g, b};
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

    public ColorSpace getColorSpace() {
        return colorSpace;
    }

    @Override
    public int getTransferType() {
        return transferType;
    }

    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        int[] bandMasks;
        if (alphaMask != 0) {
            bandMasks = new int[] {redMask, greenMask, blueMask, alphaMask};
        } else {
            bandMasks = new int[] {redMask, greenMask, blueMask};
        }
        return new SinglePixelPackedSampleModel(transferType, w, h, bandMasks);
    }

    @Override
    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        SampleModel sm = createCompatibleSampleModel(w, h);
        DataBuffer db = new DataBufferInt(new int[w * h], w * h);
        return Raster.createWritableRaster(sm, db, new Point(0, 0));
    }

    @Override
    public int getRGB(int pixel) {
        if (alphaMask != 0) {
            return pixel;
        }
        return 0xFF000000 | (pixel & 0xFFFFFF);
    }
}

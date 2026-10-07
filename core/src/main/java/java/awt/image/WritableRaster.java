package java.awt.image;

import java.awt.Point;

/**
 * AWT WritableRaster compatibility stub for Android runtime.
 *
 * <p>Thin view over the shared {@code int[]}; pixel writes go straight to the
 * backing {@link DataBufferInt}, which is how {@code ModelOutlineRenderer}
 * paints object outlines.
 */
public class WritableRaster extends Raster {

    public WritableRaster(SampleModel sampleModel, DataBuffer dataBuffer, Point origin) {
        super(sampleModel, dataBuffer, origin);
    }

    private int[] data() {
        return ((DataBufferInt) dataBuffer).getData();
    }

    public void setPixel(int x, int y, int[] iArray) {
        int[] d = data();
        int idx = (y - minY) * width + (x - minX);
        System.arraycopy(iArray, 0, d, idx * iArray.length, iArray.length);
    }

    public int[] getPixel(int x, int y, int[] iArray) {
        int bands = getNumBands();
        if (iArray == null) {
            iArray = new int[bands];
        }
        int pixel = data()[(y - minY) * width + (x - minX)];
        for (int b = 0; b < bands; b++) {
            iArray[b] = (pixel >>> (8 * (bands - 1 - b))) & 0xFF;
        }
        return iArray;
    }

    public void setSample(int x, int y, int b, int s) {
        int[] d = data();
        int idx = (y - minY) * width + (x - minX);
        int shift = 8 * (getNumBands() - 1 - b);
        d[idx] = (d[idx] & ~(0xFF << shift)) | ((s & 0xFF) << shift);
    }

    public int getSample(int x, int y, int b) {
        int pixel = data()[(y - minY) * width + (x - minX)];
        return (pixel >>> (8 * (getNumBands() - 1 - b))) & 0xFF;
    }

    public int[] getPixels(int x, int y, int w, int h, int[] iArray) {
        int bands = getNumBands();
        if (iArray == null) {
            iArray = new int[w * h * bands];
        }
        int[] d = data();
        int o = 0;
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int pixel = d[(y + yy - minY) * width + (x + xx - minX)];
                for (int b = 0; b < bands; b++) {
                    iArray[o++] = (pixel >>> (8 * (bands - 1 - b))) & 0xFF;
                }
            }
        }
        return iArray;
    }
}

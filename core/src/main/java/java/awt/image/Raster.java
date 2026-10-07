package java.awt.image;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * AWT Raster compatibility stub for Android runtime.
 */
public class Raster {
    protected SampleModel sampleModel;
    protected DataBuffer dataBuffer;
    protected int minX;
    protected int minY;
    protected int width;
    protected int height;

    protected Raster(SampleModel sampleModel, DataBuffer dataBuffer, Point origin) {
        this.sampleModel = sampleModel;
        this.dataBuffer = dataBuffer;
        this.minX = origin == null ? 0 : origin.x;
        this.minY = origin == null ? 0 : origin.y;
        this.width = sampleModel == null ? 0 : sampleModel.getWidth();
        this.height = sampleModel == null ? 0 : sampleModel.getHeight();
    }

    public static WritableRaster createWritableRaster(SampleModel sm, DataBuffer db, Point location) {
        return new WritableRaster(sm, db, location);
    }

    public static WritableRaster createWritableRaster(SampleModel sm, Point location) {
        int size = sm.getWidth() * sm.getHeight();
        return new WritableRaster(sm, new DataBufferInt(new int[size], size), location);
    }

    public SampleModel getSampleModel() {
        return sampleModel;
    }

    public DataBuffer getDataBuffer() {
        return dataBuffer;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getNumBands() {
        return sampleModel == null ? 0 : sampleModel.getNumBands();
    }

    public Rectangle getBounds() {
        return new Rectangle(minX, minY, width, height);
    }
}

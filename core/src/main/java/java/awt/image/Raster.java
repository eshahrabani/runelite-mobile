package java.awt.image;

import java.awt.Point;

/**
 * AWT Raster compatibility stub for Android runtime.
 */
public class Raster {
    protected SampleModel sampleModel;
    protected DataBuffer dataBuffer;

    protected Raster(SampleModel sampleModel, DataBuffer dataBuffer, Point origin) {
        this.sampleModel = sampleModel;
        this.dataBuffer = dataBuffer;
    }

    public static WritableRaster createWritableRaster(SampleModel sm, DataBuffer db, Point location) {
        return new WritableRaster(sm, db, location);
    }

    public SampleModel getSampleModel() {
        return sampleModel;
    }

    public DataBuffer getDataBuffer() {
        return dataBuffer;
    }
}

package java.awt.image;

/**
 * AWT SampleModel compatibility stub for Android runtime.
 */
public abstract class SampleModel {
    protected int width;
    protected int height;

    public SampleModel(int dataType, int w, int h, int numBands) {
        this.width = w;
        this.height = h;
    }

    public final int getWidth() {
        return width;
    }

    public final int getHeight() {
        return height;
    }
}

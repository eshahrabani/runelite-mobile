package java.awt;

/**
 * AWT Image compatibility stub for Android runtime.
 */
public class Image {
    protected int[] pixels;
    protected int width;
    protected int height;

    public Image() {}

    public Image(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[] getPixels() {
        return pixels;
    }

    public Graphics getGraphics() {
        return new Graphics(this);
    }
}

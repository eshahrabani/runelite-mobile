package java.awt;

/**
 * AWT Graphics2D compatibility stub for Android runtime.
 */
public class Graphics2D extends Graphics {
    public Graphics2D() {
        super();
    }

    public Graphics2D(Image image) {
        super(image);
    }

    public Graphics2D(int[] destPixels, int destWidth, int destHeight) {
        super(destPixels, destWidth, destHeight);
    }

    public void setRenderingHint(Object key, Object value) {}
}

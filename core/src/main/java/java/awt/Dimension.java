package java.awt;

/**
 * AWT Dimension compatibility stub for Android runtime.
 */
public class Dimension {
    public int width;
    public int height;

    public Dimension() {}
    
    public Dimension(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() { return width; }

    public double getHeight() { return height; }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setSize(Dimension d) {
        this.width = d.width;
        this.height = d.height;
    }
}

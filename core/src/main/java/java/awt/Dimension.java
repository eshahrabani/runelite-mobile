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

    public Dimension(Dimension d) {
        this(d.width, d.height);
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setSize(Dimension d) {
        this.width = d.width;
        this.height = d.height;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Dimension)) {
            return false;
        }
        Dimension d = (Dimension) obj;
        return width == d.width && height == d.height;
    }

    @Override
    public int hashCode() {
        return width * 31 + height;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[width=" + width + ",height=" + height + "]";
    }
}

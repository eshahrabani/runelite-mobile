package java.awt;

/**
 * AWT FontMetrics compatibility stub for Android runtime.
 */
public class FontMetrics {
    protected Font font;

    protected FontMetrics(Font font) {
        this.font = font;
    }

    public int stringWidth(String str) {
        return str.length() * 6; // Simple estimation for text width estimation
    }

    public int getHeight() {
        return font != null ? font.getSize() : 12;
    }

    public java.awt.geom.Rectangle2D getStringBounds(String str, Graphics g) {
        return new java.awt.geom.Rectangle2D() {
            @Override
            public double getX() { return 0; }
            @Override
            public double getY() { return 0; }
            @Override
            public double getWidth() { return stringWidth(str); }
            @Override
            public double getHeight() { return getHeight(); }
        };
    }
}

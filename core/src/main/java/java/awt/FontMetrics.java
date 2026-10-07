package java.awt;

import org.runelite.mobile.bridge.TextBridge;

/**
 * AWT FontMetrics compatibility stub for Android runtime.
 *
 * <p>Every metric is obtained from the platform text renderer through
 * {@link TextBridge} when one is installed. Without a renderer the values fall
 * back to a deterministic approximation: 6 px per character for widths and the
 * point size for vertical metrics, so overlay code never dies.
 */
public class FontMetrics {

    protected Font font;

    protected FontMetrics(Font font) {
        this.font = font;
    }

    public Font getFont() {
        return font;
    }

    public int stringWidth(String str) {
        if (str == null) {
            return 0;
        }
        Object handle = font != null ? font.getTextRendererHandle() : null;
        if (TextBridge.available() && handle != null) {
            return TextBridge.stringWidth(handle, str);
        }
        return str.length() * 6;
    }

    public int getHeight() {
        return TextBridge.height(handleOrNull(), sizePx());
    }

    public int getAscent() {
        return TextBridge.ascent(handleOrNull(), sizePx());
    }

    public int getDescent() {
        return TextBridge.descent(handleOrNull(), sizePx());
    }

    public int getMaxDescent() {
        return getDescent();
    }

    public java.awt.geom.Rectangle2D getStringBounds(String str, Graphics g) {
        final int width = stringWidth(str);
        final int height = getHeight();
        return new java.awt.geom.Rectangle2D() {
            @Override
            public double getX() {
                return 0;
            }

            @Override
            public double getY() {
                return 0;
            }

            @Override
            public double getWidth() {
                return width;
            }

            @Override
            public double getHeight() {
                return height;
            }

            // No @Override: these are implemented only if the (sibling-owned)
            // Rectangle2D declares them abstract; otherwise they are harmless
            // additions. Declared without annotations so this compiles against
            // either shape of the abstract base.
            public double getMinX() {
                return 0;
            }

            public double getMinY() {
                return 0;
            }

            public double getMaxX() {
                return width;
            }

            public double getMaxY() {
                return height;
            }

            public void setFrameFromDiagonal(double x1, double y1, double x2, double y2) {
                // The bounds are immutable; nothing to update in this stub.
            }
        };
    }

    private Object handleOrNull() {
        return font != null ? font.getTextRendererHandle() : null;
    }

    private int sizePx() {
        return font != null ? font.getSize() : 12;
    }
}

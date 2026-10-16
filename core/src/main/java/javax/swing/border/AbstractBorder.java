package javax.swing.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

/**
 * Swing {@code AbstractBorder}: the convenience superclass for borders that do not draw
 * an edge and take no space by default.
 */
public abstract class AbstractBorder implements Border, java.io.Serializable {

    protected AbstractBorder() {
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return getBorderInsets(c, new Insets(0, 0, 0, 0));
    }

    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(0, 0, 0, 0);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }

    @Override
    public Rectangle getInteriorRectangle(Component c, int x, int y, int width, int height) {
        Insets insets = getBorderInsets(c);
        return new Rectangle(x + insets.left, y + insets.top,
                width - insets.left - insets.right, height - insets.top - insets.bottom);
    }
}

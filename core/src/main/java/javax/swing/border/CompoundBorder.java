package javax.swing.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/** Swing {@code CompoundBorder}: an outer border wrapping an inner one. */
public class CompoundBorder extends AbstractBorder {
    protected Border outsideBorder;
    protected Border insideBorder;

    public CompoundBorder() {
    }

    public CompoundBorder(Border outsideBorder, Border insideBorder) {
        this.outsideBorder = outsideBorder;
        this.insideBorder = insideBorder;
    }

    public void setOutsideBorder(Border outsideBorder) {
        this.outsideBorder = outsideBorder;
    }

    public void setInsideBorder(Border insideBorder) {
        this.insideBorder = insideBorder;
    }

    public Border getOutsideBorder() {
        return outsideBorder;
    }

    public Border getInsideBorder() {
        return insideBorder;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (outsideBorder != null) {
            outsideBorder.paintBorder(c, g, x, y, width, height);
        }
        if (insideBorder != null) {
            Insets insets = getBorderInsets(c);
            insideBorder.paintBorder(c, g, x + insets.left, y + insets.top,
                    width - insets.left - insets.right, height - insets.top - insets.bottom);
        }
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return getBorderInsets(c, new Insets(0, 0, 0, 0));
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        Insets outside = outsideBorder == null ? new Insets(0, 0, 0, 0) : outsideBorder.getBorderInsets(c);
        Insets inside = insideBorder == null ? new Insets(0, 0, 0, 0) : insideBorder.getBorderInsets(c);
        insets.set(outside.top + inside.top, outside.left + inside.left,
                outside.bottom + inside.bottom, outside.right + inside.right);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return (outsideBorder != null && outsideBorder.isBorderOpaque())
                || (insideBorder != null && insideBorder.isBorderOpaque());
    }

    public static boolean isBorderOpaque(Border border) {
        return border != null && border.isBorderOpaque();
    }
}

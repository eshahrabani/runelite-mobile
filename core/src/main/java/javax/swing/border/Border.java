package javax.swing.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

/**
 * Swing {@code Border}: decorates a component's edge and reports the insets that
 * shrink its interior. The port's real border set lives in this package
 * ({@code EmptyBorder}, {@code LineBorder}, {@code MatteBorder}, {@code CompoundBorder},
 * {@code BevelBorder}, {@code TitledBorder}) because RuneLite's panels and the
 * configuration UI use them for spacing and frames.
 */
public interface Border {
    void paintBorder(Component c, Graphics g, int x, int y, int width, int height);

    Insets getBorderInsets(Component c);

    boolean isBorderOpaque();

    default Rectangle getInteriorRectangle(Component c, int x, int y, int width, int height) {
        Insets insets = getBorderInsets(c);
        return new Rectangle(x + insets.left, y + insets.top,
                width - insets.left - insets.right, height - insets.top - insets.bottom);
    }

    static Rectangle getInteriorRectangle(Component c, Border b, int x, int y, int width, int height) {
        return b == null ? new Rectangle(x, y, width, height) : b.getInteriorRectangle(c, x, y, width, height);
    }
}

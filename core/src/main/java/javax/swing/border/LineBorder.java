package javax.swing.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/** Swing {@code LineBorder}: a line (optionally rounded) around the component. */
public class LineBorder extends AbstractBorder {
    private static final Insets DEFAULT_INSETS = new Insets(1, 1, 1, 1);

    private final Color lineColor;
    private final int thickness;
    private final boolean roundedCorners;

    public LineBorder(Color color) {
        this(color, 1, false);
    }

    public LineBorder(Color color, int thickness) {
        this(color, thickness, false);
    }

    public LineBorder(Color color, int thickness, boolean roundedCorners) {
        this.lineColor = color == null ? Color.BLACK : color;
        this.thickness = Math.max(1, thickness);
        this.roundedCorners = roundedCorners;
    }

    public Color getLineColor() {
        return lineColor;
    }

    public int getThickness() {
        return thickness;
    }

    public boolean getRoundedCorners() {
        return roundedCorners;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color old = g.getColor();
        g.setColor(lineColor);
        for (int i = 0; i < thickness; i++) {
            if (roundedCorners) {
                // A rounded line degenerate to a rectangle with clipped corners.
                g.fillRect(x + i + 1, y + i, Math.max(0, width - 2 * (i + 1)), 1);
                g.fillRect(x + i + 1, y + height - 1 - i, Math.max(0, width - 2 * (i + 1)), 1);
                g.fillRect(x + i, y + i + 1, 1, Math.max(0, height - 2 * (i + 1)));
                g.fillRect(x + width - 1 - i, y + i + 1, 1, Math.max(0, height - 2 * (i + 1)));
            } else {
                g.drawRect(x + i, y + i, Math.max(0, width - 1 - 2 * i), Math.max(0, height - 1 - 2 * i));
            }
        }
        g.setColor(old);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return thickness == 1 ? new Insets(1, 1, 1, 1) : new Insets(thickness, thickness, thickness, thickness);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(thickness, thickness, thickness, thickness);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return !roundedCorners;
    }

    /** The JDK's shared one-pixel black border. */
    public static Border createBlackLineBorder() {
        return new LineBorder(Color.BLACK);
    }

    public static Border createGrayLineBorder() {
        return new LineBorder(Color.GRAY);
    }
}

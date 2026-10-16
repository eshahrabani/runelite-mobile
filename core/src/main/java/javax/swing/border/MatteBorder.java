package javax.swing.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/** Swing {@code MatteBorder}: an edge of a solid colour (or a tiled icon) around the component. */
public class MatteBorder extends AbstractBorder {
    protected int top;
    protected int left;
    protected int bottom;
    protected int right;
    protected Color color;
    protected javax.swing.Icon tileIcon;

    public MatteBorder(int top, int left, int bottom, int right, Color matteColor) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.color = matteColor;
    }

    public MatteBorder(Insets borderInsets, Color matteColor) {
        this(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right, matteColor);
    }

    public MatteBorder(int top, int left, int bottom, int right, javax.swing.Icon tileIcon) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.tileIcon = tileIcon;
    }

    public MatteBorder(Insets borderInsets, javax.swing.Icon tileIcon) {
        this(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right, tileIcon);
    }

    public MatteBorder(javax.swing.Icon tileIcon) {
        this(0, 0, 0, 0, tileIcon);
    }

    public Insets getBorderInsets() {
        return new Insets(top, left, bottom, right);
    }

    public Color getMatteColor() {
        return color;
    }

    public javax.swing.Icon getTileIcon() {
        return tileIcon;
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(top, left, bottom, right);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(top, left, bottom, right);
        return insets;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (color != null) {
            Color old = g.getColor();
            g.setColor(color);
            if (top > 0) {
                g.fillRect(x, y, width, top);
            }
            if (left > 0) {
                g.fillRect(x, y + top, left, height - top - bottom);
            }
            if (bottom > 0) {
                g.fillRect(x, y + height - bottom, width, bottom);
            }
            if (right > 0) {
                g.fillRect(x + width - right, y + top, right, height - top - bottom);
            }
            g.setColor(old);
            return;
        }
        javax.swing.Icon icon = tileIcon;
        if (icon != null) {
            int iw = icon.getIconWidth();
            int ih = icon.getIconHeight();
            if (iw <= 0 || ih <= 0) {
                return;
            }
            for (int ty = y; ty < y + height; ty += ih) {
                for (int tx = x; tx < x + width; tx += iw) {
                    icon.paintIcon(c, g, tx, ty);
                }
            }
        }
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}

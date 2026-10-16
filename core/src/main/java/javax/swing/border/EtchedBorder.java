package javax.swing.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/** Swing {@code EtchedBorder}: a lowered or raised etched edge, two one-pixel lines. */
public class EtchedBorder extends AbstractBorder {
    public static final int RAISED = 0;
    public static final int LOWERED = 1;

    private final int etchType;
    private Color highlight;
    private Color shadow;

    public EtchedBorder() {
        this(LOWERED);
    }

    public EtchedBorder(int etchType) {
        this.etchType = etchType;
    }

    public EtchedBorder(Color highlight, Color shadow) {
        this(LOWERED, highlight, shadow);
    }

    public EtchedBorder(int etchType, Color highlight, Color shadow) {
        this.etchType = etchType;
        this.highlight = highlight;
        this.shadow = shadow;
    }

    public int getEtchType() {
        return etchType;
    }

    public Color getHighlightColor(Component c) {
        if (highlight != null) {
            return highlight;
        }
        Color base = c != null ? c.getBackground() : null;
        return base != null ? base.brighter() : new Color(220, 220, 220);
    }

    public Color getShadowColor(Component c) {
        if (shadow != null) {
            return shadow;
        }
        Color base = c != null ? c.getBackground() : null;
        return base != null ? base.darker() : new Color(60, 60, 60);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color old = g.getColor();
        Color hl = getHighlightColor(c);
        Color sh = getShadowColor(c);
        if (etchType == LOWERED) {
            g.setColor(sh);
            g.drawRect(x, y, width - 1, height - 1);
            g.setColor(hl);
            g.drawRect(x + 1, y + 1, width - 3, height - 3);
        } else {
            g.setColor(hl);
            g.drawRect(x, y, width - 1, height - 1);
            g.setColor(sh);
            g.drawRect(x + 1, y + 1, width - 3, height - 3);
        }
        g.setColor(old);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(2, 2, 2, 2);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(2, 2, 2, 2);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return true;
    }
}

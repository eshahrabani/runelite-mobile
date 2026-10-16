package javax.swing.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/** Swing {@code BevelBorder}: a two-line raised or lowered edge. */
public class BevelBorder extends AbstractBorder {
    public static final int RAISED = 0;
    public static final int LOWERED = 1;

    protected int bevelType;
    protected Color highlightOuter;
    protected Color highlightInner;
    protected Color shadowInner;
    protected Color shadowOuter;

    public BevelBorder(int bevelType) {
        this.bevelType = bevelType;
    }

    public BevelBorder(int bevelType, Color highlight, Color shadow) {
        this(bevelType, highlight.brighter(), highlight, shadow, shadow.brighter());
    }

    public BevelBorder(int bevelType, Color highlightOuter, Color highlightInner,
                       Color shadowOuter, Color shadowInner) {
        this.bevelType = bevelType;
        this.highlightOuter = highlightOuter;
        this.highlightInner = highlightInner;
        this.shadowOuter = shadowOuter;
        this.shadowInner = shadowInner;
    }

    public int getBevelType() {
        return bevelType;
    }

    public Color getHighlightOuterColor(Component c) {
        return highlightOuter != null ? highlightOuter : defaultColor(c, true, true);
    }

    public Color getHighlightInnerColor(Component c) {
        return highlightInner != null ? highlightInner : defaultColor(c, true, false);
    }

    public Color getShadowInnerColor(Component c) {
        return shadowInner != null ? shadowInner : defaultColor(c, false, false);
    }

    public Color getShadowOuterColor(Component c) {
        return shadowOuter != null ? shadowOuter : defaultColor(c, false, true);
    }

    private Color defaultColor(Component c, boolean highlight, boolean outer) {
        Color base = c != null ? c.getBackground() : new Color(200, 200, 200);
        if (base == null) {
            base = new Color(200, 200, 200);
        }
        return highlight ? (outer ? base.brighter() : base) : (outer ? base.darker() : base.darker().darker());
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color old = g.getColor();
        if (bevelType == RAISED) {
            g.setColor(getHighlightOuterColor(c));
            g.drawLine(x, y, x + width - 2, y);
            g.drawLine(x, y, x, y + height - 2);
            g.setColor(getShadowOuterColor(c));
            g.drawLine(x + 1, y + height - 2, x + width - 2, y + height - 2);
            g.drawLine(x + width - 2, y + 1, x + width - 2, y + height - 2);
            g.setColor(getHighlightInnerColor(c));
            g.drawLine(x + 1, y + 1, x + width - 3, y + 1);
            g.drawLine(x + 1, y + 1, x + 1, y + height - 3);
            g.setColor(getShadowInnerColor(c));
            g.drawLine(x + 2, y + height - 3, x + width - 3, y + height - 3);
            g.drawLine(x + width - 3, y + 2, x + width - 3, y + height - 3);
        } else {
            g.setColor(getShadowOuterColor(c));
            g.drawLine(x, y, x + width - 2, y);
            g.drawLine(x, y, x, y + height - 2);
            g.setColor(getHighlightOuterColor(c));
            g.drawLine(x + 1, y + height - 2, x + width - 2, y + height - 2);
            g.drawLine(x + width - 2, y + 1, x + width - 2, y + height - 2);
            g.setColor(getShadowInnerColor(c));
            g.drawLine(x + 1, y + 1, x + width - 3, y + 1);
            g.drawLine(x + 1, y + 1, x + 1, y + height - 3);
            g.setColor(getHighlightInnerColor(c));
            g.drawLine(x + 2, y + height - 3, x + width - 3, y + height - 3);
            g.drawLine(x + width - 3, y + 2, x + width - 3, y + height - 3);
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

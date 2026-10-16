package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * Swing {@code JSeparator}: a one-pixel divider line (RuneLite's truncated list-cell
 * renderer uses one between rows).
 *
 * <p>Hand-written: there is no {@code BasicSeparatorUI} in this slice, so the component
 * paints its own line when no UI is installed. The preferred size follows the JDK's
 * {@code BasicSeparatorUI}: a zero-length run on the long axis (so the layout stretches it)
 * and one pixel on the thick axis.
 */
public class JSeparator extends JComponent implements SwingConstants, javax.accessibility.Accessible {

    private int orientation;

    public JSeparator() {
        this(HORIZONTAL);
    }

    public JSeparator(int orientation) {
        super();
        this.orientation = orientation == VERTICAL ? VERTICAL : HORIZONTAL;
        setOpaque(false);
        Color foreground = UIManager.getColor("Separator.foreground");
        if (foreground != null) {
            setForeground(foreground);
        }
    }

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        int old = this.orientation;
        this.orientation = orientation == VERTICAL ? VERTICAL : HORIZONTAL;
        firePropertyChange("orientation", old, this.orientation);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return orientation == VERTICAL ? new Dimension(1, 0) : new Dimension(0, 1);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color color = getForeground();
        if (color == null) {
            color = UIManager.getColor("Separator.foreground");
        }
        if (color == null) {
            return;
        }
        g.setColor(color);
        if (orientation == VERTICAL) {
            g.fillRect(0, 0, 1, getHeight());
        } else {
            g.fillRect(0, 0, getWidth(), 1);
        }
    }
}

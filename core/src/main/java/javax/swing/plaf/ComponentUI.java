package javax.swing.plaf;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;

/**
 * Swing {@code ComponentUI}: the delegate a {@code JComponent} paints through.
 *
 * <p>{@link #update} is the entry point the port's paint pipeline calls: it fills the
 * component's background when the component is opaque and then runs {@link #paint}, so a
 * widget with an installed UI gets the standard Swing background behaviour for free.
 */
public abstract class ComponentUI {

    protected ComponentUI() {
    }

    /** Installs the delegate's listeners/defaults on the component. */
    public void installUI(JComponent c) {
    }

    public void uninstallUI(JComponent c) {
    }

    public void paint(Graphics g, JComponent c) {
    }

    public void update(Graphics g, JComponent c) {
        if (c.isOpaque()) {
            Color background = c.getBackground();
            if (background != null) {
                g.setColor(background);
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
        }
        paint(g, c);
    }

    public Dimension getPreferredSize(JComponent c) {
        return null;
    }

    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }

    public boolean contains(JComponent c, int x, int y) {
        return x >= 0 && y >= 0 && x < c.getWidth() && y < c.getHeight();
    }

    public int getBaseline(JComponent c, int width, int height) {
        return -1;
    }

    public void installDefaults(JComponent c) {
    }

    public void uninstallDefaults(JComponent c) {
    }

    public void installListeners(JComponent c) {
    }

    public void uninstallListeners(JComponent c) {
    }
}

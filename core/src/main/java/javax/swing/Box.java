package javax.swing;

import java.awt.Component;
import java.awt.Dimension;

/**
 * {@code javax.swing.Box} factory methods. The port performs no layout, so the
 * struts/glues only have to exist as components with the right preferred size; the
 * side panel is native Android UI and never reads them.
 */
public class Box extends JComponent {

    public Box(int axis) {
    }

    public static Component createGlue() {
        return createRigidArea(new Dimension(0, 0));
    }

    public static Component createHorizontalGlue() {
        return createRigidArea(new Dimension(0, 0));
    }

    public static Component createVerticalGlue() {
        return createRigidArea(new Dimension(0, 0));
    }

    public static Component createRigidArea(Dimension d) {
        return new Filler(d);
    }

    /** Minimal filler component (the desktop version is an inner class of Box). */
    public static class Filler extends JComponent {
        private final Dimension preferred;

        public Filler(Dimension preferred) {
            this.preferred = preferred;
        }

        public Dimension getPreferredSize() {
            return preferred;
        }

        public Dimension getMinimumSize() {
            return preferred;
        }
    }
}

package javax.swing;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Swing {@code Scrollable}: a view that scrolls itself inside a {@link JScrollPane}
 * (the plugin panels' {@code VirtualList} is one).
 */
public interface Scrollable {
    /** Default: the view's preferred size. */
    default Dimension getPreferredScrollableViewportSize() {
        return this instanceof JComponent ? ((JComponent) this).getPreferredSize() : new Dimension(100, 100);
    }

    /** Default: one text line-ish step. */
    default int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    /** Default: one viewport page minus an overlap. */
    default int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        int extent = orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        return Math.max(visibleRect.width, extent - 16);
    }

    default boolean getScrollableTracksViewportWidth() {
        return false;
    }

    default boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

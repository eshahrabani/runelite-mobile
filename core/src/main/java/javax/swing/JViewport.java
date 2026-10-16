package javax.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Swing {@code JViewport}: the scrolling window a {@link JScrollPane} puts its view in.
 *
 * <p>The view's position is {@code viewPosition} (the top-left corner of the view that is
 * shown); the view is translated by {@code -viewPosition} when painted and clipped to the
 * viewport's extent. A non-{@link Scrollable} view is stretched to at least the extent on
 * each axis — this is what makes a plugin panel fill the sidebar width — while a
 * {@code Scrollable} view decides for itself.
 */
public class JViewport extends JComponent implements javax.accessibility.Accessible {
    /** Name of the "view position changed" change event property. */
    public static final String VIEWPOSITION_CHANGED_PROPERTY = "viewPosition";
    /** Name of the "view size changed" change event property. */
    public static final String VIEWSIZE_CHANGED_PROPERTY = "viewSize";
    /** Name of the "view changed" change event property. */
    public static final String VIEW_CHANGED_PROPERTY = "view";

    private Component view;
    private final Point viewPosition = new Point(0, 0);
    private Dimension extentSize = new Dimension(0, 0);
    private java.util.List<ChangeListener> changeListeners;
    private boolean scrollMode;

    /**
     * Tells the viewport (and through it the enclosing {@code JScrollPane}) that the view
     * changed size, so the scroll bar models are re-derived from the new view size. The JDK
     * has the same listener; without it a view that only grows would keep scroll bar models
     * sized for its old bounds until something else forced a relayout.
     */
    private final ComponentListener viewListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            fireStateChanged();
            revalidate();
        }
    };

    public JViewport() {
        super();
        setOpaque(true);
        setLayout(new ViewportLayout());
    }

    public void setView(Component view) {
        if (this.view == view) {
            return;
        }
        if (this.view != null) {
            this.view.removeComponentListener(viewListener);
            remove(this.view);
        }
        this.view = view;
        if (view != null) {
            add(view);
            view.addComponentListener(viewListener);
        }
        firePropertyChange(VIEW_CHANGED_PROPERTY, null, view);
        revalidate();
        repaint();
    }

    public Component getView() {
        return view;
    }

    public void setViewPosition(Point p) {
        if (p == null || view == null) {
            return;
        }
        Point old = new Point(viewPosition);
        viewPosition.setLocation(p);
        Point clamped = computeViewPosition(new Rectangle(p, extentSize));
        if (!clamped.equals(viewPosition)) {
            viewPosition.setLocation(clamped);
        }
        if (!old.equals(viewPosition)) {
            fireStateChanged();
            repaint();
        }
    }

    public Point getViewPosition() {
        return new Point(viewPosition);
    }

    public Dimension getViewSize() {
        if (view == null) {
            return new Dimension(0, 0);
        }
        Dimension size = view.getSize();
        if (size.width <= 0 || size.height <= 0) {
            Dimension pref = view.getPreferredSize();
            size = pref == null ? new Dimension(0, 0) : pref;
        }
        return size;
    }

    public Rectangle getViewRect() {
        Point p = getViewPosition();
        Dimension size = getExtentSize();
        return new Rectangle(p.x, p.y, size.width, size.height);
    }

    public void setExtentSize(Dimension newExtent) {
        Dimension old = this.extentSize;
        this.extentSize = newExtent == null ? new Dimension(0, 0) : new Dimension(newExtent);
        if (!old.equals(this.extentSize)) {
            firePropertyChange(VIEWSIZE_CHANGED_PROPERTY, old, this.extentSize);
            // The extent is what a JScrollPane derives its scroll bar models from, so it
            // has to be a state change and not only a property change (JDK behaviour).
            fireStateChanged();
            repaint();
        }
    }

    public Dimension getExtentSize() {
        return new Dimension(extentSize);
    }

    /**
     * Converts a size in pixel coordinates to view coordinates.
     *
     * <p>Views are never transformed on this port (there is no {@code ViewportUI} with
     * logical coordinates), so this is the identity, as in the JDK's base implementation.
     */
    public Dimension toViewCoordinates(Dimension size) {
        return new Dimension(size);
    }

    /** See {@link #toViewCoordinates(Dimension)}: the identity mapping. */
    public Point toViewCoordinates(Point p) {
        return new Point(p);
    }

    /** Scrolls so the given rectangle (in view coordinates) is visible. */
    public void scrollRectToVisible(Rectangle contentRect) {
        scrollToVisible(contentRect);
    }

    public void scrollToVisible(Rectangle contentRect) {
        if (contentRect == null || view == null) {
            return;
        }
        Point position = getViewPosition();
        Dimension extent = getExtentSize();
        Dimension viewSize = getViewSize();
        int x = position.x;
        int y = position.y;
        if (contentRect.x < position.x) {
            x = contentRect.x;
        } else if (contentRect.x + contentRect.width > position.x + extent.width) {
            x = contentRect.x + contentRect.width - extent.width;
        }
        if (contentRect.y < position.y) {
            y = contentRect.y;
        } else if (contentRect.y + contentRect.height > position.y + extent.height) {
            y = contentRect.y + contentRect.height - extent.height;
        }
        x = Math.max(0, Math.min(x, Math.max(0, viewSize.width - extent.width)));
        y = Math.max(0, Math.min(y, Math.max(0, viewSize.height - extent.height)));
        setViewPosition(new Point(x, y));
    }

    /** Clamps a candidate view position so the view cannot be scrolled past its ends. */
    private Point computeViewPosition(Rectangle proposed) {
        Dimension viewSize = getViewSize();
        Dimension extent = getExtentSize();
        int maxX = Math.max(0, viewSize.width - extent.width);
        int maxY = Math.max(0, viewSize.height - extent.height);
        return new Point(Math.max(0, Math.min(proposed.x, maxX)), Math.max(0, Math.min(proposed.y, maxY)));
    }

    public void setScrollMode(boolean blk) {
        this.scrollMode = blk;
    }

    public boolean getScrollMode() {
        return scrollMode;
    }

    @Override
    protected void paintChildren(Graphics g) {
        Component v = view;
        if (v == null || !v.isVisible()) {
            return;
        }
        Point p = getViewPosition();
        Graphics gc = g.create();
        gc.translate(-p.x, -p.y);
        v.paint(gc);
        gc.dispose();
    }

    public void addChangeListener(ChangeListener listener) {
        if (changeListeners == null) {
            changeListeners = new java.util.ArrayList<>();
        }
        changeListeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        if (changeListeners != null) {
            changeListeners.remove(listener);
        }
    }

    public ChangeListener[] getChangeListeners() {
        return changeListeners == null ? new ChangeListener[0] : changeListeners.toArray(new ChangeListener[0]);
    }

    protected void fireStateChanged() {
        if (changeListeners == null || changeListeners.isEmpty()) {
            return;
        }
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : new java.util.ArrayList<>(changeListeners)) {
            l.stateChanged(event);
        }
    }

    @Override
    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }

    /**
     * Swing {@code ViewportLayout}: the JDK rule — a non-{@code Scrollable} view is
     * stretched to at least the extent size, a {@code Scrollable} one is asked.
     */
    public static class ViewportLayout implements java.awt.LayoutManager, java.io.Serializable {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container parent) {
            Component view = ((JViewport) parent).getView();
            if (view == null) {
                return new Dimension(0, 0);
            }
            // A Scrollable view reports the size it wants the viewport to have; a plain
            // component is asked for its preferred size (JDK ViewportLayout behaviour).
            Dimension d = view instanceof Scrollable
                    ? ((Scrollable) view).getPreferredScrollableViewportSize()
                    : view.getPreferredSize();
            return d == null ? new Dimension(0, 0) : d;
        }

        @Override
        public Dimension minimumLayoutSize(java.awt.Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(java.awt.Container parent) {
            JViewport viewport = (JViewport) parent;
            Component view = viewport.getView();
            if (view == null) {
                return;
            }
            Dimension extent = new Dimension(viewport.getWidth(), viewport.getHeight());
            viewport.setExtentSize(extent);
            // All dimensions below are in viewport coordinates (there is no view
            // transformation on this port, so view coordinates == viewport coordinates).
            Dimension viewPref = view.getPreferredSize();
            if (viewPref == null) {
                viewPref = new Dimension(0, 0);
            }
            int w = viewPref.width;
            int h = viewPref.height;
            if (view instanceof Scrollable) {
                Scrollable scrollable = (Scrollable) view;
                // A tracking view is made exactly the viewport's size — that is what makes
                // it track; a non-tracking one keeps its preferred size and scrolls.
                if (scrollable.getScrollableTracksViewportWidth()) {
                    w = extent.width;
                }
                if (scrollable.getScrollableTracksViewportHeight()) {
                    h = extent.height;
                }
            } else if (!viewport.getScrollMode()) {
                // A plain component is stretched to at least the extent: this is the rule
                // that makes a plugin panel fill the sidebar's width.
                w = Math.max(w, extent.width);
                h = Math.max(h, extent.height);
            }
            view.setBounds(0, 0, Math.max(0, w), Math.max(0, h));
            // Reposition: the view may have shrunk, so the old position can be stale.
            viewport.setViewPosition(viewport.getViewPosition());
        }
    }
}

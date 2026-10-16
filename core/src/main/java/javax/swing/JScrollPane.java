package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Swing {@code JScrollPane}: a {@link JViewport} plus the two {@link JScrollBar}s that
 * scroll it, wired to each other.
 *
 * <p>Hand-written rather than generated: this is how every RuneLite plugin panel is made
 * scrollable ({@code PluginPanel} wraps its content in a {@code JScrollPane} and
 * {@code PluginListPanel} scrolls back to the remembered offset through
 * {@code getVerticalScrollBar()}), so the type has to assemble, lay out and synchronise
 * real components. The generated file declared only the handful of members the shipped
 * jars reference; all of them are kept here, and the rest of the JDK's surface is added.
 * The marker sentence is removed so the generator leaves this file alone.
 *
 * <p>Port deviations (all forced by the missing {@code java.desktop}):
 * <ul>
 *   <li>The JDK's {@code ScrollPaneLayout} is not a public class here — nothing in the
 *       shipped jars references it — so the JDK's layout algorithm (viewport, two scroll
 *       bars, row/column headers, four corners, the "does the scroll bar fit" re-check
 *       after the viewport has been sized) is inlined as the scroll pane's private layout
 *       manager.</li>
 *   <li>Likewise there is no {@code ScrollPaneUI}: the pane paints its own background and
 *       viewport border and owns its listeners directly.</li>
 *   <li>{@code java.awt.ComponentOrientation} does not exist on this port, so every
 *       scroll pane is left-to-right: {@code LOWER_LEADING_CORNER} is the lower left one,
 *       {@code LOWER_TRAILING_CORNER} the lower right one, and so on.</li>
 *   <li>Wheel handling is the JDK's {@code BasicScrollPaneUI.Handler.mouseWheelMoved}
 *       without the {@code JScrollBar.fastWheelScrolling} fast path (nothing sets that
 *       client property on this port).</li>
 *   <li>The JDK's {@code MouseWheelEvent.getUnitsToScroll()} is computed here as
 *       {@code getScrollAmount() * getWheelRotation()} because the port's
 *       {@code MouseWheelEvent} exposes the two factors separately.</li>
 * </ul>
 */
public class JScrollPane extends JComponent implements ScrollPaneConstants, javax.accessibility.Accessible {

    private Border viewportBorder;

    /** When the vertical scroll bar is displayed: {@code VERTICAL_SCROLLBAR_*}. */
    protected int verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED;

    /** When the horizontal scroll bar is displayed: {@code HORIZONTAL_SCROLLBAR_*}. */
    protected int horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED;

    /** The scrolling window the view lives in. */
    protected JViewport viewport;

    /** The vertical scroll bar; may be replaced or removed. */
    protected JScrollBar verticalScrollBar;

    /** The horizontal scroll bar; may be replaced or removed. */
    protected JScrollBar horizontalScrollBar;

    /** The optional row header (the leading side of the viewport). */
    protected JViewport rowHeader;

    /** The optional column header (above the viewport). */
    protected JViewport columnHeader;

    protected Component lowerLeft;
    protected Component lowerRight;
    protected Component upperLeft;
    protected Component upperRight;

    private boolean wheelScrollState = true;

    private final Layout layout = new Layout();
    private final ChangeListener viewportChangeListener = new ViewportChangeListener();
    private final ChangeListener vsbChangeListener = new BarModelListener(true);
    private final ChangeListener hsbChangeListener = new BarModelListener(false);
    private final MouseWheelListener viewportWheelListener = new ViewportWheelListener();

    /** Creates an empty scroll pane with both scroll bars shown as needed. */
    public JScrollPane() {
        this(null, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    /** Creates a scroll pane that displays {@code view}. */
    public JScrollPane(Component view) {
        this(view, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    /** Creates a scroll pane with the given scroll bar display policies. */
    public JScrollPane(int vsbPolicy, int hsbPolicy) {
        this(null, vsbPolicy, hsbPolicy);
    }

    /**
     * Creates a scroll pane that displays {@code view} with the given scroll bar display
     * policies.
     */
    public JScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
        setLayout(layout);
        setVerticalScrollBarPolicy(vsbPolicy);
        setHorizontalScrollBarPolicy(hsbPolicy);
        setViewport(createViewport());
        setVerticalScrollBar(createVerticalScrollBar());
        setHorizontalScrollBar(createHorizontalScrollBar());
        if (view != null) {
            setViewportView(view);
        }
        setOpaque(true);
        Color background = UIManager.getColor("ScrollPane.background");
        if (background != null) {
            setBackground(background);
        }
        Border border = UIManager.getBorder("ScrollPane.border");
        if (border != null) {
            setBorder(border);
        }
        verticalScrollBar.setVisible(false);
        horizontalScrollBar.setVisible(false);
    }

    /** {@code "ScrollPaneUI"} — no such delegate exists on this port, see the class doc. */
    @Override
    public String getUIClassID() {
        return "ScrollPaneUI";
    }

    // --------------------------------------------------------------- children

    /** Creates the viewport a new scroll pane starts with; subclasses may narrow it. */
    protected JViewport createViewport() {
        return new JViewport();
    }

    /** Creates a vertical scroll bar whose increments follow the view. */
    protected JScrollBar createVerticalScrollBar() {
        return new ScrollBar(JScrollBar.VERTICAL);
    }

    /** Creates a horizontal scroll bar whose increments follow the view. */
    protected JScrollBar createHorizontalScrollBar() {
        return new ScrollBar(JScrollBar.HORIZONTAL);
    }

    public JViewport getViewport() {
        return viewport;
    }

    /** Replaces the viewport, moving the change listener and wheel handler over to it. */
    public void setViewport(JViewport viewport) {
        JViewport old = this.viewport;
        if (old != null) {
            old.removeChangeListener(viewportChangeListener);
            old.removeMouseWheelListener(viewportWheelListener);
            remove(old);
        }
        this.viewport = viewport;
        if (viewport != null) {
            add(viewport, VIEWPORT);
            viewport.addChangeListener(viewportChangeListener);
            viewport.addMouseWheelListener(viewportWheelListener);
        }
        firePropertyChange("viewport", old, viewport);
        revalidate();
    }

    /** The component being scrolled, or null. */
    public Component getViewportView() {
        return viewport == null ? null : viewport.getView();
    }

    /**
     * Makes {@code view} the scrolled component, creating the viewport if the application
     * removed it. Applications should not add children to a scroll pane directly.
     */
    public void setViewportView(Component view) {
        if (getViewport() == null) {
            setViewport(createViewport());
        }
        getViewport().setView(view);
    }

    public JViewport getRowHeader() {
        return rowHeader;
    }

    /** Replaces the row header viewport. */
    public void setRowHeader(JViewport rowHeader) {
        JViewport old = this.rowHeader;
        if (old != null) {
            remove(old);
        }
        this.rowHeader = rowHeader;
        if (rowHeader != null) {
            add(rowHeader, ROW_HEADER);
        }
        firePropertyChange("rowHeader", old, rowHeader);
        revalidate();
    }

    /** Makes {@code view} the row header, creating the viewport if there is none yet. */
    public void setRowHeaderView(Component view) {
        if (getRowHeader() == null) {
            setRowHeader(createViewport());
        }
        getRowHeader().setView(view);
    }

    public JViewport getColumnHeader() {
        return columnHeader;
    }

    /** Replaces the column header viewport. */
    public void setColumnHeader(JViewport columnHeader) {
        JViewport old = this.columnHeader;
        if (old != null) {
            remove(old);
        }
        this.columnHeader = columnHeader;
        if (columnHeader != null) {
            add(columnHeader, COLUMN_HEADER);
        }
        firePropertyChange("columnHeader", old, columnHeader);
        revalidate();
    }

    /** Makes {@code view} the column header, creating the viewport if there is none yet. */
    public void setColumnHeaderView(Component view) {
        if (getColumnHeader() == null) {
            setColumnHeader(createViewport());
        }
        getColumnHeader().setView(view);
    }

    /** The component in one of the four corners, or null. */
    public Component getCorner(String key) {
        switch (normalizeCornerKey(key)) {
            case LOWER_LEFT_CORNER:
                return lowerLeft;
            case LOWER_RIGHT_CORNER:
                return lowerRight;
            case UPPER_LEFT_CORNER:
                return upperLeft;
            case UPPER_RIGHT_CORNER:
                return upperRight;
            default:
                throw new IllegalArgumentException("invalid corner key");
        }
    }

    /** Puts {@code corner} into one of the four corners of the scroll pane. */
    public void setCorner(String key, Component corner) {
        Component old;
        switch (normalizeCornerKey(key)) {
            case LOWER_LEFT_CORNER:
                old = lowerLeft;
                lowerLeft = corner;
                break;
            case LOWER_RIGHT_CORNER:
                old = lowerRight;
                lowerRight = corner;
                break;
            case UPPER_LEFT_CORNER:
                old = upperLeft;
                upperLeft = corner;
                break;
            case UPPER_RIGHT_CORNER:
                old = upperRight;
                upperRight = corner;
                break;
            default:
                throw new IllegalArgumentException("invalid corner key");
        }
        if (old != null && old != corner) {
            remove(old);
        }
        if (corner != null) {
            add(corner, key);
        }
    }

    /**
     * Maps the orientation-relative corner keys onto the fixed ones. The port has no
     * {@code ComponentOrientation}, so leading is always the left/top one.
     */
    private static String normalizeCornerKey(String key) {
        if (LOWER_LEADING_CORNER.equals(key)) {
            return LOWER_LEFT_CORNER;
        }
        if (UPPER_LEADING_CORNER.equals(key)) {
            return UPPER_LEFT_CORNER;
        }
        if (LOWER_TRAILING_CORNER.equals(key)) {
            return LOWER_RIGHT_CORNER;
        }
        if (UPPER_TRAILING_CORNER.equals(key)) {
            return UPPER_RIGHT_CORNER;
        }
        return key;
    }

    // ------------------------------------------------------------- scroll bars

    public int getVerticalScrollBarPolicy() {
        return verticalScrollBarPolicy;
    }

    /** Sets when the vertical scroll bar appears; revalidates and repaints. */
    public void setVerticalScrollBarPolicy(int policy) {
        switch (policy) {
            case VERTICAL_SCROLLBAR_AS_NEEDED:
            case VERTICAL_SCROLLBAR_NEVER:
            case VERTICAL_SCROLLBAR_ALWAYS:
                break;
            default:
                throw new IllegalArgumentException("invalid verticalScrollBarPolicy");
        }
        int old = verticalScrollBarPolicy;
        verticalScrollBarPolicy = policy;
        firePropertyChange(VERTICAL_SCROLLBAR_POLICY, old, policy);
        revalidate();
        repaint();
    }

    public int getHorizontalScrollBarPolicy() {
        return horizontalScrollBarPolicy;
    }

    /** Sets when the horizontal scroll bar appears; revalidates and repaints. */
    public void setHorizontalScrollBarPolicy(int policy) {
        switch (policy) {
            case HORIZONTAL_SCROLLBAR_AS_NEEDED:
            case HORIZONTAL_SCROLLBAR_NEVER:
            case HORIZONTAL_SCROLLBAR_ALWAYS:
                break;
            default:
                throw new IllegalArgumentException("invalid horizontalScrollBarPolicy");
        }
        int old = horizontalScrollBarPolicy;
        horizontalScrollBarPolicy = policy;
        firePropertyChange(HORIZONTAL_SCROLLBAR_POLICY, old, policy);
        revalidate();
        repaint();
    }

    public JScrollBar getVerticalScrollBar() {
        return verticalScrollBar;
    }

    /** Replaces the vertical scroll bar, moving the model listener over to the new one. */
    public void setVerticalScrollBar(JScrollBar verticalScrollBar) {
        JScrollBar old = this.verticalScrollBar;
        if (old != null) {
            old.getModel().removeChangeListener(vsbChangeListener);
            remove(old);
        }
        this.verticalScrollBar = verticalScrollBar;
        if (verticalScrollBar != null) {
            add(verticalScrollBar, VERTICAL_SCROLLBAR);
            verticalScrollBar.getModel().addChangeListener(vsbChangeListener);
        }
        firePropertyChange("verticalScrollBar", old, verticalScrollBar);
        revalidate();
    }

    public JScrollBar getHorizontalScrollBar() {
        return horizontalScrollBar;
    }

    /** Replaces the horizontal scroll bar, moving the model listener over to the new one. */
    public void setHorizontalScrollBar(JScrollBar horizontalScrollBar) {
        JScrollBar old = this.horizontalScrollBar;
        if (old != null) {
            old.getModel().removeChangeListener(hsbChangeListener);
            remove(old);
        }
        this.horizontalScrollBar = horizontalScrollBar;
        if (horizontalScrollBar != null) {
            add(horizontalScrollBar, HORIZONTAL_SCROLLBAR);
            horizontalScrollBar.getModel().addChangeListener(hsbChangeListener);
        }
        firePropertyChange("horizontalScrollBar", old, horizontalScrollBar);
        revalidate();
    }

    // -------------------------------------------------------------- border

    public Border getViewportBorder() {
        return viewportBorder;
    }

    /** Sets a border painted around the viewport (inside the scroll pane's own border). */
    public void setViewportBorder(Border viewportBorder) {
        Border old = this.viewportBorder;
        this.viewportBorder = viewportBorder;
        firePropertyChange("viewportBorder", old, viewportBorder);
        revalidate();
        repaint();
    }

    /**
     * The rectangle the viewport border is painted in: the pane's own insets, reduced by
     * the space the column and row headers take.
     */
    public Rectangle getViewportBorderBounds() {
        Rectangle borderR = new Rectangle(getSize());

        Insets insets = getInsets();
        if (insets != null) {
            borderR.x = insets.left;
            borderR.y = insets.top;
            borderR.width -= insets.left + insets.right;
            borderR.height -= insets.top + insets.bottom;
        }

        if (columnHeader != null && columnHeader.isVisible()) {
            int colHeadHeight = columnHeader.getHeight();
            borderR.y += colHeadHeight;
            borderR.height -= colHeadHeight;
        }
        if (rowHeader != null && rowHeader.isVisible()) {
            int rowHeadWidth = rowHeader.getWidth();
            borderR.x += rowHeadWidth;
            borderR.width -= rowHeadWidth;
        }
        return borderR;
    }

    // --------------------------------------------------------------- painting

    /**
     * Fills the background and paints the viewport border. Children are painted by
     * {@code JComponent.paint}'s no-UI branch, so this must not paint them (unlike the
     * JDK, whose {@code ScrollPaneUI} runs inside {@code paintComponent} before
     * {@code paintChildren}).
     */
    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            Color background = getBackground();
            if (background != null) {
                g.setColor(background);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
        Border border = viewportBorder;
        if (border != null) {
            Rectangle r = getViewportBorderBounds();
            border.paintBorder(this, g, r.x, r.y, r.width, r.height);
        }
    }

    // ----------------------------------------------------------------- wheel

    /** True when a mouse wheel over this pane scrolls it. */
    public boolean isWheelScrollingEnabled() {
        return wheelScrollState;
    }

    /** Enables/disables wheel scrolling for this pane. */
    public void setWheelScrollingEnabled(boolean handleWheel) {
        boolean old = wheelScrollState;
        wheelScrollState = handleWheel;
        firePropertyChange("wheelScrollingEnabled", old, handleWheel);
    }

    /**
     * Handles a wheel event that reached this pane.
     *
     * <p>The event is dispatched to the deepest component under the pointer and bubbles up
     * the JComponent ancestry ({@code Component.dispatchEvent}), so a wheel over a plugin
     * panel's content arrives here after the chain
     * view → {@code JViewport} → {@code JScrollPane} has been walked. The viewport also has
     * this pane registered as a {@code MouseWheelListener}, which covers a wheel that
     * targets the viewport itself because the view does not bubble (a plain AWT child).
     */
    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e) {
        super.processMouseWheelEvent(e);
        if (!e.isConsumed()) {
            handleMouseWheel(e);
        }
    }

    /**
     * Scrolls the vertical scroll bar by the wheel's unit count (or the horizontal bar when
     * there is no vertical bar or shift is held). The event is consumed when the pane takes
     * it, exactly as the JDK does, so an enclosing scroll pane does not scroll as well.
     */
    private void handleMouseWheel(MouseWheelEvent e) {
        if (!wheelScrollState || e.getWheelRotation() == 0) {
            return;
        }
        JScrollBar toScroll = verticalScrollBar;
        int direction = e.getWheelRotation() < 0 ? -1 : 1;
        int orientation = SwingConstants.VERTICAL;

        if (toScroll == null || !toScroll.isVisible() || e.isShiftDown()) {
            toScroll = horizontalScrollBar;
            if (toScroll == null || !toScroll.isVisible()) {
                return;
            }
            orientation = SwingConstants.HORIZONTAL;
        }

        e.consume();

        if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
            if (viewport == null || viewport.getView() == null) {
                return;
            }
            int units = Math.abs(e.getScrollAmount() * e.getWheelRotation());
            // One wheel notch must not jump further than a page; a fast-spinning wheel
            // still moves by the full unit count (JDK behaviour).
            boolean limitScroll = Math.abs(e.getWheelRotation()) == 1;
            BasicScrollBarUI.scrollByUnits(toScroll, direction, units, limitScroll);
        } else if (e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
            BasicScrollBarUI.scrollByBlock(toScroll, direction);
        }
    }

    // ------------------------------------------------------------ synchronising

    /** A wheel over the viewport (or over a view that does not bubble) reaches the pane. */
    private class ViewportWheelListener implements MouseWheelListener {

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (!e.isConsumed()) {
                handleMouseWheel(e);
            }
        }
    }

    /** A view position or extent change means both scroll bar models need re-deriving. */
    private class ViewportChangeListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            syncScrollPaneWithViewport();
        }
    }

    /** A scroll bar model change moves the viewport's view position. */
    private class BarModelListener implements ChangeListener {

        private final boolean vertical;

        BarModelListener(boolean vertical) {
            this.vertical = vertical;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            Object source = e.getSource();
            if (!(source instanceof BoundedRangeModel) || viewport == null) {
                return;
            }
            BoundedRangeModel model = (BoundedRangeModel) source;
            Point p = viewport.getViewPosition();
            if (vertical) {
                p.y = model.getValue();
            } else {
                p.x = model.getValue();
            }
            viewport.setViewPosition(p);
        }
    }

    /**
     * Makes the scroll bars and the headers agree with the viewport: each bar's value is
     * the view position along its axis and its extent is the viewport's, so the thumb is
     * proportional. The JDK's right-to-left handling is dropped (see the class javadoc).
     */
    protected void syncScrollPaneWithViewport() {
        if (viewport == null) {
            return;
        }
        Dimension extentSize = viewport.getExtentSize();
        Dimension viewSize = viewport.getViewSize();
        Point viewPosition = viewport.getViewPosition();

        if (verticalScrollBar != null) {
            int extent = extentSize.height;
            int max = viewSize.height;
            int value = Math.max(0, Math.min(viewPosition.y, max - extent));
            verticalScrollBar.setValues(value, extent, 0, max);
        }
        if (horizontalScrollBar != null) {
            int extent = extentSize.width;
            int max = viewSize.width;
            int value = Math.max(0, Math.min(viewPosition.x, max - extent));
            horizontalScrollBar.setValues(value, extent, 0, max);
        }
        if (rowHeader != null) {
            Point p = rowHeader.getViewPosition();
            p.y = viewPosition.y;
            p.x = 0;
            rowHeader.setViewPosition(p);
        }
        if (columnHeader != null) {
            Point p = columnHeader.getViewPosition();
            p.x = viewPosition.x;
            p.y = 0;
            columnHeader.setViewPosition(p);
        }
    }

    /**
     * The JDK's scroll pane scroll bar: a scroll bar whose unit and block increments come
     * from the view when it is a {@link Scrollable} (per-item scrolling for a virtual list)
     * and from the viewport otherwise. The default is 16 px per unit — RuneLite's scroll
     * speed, which its own look and feel installs on every scroll bar.
     */
    protected class ScrollBar extends JScrollBar {

        /** Creates a scroll bar of the given orientation. */
        public ScrollBar(int orientation) {
            super(orientation);
            setUnitIncrement(16);
        }

        /**
         * The view's unit increment for the given direction, or 16 px.
         *
         * <p>Deviation: the JDK stops asking the view once {@code setUnitIncrement} has
         * been called explicitly; the port always prefers the view's answer, which is what
         * the plan specifies.
         */
        @Override
        public int getUnitIncrement(int direction) {
            JViewport vp = getViewport();
            if (vp != null && vp.getView() instanceof Scrollable) {
                Scrollable view = (Scrollable) vp.getView();
                return view.getScrollableUnitIncrement(vp.getViewRect(), getOrientation(), direction);
            }
            return super.getUnitIncrement(direction);
        }

        /**
         * The view's block increment, or one viewport page when the view is not
         * {@link Scrollable} (the viewport's extent along this bar's axis).
         */
        @Override
        public int getBlockIncrement(int direction) {
            JViewport vp = getViewport();
            if (vp == null) {
                return super.getBlockIncrement(direction);
            }
            if (vp.getView() instanceof Scrollable) {
                Scrollable view = (Scrollable) vp.getView();
                return view.getScrollableBlockIncrement(vp.getViewRect(), getOrientation(), direction);
            }
            Dimension extent = vp.getExtentSize();
            return getOrientation() == VERTICAL ? extent.height : extent.width;
        }
    }

    // ------------------------------------------------------------------ layout

    /**
     * The JDK's {@code ScrollPaneLayout} algorithm inlined: it places the viewport, the two
     * scroll bars, the headers and the corners, and decides whether a scroll bar is needed
     * by comparing the view's preferred size with the space left for the viewport (honouring
     * {@code Scrollable.getScrollableTracksViewport*} which switches a scroll bar off).
     */
    private class Layout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
            // The children are found through the scroll pane's fields, not by name.
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return layoutSize(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return layoutSize(parent, true);
        }

        /**
         * @param minimum true to measure with the children's minimum sizes (the JDK's
         *                {@code minimumLayoutSize}), false for their preferred sizes
         */
        private Dimension layoutSize(Container parent, boolean minimum) {
            Insets insets = parent.getInsets();
            int width = insets.left + insets.right;
            int height = insets.top + insets.bottom;

            Dimension extentSize = null;
            Dimension viewSize = null;
            Component view = null;

            if (viewport != null) {
                extentSize = minimum ? viewport.getMinimumSize() : viewport.getPreferredSize();
                view = viewport.getView();
                if (view != null) {
                    viewSize = minimum ? view.getMinimumSize() : view.getPreferredSize();
                } else {
                    viewSize = new Dimension(0, 0);
                }
            }
            if (extentSize != null) {
                width += extentSize.width;
                height += extentSize.height;
            }

            Border border = viewportBorder;
            if (border != null) {
                Insets vpbInsets = border.getBorderInsets(parent);
                width += vpbInsets.left + vpbInsets.right;
                height += vpbInsets.top + vpbInsets.bottom;
            }

            if (rowHeader != null && rowHeader.isVisible()) {
                Dimension d = minimum ? rowHeader.getMinimumSize() : rowHeader.getPreferredSize();
                width += d.width;
            }
            if (columnHeader != null && columnHeader.isVisible()) {
                Dimension d = minimum ? columnHeader.getMinimumSize() : columnHeader.getPreferredSize();
                height += d.height;
            }

            if (verticalScrollBar != null && verticalScrollBarPolicy != VERTICAL_SCROLLBAR_NEVER) {
                if (verticalScrollBarPolicy == VERTICAL_SCROLLBAR_ALWAYS) {
                    width += barSize(verticalScrollBar, minimum).width;
                } else if (viewSize != null && extentSize != null) {
                    boolean canScroll = !(view instanceof Scrollable)
                            || !((Scrollable) view).getScrollableTracksViewportHeight();
                    if (canScroll && viewSize.height > extentSize.height) {
                        width += barSize(verticalScrollBar, minimum).width;
                    }
                }
            }

            if (horizontalScrollBar != null && horizontalScrollBarPolicy != HORIZONTAL_SCROLLBAR_NEVER) {
                if (horizontalScrollBarPolicy == HORIZONTAL_SCROLLBAR_ALWAYS) {
                    height += barSize(horizontalScrollBar, minimum).height;
                } else if (viewSize != null && extentSize != null) {
                    boolean canScroll = !(view instanceof Scrollable)
                            || !((Scrollable) view).getScrollableTracksViewportWidth();
                    if (canScroll && viewSize.width > extentSize.width) {
                        height += barSize(horizontalScrollBar, minimum).height;
                    }
                }
            }

            return new Dimension(width, height);
        }

        private Dimension barSize(JScrollBar bar, boolean minimum) {
            return minimum ? bar.getMinimumSize() : bar.getPreferredSize();
        }

        @Override
        public void layoutContainer(Container parent) {
            Rectangle availR = parent.getBounds();
            Insets insets = parent.getInsets();
            availR.x = insets.left;
            availR.y = insets.top;
            availR.width -= insets.left + insets.right;
            availR.height -= insets.top + insets.bottom;

            // The column header takes the top, the row header the leading side.
            Rectangle colHeadR = new Rectangle(0, availR.y, 0, 0);
            if (columnHeader != null && columnHeader.isVisible()) {
                int colHeadHeight = Math.min(availR.height, columnHeader.getPreferredSize().height);
                colHeadR.height = colHeadHeight;
                availR.y += colHeadHeight;
                availR.height -= colHeadHeight;
            }

            Rectangle rowHeadR = new Rectangle(0, 0, 0, 0);
            if (rowHeader != null && rowHeader.isVisible()) {
                int rowHeadWidth = Math.min(availR.width, rowHeader.getPreferredSize().width);
                rowHeadR.width = rowHeadWidth;
                availR.width -= rowHeadWidth;
                rowHeadR.x = availR.x;
                availR.x += rowHeadWidth;
            }

            Border border = viewportBorder;
            Insets vpbInsets;
            if (border != null) {
                vpbInsets = border.getBorderInsets(parent);
                availR.x += vpbInsets.left;
                availR.y += vpbInsets.top;
                availR.width -= vpbInsets.left + vpbInsets.right;
                availR.height -= vpbInsets.top + vpbInsets.bottom;
            } else {
                vpbInsets = new Insets(0, 0, 0, 0);
            }

            Component view = viewport != null ? viewport.getView() : null;
            Dimension viewPrefSize = view != null ? view.getPreferredSize() : new Dimension(0, 0);
            Dimension extentSize = viewport != null
                    ? viewport.toViewCoordinates(availR.getSize())
                    : new Dimension(0, 0);

            boolean viewTracksViewportWidth = false;
            boolean viewTracksViewportHeight = false;
            boolean isEmpty = availR.width < 0 || availR.height < 0;
            Scrollable sv = null;
            if (!isEmpty && view instanceof Scrollable) {
                sv = (Scrollable) view;
                viewTracksViewportWidth = sv.getScrollableTracksViewportWidth();
                viewTracksViewportHeight = sv.getScrollableTracksViewportHeight();
            }

            Rectangle vsbR = new Rectangle(0, availR.y - vpbInsets.top, 0, 0);
            boolean vsbNeeded;
            if (isEmpty) {
                vsbNeeded = false;
            } else if (verticalScrollBarPolicy == VERTICAL_SCROLLBAR_ALWAYS) {
                vsbNeeded = true;
            } else if (verticalScrollBarPolicy == VERTICAL_SCROLLBAR_NEVER) {
                vsbNeeded = false;
            } else {
                vsbNeeded = !viewTracksViewportHeight && viewPrefSize.height > extentSize.height;
            }

            if (verticalScrollBar != null && vsbNeeded) {
                adjustForVSB(true, availR, vsbR, vpbInsets);
                extentSize = viewport.toViewCoordinates(availR.getSize());
            }

            Rectangle hsbR = new Rectangle(availR.x - vpbInsets.left, 0, 0, 0);
            boolean hsbNeeded;
            if (isEmpty) {
                hsbNeeded = false;
            } else if (horizontalScrollBarPolicy == HORIZONTAL_SCROLLBAR_ALWAYS) {
                hsbNeeded = true;
            } else if (horizontalScrollBarPolicy == HORIZONTAL_SCROLLBAR_NEVER) {
                hsbNeeded = false;
            } else {
                hsbNeeded = !viewTracksViewportWidth && viewPrefSize.width > extentSize.width;
            }

            if (horizontalScrollBar != null && hsbNeeded) {
                adjustForHSB(true, availR, hsbR, vpbInsets);

                // Adding the horizontal bar took height away from the viewport, which may
                // mean the vertical bar is needed after all.
                if (verticalScrollBar != null && !vsbNeeded
                        && verticalScrollBarPolicy != VERTICAL_SCROLLBAR_NEVER) {
                    extentSize = viewport.toViewCoordinates(availR.getSize());
                    vsbNeeded = viewPrefSize.height > extentSize.height;
                    if (vsbNeeded) {
                        adjustForVSB(true, availR, vsbR, vpbInsets);
                    }
                }
            }

            // Size the viewport first, then ask the view again: a Scrollable usually answers
            // from the viewport's size.
            if (viewport != null) {
                viewport.setBounds(availR);

                if (sv != null) {
                    extentSize = viewport.toViewCoordinates(availR.getSize());

                    boolean oldHSBNeeded = hsbNeeded;
                    boolean oldVSBNeeded = vsbNeeded;
                    viewTracksViewportWidth = sv.getScrollableTracksViewportWidth();
                    viewTracksViewportHeight = sv.getScrollableTracksViewportHeight();

                    if (verticalScrollBar != null && verticalScrollBarPolicy == VERTICAL_SCROLLBAR_AS_NEEDED) {
                        boolean newVSBNeeded = !viewTracksViewportHeight
                                && viewPrefSize.height > extentSize.height;
                        if (newVSBNeeded != vsbNeeded) {
                            vsbNeeded = newVSBNeeded;
                            adjustForVSB(vsbNeeded, availR, vsbR, vpbInsets);
                            extentSize = viewport.toViewCoordinates(availR.getSize());
                        }
                    }
                    if (horizontalScrollBar != null && horizontalScrollBarPolicy == HORIZONTAL_SCROLLBAR_AS_NEEDED) {
                        boolean newHSBNeeded = !viewTracksViewportWidth
                                && viewPrefSize.width > extentSize.width;
                        if (newHSBNeeded != hsbNeeded) {
                            hsbNeeded = newHSBNeeded;
                            adjustForHSB(hsbNeeded, availR, hsbR, vpbInsets);
                            if (verticalScrollBar != null && !vsbNeeded
                                    && verticalScrollBarPolicy != VERTICAL_SCROLLBAR_NEVER) {
                                extentSize = viewport.toViewCoordinates(availR.getSize());
                                vsbNeeded = viewPrefSize.height > extentSize.height;
                                if (vsbNeeded) {
                                    adjustForVSB(true, availR, vsbR, vpbInsets);
                                }
                            }
                        }
                    }
                    if (oldHSBNeeded != hsbNeeded || oldVSBNeeded != vsbNeeded) {
                        viewport.setBounds(availR);
                    }
                }
            }

            // The viewport is final now; give the bars and headers their full length.
            vsbR.height = availR.height + vpbInsets.top + vpbInsets.bottom;
            hsbR.width = availR.width + vpbInsets.left + vpbInsets.right;
            rowHeadR.height = availR.height + vpbInsets.top + vpbInsets.bottom;
            rowHeadR.y = availR.y - vpbInsets.top;
            colHeadR.width = availR.width + vpbInsets.left + vpbInsets.right;
            colHeadR.x = availR.x - vpbInsets.left;

            if (rowHeader != null) {
                rowHeader.setBounds(rowHeadR);
            }
            if (columnHeader != null) {
                columnHeader.setBounds(colHeadR);
            }

            if (verticalScrollBar != null) {
                verticalScrollBar.setVisible(vsbNeeded);
                if (vsbNeeded) {
                    verticalScrollBar.setBounds(vsbR);
                }
            }
            if (horizontalScrollBar != null) {
                horizontalScrollBar.setVisible(hsbNeeded);
                if (hsbNeeded) {
                    horizontalScrollBar.setBounds(hsbR);
                }
            }

            if (lowerLeft != null) {
                lowerLeft.setBounds(rowHeadR.x, hsbR.y, rowHeadR.width, hsbR.height);
            }
            if (lowerRight != null) {
                lowerRight.setBounds(vsbR.x, hsbR.y, vsbR.width, hsbR.height);
            }
            if (upperLeft != null) {
                upperLeft.setBounds(rowHeadR.x, colHeadR.y, rowHeadR.width, colHeadR.height);
            }
            if (upperRight != null) {
                upperRight.setBounds(vsbR.x, colHeadR.y, vsbR.width, colHeadR.height);
            }

            // The viewport's extent and view size are final now, so this is the point where
            // the scroll bar models can be derived from them. Doing it here (rather than
            // only from the viewport's change events) is what makes the very first layout
            // produce proportional thumbs.
            syncScrollPaneWithViewport();
        }

        /** Takes the vertical bar's width off {@code available} and positions it. */
        private void adjustForVSB(boolean wantsVSB, Rectangle available, Rectangle vsbR, Insets vpbInsets) {
            int oldWidth = vsbR.width;
            if (wantsVSB) {
                int vsbWidth = Math.max(0, Math.min(verticalScrollBar.getPreferredSize().width,
                        available.width));
                available.width -= vsbWidth;
                vsbR.width = vsbWidth;
                vsbR.x = available.x + available.width + vpbInsets.right;
            } else {
                available.width += oldWidth;
            }
        }

        /** Takes the horizontal bar's height off {@code available} and positions it. */
        private void adjustForHSB(boolean wantsHSB, Rectangle available, Rectangle hsbR, Insets vpbInsets) {
            int oldHeight = hsbR.height;
            if (wantsHSB) {
                int hsbHeight = Math.max(0, Math.min(available.height,
                        horizontalScrollBar.getPreferredSize().height));
                available.height -= hsbHeight;
                hsbR.y = available.y + available.height + vpbInsets.bottom;
                hsbR.height = hsbHeight;
            } else {
                available.height += oldHeight;
            }
        }
    }
}

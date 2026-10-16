package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import javax.swing.BoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ScrollBarUI;

/**
 * Swing {@code BasicScrollBarUI}: the look and feel for a {@link JScrollBar} on this port
 * — a slim track with a proportional thumb, the RuneLite sidebar's scroll bar.
 *
 * <p>Hand-written rather than generated: the sidebar's plugin panels scroll, so a scroll
 * bar has to be laid out, painted and hit-tested for real, and the RuneLite look and feel
 * subclasses this class for its own scroll bars. The layout maths, the thumb drag/block
 * page behaviour and the {@code scrollByUnit(s)}/{@code scrollByBlock} helpers (which the
 * port's {@link javax.swing.JScrollPane} wheel handler calls) are the JDK's.
 *
 * <p>Port deviations, all noted at the point of use:
 * <ul>
 *   <li>{@link #createDecreaseButton}/{@link #createIncreaseButton} return a 0x0 button:
 *       RuneLite's scroll bars have no arrow buttons (its FlatLaf look is track + thumb
 *       only). The members keep the JDK's signatures and the layout keeps the JDK's button
 *       arithmetic, so a look and feel that overrides them with a real button still works.</li>
 *   <li>The thumb is painted as one solid {@code ScrollBar.thumb} rectangle. The JDK's
 *       bevel would draw {@code ScrollBar.thumbDarkShadow}/{@code thumbHighlight}/…,
 *       which this theme seeds to {@code ScrollBar.track}, leaving a track-coloured line
 *       along the thumb's right and bottom edge — a rendering artefact on a 7 px bar.</li>
 *   <li>{@code ComponentOrientation} does not exist on this port: every bar is laid out
 *       left-to-right.</li>
 *   <li>No keyboard bindings and no thumb rollover: this is a touch UI and the port has no
 *       {@code LazyActionMap}/{@code UIAction}, and its scroll bars never take the focus.</li>
 *   <li>{@code paint} finishes with {@code c.paintComponents}: this port's
 *       {@code JComponent.paint} hands a component with an installed UI straight to the
 *       delegate instead of running {@code paintChildren} itself (the JDK always runs it),
 *       so the UI is the one that paints the arrow buttons.</li>
 * </ul>
 */
public class BasicScrollBarUI extends ScrollBarUI implements LayoutManager, SwingConstants {

    /** The scroll timer's repeat delay in milliseconds (JDK value). */
    private static final int SCROLL_SPEED_THROTTLE = 60;

    /** The smallest acceptable thumb; the thumb is hidden when the track is smaller. */
    protected Dimension minimumThumbSize;

    /** The largest acceptable thumb. */
    protected Dimension maximumThumbSize;

    protected Color thumbHighlightColor;
    protected Color thumbLightShadowColor;
    protected Color thumbDarkShadowColor;
    protected Color thumbColor;
    protected Color trackColor;
    protected Color trackHighlightColor;

    /** The scroll bar this delegate is installed on. */
    protected JScrollBar scrollbar;
    protected JButton incrButton;
    protected JButton decrButton;
    protected boolean isDragging;
    protected TrackListener trackListener;
    protected ArrowButtonListener buttonListener;
    protected ModelListener modelListener;
    protected Rectangle thumbRect;
    protected Rectangle trackRect;

    protected int trackHighlight;

    protected static final int NO_HIGHLIGHT = 0;
    protected static final int DECREASE_HIGHLIGHT = 1;
    protected static final int INCREASE_HIGHLIGHT = 2;

    protected ScrollListener scrollListener;
    protected Timer scrollTimer;

    /** The bar's width when vertical (its height when horizontal): {@code ScrollBar.width}. */
    protected int scrollBarWidth;

    /** Distance between the increment button and the track (may be negative when shaped). */
    protected int incrGap;

    /** Distance between the decrement button and the track (may be negative when shaped). */
    protected int decrGap;

    private boolean supportsAbsolutePositioning;

    /** Created by {@code UIManager.getUI} through reflection. */
    public static ComponentUI createUI(JComponent c) {
        return new BasicScrollBarUI();
    }

    @Override
    public void installUI(JComponent c) {
        scrollbar = (JScrollBar) c;
        thumbRect = new Rectangle(0, 0, 0, 0);
        trackRect = new Rectangle(0, 0, 0, 0);
        installDefaults();
        installComponents();
        installListeners();
    }

    @Override
    public void uninstallUI(JComponent c) {
        uninstallListeners();
        uninstallDefaults();
        uninstallComponents();
        thumbRect = null;
        trackRect = null;
        scrollbar = null;
        incrButton = null;
        decrButton = null;
    }

    /** Reads the theme's scroll bar keys and installs the layout manager. */
    protected void installDefaults() {
        scrollBarWidth = UIManager.getInt("ScrollBar.width");
        if (scrollBarWidth <= 0) {
            scrollBarWidth = 16;
        }
        minimumThumbSize = dimension("ScrollBar.minimumThumbSize", new Dimension(scrollBarWidth, scrollBarWidth));
        maximumThumbSize = dimension("ScrollBar.maximumThumbSize",
                new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

        Boolean absB = asBoolean(UIManager.get("ScrollBar.allowsAbsolutePositioning"));
        supportsAbsolutePositioning = absB != null && absB;

        trackHighlight = NO_HIGHLIGHT;
        if (scrollbar.getLayout() == null) {
            scrollbar.setLayout(this);
        }
        configureScrollBarColors();
        scrollbar.setOpaque(true);
        // The theme's slim bar is 7 px wide; the JDK's default border would widen it.
        scrollbar.setBorder(null);

        incrGap = UIManager.getInt("ScrollBar.incrementButtonGap");
        decrGap = UIManager.getInt("ScrollBar.decrementButtonGap");
    }

    protected void uninstallDefaults() {
        scrollbar.setBorder(null);
        if (scrollbar.getLayout() == this) {
            scrollbar.setLayout(null);
        }
    }

    protected void configureScrollBarColors() {
        thumbHighlightColor = color("ScrollBar.thumbHighlight", new Color(25, 25, 25));
        thumbLightShadowColor = color("ScrollBar.thumbShadow", new Color(25, 25, 25));
        thumbDarkShadowColor = color("ScrollBar.thumbDarkShadow", new Color(25, 25, 25));
        thumbColor = color("ScrollBar.thumb", new Color(77, 77, 77));
        trackColor = color("ScrollBar.track", new Color(25, 25, 25));
        trackHighlightColor = color("ScrollBar.trackHighlight", trackColor);

        Color background = color("ScrollBar.background", null);
        if (background != null && scrollbar.getBackground() == null) {
            scrollbar.setBackground(background);
        }
    }

    private static Color color(Object key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value != null ? value : fallback;
    }

    private static Dimension dimension(Object key, Dimension fallback) {
        Object value = UIManager.get(key);
        return value instanceof Dimension ? (Dimension) value : fallback;
    }

    private static Boolean asBoolean(Object value) {
        return value instanceof Boolean ? (Boolean) value : null;
    }

    protected void installComponents() {
        switch (scrollbar.getOrientation()) {
            case JScrollBar.VERTICAL:
                incrButton = createIncreaseButton(SOUTH);
                decrButton = createDecreaseButton(NORTH);
                break;
            case JScrollBar.HORIZONTAL:
            default:
                incrButton = createIncreaseButton(EAST);
                decrButton = createDecreaseButton(WEST);
                break;
        }
        scrollbar.add(incrButton);
        scrollbar.add(decrButton);
        // Force the children's enabled state to be updated.
        scrollbar.setEnabled(scrollbar.isEnabled());
    }

    protected void uninstallComponents() {
        if (incrButton != null) {
            scrollbar.remove(incrButton);
        }
        if (decrButton != null) {
            scrollbar.remove(decrButton);
        }
    }

    protected void installListeners() {
        trackListener = createTrackListener();
        buttonListener = createArrowButtonListener();
        modelListener = createModelListener();

        scrollbar.addMouseListener(trackListener);
        scrollbar.addMouseMotionListener(trackListener);
        scrollbar.getModel().addChangeListener(modelListener);

        if (incrButton != null) {
            incrButton.addMouseListener(buttonListener);
        }
        if (decrButton != null) {
            decrButton.addMouseListener(buttonListener);
        }

        scrollListener = createScrollListener();
        scrollTimer = new Timer(SCROLL_SPEED_THROTTLE, scrollListener);
        scrollTimer.setInitialDelay(300);
    }

    protected void uninstallListeners() {
        scrollbar.removeMouseListener(trackListener);
        scrollbar.removeMouseMotionListener(trackListener);
        BoundedRangeModel model = scrollbar.getModel();
        if (model != null) {
            model.removeChangeListener(modelListener);
        }
        if (incrButton != null) {
            incrButton.removeMouseListener(buttonListener);
        }
        if (decrButton != null) {
            decrButton.removeMouseListener(buttonListener);
        }
        if (scrollTimer != null) {
            scrollTimer.stop();
        }
        scrollTimer = null;
        trackListener = null;
        buttonListener = null;
        modelListener = null;
        scrollListener = null;
    }

    protected TrackListener createTrackListener() {
        return new TrackListener();
    }

    protected ArrowButtonListener createArrowButtonListener() {
        return new ArrowButtonListener();
    }

    protected ModelListener createModelListener() {
        return new ModelListener();
    }

    protected ScrollListener createScrollListener() {
        return new ScrollListener();
    }

    // ------------------------------------------------------------------ paint

    @Override
    public void paint(Graphics g, JComponent c) {
        paintTrack(g, c, getTrackBounds());
        Rectangle thumbBounds = getThumbBounds();
        if (thumbBounds != null && !thumbBounds.isEmpty()) {
            paintThumb(g, c, thumbBounds);
        }
        // See the class javadoc: with a UI installed this port's JComponent.paint does not
        // paint the children itself, so the delegate has to.
        c.paintComponents(g);
    }

    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        if (trackBounds == null || trackBounds.isEmpty()) {
            return;
        }
        g.setColor(trackColor);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);

        if (trackHighlight == DECREASE_HIGHLIGHT) {
            paintDecreaseHighlight(g);
        } else if (trackHighlight == INCREASE_HIGHLIGHT) {
            paintIncreaseHighlight(g);
        }
    }

    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        // Deviation: one solid rectangle instead of the JDK's bevel — see the class javadoc.
        g.setColor(thumbColor);
        g.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);
    }

    /** Paints the track between the start of the track and the top/left of the thumb. */
    protected void paintDecreaseHighlight(Graphics g) {
        Insets insets = scrollbar.getInsets();
        Rectangle thumbR = getThumbBounds();
        g.setColor(trackHighlightColor);

        if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            int x = insets.left;
            int y = trackRect.y;
            int w = scrollbar.getWidth() - (insets.left + insets.right);
            int h = thumbR.y - y;
            g.fillRect(x, y, w, h);
        } else {
            int x = trackRect.x;
            int w = thumbR.x - x;
            int y = insets.top;
            int h = scrollbar.getHeight() - (insets.top + insets.bottom);
            g.fillRect(x, y, w, h);
        }
    }

    /** Paints the track between the bottom/right of the thumb and the end of the track. */
    protected void paintIncreaseHighlight(Graphics g) {
        Insets insets = scrollbar.getInsets();
        Rectangle thumbR = getThumbBounds();
        g.setColor(trackHighlightColor);

        if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            int x = insets.left;
            int y = thumbR.y + thumbR.height;
            int w = scrollbar.getWidth() - (insets.left + insets.right);
            int h = trackRect.y + trackRect.height - y;
            g.fillRect(x, y, w, h);
        } else {
            int x = thumbR.x + thumbR.width;
            int w = trackRect.x + trackRect.width - x;
            int y = insets.top;
            int h = scrollbar.getHeight() - (insets.top + insets.bottom);
            g.fillRect(x, y, w, h);
        }
    }

    /**
     * A vertical bar is {@code ScrollBar.width} wide and 48 px long (the JDK's minimum
     * usable length; a scroll pane squeezes the bar into whatever length the layout has).
     */
    @Override
    public Dimension getPreferredSize(JComponent c) {
        return (scrollbar.getOrientation() == JScrollBar.VERTICAL)
                ? new Dimension(scrollBarWidth, 48)
                : new Dimension(48, scrollBarWidth);
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * The port's arrow buttons: RuneLite's scroll bars have none, so they report a 0x0
     * preferred size, which makes the JDK's layout give them empty bounds and hand the
     * whole bar to the track.
     */
    protected JButton createDecreaseButton(int orientation) {
        return new NoSizeButton();
    }

    /** See {@link #createDecreaseButton}. */
    protected JButton createIncreaseButton(int orientation) {
        return new NoSizeButton();
    }

    /** A button that takes no space and never paints; see {@link #createDecreaseButton}. */
    private static class NoSizeButton extends JButton {

        NoSizeButton() {
            super();
            setFocusable(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(0, 0);
        }
    }

    protected Dimension getMinimumThumbSize() {
        return minimumThumbSize;
    }

    protected Dimension getMaximumThumbSize() {
        return maximumThumbSize;
    }

    /** Whether a middle-drag may position the thumb absolutely (theme key, default false). */
    public boolean getSupportsAbsolutePositioning() {
        return supportsAbsolutePositioning;
    }

    // -------------------------------------------------------------- layout

    @Override
    public void addLayoutComponent(String name, Component child) {
    }

    @Override
    public void removeLayoutComponent(Component child) {
    }

    @Override
    public Dimension preferredLayoutSize(Container scrollbarContainer) {
        return getPreferredSize((JComponent) scrollbarContainer);
    }

    @Override
    public Dimension minimumLayoutSize(Container scrollbarContainer) {
        return getPreferredSize((JComponent) scrollbarContainer);
    }

    @Override
    public void layoutContainer(Container scrollbarContainer) {
        // While the user drags the thumb, the drag code owns the thumb's position: a
        // relayout would snap it back to the model's (still coarse) value.
        if (isDragging) {
            return;
        }
        JScrollBar bar = (JScrollBar) scrollbarContainer;
        switch (bar.getOrientation()) {
            case JScrollBar.VERTICAL:
                layoutVScrollbar(bar);
                break;
            case JScrollBar.HORIZONTAL:
            default:
                layoutHScrollbar(bar);
                break;
        }
    }

    /** The JDK's vertical layout: buttons at the ends, track in between, proportional thumb. */
    protected void layoutVScrollbar(JScrollBar sb) {
        Dimension sbSize = sb.getSize();
        Insets sbInsets = sb.getInsets();

        int itemW = sbSize.width - (sbInsets.left + sbInsets.right);
        int itemX = sbInsets.left;

        int decrButtonH = decrButton == null ? 0 : decrButton.getPreferredSize().height;
        int decrButtonY = sbInsets.top;

        int incrButtonH = incrButton == null ? 0 : incrButton.getPreferredSize().height;
        int incrButtonY = sbSize.height - (sbInsets.bottom + incrButtonH);

        int sbInsetsH = sbInsets.top + sbInsets.bottom;
        int sbButtonsH = decrButtonH + incrButtonH;
        int gaps = decrGap + incrGap;
        float trackH = sbSize.height - (sbInsetsH + sbButtonsH) - gaps;

        float min = sb.getMinimum();
        float extent = sb.getVisibleAmount();
        float range = sb.getMaximum() - min;
        float value = getValue(sb);

        int thumbH = (range <= 0)
                ? getMaximumThumbSize().height
                : (int) (trackH * (extent / range));
        thumbH = Math.max(thumbH, getMinimumThumbSize().height);
        thumbH = Math.min(thumbH, getMaximumThumbSize().height);

        int thumbY = incrButtonY - incrGap - thumbH;
        if (value < (sb.getMaximum() - sb.getVisibleAmount())) {
            float thumbRange = trackH - thumbH;
            thumbY = (int) (0.5f + (thumbRange * ((value - min) / (range - extent))));
            thumbY += decrButtonY + decrButtonH + decrGap;
        }

        int trackX = itemX;
        int trackW = itemW;

        // Buttons are expected to fit; if they do not, they share the space.
        int sbAvailButtonH = (sbSize.height - sbInsetsH);
        if (sbAvailButtonH < sbButtonsH) {
            incrButtonH = decrButtonH = sbAvailButtonH / 2;
            incrButtonY = sbSize.height - (sbInsets.bottom + incrButtonH);
        }
        if (decrButton != null) {
            decrButton.setBounds(itemX, decrButtonY, itemW, decrButtonH);
        }
        if (incrButton != null) {
            incrButton.setBounds(itemX, incrButtonY, itemW, incrButtonH);
        }

        int itrackY = decrButtonY + decrButtonH + decrGap;
        int itrackH = incrButtonY - incrGap - itrackY;
        trackRect.setBounds(trackX, itrackY, trackW, itrackH);

        if (thumbH >= (int) trackH) {
            if (UIManager.getBoolean("ScrollBar.alwaysShowThumb")) {
                setThumbBounds(itemX, itrackY, itemW, itrackH);
            } else {
                setThumbBounds(0, 0, 0, 0);
            }
        } else {
            if ((thumbY + thumbH) > incrButtonY - incrGap) {
                thumbY = incrButtonY - incrGap - thumbH;
            }
            if (thumbY < (decrButtonY + decrButtonH + decrGap)) {
                thumbY = decrButtonY + decrButtonH + decrGap + 1;
            }
            setThumbBounds(itemX, thumbY, itemW, thumbH);
        }
    }

    /** The JDK's horizontal layout; the port is left-to-right only. */
    protected void layoutHScrollbar(JScrollBar sb) {
        Dimension sbSize = sb.getSize();
        Insets sbInsets = sb.getInsets();

        int itemH = sbSize.height - (sbInsets.top + sbInsets.bottom);
        int itemY = sbInsets.top;

        int leftButtonW = decrButton == null ? 0 : decrButton.getPreferredSize().width;
        int rightButtonW = incrButton == null ? 0 : incrButton.getPreferredSize().width;
        int leftButtonX = sbInsets.left;
        int rightButtonX = sbSize.width - (sbInsets.right + rightButtonW);

        int sbInsetsW = sbInsets.left + sbInsets.right;
        int sbButtonsW = leftButtonW + rightButtonW;
        float trackW = sbSize.width - (sbInsetsW + sbButtonsW) - (decrGap + incrGap);

        float min = sb.getMinimum();
        float max = sb.getMaximum();
        float extent = sb.getVisibleAmount();
        float range = max - min;
        float value = getValue(sb);

        int thumbW = (range <= 0)
                ? getMaximumThumbSize().width
                : (int) (trackW * (extent / range));
        thumbW = Math.max(thumbW, getMinimumThumbSize().width);
        thumbW = Math.min(thumbW, getMaximumThumbSize().width);

        int thumbX = rightButtonX - incrGap - thumbW;
        if (value < (max - sb.getVisibleAmount())) {
            float thumbRange = trackW - thumbW;
            thumbX = (int) (0.5f + (thumbRange * ((value - min) / (range - extent))));
            thumbX += leftButtonX + leftButtonW + decrGap;
        }

        int sbAvailButtonW = (sbSize.width - sbInsetsW);
        if (sbAvailButtonW < sbButtonsW) {
            rightButtonW = leftButtonW = sbAvailButtonW / 2;
            rightButtonX = sbSize.width - (sbInsets.right + rightButtonW + incrGap);
        }

        if (decrButton != null) {
            decrButton.setBounds(leftButtonX, itemY, leftButtonW, itemH);
        }
        if (incrButton != null) {
            incrButton.setBounds(rightButtonX, itemY, rightButtonW, itemH);
        }

        int itrackX = leftButtonX + leftButtonW + decrGap;
        int itrackW = rightButtonX - incrGap - itrackX;
        trackRect.setBounds(itrackX, itemY, itrackW, itemH);

        if (thumbW >= (int) trackW) {
            if (UIManager.getBoolean("ScrollBar.alwaysShowThumb")) {
                setThumbBounds(itrackX, itemY, itrackW, itemH);
            } else {
                setThumbBounds(0, 0, 0, 0);
            }
        } else {
            if (thumbX + thumbW > rightButtonX - incrGap) {
                thumbX = rightButtonX - incrGap - thumbW;
            }
            if (thumbX < leftButtonX + leftButtonW + decrGap) {
                thumbX = leftButtonX + leftButtonW + decrGap + 1;
            }
            setThumbBounds(thumbX, itemY, thumbW, itemH);
        }
    }

    /**
     * Sets the thumb's bounds and repaints the union of the old and the new bounds.
     * <p>Deviation: the JDK also resets the (mouse-hover) rollover state here; the port has
     * no rollover, so it only repaints.
     */
    protected void setThumbBounds(int x, int y, int width, int height) {
        if (thumbRect.x == x && thumbRect.y == y && thumbRect.width == width && thumbRect.height == height) {
            return;
        }
        int minX = Math.min(x, thumbRect.x);
        int minY = Math.min(y, thumbRect.y);
        int maxX = Math.max(x + width, thumbRect.x + thumbRect.width);
        int maxY = Math.max(y + height, thumbRect.y + thumbRect.height);

        thumbRect.setBounds(x, y, width, height);
        scrollbar.repaint(minX, minY, maxX - minX, maxY - minY);
    }

    /** The current thumb rectangle (the live instance, as in the JDK — do not modify it). */
    protected Rectangle getThumbBounds() {
        return thumbRect;
    }

    /** The current track rectangle (the live instance — do not modify it). */
    protected Rectangle getTrackBounds() {
        return trackRect;
    }

    /** The bar's value as the layout should see it (the model's value). */
    private int getValue(JScrollBar sb) {
        return sb.getValue();
    }

    // ------------------------------------------------------------- scrolling

    /**
     * Pages the bar by one block increment; used by this UI's track clicks and by
     * {@link javax.swing.JScrollPane}'s wheel handler.
     */
    public static void scrollByBlock(JScrollBar scrollbar, int direction) {
        int oldValue = scrollbar.getValue();
        int blockIncrement = scrollbar.getBlockIncrement(direction);
        int delta = blockIncrement * ((direction > 0) ? +1 : -1);
        int newValue = oldValue + delta;

        // Check for overflow.
        if (delta > 0 && newValue < oldValue) {
            newValue = scrollbar.getMaximum();
        } else if (delta < 0 && newValue > oldValue) {
            newValue = scrollbar.getMinimum();
        }

        scrollbar.setValue(newValue);
    }

    protected void scrollByBlock(int direction) {
        scrollByBlock(scrollbar, direction);
        trackHighlight = direction > 0 ? INCREASE_HIGHLIGHT : DECREASE_HIGHLIGHT;
        Rectangle dirtyRect = getTrackBounds();
        scrollbar.repaint(dirtyRect.x, dirtyRect.y, dirtyRect.width, dirtyRect.height);
    }

    /**
     * Scrolls the bar by {@code units} unit increments in {@code direction}.
     *
     * @param limitToBlock when true the total movement is clamped to one block increment
     *                     (so a single wheel notch cannot jump further than a page)
     */
    public static void scrollByUnits(JScrollBar scrollbar, int direction, int units, boolean limitToBlock) {
        int delta;
        int limit = -1;

        if (limitToBlock) {
            if (direction < 0) {
                limit = scrollbar.getValue() - scrollbar.getBlockIncrement(direction);
            } else {
                limit = scrollbar.getValue() + scrollbar.getBlockIncrement(direction);
            }
        }

        for (int i = 0; i < units; i++) {
            if (direction > 0) {
                delta = scrollbar.getUnitIncrement(direction);
            } else {
                delta = -scrollbar.getUnitIncrement(direction);
            }

            int oldValue = scrollbar.getValue();
            int newValue = oldValue + delta;

            // Check for overflow.
            if (delta > 0 && newValue < oldValue) {
                newValue = scrollbar.getMaximum();
            } else if (delta < 0 && newValue > oldValue) {
                newValue = scrollbar.getMinimum();
            }
            if (oldValue == newValue) {
                break;
            }

            if (limitToBlock && i > 0) {
                if ((direction < 0 && newValue < limit) || (direction > 0 && newValue > limit)) {
                    break;
                }
            }
            scrollbar.setValue(newValue);
        }
    }

    protected void scrollByUnit(int direction) {
        scrollByUnits(scrollbar, direction, 1, false);
    }

    // ------------------------------------------------------------- listeners

    /**
     * Repaints the bar when the model changes. The port's scroll bar is laid out from the
     * model on every {@code validateTree}, so a revalidate (coalesced by
     * {@code RepaintManager}) is what moves the thumb; the JDK instead caches the value
     * during a drag.
     */
    protected class ModelListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            scrollbar.revalidate();
            scrollbar.repaint();
        }
    }

    /**
     * The mouse behaviour the plan requires: press on the thumb and drag to move the value
     * proportionally, press on the track to page towards the pointer, and keep paging while
     * the track is held.
     */
    protected class TrackListener extends MouseAdapter implements MouseMotionListener {

        protected transient int offset;
        protected transient int currentMouseX;
        protected transient int currentMouseY;
        private transient int direction = +1;

        @Override
        public void mousePressed(MouseEvent e) {
            if (!scrollbar.isEnabled() || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            scrollbar.setValueIsAdjusting(true);
            currentMouseX = e.getX();
            currentMouseY = e.getY();

            if (getThumbBounds().contains(currentMouseX, currentMouseY)) {
                // Remember where inside the thumb the press landed: the drag keeps that
                // point under the finger (the JDK's proportional drag).
                offset = (scrollbar.getOrientation() == JScrollBar.VERTICAL)
                        ? currentMouseY - getThumbBounds().y
                        : currentMouseX - getThumbBounds().x;
                isDragging = true;
                return;
            }

            isDragging = false;
            direction = +1;

            Dimension sbSize = scrollbar.getSize();
            switch (scrollbar.getOrientation()) {
                case JScrollBar.VERTICAL:
                    if (getThumbBounds().isEmpty()) {
                        direction = (currentMouseY < sbSize.height / 2) ? -1 : +1;
                    } else {
                        direction = (currentMouseY < getThumbBounds().y) ? -1 : +1;
                    }
                    break;
                case JScrollBar.HORIZONTAL:
                default:
                    if (getThumbBounds().isEmpty()) {
                        direction = (currentMouseX < sbSize.width / 2) ? -1 : +1;
                    } else {
                        direction = (currentMouseX < getThumbBounds().x) ? -1 : +1;
                    }
                    break;
            }
            scrollByBlock(direction);

            scrollTimer.stop();
            scrollListener.setDirection(direction);
            scrollListener.setScrollByBlock(true);
            startScrollTimerIfNecessary();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!scrollbar.isEnabled() || getThumbBounds().isEmpty()) {
                return;
            }
            if (isDragging) {
                setValueFrom(e);
            } else {
                currentMouseX = e.getX();
                currentMouseY = e.getY();
                startScrollTimerIfNecessary();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!scrollbar.isEnabled()) {
                return;
            }
            if (isDragging) {
                isDragging = false;
                // Put the thumb back under the layout's control (layoutContainer skipped
                // while dragging).
                scrollbar.revalidate();
            }
            Rectangle r = getTrackBounds();
            scrollbar.repaint(r.x, r.y, r.width, r.height);

            trackHighlight = NO_HIGHLIGHT;
            offset = 0;
            scrollTimer.stop();
            scrollbar.setValueIsAdjusting(false);
        }

        /** Moves the thumb to the pointer and sets the value from the thumb's position. */
        private void setValueFrom(MouseEvent e) {
            BoundedRangeModel model = scrollbar.getModel();
            Rectangle thumbR = getThumbBounds();
            int thumbMin;
            int thumbMax;
            int thumbPos;

            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                thumbMin = trackRect.y;
                thumbMax = trackRect.y + trackRect.height - thumbR.height;
                thumbPos = Math.min(thumbMax, Math.max(thumbMin, e.getY() - offset));
                setThumbBounds(thumbR.x, thumbPos, thumbR.width, thumbR.height);
            } else {
                thumbMin = trackRect.x;
                thumbMax = trackRect.x + trackRect.width - thumbR.width;
                thumbPos = Math.min(thumbMax, Math.max(thumbMin, e.getX() - offset));
                setThumbBounds(thumbPos, thumbR.y, thumbR.width, thumbR.height);
            }

            if (thumbPos == thumbMax) {
                scrollbar.setValue(model.getMaximum() - model.getExtent());
                return;
            }

            float valueMax = model.getMaximum() - model.getExtent();
            float valueRange = valueMax - model.getMinimum();
            float thumbValue = thumbPos - thumbMin;
            float thumbRange = thumbMax - thumbMin;
            if (thumbRange <= 0) {
                // The thumb fills the track: there is nothing to position (the JDK cannot
                // reach this state because it hides a thumb that does not fit).
                return;
            }
            scrollbar.setValue((int) (0.5 + ((thumbValue / thumbRange) * valueRange)));
        }

        /**
         * Starts the repeat timer when a held track press points past the thumb, so the
         * value keeps paging until the pointer is caught up with.
         */
        private void startScrollTimerIfNecessary() {
            if (scrollTimer.isRunning()) {
                return;
            }
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                int thumbY = getThumbBounds().y;
                if ((direction > 0 && thumbY + getThumbBounds().height <= currentMouseY)
                        || (direction < 0 && thumbY >= currentMouseY)) {
                    scrollTimer.start();
                }
            } else {
                int thumbX = getThumbBounds().x;
                int thumbRight = thumbX + getThumbBounds().width;
                if ((direction > 0 && thumbRight <= currentMouseX)
                        || (direction < 0 && thumbX >= currentMouseX)) {
                    scrollTimer.start();
                }
            }
        }
    }

    /**
     * The arrow buttons' press handler. This port's buttons take no space, so it only runs
     * for a look and feel that installs real ones.
     */
    protected class ArrowButtonListener extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            if (!scrollbar.isEnabled() || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            int direction = (e.getSource() == incrButton) ? 1 : -1;

            scrollByUnit(direction);
            scrollTimer.stop();
            scrollListener.setDirection(direction);
            scrollListener.setScrollByBlock(false);
            scrollTimer.start();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            scrollTimer.stop();
            scrollbar.setValueIsAdjusting(false);
        }
    }

    /** The timer's action: repeats the unit or block scroll started by a press. */
    protected class ScrollListener implements ActionListener {

        private int direction = +1;
        private boolean useBlockIncrement;

        public ScrollListener() {
        }

        public ScrollListener(int dir, boolean block) {
            direction = dir;
            useBlockIncrement = block;
        }

        public void setDirection(int direction) {
            this.direction = direction;
        }

        public void setScrollByBlock(boolean block) {
            this.useBlockIncrement = block;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (useBlockIncrement) {
                scrollByBlock(direction);
                // Stop scrolling once the thumb has caught up with the pointer.
                if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                    Rectangle thumb = getThumbBounds();
                    if (direction > 0) {
                        if (thumb.y + thumb.height >= trackListener.currentMouseY) {
                            ((Timer) e.getSource()).stop();
                        }
                    } else if (thumb.y <= trackListener.currentMouseY) {
                        ((Timer) e.getSource()).stop();
                    }
                } else {
                    Rectangle thumb = getThumbBounds();
                    if (direction > 0 ? thumb.x + thumb.width >= trackListener.currentMouseX
                            : thumb.x <= trackListener.currentMouseX) {
                        ((Timer) e.getSource()).stop();
                    }
                }
            } else {
                scrollByUnit(direction);
            }

            if (direction > 0 && scrollbar.getValue() + scrollbar.getVisibleAmount() >= scrollbar.getMaximum()) {
                ((Timer) e.getSource()).stop();
            } else if (direction < 0 && scrollbar.getValue() <= scrollbar.getMinimum()) {
                ((Timer) e.getSource()).stop();
            }
        }
    }
}

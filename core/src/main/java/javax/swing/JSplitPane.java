package javax.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * Hand-written {@code javax.swing.JSplitPane}: two components with a draggable divider
 * between them.
 *
 * <p>The desktop class delegates every pixel to {@code BasicSplitPaneUI} (plus
 * one-touch expand buttons and a non-continuous drag overlay). This port keeps the parts
 * the client can actually see: orientation, the four component slots and their
 * {@code LEFT}/{@code RIGHT}/{@code TOP}/{@code BOTTOM} constraints, the divider
 * location (integer and proportional), the resize weight, the divider size, and dragging
 * the divider with the mouse — the pane itself listens, converts the event into its own
 * space and re-lays out. {@code setOneTouchExpandable} is stored only: the one-touch
 * arrows are not painted, and the layout always splits the space at the divider.
 *
 * <p>Splitter drags report {@code MOUSE_DRAGGED} to the component under the pointer,
 * which bubbles up to the pane (lightweight Swing bubbling); the coordinates are
 * converted back with {@link SwingUtilities#convertPoint(Component, int, int, Component)}
 * so the divider follows the pointer even when it is over a child.
 */
public class JSplitPane extends JComponent implements javax.accessibility.Accessible {

    public static final int HORIZONTAL_SPLIT = 1;
    public static final int VERTICAL_SPLIT = 0;

    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";

    /** Hit band around the divider, in logical pixels (the JDK's default divider size). */
    private static final int DEFAULT_DIVIDER_SIZE = 10;

    private int orientation;
    private Component leftComponent;
    private Component rightComponent;
    private int dividerSize = DEFAULT_DIVIDER_SIZE;
    private int dividerLocation = -1;
    private int lastDividerLocation = -1;
    private double resizeWeight;
    private boolean continuousLayout = true;
    private boolean oneTouchExpandable;

    /** The grab offset inside the divider while dragging, in orientation-axis pixels. */
    private int dragOffset;
    private boolean dragging;

    public JSplitPane() {
        this(HORIZONTAL_SPLIT, null, null);
    }

    public JSplitPane(int newOrientation) {
        this(newOrientation, null, null);
    }

    public JSplitPane(int newOrientation, boolean newContinuousLayout) {
        this(newOrientation, null, null);
        setContinuousLayout(newContinuousLayout);
    }

    public JSplitPane(int newOrientation, Component newLeftComponent, Component newRightComponent) {
        super();
        setOrientation(newOrientation);
        setLayout(null);
        setOpaque(true);
        setDividerSize(UIManager.getInt("SplitPane.dividerSize") > 0
                ? UIManager.getInt("SplitPane.dividerSize")
                : DEFAULT_DIVIDER_SIZE);
        setLeftComponent(newLeftComponent);
        setRightComponent(newRightComponent);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onDividerPressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                onDividerDragged(e);
            }
        });
    }

    public JSplitPane(int newOrientation, boolean newContinuousLayout,
                      Component newLeftComponent, Component newRightComponent) {
        this(newOrientation, newLeftComponent, newRightComponent);
        setContinuousLayout(newContinuousLayout);
    }

    // ------------------------------------------------------------ orientation/parts

    /** {@link #HORIZONTAL_SPLIT} or {@link #VERTICAL_SPLIT}. */
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL_SPLIT && orientation != VERTICAL_SPLIT) {
            throw new IllegalArgumentException("cannot set orientation to " + orientation);
        }
        if (this.orientation == orientation) {
            return;
        }
        this.orientation = orientation;
        revalidate();
        repaint();
    }

    public int getOrientation() {
        return orientation;
    }

    public void setLeftComponent(Component comp) {
        leftComponent = replace(leftComponent, comp);
    }

    public Component getLeftComponent() {
        return leftComponent;
    }

    public void setRightComponent(Component comp) {
        rightComponent = replace(rightComponent, comp);
    }

    public Component getRightComponent() {
        return rightComponent;
    }

    /** Top slot: the same component as the left one in a vertical split (JDK semantics). */
    public void setTopComponent(Component comp) {
        setLeftComponent(comp);
    }

    public Component getTopComponent() {
        return getLeftComponent();
    }

    /** Bottom slot: the same component as the right one in a vertical split. */
    public void setBottomComponent(Component comp) {
        setRightComponent(comp);
    }

    public Component getBottomComponent() {
        return getRightComponent();
    }

    public void remove(Component component) {
        if (component == leftComponent) {
            leftComponent = null;
        }
        if (component == rightComponent) {
            rightComponent = null;
        }
        super.remove(component);
        revalidate();
        repaint();
    }

    public void removeAll() {
        leftComponent = null;
        rightComponent = null;
        super.removeAll();
        revalidate();
        repaint();
    }

    /**
     * Adds a component: a {@code LEFT}/{@code RIGHT}/{@code TOP}/{@code BOTTOM} constraint
     * (the JDK's String constants) fills that slot, otherwise the first two components
     * become the two slots.
     */
    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (LEFT.equals(constraints)) {
            setLeftComponent(comp);
            return;
        }
        if (RIGHT.equals(constraints)) {
            setRightComponent(comp);
            return;
        }
        if (TOP.equals(constraints)) {
            setTopComponent(comp);
            return;
        }
        if (BOTTOM.equals(constraints)) {
            setBottomComponent(comp);
            return;
        }
        if (leftComponent == null) {
            setLeftComponent(comp);
        } else if (rightComponent == null) {
            setRightComponent(comp);
        } else {
            super.addImpl(comp, constraints, index);
        }
    }

    /** Puts {@code comp} into {@code slot}, adding it to the container and dropping the old one. */
    private Component replace(Component slot, Component comp) {
        if (slot == comp) {
            return slot;
        }
        if (slot != null) {
            super.remove(slot);
        }
        if (comp != null) {
            super.addImpl(comp, null, -1);
        }
        revalidate();
        repaint();
        return comp;
    }

    // ------------------------------------------------------------ divider

    public void setDividerSize(int newSize) {
        if (newSize < 0) {
            return;
        }
        dividerSize = newSize;
        revalidate();
        repaint();
    }

    public int getDividerSize() {
        return dividerSize;
    }

    /** Sets the divider location in the pane's own (inset-excluded) space. */
    public void setDividerLocation(int location) {
        if (location < 0) {
            throw new IllegalArgumentException("cannot set divider location to " + location);
        }
        lastDividerLocation = dividerLocation;
        dividerLocation = location;
        revalidate();
        repaint();
    }

    /** The JDK's proportional form: {@code 0.0}..{@code 1.0} of the available extent. */
    public void setDividerLocation(double proportionalLocation) {
        if (proportionalLocation < 0.0 || proportionalLocation > 1.0) {
            throw new IllegalArgumentException("cannot set divider location to " + proportionalLocation);
        }
        int extent = availableExtent();
        setDividerLocation((int) (proportionalLocation * extent));
    }

    /**
     * The divider location after the last layout, or the value that was set;
     * {@code -1} before the pane has ever been laid out (the JDK's state too).
     */
    public int getDividerLocation() {
        return dividerLocation;
    }

    public int getLastDividerLocation() {
        return lastDividerLocation;
    }

    public void setLastDividerLocation(int newLastLocation) {
        lastDividerLocation = newLastLocation;
    }

    /** Fraction of a resize taken by the leading component (0 = all to the trailing one). */
    public void setResizeWeight(double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("cannot set resize weight to " + value);
        }
        resizeWeight = value;
    }

    public double getResizeWeight() {
        return resizeWeight;
    }

    /** Stored; the one-touch expand buttons are not painted on this port. */
    public void setOneTouchExpandable(boolean newValue) {
        oneTouchExpandable = newValue;
        revalidate();
        repaint();
    }

    public boolean isOneTouchExpandable() {
        return oneTouchExpandable;
    }

    /** Stored; every layout on this port is continuous. */
    public void setContinuousLayout(boolean newContinuousLayout) {
        continuousLayout = newContinuousLayout;
    }

    public boolean isContinuousLayout() {
        return continuousLayout;
    }

    public int getMinimumDividerLocation() {
        return 0;
    }

    public int getMaximumDividerLocation() {
        return Math.max(0, availableExtent() - dividerSize);
    }

    // ------------------------------------------------------------ geometry

    /** Extent of the pane's content along the split axis, insets excluded. */
    private int availableExtent() {
        Insets insets = getInsets();
        return orientation == HORIZONTAL_SPLIT
                ? getWidth() - insets.left - insets.right
                : getHeight() - insets.top - insets.bottom;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Dimension leading = leftComponent == null ? new Dimension(0, 0) : leftComponent.getPreferredSize();
        Dimension trailing = rightComponent == null ? new Dimension(0, 0) : rightComponent.getPreferredSize();
        Dimension size = new Dimension();
        if (orientation == HORIZONTAL_SPLIT) {
            size.width = leading.width + dividerSize + trailing.width;
            size.height = Math.max(leading.height, trailing.height);
        } else {
            size.height = leading.height + dividerSize + trailing.height;
            size.width = Math.max(leading.width, trailing.width);
        }
        Insets insets = getInsets();
        size.width += insets.left + insets.right;
        size.height += insets.top + insets.bottom;
        return size;
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        return new Dimension(dividerSize, dividerSize);
    }

    @Override
    public void doLayout() {
        Insets insets = getInsets();
        int x = insets.left;
        int y = insets.top;
        int w = getWidth() - insets.left - insets.right;
        int h = getHeight() - insets.top - insets.bottom;
        if (w <= 0 || h <= 0) {
            return;
        }
        int extent = orientation == HORIZONTAL_SPLIT ? w : h;
        int location = resolveDividerLocation(extent);
        if (orientation == HORIZONTAL_SPLIT) {
            if (leftComponent != null) {
                leftComponent.setBounds(x, y, Math.max(0, location), h);
            }
            if (rightComponent != null) {
                rightComponent.setBounds(x + location + dividerSize, y,
                        Math.max(0, w - location - dividerSize), h);
            }
        } else {
            if (leftComponent != null) {
                leftComponent.setBounds(x, y, w, Math.max(0, location));
            }
            if (rightComponent != null) {
                rightComponent.setBounds(x, y + location + dividerSize, w,
                        Math.max(0, h - location - dividerSize));
            }
        }
    }

    /**
     * The divider location to lay out with: the one that was set, else the leading
     * component's preferred size (the JDK asks the UI, which uses the same rule).
     */
    private int resolveDividerLocation(int extent) {
        int max = Math.max(0, extent - dividerSize);
        if (dividerLocation >= 0) {
            return Math.min(dividerLocation, max);
        }
        int preferred = 0;
        if (leftComponent != null) {
            Dimension d = leftComponent.getPreferredSize();
            preferred = orientation == HORIZONTAL_SPLIT ? d.width : d.height;
        } else {
            preferred = max / 2;
        }
        dividerLocation = Math.max(0, Math.min(preferred, max));
        return dividerLocation;
    }

    // ------------------------------------------------------------ divider dragging

    /** True when the location is inside the divider band (in pane coordinates). */
    private boolean isOnDivider(int x, int y) {
        Insets insets = getInsets();
        int location = dividerLocation >= 0 ? dividerLocation : resolveDividerLocation(availableExtent());
        if (orientation == HORIZONTAL_SPLIT) {
            int dx = x - insets.left;
            return dx >= location && dx < location + dividerSize;
        }
        int dy = y - insets.top;
        return dy >= location && dy < location + dividerSize;
    }

    private void onDividerPressed(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getX(), e.getY(), this);
        if (!isOnDivider(p.x, p.y)) {
            return;
        }
        dragging = true;
        Insets insets = getInsets();
        dragOffset = (orientation == HORIZONTAL_SPLIT ? p.x - insets.left : p.y - insets.top)
                - dividerLocation;
    }

    private void onDividerDragged(MouseEvent e) {
        if (!dragging) {
            return;
        }
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getX(), e.getY(), this);
        Insets insets = getInsets();
        int location = (orientation == HORIZONTAL_SPLIT ? p.x - insets.left : p.y - insets.top) - dragOffset;
        int max = getMaximumDividerLocation();
        setDividerLocation(Math.max(0, Math.min(location, max)));
    }

    @Override
    public String toString() {
        return getClass().getName() + "[orientation=" + orientation
                + ",dividerLocation=" + dividerLocation + "]";
    }
}

package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;

/**
 * {@code BoxLayout}: lays a container's children out along a single axis.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and this
 * layout carries the port's whole sidebar: RuneLite builds every plugin panel's vertical
 * stack, its toolbars and the client's bottom bar with {@code BoxLayout}/{@code Box}, and
 * depends on the JDK's exact behaviour for the cross axis (centre/leading/trailing
 * alignment of each child) and for glue.
 *
 * <p>Implemented JDK rules, all in terms of a size-requirement pair per child:
 * <ul>
 * <li>Along the axis children are tiled: when the container is larger than the sum of the
 *     preferred sizes the surplus is handed out in proportion to each child's
 *     {@code maximum - preferred} slack, and when it is smaller every child is squeezed in
 *     proportion to its {@code preferred - minimum} slack (this is
 *     {@code SizeRequirements.calculateTiledPositions}/{@code expand}/{@code compress},
 *     reproduced here because the port has no {@code SizeRequirements} class).</li>
 * <li>Across the axis children are aligned: each child is placed so that its own alignment
 *     point lines up with the container's alignment point, clamped by its maximum size
 *     ({@code SizeRequirements.calculateAlignedPositions}).</li>
 * <li>The container's own alignment is the alignment of its children along that axis,
 *     computed as {@code totalAscent / (totalAscent + totalDescent)} where the ascent of a
 *     requirement is {@code alignment * size}.</li>
 * <li>An invisible child contributes a zero requirement but keeps its alignment.</li>
 * </ul>
 *
 * <p>Port deviations:
 * <ul>
 * <li>{@code LINE_AXIS} resolves to {@code X_AXIS} and {@code PAGE_AXIS} to {@code Y_AXIS}
 *     because this port has no {@code ComponentOrientation} (the JDK's left-to-right,
 *     horizontal default).</li>
 * <li>{@code Component.getAlignmentX()/getAlignmentY()} do not exist on this port's
 *     {@code java.awt.Component}; a child that is not a {@link JComponent} is treated as
 *     centre aligned, which is the JDK's default for plain AWT components.</li>
 * <li>{@code getMaximumSize()} is read through {@link #maximumSizeOf} for the reason given
 *     there.</li>
 * </ul>
 */
public class BoxLayout implements LayoutManager2 {

    /** Children are laid out horizontally, left to right. */
    public static final int X_AXIS = 0;

    /** Children are laid out vertically, top to bottom. */
    public static final int Y_AXIS = 1;

    /** Children are laid out along the line direction ({@code X_AXIS} here). */
    public static final int LINE_AXIS = 2;

    /** Children are laid out along the page direction ({@code Y_AXIS} here). */
    public static final int PAGE_AXIS = 3;

    private final int axis;
    private final Container target;

    private transient Req[] xChildren;
    private transient Req[] yChildren;
    private transient Req xTotal;
    private transient Req yTotal;

    /**
     * Creates a layout manager that lays out {@code target} along {@code axis}.
     *
     * @param axis one of {@code X_AXIS}, {@code Y_AXIS}, {@code LINE_AXIS} or
     *             {@code PAGE_AXIS}
     * @throws Error if {@code axis} is not one of the four constants (the JDK throws
     *         {@code AWTError}, an {@code Error} subclass, but this port has no
     *         {@code java.awt.AWTError} class)
     */
    public BoxLayout(Container target, int axis) {
        if (axis != X_AXIS && axis != Y_AXIS && axis != LINE_AXIS && axis != PAGE_AXIS) {
            throw new Error("Invalid axis");
        }
        this.axis = axis;
        this.target = target;
    }

    /** Returns the container this layout manages. */
    public final Container getTarget() {
        return target;
    }

    /** Returns the axis this layout arranges its children along. */
    public final int getAxis() {
        return axis;
    }

    @Override
    public synchronized void invalidateLayout(Container target) {
        checkContainer(target);
        xChildren = null;
        yChildren = null;
        xTotal = null;
        yTotal = null;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        invalidateFor(comp);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        invalidateFor(comp);
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        invalidateFor(comp);
    }

    /**
     * Drops the cached requirements. The JDK calls
     * {@code invalidateLayout(comp.getParent())} unconditionally and throws an
     * {@code AWTError} when the component is unattached; the port skips the call instead,
     * because this runtime also removes components that already lost their parent.
     */
    private void invalidateFor(Component comp) {
        Container parent = (comp == null) ? null : comp.getParent();
        if (parent != null) {
            invalidateLayout(parent);
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        Dimension size;
        synchronized (this) {
            checkContainer(target);
            checkRequests();
            size = new Dimension(xTotal.preferred, yTotal.preferred);
        }
        return addInsets(target, size);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension size;
        synchronized (this) {
            checkContainer(target);
            checkRequests();
            size = new Dimension(xTotal.minimum, yTotal.minimum);
        }
        return addInsets(target, size);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        Dimension size;
        synchronized (this) {
            checkContainer(target);
            checkRequests();
            size = new Dimension(xTotal.maximum, yTotal.maximum);
        }
        return addInsets(target, size);
    }

    /** Adds the container's insets to a size, saturating instead of overflowing. */
    private static Dimension addInsets(Container target, Dimension size) {
        Insets insets = target.getInsets();
        size.width = (int) Math.min((long) size.width + (long) insets.left + (long) insets.right,
                Integer.MAX_VALUE);
        size.height = (int) Math.min((long) size.height + (long) insets.top + (long) insets.bottom,
                Integer.MAX_VALUE);
        return size;
    }

    @Override
    public synchronized float getLayoutAlignmentX(Container target) {
        checkContainer(target);
        checkRequests();
        return xTotal.alignment;
    }

    @Override
    public synchronized float getLayoutAlignmentY(Container target) {
        checkContainer(target);
        checkRequests();
        return yTotal.alignment;
    }

    @Override
    public void layoutContainer(Container target) {
        checkContainer(target);
        int nChildren = target.getComponentCount();
        int[] xOffsets = new int[nChildren];
        int[] xSpans = new int[nChildren];
        int[] yOffsets = new int[nChildren];
        int[] ySpans = new int[nChildren];

        Dimension alloc = target.getSize();
        Insets in = target.getInsets();
        alloc.width -= in.left + in.right;
        alloc.height -= in.top + in.bottom;

        // This port has no ComponentOrientation, so LINE_AXIS is X and PAGE_AXIS is Y.
        int absoluteAxis = resolveAxis(axis);
        boolean ltr = true;

        synchronized (this) {
            checkRequests();

            if (absoluteAxis == X_AXIS) {
                calculateTiledPositions(alloc.width, xChildren, xOffsets, xSpans, ltr);
                calculateAlignedPositions(alloc.height, yTotal, yChildren, yOffsets, ySpans);
            } else {
                calculateAlignedPositions(alloc.width, xTotal, xChildren, xOffsets, xSpans, ltr);
                calculateTiledPositions(alloc.height, yChildren, yOffsets, ySpans, true);
            }
        }

        for (int i = 0; i < nChildren; i++) {
            Component c = target.getComponent(i);
            c.setBounds((int) Math.min((long) in.left + (long) xOffsets[i], Integer.MAX_VALUE),
                    (int) Math.min((long) in.top + (long) yOffsets[i], Integer.MAX_VALUE),
                    xSpans[i], ySpans[i]);
        }
    }

    private void checkContainer(Container target) {
        if (this.target != target) {
            throw new Error("BoxLayout can't be shared");
        }
    }

    private void checkRequests() {
        if (xChildren == null || yChildren == null) {
            int n = target.getComponentCount();
            xChildren = new Req[n];
            yChildren = new Req[n];
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) {
                    xChildren[i] = new Req(0, 0, 0, alignmentX(c));
                    yChildren[i] = new Req(0, 0, 0, alignmentY(c));
                    continue;
                }
                Dimension min = c.getMinimumSize();
                Dimension typ = c.getPreferredSize();
                Dimension max = maximumSizeOf(c);
                xChildren[i] = new Req(min.width, typ.width, max.width, alignmentX(c));
                yChildren[i] = new Req(min.height, typ.height, max.height, alignmentY(c));
            }

            if (resolveAxis(axis) == X_AXIS) {
                xTotal = tiledRequirements(xChildren);
                yTotal = alignedRequirements(yChildren);
            } else {
                xTotal = alignedRequirements(xChildren);
                yTotal = tiledRequirements(yChildren);
            }
        }
    }

    /** Resolves the relative axes to absolute ones; this port is always left-to-right. */
    private static int resolveAxis(int axis) {
        if (axis == LINE_AXIS) {
            return X_AXIS;
        }
        if (axis == PAGE_AXIS) {
            return Y_AXIS;
        }
        return axis;
    }

    // ------------------------------------------------------- size requirements

    /**
     * This port's stand-in for {@code javax.swing.SizeRequirements}: one child's range and
     * alignment along one axis.
     */
    private static final class Req {
        int minimum;
        int preferred;
        int maximum;
        float alignment;

        Req() {
            alignment = 0.5f;
        }

        Req(int minimum, int preferred, int maximum, float alignment) {
            this.minimum = minimum;
            this.preferred = preferred;
            this.maximum = maximum;
            this.alignment = alignment;
        }
    }

    /** The sum of the children's ranges, for the axis the children are tiled along. */
    private static Req tiledRequirements(Req[] children) {
        Req total = new Req();
        long min = 0;
        long pref = 0;
        long max = 0;
        for (Req req : children) {
            min += req.minimum;
            pref += req.preferred;
            max += req.maximum;
        }
        total.minimum = (int) Math.min(min, Integer.MAX_VALUE);
        total.preferred = (int) Math.min(pref, Integer.MAX_VALUE);
        total.maximum = (int) Math.min(max, Integer.MAX_VALUE);
        return total;
    }

    /**
     * The range needed to align the children: the largest ascent plus the largest descent,
     * where a requirement's ascent is {@code alignment * size}.
     */
    private static Req alignedRequirements(Req[] children) {
        Req totalAscent = new Req();
        Req totalDescent = new Req();
        for (Req req : children) {
            int ascent = (int) (req.alignment * req.minimum);
            totalAscent.minimum = Math.max(ascent, totalAscent.minimum);
            totalDescent.minimum = Math.max(req.minimum - ascent, totalDescent.minimum);

            ascent = (int) (req.alignment * req.preferred);
            totalAscent.preferred = Math.max(ascent, totalAscent.preferred);
            totalDescent.preferred = Math.max(req.preferred - ascent, totalDescent.preferred);

            ascent = (int) (req.alignment * req.maximum);
            totalAscent.maximum = Math.max(ascent, totalAscent.maximum);
            totalDescent.maximum = Math.max(req.maximum - ascent, totalDescent.maximum);
        }
        int min = (int) Math.min((long) totalAscent.minimum + (long) totalDescent.minimum, Integer.MAX_VALUE);
        int pref = (int) Math.min((long) totalAscent.preferred + (long) totalDescent.preferred, Integer.MAX_VALUE);
        int max = (int) Math.min((long) totalAscent.maximum + (long) totalDescent.maximum, Integer.MAX_VALUE);
        float alignment = 0.0f;
        if (min > 0) {
            alignment = (float) totalAscent.minimum / min;
            alignment = alignment > 1.0f ? 1.0f : alignment < 0.0f ? 0.0f : alignment;
        }
        return new Req(min, pref, max, alignment);
    }

    /**
     * Tiles the children into {@code allocated} pixels: every child gets at least its
     * minimum and at most its maximum, with the surplus or the shortfall spread over the
     * children in proportion to their slack.
     */
    private static void calculateTiledPositions(int allocated, Req[] children, int[] offsets, int[] spans,
                                               boolean forward) {
        long min = 0;
        long pref = 0;
        long max = 0;
        for (Req child : children) {
            min += child.minimum;
            pref += child.preferred;
            max += child.maximum;
        }
        if (allocated >= pref) {
            expandedTile(allocated, pref, max, children, offsets, spans, forward);
        } else {
            compressedTile(allocated, min, pref, children, offsets, spans, forward);
        }
    }

    private static void compressedTile(int allocated, long min, long pref, Req[] request, int[] offsets,
                                      int[] spans, boolean forward) {
        float totalPlay = Math.min(pref - allocated, pref - min);
        float factor = (pref - min == 0) ? 0.0f : totalPlay / (pref - min);

        int totalOffset;
        if (forward) {
            totalOffset = 0;
            for (int i = 0; i < spans.length; i++) {
                offsets[i] = totalOffset;
                Req req = request[i];
                float play = factor * (req.preferred - req.minimum);
                spans[i] = (int) (req.preferred - play);
                totalOffset = (int) Math.min((long) totalOffset + (long) spans[i], Integer.MAX_VALUE);
            }
        } else {
            totalOffset = allocated;
            for (int i = 0; i < spans.length; i++) {
                Req req = request[i];
                float play = factor * (req.preferred - req.minimum);
                spans[i] = (int) (req.preferred - play);
                offsets[i] = totalOffset - spans[i];
                totalOffset = (int) Math.max((long) totalOffset - (long) spans[i], 0);
            }
        }
    }

    private static void expandedTile(int allocated, long pref, long max, Req[] request, int[] offsets,
                                    int[] spans, boolean forward) {
        float totalPlay = Math.min(allocated - pref, max - pref);
        float factor = (max - pref == 0) ? 0.0f : totalPlay / (max - pref);

        int totalOffset;
        if (forward) {
            totalOffset = 0;
            for (int i = 0; i < spans.length; i++) {
                offsets[i] = totalOffset;
                Req req = request[i];
                int play = (int) (factor * (req.maximum - req.preferred));
                spans[i] = (int) Math.min((long) req.preferred + (long) play, Integer.MAX_VALUE);
                totalOffset = (int) Math.min((long) totalOffset + (long) spans[i], Integer.MAX_VALUE);
            }
        } else {
            totalOffset = allocated;
            for (int i = 0; i < spans.length; i++) {
                Req req = request[i];
                int play = (int) (factor * (req.maximum - req.preferred));
                spans[i] = (int) Math.min((long) req.preferred + (long) play, Integer.MAX_VALUE);
                offsets[i] = totalOffset - spans[i];
                totalOffset = (int) Math.max((long) totalOffset - (long) spans[i], 0);
            }
        }
    }

    /** Aligns the children inside {@code allocated} pixels on their own alignment points. */
    private static void calculateAlignedPositions(int allocated, Req total, Req[] children, int[] offsets,
                                                 int[] spans, boolean normal) {
        float totalAlignment = normal ? total.alignment : 1.0f - total.alignment;
        int totalAscent = (int) (allocated * totalAlignment);
        int totalDescent = allocated - totalAscent;
        for (int i = 0; i < children.length; i++) {
            Req req = children[i];
            float alignment = normal ? req.alignment : 1.0f - req.alignment;
            int maxAscent = (int) (req.maximum * alignment);
            int maxDescent = req.maximum - maxAscent;
            int ascent = Math.min(totalAscent, maxAscent);
            int descent = Math.min(totalDescent, maxDescent);

            offsets[i] = totalAscent - ascent;
            spans[i] = (int) Math.min((long) ascent + (long) descent, Integer.MAX_VALUE);
        }
    }

    private static void calculateAlignedPositions(int allocated, Req total, Req[] children, int[] offsets,
                                                 int[] spans) {
        calculateAlignedPositions(allocated, total, children, offsets, spans, true);
    }

    // --------------------------------------------------------------- helpers

    static float alignmentX(Component c) {
        return (c instanceof JComponent) ? ((JComponent) c).getAlignmentX() : 0.5f;
    }

    static float alignmentY(Component c) {
        return (c instanceof JComponent) ? ((JComponent) c).getAlignmentY() : 0.5f;
    }

    /**
     * The JDK's {@code JComponent.getMaximumSize()}: an explicitly set maximum wins, then
     * the UI's, and when neither exists the JDK falls back to
     * {@code Component.getMaximumSize()} which returns {@code Short.MAX_VALUE} squared.
     * This port's {@code Component.getMaximumSize()} returns the preferred size instead,
     * so the JDK's value is returned here — without it every ordinary {@code JComponent}
     * would be rigid inside a box and glue-less layouts could never stretch. Shared with
     * {@link GroupLayout}, which needs the same correction for its last component spring.
     */
    static Dimension maximumSizeOf(Component component) {
        if (component instanceof JComponent) {
            JComponent jc = (JComponent) component;
            if (jc.isMaximumSizeSet()) {
                return jc.getMaximumSize();
            }
            javax.swing.plaf.ComponentUI ui = jc.getUI();
            if (ui != null) {
                Dimension uiMax = ui.getMaximumSize(jc);
                if (uiMax != null) {
                    return uiMax;
                }
            }
        }
        if (!usesPortDefaultMaximum(component)) {
            return component.getMaximumSize();
        }
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    /**
     * True when {@code component.getMaximumSize()} ends up in this port's
     * {@code Component.getMaximumSize()} (which returns the preferred size) rather than in
     * a real implementation. The JDK's {@code Component.getMaximumSize()} returns
     * {@code Short.MAX_VALUE} squared, so that is what such a component reports.
     *
     * <p>The declaring class of {@code getMaximumSize()} identifies the port's default
     * chain: {@code Component} itself, or {@code Container} when no
     * {@code LayoutManager2} is installed (the JDK's {@code Container.getMaximumSize()}
     * only forwards to a {@code LayoutManager2} and falls through to {@code Component}
     * otherwise). Anything else — {@code Box.Filler}, a custom component, or a container
     * whose layout manager answers the question — is a real maximum and is used as is.
     */
    private static boolean usesPortDefaultMaximum(Component component) {
        Class<?> declaring;
        try {
            declaring = component.getClass().getMethod("getMaximumSize").getDeclaringClass();
        } catch (NoSuchMethodException e) {
            return false;
        }
        if (declaring != Component.class && declaring != Container.class && declaring != JComponent.class) {
            // A real implementation answered (Box.Filler, a custom component).
            return false;
        }
        // Container.getMaximumSize() forwards to the layout manager when it is a
        // LayoutManager2, which the JDK does as well, so that answer is real.
        if (component instanceof Container && ((Container) component).getLayout() instanceof LayoutManager2) {
            return false;
        }
        return true;
    }
}

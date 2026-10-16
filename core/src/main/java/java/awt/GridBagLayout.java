package java.awt;

import java.util.Arrays;
import java.util.Hashtable;

/**
 * AWT {@code GridBagLayout}: the JDK's constraint based grid layout.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the port
 * really runs this layout: RuneLite's config panels, the hiscore panel, the grand exchange
 * panels, the skill calculator and {@code RecentColors} all build their forms with
 * {@code GridBagLayout}. The generated data-only stub laid nothing out, so every one of
 * those forms collapsed to a single zero-sized stack.
 *
 * <p>The layout runs the JDK's three pass algorithm:
 * <ol>
 * <li>Pass 1 resolves the grid shape: {@code gridx}/{@code gridy} of {@code RELATIVE} are
 *     filled in from the previously placed component (or from the start of the grid),
 *     {@code REMAINDER}/{@code <= 0} spans are expanded at pass 2, and each component's
 *     own size for the current size flag ({@code getPreferredSize()} or
 *     {@code getMinimumSize()}) is cached in its constraints.</li>
 * <li>Pass 2 assigns every component its final {@code tempX/tempY/tempWidth/tempHeight}.</li>
 * <li>Pass 3 walks the components in order of span width and height, folding each
 *     component's weight into the columns/rows it covers and growing
 *     {@code minWidth}/{@code minHeight} until every component fits, distributing the
 *     shortfall over the spanned cells in proportion to their weights and giving the
 *     rounding remainder to the last cell of the span.</li>
 * </ol>
 * Layout then adds the insets, the inter-cell gaps and the padding and extra space exactly
 * the way {@link #arrangeGrid} describes: surplus pixels go to the columns/rows that have
 * weight, in proportion to that weight, and the leftover unused space is centred.
 *
 * <p>Port deviations:
 * <ul>
 * <li>This port's {@code java.awt.Component} has no {@code getBaseline(int,int)} or
 *     {@code getBaselineResizeBehavior()}, so the {@code BASELINE} family of anchors
 *     (<code>BASELINE</code>, <code>ABOVE_BASELINE*</code>, <code>BELOW_BASELINE*</code>)
 *     takes the JDK's own "component has no baseline" path, which centres the component
 *     vertically in its cell. No layout data for those anchors is tracked.</li>
 * <li>The JDK's {@code java.awt.GridBagLayoutInfo} is a separate package-private class;
 *     this port keeps it as {@link GridBagLayout.GridBagLayoutInfo} nested here, because
 *     the grid bag layout is the only consumer of it.</li>
 * <li>{@code Container.getComponentOrientation()} does not exist here, so the
 *     right-to-left branch of the JDK (the {@code rightToLeft} field) is never taken.</li>
 * </ul>
 */
public class GridBagLayout implements LayoutManager2, java.io.Serializable {

    /** Integer size of the array growth when scanning spans. */
    static final int EMPIRICMULTIPLIER = 2;

    /** The largest grid the layout accepts from a component's span. */
    protected static final int MAXGRIDSIZE = 512;

    /** Size flag: use each component's minimum size. */
    protected static final int MINSIZE = 1;

    /** Size flag: use each component's preferred size. */
    protected static final int PREFERREDSIZE = 2;

    /** Maps a component to its cloned constraints. */
    protected Hashtable<Component, GridBagConstraints> comptable;

    /** Constraints used for a component that was added without any. */
    protected GridBagConstraints defaultConstraints;

    /** The information from the last {@link #getLayoutInfo} call, used by the accessors. */
    protected GridBagLayoutInfo layoutInfo;

    /** Per-column minimum widths from the last layout, as the JDK exposes them. */
    public int[] columnWidths;

    /** Per-row minimum heights from the last layout, as the JDK exposes them. */
    public int[] rowHeights;

    /** Per-column weights from the last layout, as the JDK exposes them. */
    public double[] columnWeights;

    /** Per-row weights from the last layout, as the JDK exposes them. */
    public double[] rowWeights;

    /** Always false: this port has no {@code ComponentOrientation}. */
    transient boolean rightToLeft = false;

    /** Creates a grid bag layout with default constraints. */
    public GridBagLayout() {
        comptable = new Hashtable<>();
        defaultConstraints = new GridBagConstraints();
    }

    /** Sets the constraints for a component (the constraints are cloned, as the JDK does). */
    public void setConstraints(Component comp, GridBagConstraints constraints) {
        comptable.put(comp, (GridBagConstraints) constraints.clone());
    }

    /** Returns a copy of the constraints of a component, installing the defaults if needed. */
    public GridBagConstraints getConstraints(Component comp) {
        GridBagConstraints constraints = comptable.get(comp);
        if (constraints == null) {
            setConstraints(comp, defaultConstraints);
            constraints = comptable.get(comp);
        }
        return (GridBagConstraints) constraints.clone();
    }

    /**
     * Returns the live constraints of a component (not a copy), installing the defaults if
     * the component has none. Returns {@code null} for a {@code null} component.
     */
    protected GridBagConstraints lookupConstraints(Component comp) {
        if (comp == null) {
            return null;
        }
        GridBagConstraints constraints = comptable.get(comp);
        if (constraints == null) {
            setConstraints(comp, defaultConstraints);
            constraints = comptable.get(comp);
        }
        return constraints;
    }

    private void removeConstraints(Component comp) {
        comptable.remove(comp);
    }

    /** The graphics origin of the layout grid's top-left cell. */
    public Point getLayoutOrigin() {
        Point origin = new Point(0, 0);
        if (layoutInfo != null) {
            origin.x = layoutInfo.startx;
            origin.y = layoutInfo.starty;
        }
        return origin;
    }

    /** The grid's column widths and row heights from the last layout. */
    public int[][] getLayoutDimensions() {
        if (layoutInfo == null) {
            return new int[2][0];
        }
        int[][] dim = new int[2][];
        dim[0] = new int[layoutInfo.width];
        dim[1] = new int[layoutInfo.height];
        System.arraycopy(layoutInfo.minWidth, 0, dim[0], 0, layoutInfo.width);
        System.arraycopy(layoutInfo.minHeight, 0, dim[1], 0, layoutInfo.height);
        return dim;
    }

    /** The grid's column and row weights from the last layout. */
    public double[][] getLayoutWeights() {
        if (layoutInfo == null) {
            return new double[2][0];
        }
        double[][] weights = new double[2][];
        weights[0] = new double[layoutInfo.width];
        weights[1] = new double[layoutInfo.height];
        System.arraycopy(layoutInfo.weightX, 0, weights[0], 0, layoutInfo.width);
        System.arraycopy(layoutInfo.weightY, 0, weights[1], 0, layoutInfo.height);
        return weights;
    }

    /**
     * Returns the cell coordinates that contain the point, or the nearest cell when the
     * point is outside the grid.
     */
    public Point location(int x, int y) {
        Point loc = new Point(0, 0);
        if (layoutInfo == null) {
            return loc;
        }

        int d = layoutInfo.startx;
        int i;
        // Left-to-right always (see the class note).
        for (i = 0; i < layoutInfo.width; i++) {
            d += layoutInfo.minWidth[i];
            if (d > x) {
                break;
            }
        }
        loc.x = i;

        d = layoutInfo.starty;
        for (i = 0; i < layoutInfo.height; i++) {
            d += layoutInfo.minHeight[i];
            if (d > y) {
                break;
            }
        }
        loc.y = i;

        return loc;
    }

    /** Has no effect: this layout takes no per-component strings. */
    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    /**
     * Adds a component with the given constraints.
     *
     * @throws IllegalArgumentException if {@code constraints} is neither null nor a
     *         {@link GridBagConstraints}
     */
    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        if (constraints instanceof GridBagConstraints) {
            setConstraints(comp, (GridBagConstraints) constraints);
        } else if (constraints != null) {
            throw new IllegalArgumentException("cannot add to layout: constraints must be a GridBagConstraint");
        }
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        removeConstraints(comp);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        GridBagLayoutInfo info = getLayoutInfo(parent, PREFERREDSIZE);
        return getMinSize(parent, info);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        GridBagLayoutInfo info = getLayoutInfo(parent, MINSIZE);
        return getMinSize(parent, info);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container parent) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container parent) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
        // No cached layout state beyond layoutInfo, which every query recomputes.
    }

    @Override
    public void layoutContainer(Container parent) {
        arrangeGrid(parent);
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

    // ------------------------------------------------------------- layout info

    /**
     * Fills in the grid information for the current children; the same as
     * {@link #GetLayoutInfo}.
     */
    protected GridBagLayoutInfo getLayoutInfo(Container parent, int sizeflag) {
        return GetLayoutInfo(parent, sizeflag);
    }

    /**
     * The obsolete spelling of {@link #getLayoutInfo}, kept because the JDK keeps it.
     */
    protected GridBagLayoutInfo GetLayoutInfo(Container parent, int sizeflag) {
        synchronized (parent.getTreeLock()) {
            Component[] components = parent.getComponents();

            int layoutWidth;
            int layoutHeight;
            int[] xMaxArray;
            int[] yMaxArray;
            int compindex;
            int i;
            int k;
            int px;
            int py;
            int pixels_diff;
            int nextSize;
            int curX;
            int curY;
            int curWidth;
            int curHeight;
            int curRow;
            int curCol;
            double weight_diff;
            double weight;
            int maximumArrayXIndex;
            int maximumArrayYIndex;

            /*
             * Pass 1: figure out the grid's dimensions. Zero or negative spans count as 1
             * while measuring.
             */
            layoutWidth = layoutHeight = 0;
            curRow = curCol = -1;
            long[] arraySizes = preInitMaximumArraySizes(parent);

            /*
             * The arrays are indexed by the other axis's coordinate (xMaxArray is indexed
             * by row), which is how the JDK names and uses them; the multiplier leaves room
             * for components whose positions are only resolved here.
             */
            maximumArrayXIndex = (EMPIRICMULTIPLIER * arraySizes[0] > Integer.MAX_VALUE)
                    ? Integer.MAX_VALUE : EMPIRICMULTIPLIER * (int) arraySizes[0];
            maximumArrayYIndex = (EMPIRICMULTIPLIER * arraySizes[1] > Integer.MAX_VALUE)
                    ? Integer.MAX_VALUE : EMPIRICMULTIPLIER * (int) arraySizes[1];

            if (rowHeights != null) {
                maximumArrayXIndex = Math.max(maximumArrayXIndex, rowHeights.length);
            }
            if (columnWidths != null) {
                maximumArrayYIndex = Math.max(maximumArrayYIndex, columnWidths.length);
            }

            xMaxArray = new int[Math.max(maximumArrayXIndex, 1)];
            yMaxArray = new int[Math.max(maximumArrayYIndex, 1)];

            for (compindex = 0; compindex < components.length; compindex++) {
                Component comp = components[compindex];
                if (!comp.isVisible()) {
                    continue;
                }
                GridBagConstraints constraints = lookupConstraints(comp);

                curX = constraints.gridx;
                curY = constraints.gridy;
                curWidth = constraints.gridwidth;
                if (curWidth <= 0) {
                    curWidth = 1;
                }
                curHeight = constraints.gridheight;
                if (curHeight <= 0) {
                    curHeight = 1;
                }

                // Both relative: continue the previous row, or column, or start a new grid.
                if (curX < 0 && curY < 0) {
                    if (curRow >= 0) {
                        curY = curRow;
                    } else if (curCol >= 0) {
                        curX = curCol;
                    } else {
                        curY = 0;
                    }
                }
                if (curX < 0) {
                    px = 0;
                    for (i = curY; i < (curY + curHeight); i++) {
                        px = Math.max(px, xMaxArray[i]);
                    }
                    curX = px - curX - 1;
                    if (curX < 0) {
                        curX = 0;
                    }
                } else if (curY < 0) {
                    py = 0;
                    for (i = curX; i < (curX + curWidth); i++) {
                        py = Math.max(py, yMaxArray[i]);
                    }
                    curY = py - curY - 1;
                    if (curY < 0) {
                        curY = 0;
                    }
                }

                px = curX + curWidth;
                if (layoutWidth < px) {
                    layoutWidth = px;
                }
                py = curY + curHeight;
                if (layoutHeight < py) {
                    layoutHeight = py;
                }

                for (i = curX; i < (curX + curWidth); i++) {
                    yMaxArray[i] = py;
                }
                for (i = curY; i < (curY + curHeight); i++) {
                    xMaxArray[i] = px;
                }

                // Cache the child's size for this pass.
                Dimension d = (sizeflag == PREFERREDSIZE) ? comp.getPreferredSize() : comp.getMinimumSize();
                constraints.minWidth = (d == null) ? 0 : d.width;
                constraints.minHeight = (d == null) ? 0 : d.height;

                // Zero width and height together end the current run.
                if (constraints.gridheight == 0 && constraints.gridwidth == 0) {
                    curRow = curCol = -1;
                }
                if (constraints.gridheight == 0 && curRow < 0) {
                    curCol = curX + curWidth;
                } else if (constraints.gridwidth == 0 && curCol < 0) {
                    curRow = curY + curHeight;
                }
            }

            if (columnWidths != null && layoutWidth < columnWidths.length) {
                layoutWidth = columnWidths.length;
            }
            if (rowHeights != null && layoutHeight < rowHeights.length) {
                layoutHeight = rowHeights.length;
            }

            GridBagLayoutInfo r = new GridBagLayoutInfo(Math.max(layoutWidth, 0), Math.max(layoutHeight, 0));

            /*
             * Pass 2: resolve relative positions to absolute ones and expand the
             * REMAINDER/zero spans to the end of the grid.
             */
            curRow = curCol = -1;
            Arrays.fill(xMaxArray, 0);
            Arrays.fill(yMaxArray, 0);

            for (compindex = 0; compindex < components.length; compindex++) {
                Component comp = components[compindex];
                if (!comp.isVisible()) {
                    continue;
                }
                GridBagConstraints constraints = lookupConstraints(comp);

                curX = constraints.gridx;
                curY = constraints.gridy;
                curWidth = constraints.gridwidth;
                curHeight = constraints.gridheight;

                if (curX < 0 && curY < 0) {
                    if (curRow >= 0) {
                        curY = curRow;
                    } else if (curCol >= 0) {
                        curX = curCol;
                    } else {
                        curY = 0;
                    }
                }

                if (curX < 0) {
                    if (curHeight <= 0) {
                        curHeight += r.height - curY;
                        if (curHeight < 1) {
                            curHeight = 1;
                        }
                    }
                    px = 0;
                    for (i = curY; i < (curY + curHeight); i++) {
                        px = Math.max(px, xMaxArray[i]);
                    }
                    curX = px - curX - 1;
                    if (curX < 0) {
                        curX = 0;
                    }
                } else if (curY < 0) {
                    if (curWidth <= 0) {
                        curWidth += r.width - curX;
                        if (curWidth < 1) {
                            curWidth = 1;
                        }
                    }
                    py = 0;
                    for (i = curX; i < (curX + curWidth); i++) {
                        py = Math.max(py, yMaxArray[i]);
                    }
                    curY = py - curY - 1;
                    if (curY < 0) {
                        curY = 0;
                    }
                }

                if (curWidth <= 0) {
                    curWidth += r.width - curX;
                    if (curWidth < 1) {
                        curWidth = 1;
                    }
                }
                if (curHeight <= 0) {
                    curHeight += r.height - curY;
                    if (curHeight < 1) {
                        curHeight = 1;
                    }
                }

                px = curX + curWidth;
                py = curY + curHeight;

                for (i = curX; i < (curX + curWidth); i++) {
                    yMaxArray[i] = py;
                }
                for (i = curY; i < (curY + curHeight); i++) {
                    xMaxArray[i] = px;
                }

                // Negative spans also start a new row/column.
                if (constraints.gridheight == 0 && constraints.gridwidth == 0) {
                    curRow = curCol = -1;
                }
                if (constraints.gridheight == 0 && curRow < 0) {
                    curCol = curX + curWidth;
                } else if (constraints.gridwidth == 0 && curCol < 0) {
                    curRow = curY + curHeight;
                }

                constraints.tempX = curX;
                constraints.tempY = curY;
                constraints.tempWidth = curWidth;
                constraints.tempHeight = curHeight;
            }

            r.weightX = new double[Math.max(maximumArrayYIndex, 1)];
            r.weightY = new double[Math.max(maximumArrayXIndex, 1)];
            r.minWidth = new int[Math.max(maximumArrayYIndex, 1)];
            r.minHeight = new int[Math.max(maximumArrayXIndex, 1)];

            // Caller supplied rows/columns/weights win over the computed ones.
            if (columnWidths != null) {
                System.arraycopy(columnWidths, 0, r.minWidth, 0, columnWidths.length);
            }
            if (rowHeights != null) {
                System.arraycopy(rowHeights, 0, r.minHeight, 0, rowHeights.length);
            }
            if (columnWeights != null) {
                System.arraycopy(columnWeights, 0, r.weightX, 0, Math.min(r.weightX.length, columnWeights.length));
            }
            if (rowWeights != null) {
                System.arraycopy(rowWeights, 0, r.weightY, 0, Math.min(r.weightY.length, rowWeights.length));
            }

            /*
             * Pass 3: distribute weights and minimum sizes. Components are handled in
             * increasing span order so that a narrow component's needs are known before the
             * wide ones that span it are considered.
             */
            nextSize = Integer.MAX_VALUE;

            for (i = 1; i != Integer.MAX_VALUE; i = nextSize, nextSize = Integer.MAX_VALUE) {
                for (compindex = 0; compindex < components.length; compindex++) {
                    Component comp = components[compindex];
                    if (!comp.isVisible()) {
                        continue;
                    }
                    GridBagConstraints constraints = lookupConstraints(comp);

                    if (constraints.tempWidth == i) {
                        px = constraints.tempX + constraints.tempWidth;

                        // Fold this component's weight into the columns it spans.
                        weight_diff = constraints.weightx;
                        for (k = constraints.tempX; k < px; k++) {
                            weight_diff -= r.weightX[k];
                        }
                        if (weight_diff > 0.0) {
                            weight = 0.0;
                            for (k = constraints.tempX; k < px; k++) {
                                weight += r.weightX[k];
                            }
                            for (k = constraints.tempX; weight > 0.0 && k < px; k++) {
                                double wt = r.weightX[k];
                                double dx = (wt * weight_diff) / weight;
                                r.weightX[k] += dx;
                                weight_diff -= dx;
                                weight -= wt;
                            }
                            // The remainder goes to the last cell of the span.
                            r.weightX[px - 1] += weight_diff;
                        }

                        // Grow the spanned columns until the component fits.
                        pixels_diff = constraints.minWidth + constraints.ipadx
                                + constraints.insets.left + constraints.insets.right;
                        for (k = constraints.tempX; k < px; k++) {
                            pixels_diff -= r.minWidth[k];
                        }
                        if (pixels_diff > 0) {
                            weight = 0.0;
                            for (k = constraints.tempX; k < px; k++) {
                                weight += r.weightX[k];
                            }
                            for (k = constraints.tempX; weight > 0.0 && k < px; k++) {
                                double wt = r.weightX[k];
                                int dx = (int) ((wt * ((double) pixels_diff)) / weight);
                                r.minWidth[k] += dx;
                                pixels_diff -= dx;
                                weight -= wt;
                            }
                            // Any leftovers go into the last cell of the span.
                            r.minWidth[px - 1] += pixels_diff;
                        }
                    } else if (constraints.tempWidth > i && constraints.tempWidth < nextSize) {
                        nextSize = constraints.tempWidth;
                    }

                    if (constraints.tempHeight == i) {
                        py = constraints.tempY + constraints.tempHeight;

                        weight_diff = constraints.weighty;
                        for (k = constraints.tempY; k < py; k++) {
                            weight_diff -= r.weightY[k];
                        }
                        if (weight_diff > 0.0) {
                            weight = 0.0;
                            for (k = constraints.tempY; k < py; k++) {
                                weight += r.weightY[k];
                            }
                            for (k = constraints.tempY; weight > 0.0 && k < py; k++) {
                                double wt = r.weightY[k];
                                double dy = (wt * weight_diff) / weight;
                                r.weightY[k] += dy;
                                weight_diff -= dy;
                                weight -= wt;
                            }
                            r.weightY[py - 1] += weight_diff;
                        }

                        pixels_diff = constraints.minHeight + constraints.ipady
                                + constraints.insets.top + constraints.insets.bottom;
                        for (k = constraints.tempY; k < py; k++) {
                            pixels_diff -= r.minHeight[k];
                        }
                        if (pixels_diff > 0) {
                            weight = 0.0;
                            for (k = constraints.tempY; k < py; k++) {
                                weight += r.weightY[k];
                            }
                            for (k = constraints.tempY; weight > 0.0 && k < py; k++) {
                                double wt = r.weightY[k];
                                int dy = (int) ((wt * ((double) pixels_diff)) / weight);
                                r.minHeight[k] += dy;
                                pixels_diff -= dy;
                                weight -= wt;
                            }
                            r.minHeight[py - 1] += pixels_diff;
                        }
                    } else if (constraints.tempHeight > i && constraints.tempHeight < nextSize) {
                        nextSize = constraints.tempHeight;
                    }
                }
            }

            return r;
        }
    }

    /**
     * A first estimate of the grid's width and height, so the pass-1 arrays can be
     * allocated without growing them.
     */
    private long[] preInitMaximumArraySizes(Container parent) {
        Component[] components = parent.getComponents();
        int preMaximumArrayXIndex = 0;
        int preMaximumArrayYIndex = 0;
        long[] returnArray = new long[2];

        for (Component comp : components) {
            if (!comp.isVisible()) {
                continue;
            }
            GridBagConstraints constraints = lookupConstraints(comp);
            int curX = constraints.gridx;
            int curY = constraints.gridy;
            int curWidth = constraints.gridwidth;
            int curHeight = constraints.gridheight;

            // RELATIVE means "next to the previous component"; one extra cell is enough.
            if (curX < 0) {
                curX = ++preMaximumArrayYIndex;
            }
            if (curY < 0) {
                curY = ++preMaximumArrayXIndex;
            }
            if (curWidth <= 0) {
                curWidth = 1;
            }
            if (curHeight <= 0) {
                curHeight = 1;
            }

            preMaximumArrayXIndex = Math.max(curY + curHeight, preMaximumArrayXIndex);
            preMaximumArrayYIndex = Math.max(curX + curWidth, preMaximumArrayYIndex);
        }
        returnArray[0] = preMaximumArrayXIndex;
        returnArray[1] = preMaximumArrayYIndex;
        return returnArray;
    }

    /** The same as {@link #GetMinSize}. */
    protected Dimension getMinSize(Container parent, GridBagLayoutInfo info) {
        return GetMinSize(parent, info);
    }

    /** The obsolete spelling of {@link #getMinSize}, kept because the JDK keeps it. */
    protected Dimension GetMinSize(Container parent, GridBagLayoutInfo info) {
        Dimension d = new Dimension();
        Insets insets = parent.getInsets();

        int t = 0;
        for (int i = 0; i < info.width; i++) {
            t += info.minWidth[i];
        }
        d.width = t + insets.left + insets.right;

        t = 0;
        for (int i = 0; i < info.height; i++) {
            t += info.minHeight[i];
        }
        d.height = t + insets.top + insets.bottom;

        return d;
    }

    /** The same as {@link #ArrangeGrid}. */
    protected void arrangeGrid(Container parent) {
        ArrangeGrid(parent);
    }

    /** The obsolete spelling of {@link #arrangeGrid}, kept because the JDK keeps it. */
    protected void ArrangeGrid(Container parent) {
        Insets insets = parent.getInsets();
        Component[] components = parent.getComponents();
        Rectangle r = new Rectangle();
        int i;
        int diffw;
        int diffh;
        double weight;

        if (components.length == 0
                && (columnWidths == null || columnWidths.length == 0)
                && (rowHeights == null || rowHeights.length == 0)) {
            return;
        }

        // Pass 1: measure at the preferred size, falling back to the minimum size when the
        // container cannot hold the preferred one.
        GridBagLayoutInfo info = getLayoutInfo(parent, PREFERREDSIZE);
        Dimension d = getMinSize(parent, info);

        if (parent.width < d.width || parent.height < d.height) {
            info = getLayoutInfo(parent, MINSIZE);
            d = getMinSize(parent, info);
        }

        layoutInfo = info;
        r.width = d.width;
        r.height = d.height;

        // Distribute the surplus (or remove the shortfall) according to the weights.
        diffw = parent.width - r.width;
        if (diffw != 0) {
            weight = 0.0;
            for (i = 0; i < info.width; i++) {
                weight += info.weightX[i];
            }
            if (weight > 0.0) {
                for (i = 0; i < info.width; i++) {
                    int dx = (int) ((((double) diffw) * info.weightX[i]) / weight);
                    info.minWidth[i] += dx;
                    r.width += dx;
                    if (info.minWidth[i] < 0) {
                        r.width -= info.minWidth[i];
                        info.minWidth[i] = 0;
                    }
                }
            }
            diffw = parent.width - r.width;
        } else {
            diffw = 0;
        }

        diffh = parent.height - r.height;
        if (diffh != 0) {
            weight = 0.0;
            for (i = 0; i < info.height; i++) {
                weight += info.weightY[i];
            }
            if (weight > 0.0) {
                for (i = 0; i < info.height; i++) {
                    int dy = (int) ((((double) diffh) * info.weightY[i]) / weight);
                    info.minHeight[i] += dy;
                    r.height += dy;
                    if (info.minHeight[i] < 0) {
                        r.height -= info.minHeight[i];
                        info.minHeight[i] = 0;
                    }
                }
            }
            diffh = parent.height - r.height;
        } else {
            diffh = 0;
        }

        // Whatever is still left over is centred in the container.
        info.startx = diffw / 2 + insets.left;
        info.starty = diffh / 2 + insets.top;

        for (Component comp : components) {
            if (!comp.isVisible()) {
                continue;
            }
            GridBagConstraints constraints = lookupConstraints(comp);

            r.x = info.startx;
            for (i = 0; i < constraints.tempX; i++) {
                r.x += info.minWidth[i];
            }

            r.y = info.starty;
            for (i = 0; i < constraints.tempY; i++) {
                r.y += info.minHeight[i];
            }

            r.width = 0;
            for (i = constraints.tempX; i < (constraints.tempX + constraints.tempWidth); i++) {
                r.width += info.minWidth[i];
            }

            r.height = 0;
            for (i = constraints.tempY; i < (constraints.tempY + constraints.tempHeight); i++) {
                r.height += info.minHeight[i];
            }

            adjustForGravity(constraints, r);

            // Components must not be placed outside the container.
            if (r.x < 0) {
                r.width += r.x;
                r.x = 0;
            }
            if (r.y < 0) {
                r.height += r.y;
                r.y = 0;
            }

            if ((r.width <= 0) || (r.height <= 0)) {
                comp.setBounds(0, 0, 0, 0);
            } else if (comp.x != r.x || comp.y != r.y || comp.width != r.width || comp.height != r.height) {
                comp.setBounds(r.x, r.y, r.width, r.height);
            }
        }
    }

    /** The same as {@link #AdjustForGravity}. */
    protected void adjustForGravity(GridBagConstraints constraints, Rectangle r) {
        AdjustForGravity(constraints, r);
    }

    /**
     * Applies the constraints' insets, internal padding, fill and anchor to the cell
     * rectangle the grid produced.
     */
    protected void AdjustForGravity(GridBagConstraints constraints, Rectangle r) {
        int cellHeight = r.height;
        int diffx;
        int diffy;

        // Left-to-right always (see the class note).
        r.x += constraints.insets.left;
        r.width -= (constraints.insets.left + constraints.insets.right);
        r.y += constraints.insets.top;
        r.height -= (constraints.insets.top + constraints.insets.bottom);

        diffx = 0;
        if (constraints.fill != GridBagConstraints.HORIZONTAL && constraints.fill != GridBagConstraints.BOTH
                && (r.width > (constraints.minWidth + constraints.ipadx))) {
            diffx = r.width - (constraints.minWidth + constraints.ipadx);
            r.width = constraints.minWidth + constraints.ipadx;
        }

        diffy = 0;
        if (constraints.fill != GridBagConstraints.VERTICAL && constraints.fill != GridBagConstraints.BOTH
                && (r.height > (constraints.minHeight + constraints.ipady))) {
            diffy = r.height - (constraints.minHeight + constraints.ipady);
            r.height = constraints.minHeight + constraints.ipady;
        }

        switch (constraints.anchor) {
            case GridBagConstraints.BASELINE:
            case GridBagConstraints.BASELINE_LEADING:
            case GridBagConstraints.BASELINE_TRAILING:
            case GridBagConstraints.ABOVE_BASELINE:
            case GridBagConstraints.ABOVE_BASELINE_LEADING:
            case GridBagConstraints.ABOVE_BASELINE_TRAILING:
            case GridBagConstraints.BELOW_BASELINE:
            case GridBagConstraints.BELOW_BASELINE_LEADING:
            case GridBagConstraints.BELOW_BASELINE_TRAILING:
                // No component in this port exposes a baseline, so these take the JDK's
                // "row has no baseline" path: centre the component in its cell.
                r.x += diffx / 2;
                centerVertically(constraints, r, cellHeight);
                break;
            case GridBagConstraints.CENTER:
                r.x += diffx / 2;
                r.y += diffy / 2;
                break;
            case GridBagConstraints.PAGE_START:
            case GridBagConstraints.NORTH:
                r.x += diffx / 2;
                break;
            case GridBagConstraints.NORTHEAST:
                r.x += diffx;
                break;
            case GridBagConstraints.EAST:
                r.x += diffx;
                r.y += diffy / 2;
                break;
            case GridBagConstraints.SOUTHEAST:
                r.x += diffx;
                r.y += diffy;
                break;
            case GridBagConstraints.PAGE_END:
            case GridBagConstraints.SOUTH:
                r.x += diffx / 2;
                r.y += diffy;
                break;
            case GridBagConstraints.SOUTHWEST:
                r.y += diffy;
                break;
            case GridBagConstraints.WEST:
                r.y += diffy / 2;
                break;
            case GridBagConstraints.NORTHWEST:
                break;
            // The orientation-relative anchors below use the JDK's left-to-right branch,
            // because this port has no ComponentOrientation.
            case GridBagConstraints.LINE_START:
                r.y += diffy / 2;
                break;
            case GridBagConstraints.LINE_END:
                r.x += diffx;
                r.y += diffy / 2;
                break;
            case GridBagConstraints.FIRST_LINE_START:
                break;
            case GridBagConstraints.FIRST_LINE_END:
                r.x += diffx;
                break;
            case GridBagConstraints.LAST_LINE_START:
                r.y += diffy;
                break;
            case GridBagConstraints.LAST_LINE_END:
                r.x += diffx;
                r.y += diffy;
                break;
            default:
                throw new IllegalArgumentException("illegal anchor value");
        }
    }

    /** Centres a component that fills neither direction vertically in its cell. */
    private void centerVertically(GridBagConstraints cons, Rectangle r, int cellHeight) {
        if (!cons.isVerticallyResizable()) {
            r.y += Math.max(0, (cellHeight - cons.insets.top - cons.insets.bottom
                    - cons.minHeight - cons.ipady) / 2);
        }
    }

    /**
     * The JDK's {@code java.awt.GridBagLayoutInfo}, nested here because this port cannot add
     * a new top-level {@code java.awt} class.
     */
    public static class GridBagLayoutInfo {
        /** Number of columns. */
        int width;

        /** Number of rows. */
        int height;

        /** Untranslated origin of the grid's top-left cell. */
        int startx;

        /** Untranslated origin of the grid's top-left cell. */
        int starty;

        /** Per-column minimum widths. */
        int[] minWidth;

        /** Per-row minimum heights. */
        int[] minHeight;

        /** Per-column weights. */
        double[] weightX;

        /** Per-row weights. */
        double[] weightY;

        GridBagLayoutInfo(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}

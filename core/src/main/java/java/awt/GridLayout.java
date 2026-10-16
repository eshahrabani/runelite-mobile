package java.awt;

/**
 * AWT {@code GridLayout}: the JDK's equal-size rectangular grid layout.
 *
 * <p>Hand-written because the Android runtime has no {@code java.desktop} and this
 * layout is really used by the port's Swing runtime: {@code net.runelite.client.ui
 * .PluginPanel} and RuneLite's {@code DynamicGridLayout} (which subclasses this class
 * and calls {@code getRows()/getColumns()/getHgap()/getVgap()} plus its own size
 * arithmetic) drive every side-panel card list.
 *
 * <p>Implemented rules (JDK):
 * <ul>
 * <li>Row/column normalisation: if {@code rows > 0} then
 *     {@code columns = (components + rows - 1) / rows}, else
 *     {@code rows = (components + columns - 1) / columns} — i.e. a non-zero row count
 *     always wins and the column count is derived from it. Both zero is rejected by
 *     every constructor and setter.</li>
 * <li>The grid is not laid out at all when the container has no children.</li>
 * <li>Layout divides the space left after insets and gaps into equal cells; the
 *     remainder that integer division drops is split evenly between the leading and
 *     trailing edges ({@code extraWidthAvailable}) so the grid stays centred, which is
 *     the JDK's behaviour since the 4370316 fix.</li>
 * <li>Preferred/minimum size is the largest child's preferred/minimum size times the
 *     column/row count, plus the gaps, plus the insets.</li>
 * </ul>
 *
 * <p>Port deviations:
 * <ul>
 * <li>This port's {@code java.awt.Component} has no {@code ComponentOrientation}, so the
 *     JDK's right-to-left branch is unreachable and the left-to-right branch is always
 *     used (the JDK default).</li>
 * <li>For an empty container the JDK's arithmetic degenerates (a grid with no children
 *     normalises to zero columns, so the gap term becomes negative). The port reports
 *     the conventional unconfigured-cell default instead: a cell is 100 pixels, i.e.
 *     {@code rows * (100 + vgap) + vgap} by {@code columns * (100 + hgap) + hgap}.</li>
 * </ul>
 */
public class GridLayout implements LayoutManager, java.io.Serializable {

    /** Horizontal gap between columns. */
    int hgap;

    /** Vertical gap between rows. */
    int vgap;

    /** Number of rows; {@code 0} means "as many as fit the column count". */
    int rows;

    /** Number of columns; {@code 0} means "as many as fit the row count". */
    int cols;

    /** Creates a grid with one row and as many columns as there are components. */
    public GridLayout() {
        this(1, 0, 0, 0);
    }

    /**
     * Creates a grid with the given row/column count and no gaps.
     *
     * @throws IllegalArgumentException if both counts are zero
     */
    public GridLayout(int rows, int cols) {
        this(rows, cols, 0, 0);
    }

    /**
     * Creates a grid with the given row/column count and gaps.
     *
     * @throws IllegalArgumentException if both counts are zero
     */
    public GridLayout(int rows, int cols, int hgap, int vgap) {
        if ((rows == 0) && (cols == 0)) {
            throw new IllegalArgumentException("rows and cols cannot both be zero");
        }
        this.rows = rows;
        this.cols = cols;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    /** Returns the row count as set (not normalised against the child count). */
    public int getRows() {
        return rows;
    }

    /** Sets the row count. */
    public void setRows(int rows) {
        if ((rows == 0) && (this.cols == 0)) {
            throw new IllegalArgumentException("rows and cols cannot both be zero");
        }
        this.rows = rows;
    }

    /** Returns the column count as set (not normalised against the child count). */
    public int getColumns() {
        return cols;
    }

    /** Sets the column count; ignored while the row count is non-zero. */
    public void setColumns(int cols) {
        if ((cols == 0) && (this.rows == 0)) {
            throw new IllegalArgumentException("rows and cols cannot both be zero");
        }
        this.cols = cols;
    }

    /** Returns the horizontal gap between columns. */
    public int getHgap() {
        return hgap;
    }

    /** Sets the horizontal gap between columns. */
    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    /** Returns the vertical gap between rows. */
    public int getVgap() {
        return vgap;
    }

    /** Sets the vertical gap between rows. */
    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    /** Unused: a grid places every child in the next cell in Z-order. */
    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    /** Unused: a grid places every child in the next cell in Z-order. */
    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int ncomponents = parent.getComponentCount();
            int nrows = rows;
            int ncols = cols;

            if (nrows > 0) {
                ncols = (ncomponents + nrows - 1) / nrows;
            } else {
                nrows = (ncomponents + ncols - 1) / ncols;
            }
            if (ncomponents == 0) {
                return emptyGridSize(insets, nrows, ncols);
            }
            int w = 0;
            int h = 0;
            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                Dimension d = comp.getPreferredSize();
                if (w < d.width) {
                    w = d.width;
                }
                if (h < d.height) {
                    h = d.height;
                }
            }
            return new Dimension(insets.left + insets.right + ncols * w + (ncols - 1) * hgap,
                    insets.top + insets.bottom + nrows * h + (nrows - 1) * vgap);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int ncomponents = parent.getComponentCount();
            int nrows = rows;
            int ncols = cols;

            if (nrows > 0) {
                ncols = (ncomponents + nrows - 1) / nrows;
            } else {
                nrows = (ncomponents + ncols - 1) / ncols;
            }
            if (ncomponents == 0) {
                return emptyGridSize(insets, nrows, ncols);
            }
            int w = 0;
            int h = 0;
            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                Dimension d = comp.getMinimumSize();
                if (w < d.width) {
                    w = d.width;
                }
                if (h < d.height) {
                    h = d.height;
                }
            }
            return new Dimension(insets.left + insets.right + ncols * w + (ncols - 1) * hgap,
                    insets.top + insets.bottom + nrows * h + (nrows - 1) * vgap);
        }
    }

    /**
     * The size of a grid with no children, where the JDK's per-cell arithmetic has no
     * children to measure: every cell defaults to 100 pixels.
     */
    private Dimension emptyGridSize(Insets insets, int nrows, int ncols) {
        return new Dimension(insets.left + insets.right + ncols * (100 + hgap) + hgap,
                insets.top + insets.bottom + nrows * (100 + vgap) + vgap);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int ncomponents = parent.getComponentCount();
            int nrows = rows;
            int ncols = cols;

            if (ncomponents == 0) {
                return;
            }
            if (nrows > 0) {
                ncols = (ncomponents + nrows - 1) / nrows;
            } else {
                nrows = (ncomponents + ncols - 1) / ncols;
            }
            // The JDK divides the usable space into equal cells and splits the pixels
            // dropped by the integer division between the leading and trailing edges.
            int totalGapsWidth = (ncols - 1) * hgap;
            int widthWOInsets = parent.width - (insets.left + insets.right);
            int widthOnComponent = (widthWOInsets - totalGapsWidth) / ncols;
            int extraWidthAvailable = (widthWOInsets - (widthOnComponent * ncols + totalGapsWidth)) / 2;

            int totalGapsHeight = (nrows - 1) * vgap;
            int heightWOInsets = parent.height - (insets.top + insets.bottom);
            int heightOnComponent = (heightWOInsets - totalGapsHeight) / nrows;
            int extraHeightAvailable = (heightWOInsets - (heightOnComponent * nrows + totalGapsHeight)) / 2;

            // Left-to-right only: this port has no ComponentOrientation (JDK default).
            for (int c = 0, x = insets.left + extraWidthAvailable; c < ncols; c++, x += widthOnComponent + hgap) {
                for (int r = 0, y = insets.top + extraHeightAvailable; r < nrows; r++, y += heightOnComponent + vgap) {
                    int i = r * ncols + c;
                    if (i < ncomponents) {
                        parent.getComponent(i).setBounds(x, y, widthOnComponent, heightOnComponent);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + ",rows=" + rows + ",cols=" + cols + "]";
    }
}

package java.awt;

/**
 * AWT {@code GridBagConstraints}: the per-component constraint object consumed by
 * {@link GridBagLayout}.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the
 * port's Swing runtime really runs this layout (RuneLite's own panels — the config
 * panels, the hiscore/grand exchange/skill calculator panels — build their forms with
 * {@code GridBagLayout}). Unlike the generated data-only stub it replaces, this class
 * carries the full JDK constant set, the JDK field defaults and the package-private
 * scratch space the layout algorithm needs, so {@code GridBagLayout} can be a faithful
 * port instead of a no-op.
 *
 * <p>Port deviation: this port's {@code java.awt.Component} has no
 * {@code getBaseline(int,int)} / {@code getBaselineResizeBehavior()}, so the JDK's
 * baseline scratch fields ({@code ascent}, {@code descent}, {@code centerOffset},
 * {@code centerPadding}, {@code baselineResizeBehavior}) do not exist. Every component
 * therefore behaves like a JDK component whose baseline is unavailable, and
 * {@link GridBagLayout} routes the {@code BASELINE*} anchors through exactly the code
 * path the JDK uses for that case ({@code ascent < 0}).
 */
public class GridBagConstraints implements Cloneable, java.io.Serializable {

    /** Place the component at its preferred position (gridx/gridy). */
    public static final int RELATIVE = -1;

    /** Occupy all remaining cells in the row/column. */
    public static final int REMAINDER = 0;

    /** Do not resize the component. */
    public static final int NONE = 0;

    /** Resize the component both horizontally and vertically. */
    public static final int BOTH = 1;

    /** Resize the component horizontally only. */
    public static final int HORIZONTAL = 2;

    /** Resize the component vertically only. */
    public static final int VERTICAL = 3;

    /** Centre the component inside its cell. */
    public static final int CENTER = 10;

    /** Put the component at the top of its cell. */
    public static final int NORTH = 11;

    /** Put the component at the top-right corner of its cell. */
    public static final int NORTHEAST = 12;

    /** Put the component at the right of its cell. */
    public static final int EAST = 13;

    /** Put the component at the bottom-right corner of its cell. */
    public static final int SOUTHEAST = 14;

    /** Put the component at the bottom of its cell. */
    public static final int SOUTH = 15;

    /** Put the component at the bottom-left corner of its cell. */
    public static final int SOUTHWEST = 16;

    /** Put the component at the left of its cell. */
    public static final int WEST = 17;

    /** Put the component at the top-left corner of its cell. */
    public static final int NORTHWEST = 18;

    /** Synonym for {@link #NORTH}. */
    public static final int PAGE_START = 19;

    /** Synonym for {@link #SOUTH}. */
    public static final int PAGE_END = 20;

    /** Synonym for {@link #WEST}. */
    public static final int LINE_START = 21;

    /** Synonym for {@link #EAST}. */
    public static final int LINE_END = 22;

    /** Synonym for {@link #NORTHWEST}. */
    public static final int FIRST_LINE_START = 23;

    /** Synonym for {@link #NORTHEAST}. */
    public static final int FIRST_LINE_END = 24;

    /** Synonym for {@link #SOUTHWEST}. */
    public static final int LAST_LINE_START = 25;

    /** Synonym for {@link #SOUTHEAST}. */
    public static final int LAST_LINE_END = 26;

    /** Align the component on the row's baseline (centred here, see the class note). */
    public static final int BASELINE = 0x100;

    /** Baseline alignment, first line relative. */
    public static final int BASELINE_LEADING = 0x200;

    /** Baseline alignment, last line relative. */
    public static final int BASELINE_TRAILING = 0x300;

    /** Put the component above the row's baseline. */
    public static final int ABOVE_BASELINE = 0x400;

    /** Above-baseline alignment, first line relative. */
    public static final int ABOVE_BASELINE_LEADING = 0x500;

    /** Above-baseline alignment, last line relative. */
    public static final int ABOVE_BASELINE_TRAILING = 0x600;

    /** Put the component below the row's baseline. */
    public static final int BELOW_BASELINE = 0x700;

    /** Below-baseline alignment, first line relative. */
    public static final int BELOW_BASELINE_LEADING = 0x800;

    /** Below-baseline alignment, last line relative. */
    public static final int BELOW_BASELINE_TRAILING = 0x900;

    /** The cell column containing the component's leading edge. */
    public int gridx;

    /** The cell row containing the component's top edge. */
    public int gridy;

    /** How many cells the component spans horizontally. */
    public int gridwidth;

    /** How many cells the component spans vertically. */
    public int gridheight;

    /** Extra horizontal space share used when the container is wider than needed. */
    public double weightx;

    /** Extra vertical space share used when the container is taller than needed. */
    public double weighty;

    /** Where to place the component inside its cell when it is smaller than the cell. */
    public int anchor;

    /** How to resize the component when its cell is larger than it needs. */
    public int fill;

    /** External padding: the space between the component and its cell's edges. */
    public Insets insets;

    /** Internal padding: added to the component's preferred size on both sides. */
    public int ipadx;

    /** Internal padding: added to the component's preferred size on top and bottom. */
    public int ipady;

    // ---------------------------------------------------- layout scratch space
    // Exactly the JDK's package-private fields, filled in by GridBagLayout while it
    // resolves relative grid positions and distributes size/weight over the cells.

    int tempX;
    int tempY;
    int tempWidth;
    int tempHeight;
    int minWidth;
    int minHeight;

    /**
     * Creates constraints with the JDK defaults: {@code RELATIVE} position,
     * 1x1 span, no weight, {@code CENTER}/{@code NONE}, zero insets and pads.
     */
    public GridBagConstraints() {
        gridx = RELATIVE;
        gridy = RELATIVE;
        gridwidth = 1;
        gridheight = 1;

        weightx = 0;
        weighty = 0;
        anchor = CENTER;
        fill = NONE;

        insets = new Insets(0, 0, 0, 0);
        ipadx = 0;
        ipady = 0;
    }

    /**
     * Creates fully specified constraints (the JDK's 11-argument constructor).
     */
    public GridBagConstraints(int gridx, int gridy, int gridwidth, int gridheight, double weightx, double weighty,
                              int anchor, int fill, Insets insets, int ipadx, int ipady) {
        this.gridx = gridx;
        this.gridy = gridy;
        this.gridwidth = gridwidth;
        this.gridheight = gridheight;
        this.fill = fill;
        this.ipadx = ipadx;
        this.ipady = ipady;
        this.insets = insets;
        this.anchor = anchor;
        this.weightx = weightx;
        this.weighty = weighty;
    }

    /** Returns a copy; {@link Insets} are duplicated so the copy cannot alias the original. */
    @Override
    public Object clone() {
        try {
            GridBagConstraints c = (GridBagConstraints) super.clone();
            c.insets = new Insets(insets.top, insets.left, insets.bottom, insets.right);
            return c;
        } catch (CloneNotSupportedException e) {
            // Cloneable is implemented, so this cannot happen.
            throw new InternalError(e);
        }
    }

    /** True when {@code fill} lets the component grow horizontally. */
    public boolean isHorizontallyResizable() {
        return (fill == HORIZONTAL || fill == BOTH);
    }

    /** True when {@code fill} lets the component grow vertically. */
    public boolean isVerticallyResizable() {
        return (fill == VERTICAL || fill == BOTH);
    }
}

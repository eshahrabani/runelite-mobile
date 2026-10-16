package java.awt;

/**
 * AWT {@code BorderLayout}: the JDK's five-region layout (NORTH, SOUTH, WEST, EAST,
 * CENTER).
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the
 * port's Swing runtime really runs this layout: the sidebar root, every RuneLite
 * {@code PluginPanel} and the whole client window use it. The generated data-only stub
 * laid nothing out, which left every panel at its zero size.
 *
 * <p>Implemented JDK rules:
 * <ul>
 * <li>The four relative constraints ({@code PAGE_START}/{@code PAGE_END}/
 *     {@code LINE_START}/{@code LINE_END}, and their older synonyms
 *     {@code BEFORE_FIRST_LINE}/{@code AFTER_LAST_LINE}/{@code BEFORE_LINE_BEGINS}/
 *     {@code AFTER_LINE_ENDS}) take precedence over the absolute ones
 *     ({@code NORTH}/{@code SOUTH}/{@code WEST}/{@code EAST}).</li>
 * <li>Placement order: NORTH, SOUTH, EAST, WEST and finally CENTER, which absorbs
 *     whatever space is left after the other four.</li>
 * <li>Explicit {@code addLayoutComponent(Component, Object)} accepts a {@code String}
 *     (or {@code null}, which means CENTER); anything else is rejected, and an unknown
 *     name throws {@code IllegalArgumentException}.</li>
 * <li>An invisible component is treated as absent ({@code getChild} returns
 *     {@code null} for it).</li>
 * <li>Preferred/minimum sizes add the insets, one {@code hgap}/{@code vgap} per
 *     non-CENTER child and the cell size of every child.</li>
 * </ul>
 *
 * <p>Port deviation: this port's {@code java.awt.Component} has no
 * {@code ComponentOrientation}, so the JDK's right-to-left branch is unreachable and the
 * left-to-right branch is always taken (the JDK default). The {@code (int, int, boolean)}
 * constructor is the JDK 1.0 signature; the {@code boolean} argument was the obsolete
 * "relative to the container's orientation" flag and is stored but not used, exactly as
 * the modern JDK ignores it.
 */
public class BorderLayout implements LayoutManager2, java.io.Serializable {

    /** Top of the container. */
    public static final String NORTH = "North";

    /** Bottom of the container. */
    public static final String SOUTH = "South";

    /** Right side of the container. */
    public static final String EAST = "East";

    /** Left side of the container. */
    public static final String WEST = "West";

    /** Middle of the container; takes all remaining space. */
    public static final String CENTER = "Center";

    /** Synonym for {@link #PAGE_START}. */
    public static final String BEFORE_FIRST_LINE = "First";

    /** Synonym for {@link #PAGE_END}. */
    public static final String AFTER_LAST_LINE = "Last";

    /** Synonym for {@link #LINE_START}. */
    public static final String BEFORE_LINE_BEGINS = "Before";

    /** Synonym for {@link #LINE_END}. */
    public static final String AFTER_LINE_ENDS = "After";

    /** Top of the container (orientation relative). */
    public static final String PAGE_START = BEFORE_FIRST_LINE;

    /** Bottom of the container (orientation relative). */
    public static final String PAGE_END = AFTER_LAST_LINE;

    /** Leading edge (orientation relative). */
    public static final String LINE_START = BEFORE_LINE_BEGINS;

    /** Trailing edge (orientation relative). */
    public static final String LINE_END = AFTER_LINE_ENDS;

    int hgap;
    int vgap;

    /** Absolute-region children. */
    Component north;
    Component south;
    Component east;
    Component west;

    /** CENTER child: laid out last, gets the remaining space. */
    Component center;

    /** Orientation-relative children; they take precedence over the absolute ones. */
    Component firstLine;
    Component lastLine;
    Component firstItem;
    Component lastItem;

    /** The obsolete constructor's orientation flag; stored for compatibility only. */
    private boolean isRelative;

    /** Creates a border layout with no gaps. */
    public BorderLayout() {
        this(0, 0);
    }

    /** Creates a border layout with the given horizontal and vertical gaps. */
    public BorderLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    /**
     * JDK 1.0 constructor: the {@code isRelative} flag, which selected orientation
     * relative placement, has no effect in the modern JDK and none here.
     */
    public BorderLayout(int hgap, int vgap, boolean isRelative) {
        this(hgap, vgap);
        this.isRelative = isRelative;
    }

    /** Returns the horizontal gap between the regions. */
    public int getHgap() {
        return hgap;
    }

    /** Sets the horizontal gap between the regions. */
    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    /** Returns the vertical gap between the regions. */
    public int getVgap() {
        return vgap;
    }

    /** Sets the vertical gap between the regions. */
    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        synchronized (comp.getTreeLock()) {
            if ((constraints == null) || (constraints instanceof String)) {
                addLayoutComponent((String) constraints, comp);
            } else {
                throw new IllegalArgumentException("cannot add to layout: constraint must be a string (or null)");
            }
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        synchronized (comp.getTreeLock()) {
            if (name == null) {
                name = CENTER;
            }
            if (CENTER.equals(name)) {
                center = comp;
            } else if (NORTH.equals(name)) {
                north = comp;
            } else if (SOUTH.equals(name)) {
                south = comp;
            } else if (EAST.equals(name)) {
                east = comp;
            } else if (WEST.equals(name)) {
                west = comp;
            } else if (BEFORE_FIRST_LINE.equals(name)) {
                firstLine = comp;
            } else if (AFTER_LAST_LINE.equals(name)) {
                lastLine = comp;
            } else if (BEFORE_LINE_BEGINS.equals(name)) {
                firstItem = comp;
            } else if (AFTER_LINE_ENDS.equals(name)) {
                lastItem = comp;
            } else {
                throw new IllegalArgumentException("cannot add to layout: unknown constraint: " + name);
            }
        }
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        synchronized (comp.getTreeLock()) {
            if (comp == center) {
                center = null;
            } else if (comp == north) {
                north = null;
            } else if (comp == south) {
                south = null;
            } else if (comp == east) {
                east = null;
            } else if (comp == west) {
                west = null;
            }
            if (comp == firstLine) {
                firstLine = null;
            } else if (comp == lastLine) {
                lastLine = null;
            } else if (comp == firstItem) {
                firstItem = null;
            } else if (comp == lastItem) {
                lastItem = null;
            }
        }
    }

    /**
     * Returns the child added with the given constraint, using the absolute constants;
     * the relative constants resolve to their absolute counterpart.
     *
     * @throws IllegalArgumentException for an unknown constraint
     */
    public Component getLayoutComponent(Object constraints) {
        if (CENTER.equals(constraints)) {
            return center;
        } else if (NORTH.equals(constraints)) {
            return north;
        } else if (SOUTH.equals(constraints)) {
            return south;
        } else if (WEST.equals(constraints)) {
            return west;
        } else if (EAST.equals(constraints)) {
            return east;
        } else if (PAGE_START.equals(constraints)) {
            return firstLine;
        } else if (PAGE_END.equals(constraints)) {
            return lastLine;
        } else if (LINE_START.equals(constraints)) {
            return firstItem;
        } else if (LINE_END.equals(constraints)) {
            return lastItem;
        } else {
            throw new IllegalArgumentException("cannot get component: unknown constraint: " + constraints);
        }
    }

    /**
     * Returns the component that occupies the given absolute position, honouring the
     * relative constraints first (they win over the absolute ones, as the JDK
     * documents).
     */
    public Component getLayoutComponent(Container target, Object constraints) {
        boolean ltr = true; // no ComponentOrientation in this port; LTR is the JDK default
        Component result = null;

        if (NORTH.equals(constraints)) {
            result = (firstLine != null) ? firstLine : north;
        } else if (SOUTH.equals(constraints)) {
            result = (lastLine != null) ? lastLine : south;
        } else if (WEST.equals(constraints)) {
            result = ltr ? firstItem : lastItem;
            if (result == null) {
                result = west;
            }
        } else if (EAST.equals(constraints)) {
            result = ltr ? lastItem : firstItem;
            if (result == null) {
                result = east;
            }
        } else if (CENTER.equals(constraints)) {
            result = center;
        } else {
            throw new IllegalArgumentException("cannot get component: invalid constraint: " + constraints);
        }

        return result;
    }

    /** Returns the constraint string a child was added with, or {@code null}. */
    public Object getConstraints(Component comp) {
        if (comp == null) {
            return null;
        }
        if (comp == center) {
            return CENTER;
        } else if (comp == north) {
            return NORTH;
        } else if (comp == south) {
            return SOUTH;
        } else if (comp == west) {
            return WEST;
        } else if (comp == east) {
            return EAST;
        } else if (comp == firstLine) {
            return PAGE_START;
        } else if (comp == lastLine) {
            return PAGE_END;
        } else if (comp == firstItem) {
            return LINE_START;
        } else if (comp == lastItem) {
            return LINE_END;
        }
        return null;
    }

    /** The child that occupies the given absolute position, or {@code null}. */
    private Component getChild(String key, boolean ltr) {
        Component result = null;

        if (key == NORTH) {
            result = (firstLine != null) ? firstLine : north;
        } else if (key == SOUTH) {
            result = (lastLine != null) ? lastLine : south;
        } else if (key == WEST) {
            result = ltr ? firstItem : lastItem;
            if (result == null) {
                result = west;
            }
        } else if (key == EAST) {
            result = ltr ? lastItem : firstItem;
            if (result == null) {
                result = east;
            }
        } else if (key == CENTER) {
            result = center;
        }
        if (result != null && !result.visible) {
            result = null;
        }
        return result;
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            Component c;

            if ((c = getChild(EAST, true)) != null) {
                Dimension d = c.getPreferredSize();
                dim.width += d.width + hgap;
                dim.height = Math.max(d.height, dim.height);
            }
            if ((c = getChild(WEST, true)) != null) {
                Dimension d = c.getPreferredSize();
                dim.width += d.width + hgap;
                dim.height = Math.max(d.height, dim.height);
            }
            if ((c = getChild(CENTER, true)) != null) {
                Dimension d = c.getPreferredSize();
                dim.width += d.width;
                dim.height = Math.max(d.height, dim.height);
            }
            if ((c = getChild(NORTH, true)) != null) {
                Dimension d = c.getPreferredSize();
                dim.width = Math.max(d.width, dim.width);
                dim.height += d.height + vgap;
            }
            if ((c = getChild(SOUTH, true)) != null) {
                Dimension d = c.getPreferredSize();
                dim.width = Math.max(d.width, dim.width);
                dim.height += d.height + vgap;
            }

            Insets insets = target.getInsets();
            dim.width += insets.left + insets.right;
            dim.height += insets.top + insets.bottom;

            return dim;
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            Component c;

            if ((c = getChild(EAST, true)) != null) {
                Dimension d = c.getMinimumSize();
                dim.width += d.width + hgap;
                dim.height = Math.max(d.height, dim.height);
            }
            if ((c = getChild(WEST, true)) != null) {
                Dimension d = c.getMinimumSize();
                dim.width += d.width + hgap;
                dim.height = Math.max(d.height, dim.height);
            }
            if ((c = getChild(CENTER, true)) != null) {
                Dimension d = c.getMinimumSize();
                dim.width += d.width;
                dim.height = Math.max(d.height, dim.height);
            }
            if ((c = getChild(NORTH, true)) != null) {
                Dimension d = c.getMinimumSize();
                dim.width = Math.max(d.width, dim.width);
                dim.height += d.height + vgap;
            }
            if ((c = getChild(SOUTH, true)) != null) {
                Dimension d = c.getMinimumSize();
                dim.width = Math.max(d.width, dim.width);
                dim.height += d.height + vgap;
            }

            Insets insets = target.getInsets();
            dim.width += insets.left + insets.right;
            dim.height += insets.top + insets.bottom;

            return dim;
        }
    }

    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int top = insets.top;
            int bottom = target.height - insets.bottom;
            int left = insets.left;
            int right = target.width - insets.right;
            Component c;

            if ((c = getChild(NORTH, true)) != null) {
                c.setSize(right - left, c.height);
                Dimension d = c.getPreferredSize();
                c.setBounds(left, top, right - left, d.height);
                top += d.height + vgap;
            }
            if ((c = getChild(SOUTH, true)) != null) {
                c.setSize(right - left, c.height);
                Dimension d = c.getPreferredSize();
                c.setBounds(left, bottom - d.height, right - left, d.height);
                bottom -= d.height + vgap;
            }
            if ((c = getChild(EAST, true)) != null) {
                c.setSize(c.width, bottom - top);
                Dimension d = c.getPreferredSize();
                c.setBounds(right - d.width, top, d.width, bottom - top);
                right -= d.width + hgap;
            }
            if ((c = getChild(WEST, true)) != null) {
                c.setSize(c.width, bottom - top);
                Dimension d = c.getPreferredSize();
                c.setBounds(left, top, d.width, bottom - top);
                left += d.width + hgap;
            }
            // CENTER is laid out last and keeps whatever the other four left over.
            if ((c = getChild(CENTER, true)) != null) {
                c.setBounds(left, top, right - left, bottom - top);
            }
        }
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
        // No cached layout state.
    }

    @Override
    public String toString() {
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + "]";
    }
}

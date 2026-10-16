package java.awt;

/**
 * AWT {@code FlowLayout}: the JDK's wrap-at-the-container-width layout.
 *
 * <p>Layout rule (JDK): components are placed left to right with {@code hgap} between
 * them; a component that does not fit wraps to the next row, which is {@code vgap}
 * below. Rows are aligned according to {@link #getAlignment()}; the last row is aligned
 * the same way. Preferred size is the size of a single row (all components side by side),
 * minimum size the widest component.
 */
public class FlowLayout implements LayoutManager, java.io.Serializable {
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int LEADING = 3;
    public static final int TRAILING = 4;

    private int align = CENTER;
    private int hgap;
    private int vgap;
    private boolean alignOnBaseline;

    public FlowLayout() {
        this(CENTER, 5, 5);
    }

    public FlowLayout(int align) {
        this(align, 5, 5);
    }

    public FlowLayout(int align, int hgap, int vgap) {
        setAlignment(align);
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getAlignment() {
        return align;
    }

    public void setAlignment(int align) {
        this.align = align == LEFT || align == CENTER || align == RIGHT || align == LEADING || align == TRAILING
                ? align : CENTER;
    }

    public int getHgap() {
        return hgap;
    }

    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    public int getVgap() {
        return vgap;
    }

    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    public void setAlignOnBaseline(boolean alignOnBaseline) {
        this.alignOnBaseline = alignOnBaseline;
    }

    public boolean getAlignOnBaseline() {
        return alignOnBaseline;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // FlowLayout has no per-component state.
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int h = 0;
            int w = 0;
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                if (d == null) {
                    continue;
                }
                w += d.width + hgap;
                h = Math.max(h, d.height);
            }
            if (w > 0) {
                w -= hgap;
            }
            Insets insets = target.getInsets();
            return new Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h + vgap * 2);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            boolean first = true;
            int w = 0;
            int h = 0;
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getMinimumSize();
                if (d == null) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    w += hgap;
                }
                w += d.width;
                h = Math.max(h, d.height);
            }
            Insets insets = target.getInsets();
            return new Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h);
        }
    }

    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxWidth = target.getWidth() - (insets.left + insets.right + hgap * 2);
            int rowWidth = 0;
            int rowHeight = 0;
            int start = 0;
            Component[] components = target.getComponents();
            int nmembers = components.length;
            for (int i = 0; i < nmembers; i++) {
                Component c = components[i];
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                if (d == null) {
                    continue;
                }
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    layoutRow(target, components, start, i, rowWidth, rowHeight, maxWidth, insets);
                    rowWidth = 0;
                    rowHeight = 0;
                    start = i;
                }
                rowWidth += d.width + hgap;
                rowHeight = Math.max(rowHeight, d.height);
            }
            layoutRow(target, components, start, nmembers, rowWidth, rowHeight, maxWidth, insets);
        }
    }

    private void layoutRow(Container target, Component[] components, int start, int end,
                           int rowWidth, int rowHeight, int maxWidth, Insets insets) {
        int x = insets.left + hgap;
        if (align == CENTER || align == TRAILING) {
            x += (maxWidth - rowWidth) / 2;
            if (align == TRAILING) {
                x += (maxWidth - rowWidth) - (maxWidth - rowWidth) / 2;
            }
        } else if (align == RIGHT) {
            x += maxWidth - rowWidth;
        }
        int y = insets.top + vgap;
        for (int i = start; i < end; i++) {
            Component c = components[i];
            if (!c.isVisible()) {
                continue;
            }
            Dimension d = c.getPreferredSize();
            if (d == null) {
                continue;
            }
            c.setBounds(x, y, d.width, d.height);
            x += d.width + hgap;
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + ",align=" + align + "]";
    }
}

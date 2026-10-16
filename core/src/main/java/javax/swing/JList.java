package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Swing {@code JList}: a vertical (or wrapped grid) list of values painted through a
 * {@link ListCellRenderer}.
 *
 * <p>Hand-written because the port's sidebar shows real lists. The implementation covers
 * the slice the port needs — model wiring, a {@link ListSelectionModel}, cell geometry,
 * row painting and tap-to-select — and intentionally omits drag selection, inline cell
 * editing, keyboard navigation and the accessibility tree (none of which the shipped
 * panels exercise).
 *
 * <p>Geometry: rows are measured from the renderer's preferred size; the list's preferred
 * size is the union of the cells, while {@link #getVisibleRowCount} only sizes
 * {@link #getPreferredScrollableViewportSize}. Wrapped layouts use a uniform cell size
 * derived from the first row (the JDK measures per cell there as well but treats them as
 * uniform when {@code fixedCellWidth/Height} are unset).
 *
 * <p>Selection: a tap selects the row under the pointer; the selection mode is honoured by
 * the shared {@link ListSelectionModel}, and Ctrl/Shift gestures are not wired (touch, not
 * a desktop mouse).
 */
public class JList<E> extends JComponent implements Scrollable, javax.accessibility.Accessible {

    private static final long serialVersionUID = -8161707299047008959L;

    /** Cells stack top to bottom. */
    public static final int VERTICAL = 0;
    /** Cells flow in columns, wrapping to the next column at the bottom. */
    public static final int VERTICAL_WRAP = 1;
    /** Cells flow in rows, wrapping to the next row at the right edge. */
    public static final int HORIZONTAL_WRAP = 2;

    private ListModel<E> model;
    private ListSelectionModel selectionModel = new DefaultListSelectionModel();
    private ListCellRenderer<? super E> cellRenderer = new DefaultListCellRenderer();
    private int visibleRowCount = 8;
    private int layoutOrientation = VERTICAL;
    private Color selectionBackground;
    private Color selectionForeground;

    private final ListSelectionListener selectionRepainter = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            repaint();
        }
    };

    private final ListDataListener modelListener = new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
            revalidate();
            repaint();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
            revalidate();
            repaint();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            revalidate();
            repaint();
        }
    };

    public JList() {
        this(new DefaultListModel<E>());
    }

    public JList(ListModel<E> model) {
        setOpaque(true);
        applyDefaults();
        selectionModel.addListSelectionListener(selectionRepainter);
        setModel(model);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleTap(e);
            }
        });
    }

    public JList(E[] listData) {
        this(toModel(listData));
    }

    public JList(java.util.Vector<? extends E> listData) {
        this(toModel(listData));
    }

    private static <T> ListModel<T> toModel(T[] data) {
        DefaultListModel<T> m = new DefaultListModel<>();
        for (T item : data) {
            m.addElement(item);
        }
        return m;
    }

    private static <T> ListModel<T> toModel(java.util.Vector<? extends T> data) {
        DefaultListModel<T> m = new DefaultListModel<>();
        for (T item : data) {
            m.addElement(item);
        }
        return m;
    }

    private void applyDefaults() {
        Color bg = UIManager.getColor("List.background");
        Color fg = UIManager.getColor("List.foreground");
        if (bg != null) {
            setBackground(bg);
        }
        if (fg != null) {
            setForeground(fg);
        }
        java.awt.Font f = UIManager.getFont("List.font");
        if (f != null) {
            setFont(f);
        }
        selectionBackground = UIManager.getColor("List.selectionBackground");
        selectionForeground = UIManager.getColor("List.selectionForeground");
        setPreferredSize(null);
    }

    // ------------------------------------------------------------------ model

    /** Replaces the model and registers this list for its {@code ListDataEvent}s. */
    public void setModel(ListModel<E> model) {
        if (this.model != null) {
            this.model.removeListDataListener(modelListener);
        }
        this.model = model;
        if (model == null) {
            this.model = new DefaultListModel<E>();
        }
        this.model.addListDataListener(modelListener);
        clearSelection();
        revalidate();
        repaint();
    }

    /** The model backing this list; never {@code null}. */
    public ListModel<E> getModel() {
        return model;
    }

    // -------------------------------------------------------------- renderer

    public void setCellRenderer(ListCellRenderer<? super E> cellRenderer) {
        this.cellRenderer = cellRenderer;
        revalidate();
        repaint();
    }

    public ListCellRenderer<? super E> getCellRenderer() {
        return cellRenderer;
    }

    // ------------------------------------------------------------- selection

    public void setSelectionModel(ListSelectionModel selectionModel) {
        if (selectionModel == null) {
            throw new IllegalArgumentException("selectionModel must not be null");
        }
        this.selectionModel.removeListSelectionListener(selectionRepainter);
        this.selectionModel = selectionModel;
        this.selectionModel.addListSelectionListener(selectionRepainter);
        repaint();
    }

    public ListSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void addListSelectionListener(ListSelectionListener listener) {
        selectionModel.addListSelectionListener(listener);
    }

    public void removeListSelectionListener(ListSelectionListener listener) {
        selectionModel.removeListSelectionListener(listener);
    }

    public void setSelectionMode(int selectionMode) {
        selectionModel.setSelectionMode(selectionMode);
    }

    public int getSelectionMode() {
        return selectionModel.getSelectionMode();
    }

    public int getSelectedIndex() {
        return selectionModel.getMinSelectionIndex();
    }

    public int[] getSelectedIndices() {
        return selectionModel.getSelectedIndices();
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < model.getSize()) {
            selectionModel.setSelectionInterval(index, index);
        } else {
            clearSelection();
        }
    }

    public void setSelectedIndices(int[] indices) {
        selectionModel.clearSelection();
        for (int i : indices) {
            if (i >= 0 && i < model.getSize()) {
                selectionModel.addSelectionInterval(i, i);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public List<E> getSelectedValuesList() {
        List<E> out = new ArrayList<>();
        for (int i : selectionModel.getSelectedIndices()) {
            out.add(model.getElementAt(i));
        }
        return out;
    }

    public E getSelectedValue() {
        int index = getSelectedIndex();
        return index < 0 ? null : model.getElementAt(index);
    }

    /** Clears the selection (an empty selection fires no event). */
    public void clearSelection() {
        selectionModel.clearSelection();
    }

    public void ensureIndexIsVisible(int index) {
        Rectangle bounds = getCellBounds(index, index);
        if (bounds != null) {
            scrollRectToVisible(bounds);
        }
    }

    // --------------------------------------------------------------- metrics

    public void setVisibleRowCount(int visibleRowCount) {
        this.visibleRowCount = Math.max(0, visibleRowCount);
        revalidate();
        repaint();
    }

    public int getVisibleRowCount() {
        return visibleRowCount;
    }

    public void setLayoutOrientation(int layoutOrientation) {
        if (layoutOrientation != VERTICAL && layoutOrientation != VERTICAL_WRAP
                && layoutOrientation != HORIZONTAL_WRAP) {
            throw new IllegalArgumentException("invalid layoutOrientation");
        }
        this.layoutOrientation = layoutOrientation;
        revalidate();
        repaint();
    }

    public int getLayoutOrientation() {
        return layoutOrientation;
    }

    public void setSelectionBackground(Color selectionBackground) {
        this.selectionBackground = selectionBackground;
        repaint();
    }

    public Color getSelectionBackground() {
        return selectionBackground;
    }

    public void setSelectionForeground(Color selectionForeground) {
        this.selectionForeground = selectionForeground;
        repaint();
    }

    public Color getSelectionForeground() {
        return selectionForeground;
    }

    @Override
    public void setPreferredSize(Dimension preferredSize) {
        super.setPreferredSize(preferredSize);
    }

    /** Number of visible columns for the wrapped layouts. */
    private int columnCount(int cellWidth) {
        int w = getWidth();
        if (layoutOrientation == VERTICAL || cellWidth <= 0 || w <= 0) {
            return 1;
        }
        return Math.max(1, w / cellWidth);
    }

    /** The cell rectangle for {@code index}, or {@code null} when it is out of range. */
    public Rectangle getCellBounds(int index0, int index1) {
        if (model == null || model.getSize() == 0) {
            return null;
        }
        if (index0 > index1) {
            int t = index0;
            index0 = index1;
            index1 = t;
        }
        if (index0 < 0 || index0 >= model.getSize()) {
            return null;
        }
        index1 = Math.min(index1, model.getSize() - 1);
        Rectangle first = cellGeometry(index0);
        Rectangle last = cellGeometry(index1);
        return first.union(last);
    }

    private Rectangle cellGeometry(int index) {
        if (layoutOrientation == VERTICAL) {
            int y = 0;
            for (int i = 0; i < index; i++) {
                y += rowHeight(i);
            }
            int w = Math.max(getWidth(), 1);
            return new Rectangle(0, y, w, rowHeight(index));
        }
        // Wrapped layouts use one uniform cell size, measured from the first row.
        Dimension cell = measureCell(0, 0);
        int cellW = Math.max(1, cell.width);
        int cellH = Math.max(1, cell.height);
        int columns;
        if (layoutOrientation == HORIZONTAL_WRAP) {
            columns = columnCount(cellW);
            return new Rectangle((index % columns) * cellW, (index / columns) * cellH, cellW, cellH);
        }
        int rows = Math.max(1, (getHeight() > 0 ? getHeight() : cellH * visibleRowCount) / cellH);
        return new Rectangle((index / rows) * cellW, (index % rows) * cellH, cellW, cellH);
    }

    /** The height of row {@code index}. */
    private int rowHeight(int index) {
        return Math.max(1, measureCell(index, getWidth()).height);
    }

    /** Measures the renderer for one value; falls back to the font metrics. */
    private Dimension measureCell(int index, int width) {
        E value = model.getElementAt(index);
        Component c = rendererComponent(value, index, false, false);
        if (c != null) {
            Dimension d = c.getPreferredSize();
            if (d != null && d.height > 0) {
                return new Dimension(width > 0 ? width : Math.max(d.width, 1), d.height);
            }
        }
        FontMetrics fm = getFontMetrics(getFont());
        int h = fm == null ? 16 : fm.getHeight() + 4;
        return new Dimension(width > 0 ? width : 100, h);
    }

    private Component rendererComponent(E value, int index, boolean isSelected, boolean hasFocus) {
        ListCellRenderer<? super E> renderer = cellRenderer;
        if (renderer == null) {
            renderer = new DefaultListCellRenderer();
        }
        return renderer.getListCellRendererComponent(this, value, index, isSelected, hasFocus);
    }

    /** The location of cell {@code index} (top-left), or {@code null} when out of range. */
    public Point indexToLocation(int index) {
        Rectangle r = getCellBounds(index, index);
        return r == null ? null : new Point(r.x, r.y);
    }

    /** The index of the cell containing {@code location}, or {@code -1}. */
    public int locationToIndex(Point location) {
        if (location == null || model == null) {
            return -1;
        }
        int n = model.getSize();
        if (layoutOrientation == VERTICAL) {
            int y = 0;
            for (int i = 0; i < n; i++) {
                int h = rowHeight(i);
                if (location.y >= y && location.y < y + h && location.x >= 0 && location.x < getWidth()) {
                    return i;
                }
                y += h;
            }
            return -1;
        }
        for (int i = 0; i < n; i++) {
            Rectangle r = cellGeometry(i);
            if (r.contains(location)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ geometry

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int n = model == null ? 0 : model.getSize();
        int w = 100;
        int h = 0;
        if (layoutOrientation == VERTICAL) {
            for (int i = 0; i < n; i++) {
                w = Math.max(w, measureCell(i, 0).width);
                h += rowHeight(i);
            }
        } else {
            for (int i = 0; i < n; i++) {
                Rectangle r = cellGeometry(i);
                w = Math.max(w, r.x + r.width);
                h = Math.max(h, r.y + r.height);
            }
        }
        return new Dimension(w, Math.max(h, 1));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        int rows = Math.max(1, visibleRowCount);
        int rowHeight = model != null && model.getSize() > 0 ? rowHeight(0) : 16;
        int w = 100;
        if (model != null && model.getSize() > 0) {
            w = Math.max(w, measureCell(0, 0).width);
        }
        return new Dimension(w, rows * rowHeight);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (model != null && model.getSize() > 0) {
            return Math.max(1, rowHeight(0));
        }
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        int unit = getScrollableUnitIncrement(visibleRect, orientation, direction);
        int extent = visibleRect == null ? 0 : visibleRect.height;
        return Math.max(unit, extent - unit);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // Vertical lists stretch to the viewport width (their rows span it); a wrapped
        // layout sizes itself from its cells.
        return layoutOrientation != HORIZONTAL_WRAP;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // ----------------------------------------------------------------- paint

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (model == null) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        int n = model.getSize();
        if (layoutOrientation == VERTICAL) {
            int y = 0;
            for (int i = 0; i < n; i++) {
                int h = rowHeight(i);
                Rectangle cell = new Rectangle(0, y, Math.max(getWidth(), 1), h);
                if (clip == null || cell.intersects(clip)) {
                    paintCell(g, i, cell);
                }
                y += h;
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            Rectangle cell = cellGeometry(i);
            if (clip == null || cell.intersects(clip)) {
                paintCell(g, i, cell);
            }
        }
    }

    /** Paints row {@code index} at {@code cell}, stamped through the renderer. */
    private void paintCell(Graphics g, int index, Rectangle cell) {
        boolean selected = selectionModel.isSelectedIndex(index);
        Component c = rendererComponent(model.getElementAt(index), index, selected, false);
        if (c == null) {
            return;
        }
        Graphics g2 = g.create();
        try {
            g2.translate(cell.x, cell.y);
            g2.clipRect(0, 0, cell.width, cell.height);
            c.setBounds(0, 0, cell.width, cell.height);
            c.setVisible(true);
            c.paint(g2);
        } finally {
            g2.dispose();
        }
    }

    // ----------------------------------------------------------------- input

    private void handleTap(MouseEvent e) {
        if (!isEnabled()) {
            return;
        }
        int index = locationToIndex(e.getPoint());
        if (index < 0) {
            return;
        }
        // A plain tap replaces the selection (Ctrl/Shift are not wired on touch).
        selectionModel.setSelectionInterval(index, index);
        e.consume();
    }

    @Override
    public void addFocusListener(FocusListener listener) {
        super.addFocusListener(listener);
    }

    @Override
    protected void processFocusEvent(FocusEvent e) {
        super.processFocusEvent(e);
        repaint();
    }
}

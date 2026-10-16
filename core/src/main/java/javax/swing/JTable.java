package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

/**
 * Swing {@code JTable}: a table painted from a {@link TableModel}, with a header row and
 * row selection.
 *
 * <p>Hand-written to the depth the port needs (see the Swing sidebar plan, "B5"): cells are
 * painted through a single {@link JLabel} stamp, column names come from the model and row
 * selection is a {@link ListSelectionModel}. There is no {@code TableColumnModel} on this
 * port, so column widths are apportioned to fill the table width.
 *
 * <p>Deliberately not implemented: cell editing, sorting ({@code RowSorter} is not built),
 * column resizing/reordering, {@code JTableHeader} and the accessibility tree. The
 * {@code TableColumnModelListener}/{@code CellEditorListener}/{@code RowSorterListener}
 * methods exist only because the shipped class declares those interfaces.
 */
public class JTable extends JComponent
        implements TableModelListener, Scrollable, TableColumnModelListener,
        ListSelectionListener, CellEditorListener, javax.accessibility.Accessible,
        RowSorterListener {

    private static final long serialVersionUID = -8499045100598815155L;

    private static final int DEFAULT_ROW_HEIGHT = 20;

    private TableModel dataModel;
    private final ListSelectionModel selectionModel = new DefaultListSelectionModel();
    private final JLabel renderCell = new JLabel();
    private int rowHeight = DEFAULT_ROW_HEIGHT;
    private Color gridColor;
    private Color selectionBackground;
    private Color selectionForeground;

    public JTable(TableModel dm) {
        setOpaque(true);
        Color bg = UIManager.getColor("Table.background");
        Color fg = UIManager.getColor("Table.foreground");
        if (bg != null) {
            setBackground(bg);
        }
        if (fg != null) {
            setForeground(fg);
        }
        java.awt.Font f = UIManager.getFont("Table.font");
        if (f != null) {
            setFont(f);
        }
        gridColor = UIManager.getColor("Table.gridColor");
        selectionBackground = UIManager.getColor("Table.selectionBackground");
        selectionForeground = UIManager.getColor("Table.selectionForeground");
        renderCell.setOpaque(true);
        selectionModel.addListSelectionListener(this);
        setModel(dm);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleTap(e);
            }
        });
    }

    // ------------------------------------------------------------------ model

    public void setModel(TableModel dataModel) {
        if (this.dataModel != null) {
            this.dataModel.removeTableModelListener(this);
        }
        this.dataModel = dataModel;
        if (dataModel != null) {
            dataModel.addTableModelListener(this);
        }
        clearSelection();
        revalidate();
        repaint();
    }

    public TableModel getModel() {
        return dataModel;
    }

    public int getRowCount() {
        return dataModel == null ? 0 : dataModel.getRowCount();
    }

    public int getColumnCount() {
        return dataModel == null ? 0 : dataModel.getColumnCount();
    }

    public Object getValueAt(int row, int column) {
        return dataModel == null ? null : dataModel.getValueAt(row, column);
    }

    public String getColumnName(int column) {
        return dataModel == null ? null : dataModel.getColumnName(column);
    }

    public int getRowHeight() {
        return rowHeight;
    }

    public void setRowHeight(int rowHeight) {
        this.rowHeight = Math.max(1, rowHeight);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------- selection

    public ListSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void setRowSelectionInterval(int index0, int index1) {
        selectionModel.setSelectionInterval(index0, index1);
    }

    public void setRowSelectionAllowed(boolean rowSelectionAllowed) {
        // Row-only selection is the only mode this port implements; the flag is a no-op
        // kept so the shipped callers do not have to know that.
    }

    public boolean isRowSelectionAllowed() {
        return true;
    }

    public int getSelectedRow() {
        return selectionModel.getMinSelectionIndex();
    }

    public int getSelectedRowCount() {
        return selectionModel.getSelectedIndices().length;
    }

    public int[] getSelectedRows() {
        return selectionModel.getSelectedIndices();
    }

    public boolean isRowSelected(int row) {
        return selectionModel.isSelectedIndex(row);
    }

    public void clearSelection() {
        selectionModel.clearSelection();
    }

    public void addListSelectionListener(ListSelectionListener listener) {
        selectionModel.addListSelectionListener(listener);
    }

    public void removeListSelectionListener(ListSelectionListener listener) {
        selectionModel.removeListSelectionListener(listener);
    }

    // -------------------------------------------------------------- geometry

    /** The height of the column-name header. */
    public int getTableHeaderHeight() {
        return rowHeight;
    }

    /** One width per column, apportioned so the columns fill the table width. */
    private int[] columnWidths() {
        int count = Math.max(1, getColumnCount());
        int available = Math.max(1, getWidth() - getInsets().left - getInsets().right);
        int base = available / count;
        int[] widths = new int[count];
        int used = 0;
        for (int i = 0; i < count; i++) {
            widths[i] = base;
            used += base;
        }
        widths[count - 1] += available - used;
        return widths;
    }

    /** The rectangle of one cell, or {@code null} when out of range. */
    public Rectangle getCellRect(int row, int column, boolean includeSpacing) {
        if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
            return null;
        }
        Insets insets = getInsets();
        int[] widths = columnWidths();
        int x = insets.left;
        for (int i = 0; i < column; i++) {
            x += widths[i];
        }
        int y = insets.top + getTableHeaderHeight() + row * rowHeight;
        return new Rectangle(x, y, widths[column], rowHeight);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        return getPreferredScrollableViewportSize();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int width = 0;
        for (int c = 0; c < getColumnCount(); c++) {
            String name = getColumnName(c);
            width += fm == null || name == null ? 75 : Math.max(75, fm.stringWidth(name) + 12);
        }
        return new Dimension(Math.max(width, 60),
                getTableHeaderHeight() + Math.max(1, getRowCount()) * rowHeight);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return rowHeight;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        int extent = visibleRect == null ? 0 : visibleRect.height;
        return Math.max(rowHeight, extent - rowHeight);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // ------------------------------------------------------------------ paint

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (dataModel == null) {
            return;
        }
        Insets insets = getInsets();
        int columns = getColumnCount();
        int rows = getRowCount();
        int[] widths = columnWidths();
        int tableWidth = Math.max(0, getWidth() - insets.left - insets.right);
        Color textColor = getForeground() != null ? getForeground() : Color.BLACK;
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        int baseline = insets.top + (rowHeight + fm.getAscent() - fm.getDescent()) / 2;

        // Header row.
        Color headerBg = getBackground();
        if (headerBg != null) {
            g.setColor(headerBg);
            g.fillRect(insets.left, insets.top, tableWidth, rowHeight);
        }
        g.setColor(textColor);
        int x = insets.left;
        for (int c = 0; c < columns; c++) {
            String name = getColumnName(c);
            if (name != null) {
                Graphics g2 = g.create();
                try {
                    g2.clipRect(x, insets.top, widths[c], rowHeight);
                    g2.drawString(name, x + 4, baseline);
                } finally {
                    g2.dispose();
                }
            }
            x += widths[c];
        }
        if (gridColor != null) {
            g.setColor(gridColor);
            g.drawLine(insets.left, insets.top + rowHeight - 1,
                    insets.left + tableWidth, insets.top + rowHeight - 1);
        }

        Rectangle clip = g.getClipBounds();
        for (int r = 0; r < rows; r++) {
            int y = insets.top + rowHeight + r * rowHeight;
            Rectangle rowRect = new Rectangle(insets.left, y, tableWidth, rowHeight);
            if (clip != null && !rowRect.intersects(clip)) {
                continue;
            }
            boolean selected = selectionModel.isSelectedIndex(r);
            x = insets.left;
            for (int c = 0; c < columns; c++) {
                Object value = dataModel.getValueAt(r, c);
                paintCell(g, value, selected, x, y, widths[c], r, c);
                x += widths[c];
            }
            if (gridColor != null) {
                g.setColor(gridColor);
                g.drawLine(insets.left, y + rowHeight - 1, insets.left + tableWidth, y + rowHeight - 1);
            }
        }
    }

    private void paintCell(Graphics g, Object value, boolean selected, int x, int y, int width,
                           int row, int column) {
        renderCell.setText(value == null ? "" : value.toString());
        renderCell.setFont(getFont());
        if (selected) {
            renderCell.setBackground(selectionBackground != null ? selectionBackground : getBackground());
            renderCell.setForeground(selectionForeground != null ? selectionForeground : getForeground());
        } else {
            renderCell.setBackground(getBackground());
            renderCell.setForeground(getForeground());
        }
        Graphics g2 = g.create();
        try {
            g2.translate(x, y);
            g2.clipRect(0, 0, width, rowHeight);
            renderCell.setBounds(0, 0, width, rowHeight);
            renderCell.setVisible(true);
            renderCell.paint(g2);
        } finally {
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ input

    private void handleTap(MouseEvent e) {
        if (!isEnabled()) {
            return;
        }
        Point p = e.getPoint();
        Insets insets = getInsets();
        int row = (p.y - insets.top - getTableHeaderHeight()) / rowHeight;
        if (row < 0 || row >= getRowCount()) {
            return;
        }
        selectionModel.setSelectionInterval(row, row);
        e.consume();
    }

    // --------------------------------------------------------------- events

    @Override
    public void tableChanged(TableModelEvent e) {
        revalidate();
        repaint();
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        repaint();
    }

    @Override
    public void columnAdded(TableColumnModelEvent e) {
    }

    @Override
    public void columnRemoved(TableColumnModelEvent e) {
    }

    @Override
    public void columnMoved(TableColumnModelEvent e) {
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {
    }

    @Override
    public void columnSelectionChanged(ListSelectionEvent e) {
    }

    @Override
    public void editingStopped(ChangeEvent e) {
    }

    @Override
    public void editingCanceled(ChangeEvent e) {
    }

    @Override
    public void sorterChanged(RowSorterEvent e) {
    }
}

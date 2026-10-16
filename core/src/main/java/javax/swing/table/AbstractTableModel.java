package javax.swing.table;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * Swing {@code AbstractTableModel}: the listener plumbing and {@code fireXxx} helpers
 * shared by table models.
 *
 * <p>Hand-written because the port's {@link javax.swing.JTable} renders and updates real
 * models. The listener store is a plain list (the JDK's {@code EventListenerList} is not an
 * Android class); event ranges follow the JDK's conventions, including {@code HEADER_ROW}
 * and {@code ALL_COLUMNS}.
 */
public abstract class AbstractTableModel implements TableModel, java.io.Serializable {
    private static final long serialVersionUID = -5798593159423650347L;

    private final List<TableModelListener> listenerList = new ArrayList<>();

    @Override
    public int getRowCount() {
        return 0;
    }

    @Override
    public int getColumnCount() {
        return 0;
    }

    @Override
    public String getColumnName(int column) {
        return "A" + (char) ('A' + column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return Object.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
        if (l != null) {
            listenerList.add(l);
        }
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
        listenerList.remove(l);
    }

    /** The currently registered listeners. */
    public TableModelListener[] getTableModelListeners() {
        return listenerList.toArray(new TableModelListener[0]);
    }

    // ----------------------------------------------------------------- events

    /** Every row and column changed (widths and names may have too). */
    public void fireTableDataChanged() {
        fireTableChanged(new TableModelEvent(this));
    }

    /** The whole structure changed. */
    public void fireTableStructureChanged() {
        fireTableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
    }

    /** Rows {@code firstRow..lastRow} were inserted. */
    public void fireTableRowsInserted(int firstRow, int lastRow) {
        fireTableChanged(new TableModelEvent(this, firstRow, lastRow,
                TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
    }

    /** Rows {@code firstRow..lastRow} were updated. */
    public void fireTableRowsUpdated(int firstRow, int lastRow) {
        fireTableChanged(new TableModelEvent(this, firstRow, lastRow,
                TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
    }

    /** Rows {@code firstRow..lastRow} were deleted. */
    public void fireTableRowsDeleted(int firstRow, int lastRow) {
        fireTableChanged(new TableModelEvent(this, firstRow, lastRow,
                TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE));
    }

    /** One cell was updated. */
    public void fireTableCellUpdated(int row, int column) {
        fireTableChanged(new TableModelEvent(this, row, row, column));
    }

    /** Broadcasts {@code e} to every registered listener. */
    public void fireTableChanged(TableModelEvent e) {
        for (TableModelListener l : listenerList.toArray(new TableModelListener[0])) {
            l.tableChanged(e);
        }
    }
}

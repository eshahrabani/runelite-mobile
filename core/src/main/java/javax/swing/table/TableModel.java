package javax.swing.table;

import javax.swing.event.TableModelListener;

/**
 * Swing {@code TableModel}: the data behind a {@link javax.swing.JTable}.
 *
 * <p>Hand-written because the port's sidebar shows real tables. The member set is the
 * JDK's; the port does not use the {@code RowSorter} machinery.
 */
public interface TableModel {
    int getRowCount();

    int getColumnCount();

    String getColumnName(int columnIndex);

    Class<?> getColumnClass(int columnIndex);

    boolean isCellEditable(int rowIndex, int columnIndex);

    Object getValueAt(int rowIndex, int columnIndex);

    void setValueAt(Object aValue, int rowIndex, int columnIndex);

    void addTableModelListener(TableModelListener l);

    void removeTableModelListener(TableModelListener l);
}

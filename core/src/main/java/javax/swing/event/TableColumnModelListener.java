package javax.swing.event;

import java.util.EventListener;

/** Listener for table column model changes. */
public interface TableColumnModelListener extends EventListener {
    void columnAdded(TableColumnModelEvent e);

    void columnRemoved(TableColumnModelEvent e);

    void columnMoved(TableColumnModelEvent e);

    void columnMarginChanged(ChangeEvent e);

    void columnSelectionChanged(ListSelectionEvent e);
}

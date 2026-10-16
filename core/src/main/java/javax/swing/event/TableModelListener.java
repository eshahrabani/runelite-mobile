package javax.swing.event;

import java.util.EventListener;

/** Listener for table model changes. */
public interface TableModelListener extends EventListener {
    void tableChanged(TableModelEvent e);
}

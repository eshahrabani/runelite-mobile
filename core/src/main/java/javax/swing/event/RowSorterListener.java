package javax.swing.event;

import java.util.EventListener;

/** Listener for {@link RowSorterEvent}s. */
public interface RowSorterListener extends EventListener {
    void sorterChanged(RowSorterEvent e);
}

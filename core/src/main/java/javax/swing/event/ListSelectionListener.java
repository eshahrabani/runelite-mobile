package javax.swing.event;

import java.util.EventListener;

/** Listener for {@link ListSelectionEvent}s. */
public interface ListSelectionListener extends EventListener {
    void valueChanged(ListSelectionEvent e);
}

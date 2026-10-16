package javax.swing.event;

import java.util.EventListener;

/** Listener for {@link TreeSelectionEvent}s. */
public interface TreeSelectionListener extends EventListener {
    void valueChanged(TreeSelectionEvent e);
}

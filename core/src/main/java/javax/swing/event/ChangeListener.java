package javax.swing.event;

import java.util.EventListener;

/** Listener for {@link ChangeEvent}s. */
public interface ChangeListener extends EventListener {
    void stateChanged(ChangeEvent e);
}

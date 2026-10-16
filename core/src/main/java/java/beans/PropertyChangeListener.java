package java.beans;

import java.util.EventListener;

/** Listener for bound-property changes. */
public interface PropertyChangeListener extends EventListener {
    void propertyChange(PropertyChangeEvent evt);
}

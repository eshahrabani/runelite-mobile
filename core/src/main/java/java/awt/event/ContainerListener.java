package java.awt.event;

import java.util.EventListener;

/** Listener for component add/remove events on a {@link java.awt.Container}. */
public interface ContainerListener extends EventListener {
    void componentAdded(ContainerEvent e);

    void componentRemoved(ContainerEvent e);
}

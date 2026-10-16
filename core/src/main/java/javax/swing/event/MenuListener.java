package javax.swing.event;

import java.util.EventListener;

/** Listener for {@link MenuEvent}s. */
public interface MenuListener extends EventListener {
    void menuSelected(MenuEvent e);

    void menuDeselected(MenuEvent e);

    void menuCanceled(MenuEvent e);
}

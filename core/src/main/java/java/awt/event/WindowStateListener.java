package java.awt.event;

import java.util.EventListener;

/** Listener for window state transitions (iconified/deiconified/maximised). */
public interface WindowStateListener extends EventListener {
    void windowStateChanged(WindowEvent e);
}

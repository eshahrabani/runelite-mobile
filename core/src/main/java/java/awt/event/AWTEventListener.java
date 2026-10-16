package java.awt.event;

import java.awt.AWTEvent;
import java.util.EventListener;

/** AWT {@code AWTEventListener}: a global listener for a mask of AWT events. */
public interface AWTEventListener extends EventListener {
    void eventDispatched(AWTEvent event);
}

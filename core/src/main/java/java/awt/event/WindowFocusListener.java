package java.awt.event;

import java.util.EventListener;

/** AWT {@code WindowFocusListener}: a window gained or lost the focus. */
public interface WindowFocusListener extends EventListener {
    void windowGainedFocus(WindowEvent e);

    void windowLostFocus(WindowEvent e);
}

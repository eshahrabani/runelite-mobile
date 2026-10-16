package java.awt.event;

import java.util.EventListener;

/**
 * AWT {@code ActionListener}: the callback every button, menu item and keyboard binding
 * fires. Hand-written: the shipped Swing classes implement it, and the port's widgets
 * really dispatch {@code actionPerformed}.
 */
public interface ActionListener extends EventListener {
    void actionPerformed(ActionEvent e);
}

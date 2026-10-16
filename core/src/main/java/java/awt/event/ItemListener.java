package java.awt.event;

import java.util.EventListener;

/** AWT {@code ItemListener}: a selectable item (checkbox, combo box, list) changed state. */
public interface ItemListener extends EventListener {
    void itemStateChanged(ItemEvent e);
}

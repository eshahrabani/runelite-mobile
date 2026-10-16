package java.awt;

import java.awt.event.ItemListener;

/**
 * AWT {@code ItemSelectable}: a component with a set of selectable items
 * (checkbox, combo box, list). The port's widgets fire real {@code ItemEvent}s, so the
 * interface carries the JDK members instead of the generated stub's empty body.
 */
public interface ItemSelectable {
    Object[] getSelectedObjects();

    void addItemListener(ItemListener l);

    void removeItemListener(ItemListener l);
}

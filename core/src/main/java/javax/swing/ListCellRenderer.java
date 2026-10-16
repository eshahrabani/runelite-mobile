package javax.swing;

import java.awt.Component;

/**
 * Swing {@code ListCellRenderer}: paints one row of a {@link JList} or the closed cell of
 * a {@link JComboBox}.
 *
 * <p>Hand-written because the sidebar's lists render real cells. The renderer returns a
 * component which the list/box lays out at the cell rectangle and paints with a clipped,
 * translated {@code Graphics} — the JDK's "rubber stamp" contract.
 */
public interface ListCellRenderer<E> {
    /**
     * Returns the component used to paint {@code value} at {@code index}. Implementations
     * usually reconfigure {@code this} and return it; {@code index == -1} means "the
     * selected value of a combo box" while {@code index == -2} is not used here.
     */
    Component getListCellRendererComponent(JList<? extends E> list, E value, int index,
                                           boolean isSelected, boolean cellHasFocus);
}

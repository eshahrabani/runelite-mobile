package javax.swing;

import javax.swing.event.ListDataListener;

/**
 * Swing {@code ListModel}: the data behind a {@link JList} or {@link JComboBox}.
 *
 * <p>Hand-written because the sidebar renders real Swing lists on this port. The member
 * set is the JDK's; the shipped RuneLite jars call {@code getSize}/{@code getElementAt}
 * and register {@link ListDataListener}s through {@link #addListDataListener}.
 */
public interface ListModel<E> {
    /** Number of elements in the model. */
    int getSize();

    /** The element at {@code index}; indices outside {@code [0, getSize())} are a caller bug. */
    E getElementAt(int index);

    /** Registers a listener that is notified of insert/remove/change. */
    void addListDataListener(ListDataListener l);

    /** Removes a previously registered listener. */
    void removeListDataListener(ListDataListener l);
}

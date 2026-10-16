package javax.swing;

import javax.swing.event.ListSelectionListener;

/**
 * Swing {@code ListSelectionModel}: the selection state shared by {@link JList} (and, in
 * the JDK, by tables and trees).
 *
 * <p>Hand-written because the port's lists really select rows. The mode constants and the
 * method set match the JDK; selection changes are reported with exactly one
 * {@link javax.swing.event.ListSelectionEvent} per mutation.
 */
public interface ListSelectionModel {
    /** Only one index may be selected at a time. */
    int SINGLE_SELECTION = 0;
    /** One contiguous interval may be selected. */
    int SINGLE_INTERVAL_SELECTION = 1;
    /** Any number of disjoint intervals may be selected. */
    int MULTIPLE_INTERVAL_SELECTION = 2;

    void setSelectionInterval(int index0, int index1);

    void addSelectionInterval(int index0, int index1);

    void removeSelectionInterval(int index0, int index1);

    int getMinSelectionIndex();

    int getMaxSelectionIndex();

    boolean isSelectedIndex(int index);

    int getAnchorSelectionIndex();

    void setAnchorSelectionIndex(int index);

    int getLeadSelectionIndex();

    void setLeadSelectionIndex(int index);

    void clearSelection();

    boolean isSelectionEmpty();

    void insertIndexInterval(int index, int length, boolean before);

    void removeIndexInterval(int index0, int index1);

    void setValueIsAdjusting(boolean valueIsAdjusting);

    boolean getValueIsAdjusting();

    void setSelectionMode(int selectionMode);

    int getSelectionMode();

    void addListSelectionListener(ListSelectionListener l);

    void removeListSelectionListener(ListSelectionListener l);

    /** The selected indices in ascending order. */
    int[] getSelectedIndices();
}

package javax.swing;

/**
 * Swing {@code ComboBoxModel}: a {@link ListModel} that also carries the combo box's
 * current selection.
 *
 * <p>Hand-written because {@link JComboBox} needs a working model. A change of the
 * selected item is announced as a {@code CONTENTS_CHANGED} event, which the JComboBox
 * turns into one {@code ItemEvent} and one {@code ActionEvent}.
 */
public interface ComboBoxModel<E> extends ListModel<E> {
    /** Sets the selected item; {@code anItem} need not be one of the model's elements. */
    void setSelectedItem(Object anItem);

    /** The selected item, or {@code null} when nothing is selected. */
    Object getSelectedItem();
}

package javax.swing;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Swing {@code Action}: an {@code ActionListener} with a name, icons and enabled state
 * that components (buttons, menu items, text components through key bindings) share.
 */
public interface Action extends java.awt.event.ActionListener {
    String DEFAULT = "Default";
    String NAME = "Name";
    String SHORT_DESCRIPTION = "ShortDescription";
    String LONG_DESCRIPTION = "LongDescription";
    String SMALL_ICON = "SmallIcon";
    String ACTION_COMMAND_KEY = "ActionCommandKey";
    String ACCELERATOR_KEY = "AcceleratorKey";
    String MNEMONIC_KEY = "MnemonicKey";
    String SELECTED_KEY = "SwingSelectedKey";
    String DISPLAYED_MNEMONIC_INDEX_KEY = "SwingDisplayedMnemonicIndexKey";
    String LARGE_ICON_KEY = "SwingLargeIconKey";

    Object getValue(String key);

    void putValue(String key, Object value);

    void setEnabled(boolean b);

    boolean isEnabled();

    void addPropertyChangeListener(PropertyChangeListener listener);

    void removePropertyChangeListener(PropertyChangeListener listener);
}

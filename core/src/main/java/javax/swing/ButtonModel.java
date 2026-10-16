package javax.swing;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import javax.swing.event.ChangeListener;

/**
 * Swing {@code ButtonModel}: the state a {@link AbstractButton} delegates to.
 *
 * <p>Hand-written because the port really drives the button state machine: the shipped
 * config/sidebar panels read {@code isPressed()/isRollover()/isSelected()} and the
 * RuneLite look-and-feel's buttons paint from the model, so these have to be live values
 * rather than the generated stub's defaults. The listener method set matches the JDK so a
 * {@code JButton}/{@code JCheckBox} behaves as its desktop counterpart.
 */
public interface ButtonModel extends java.awt.ItemSelectable {
    boolean isArmed();

    boolean isSelected();

    boolean isEnabled();

    boolean isPressed();

    boolean isRollover();

    void setArmed(boolean b);

    void setSelected(boolean b);

    void setEnabled(boolean b);

    void setPressed(boolean b);

    void setRollover(boolean b);

    void setMnemonic(int key);

    int getMnemonic();

    void setActionCommand(String s);

    String getActionCommand();

    void setGroup(ButtonGroup group);

    ButtonGroup getGroup();

    void addActionListener(ActionListener l);

    void removeActionListener(ActionListener l);

    void addItemListener(ItemListener l);

    void removeItemListener(ItemListener l);

    void addChangeListener(ChangeListener l);

    void removeChangeListener(ChangeListener l);
}

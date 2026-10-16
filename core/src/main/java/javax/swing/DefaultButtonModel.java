package javax.swing;

import java.awt.ItemSelectable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Swing {@code DefaultButtonModel}: the live state machine behind every
 * {@link AbstractButton} on this port.
 *
 * <p>Hand-written because the port needs the real semantics: a press arms,
 * {@code setPressed(false)} while armed fires exactly one {@code ACTION_PERFORMED}, and a
 * selection change fires an item state change plus a {@link ChangeEvent}. Painting reads
 * {@code isRollover()/isPressed()/isSelected()} directly, so those flags must be exact.
 */
public class DefaultButtonModel implements ButtonModel {

    private static final int ARMED = 1;
    private static final int SELECTED_BIT = 1 << 1;
    private static final int PRESSED = 1 << 2;
    private static final int ENABLED = 1 << 3;
    private static final int ROLLOVER = 1 << 4;

    private int stateMask = ENABLED;
    private String actionCommand;
    private ButtonGroup group;
    private int mnemonic = -1;
    /** The button that owns this model, used as the {@code ActionEvent}/{@code ItemEvent} source. */
    private Object source;

    private final List<ActionListener> actionListeners = new ArrayList<>();
    private final List<ItemListener> itemListeners = new ArrayList<>();
    private final List<ChangeListener> changeListeners = new ArrayList<>();

    public DefaultButtonModel() {
    }

    // ------------------------------------------------------------- state

    @Override
    public boolean isArmed() {
        return (stateMask & ARMED) != 0;
    }

    @Override
    public boolean isSelected() {
        return (stateMask & SELECTED_BIT) != 0;
    }

    @Override
    public boolean isEnabled() {
        return (stateMask & ENABLED) != 0;
    }

    @Override
    public boolean isPressed() {
        return (stateMask & PRESSED) != 0;
    }

    @Override
    public boolean isRollover() {
        return (stateMask & ROLLOVER) != 0;
    }

    @Override
    public void setArmed(boolean b) {
        if (isArmed() == b || !isEnabled()) {
            return;
        }
        stateMask = b ? stateMask | ARMED : stateMask & ~ARMED;
        fireStateChanged();
    }

    @Override
    public void setEnabled(boolean b) {
        if (isEnabled() == b) {
            return;
        }
        stateMask = b ? stateMask | ENABLED : stateMask & ~ENABLED;
        // A button disabled while pressed/armed drops that transient state.
        if (!b) {
            stateMask &= ~(PRESSED | ARMED | ROLLOVER);
        }
        fireStateChanged();
    }

    @Override
    public void setPressed(boolean b) {
        if (isPressed() == b || !isEnabled()) {
            return;
        }
        if (!b && isArmed()) {
            fireActionPerformed(new ActionEvent(getSource(), ActionEvent.ACTION_PERFORMED, getActionCommand()));
        }
        stateMask = b ? stateMask | PRESSED : stateMask & ~PRESSED;
        fireStateChanged();
    }

    @Override
    public void setRollover(boolean b) {
        if (isRollover() == b || !isEnabled()) {
            return;
        }
        stateMask = b ? stateMask | ROLLOVER : stateMask & ~ROLLOVER;
        fireStateChanged();
    }

    @Override
    public void setSelected(boolean b) {
        if (isSelected() == b || !isEnabled()) {
            return;
        }
        if (b && group != null) {
            // The group deselects its other members before this one takes the state.
            group.setSelected(this, true);
        }
        stateMask = b ? stateMask | SELECTED_BIT : stateMask & ~SELECTED_BIT;
        fireItemStateChanged(b ? ItemEvent.SELECTED : ItemEvent.DESELECTED);
        fireStateChanged();
    }

    // ---------------------------------------------------------- command

    @Override
    public String getActionCommand() {
        return actionCommand;
    }

    @Override
    public void setActionCommand(String actionCommand) {
        this.actionCommand = actionCommand;
    }

    @Override
    public int getMnemonic() {
        return mnemonic;
    }

    @Override
    public void setMnemonic(int mnemonic) {
        this.mnemonic = mnemonic;
    }

    @Override
    public ButtonGroup getGroup() {
        return group;
    }

    @Override
    public void setGroup(ButtonGroup group) {
        this.group = group;
    }

    @Override
    public Object[] getSelectedObjects() {
        return isSelected() ? new Object[] { this } : null;
    }

    /**
     * The button this model belongs to. Swing fires {@code ActionEvent}/{@code ItemEvent}
     * with the button as source (shipped listeners cast {@code e.getSource()} to the
     * button), whereas a bare model would be the source of the desktop JDK's internal
     * event; {@link AbstractButton} installs itself here.
     */
    public void setSource(Object source) {
        this.source = source;
    }

    public Object getSource() {
        return source != null ? source : this;
    }

    // -------------------------------------------------------- listeners

    @Override
    public void addActionListener(ActionListener l) {
        if (l != null) {
            actionListeners.add(l);
        }
    }

    @Override
    public void removeActionListener(ActionListener l) {
        actionListeners.remove(l);
    }

    public ActionListener[] getActionListeners() {
        return actionListeners.toArray(new ActionListener[0]);
    }

    @Override
    public void addItemListener(ItemListener l) {
        if (l != null) {
            itemListeners.add(l);
        }
    }

    @Override
    public void removeItemListener(ItemListener l) {
        itemListeners.remove(l);
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        if (l != null) {
            changeListeners.add(l);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        changeListeners.remove(l);
    }

    public ChangeListener[] getChangeListeners() {
        return changeListeners.toArray(new ChangeListener[0]);
    }

    protected void fireStateChanged() {
        if (changeListeners.isEmpty()) {
            return;
        }
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : changeListeners.toArray(new ChangeListener[0])) {
            l.stateChanged(event);
        }
    }

    protected void fireActionPerformed(ActionEvent event) {
        if (actionListeners.isEmpty()) {
            return;
        }
        for (ActionListener l : actionListeners.toArray(new ActionListener[0])) {
            l.actionPerformed(event);
        }
    }

    /**
     * Reports a selection change to the item listeners, with the owning button as both the
     * event source and the {@code ItemSelectable} (the shipped config panels cast
     * {@code e.getSource()} to the check box and read {@code getItem()/getStateChange()}).
     */
    protected void fireItemStateChanged(int stateChange) {
        if (itemListeners.isEmpty()) {
            return;
        }
        Object src = getSource();
        ItemSelectable selectable = src instanceof ItemSelectable ? (ItemSelectable) src : this;
        ItemEvent event = new ItemEvent(selectable, ItemEvent.ITEM_STATE_CHANGED, this, stateChange);
        for (ItemListener l : itemListeners.toArray(new ItemListener[0])) {
            l.itemStateChanged(event);
        }
    }
}

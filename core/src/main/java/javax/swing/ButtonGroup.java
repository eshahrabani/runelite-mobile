package javax.swing;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * Swing {@code ButtonGroup}: mutual exclusion for a set of toggle buttons (the shipped
 * loot-tracker panel uses it for radio rows).
 *
 * <p>Hand-written because exclusivity must actually happen: adding a button registers its
 * model with this group, so selecting a member deselects the others. The selection change
 * itself is performed by {@link DefaultButtonModel#setSelected(boolean)} calling back into
 * {@link #setSelected(ButtonModel, boolean)}.
 */
public class ButtonGroup {

    private final Vector<AbstractButton> buttons = new Vector<>();

    public ButtonGroup() {
    }

    public void add(AbstractButton b) {
        if (b == null || buttons.contains(b)) {
            return;
        }
        buttons.addElement(b);
        ButtonModel model = b.getModel();
        if (model instanceof DefaultButtonModel) {
            ((DefaultButtonModel) model).setGroup(this);
        }
    }

    public void remove(AbstractButton b) {
        if (b == null) {
            return;
        }
        if (buttons.removeElement(b)) {
            ButtonModel model = b.getModel();
            if (model instanceof DefaultButtonModel && model.getGroup() == this) {
                ((DefaultButtonModel) model).setGroup(null);
            }
        }
    }

    public void clearSelection() {
        for (AbstractButton b : new ArrayList<>(buttons)) {
            b.getModel().setSelected(false);
        }
    }

    /**
     * Deselects every member other than {@code m} when {@code b} is true.
     *
     * <p>Called by the model taking the selected state; the recursion is bounded because
     * the deselection calls arrive with {@code b == false}.
     */
    public void setSelected(ButtonModel m, boolean b) {
        if (!b) {
            return;
        }
        for (AbstractButton button : new ArrayList<>(buttons)) {
            ButtonModel model = button.getModel();
            if (model != m) {
                model.setSelected(false);
            }
        }
    }

    public boolean isSelected(ButtonModel m) {
        return m != null && m.isSelected();
    }

    public ButtonModel getSelection() {
        for (AbstractButton b : buttons) {
            if (b.getModel().isSelected()) {
                return b.getModel();
            }
        }
        return null;
    }

    public Enumeration<AbstractButton> getElements() {
        return buttons.elements();
    }

    public int getButtonCount() {
        return buttons.size();
    }

    public List<AbstractButton> getButtons() {
        return new ArrayList<>(buttons);
    }
}

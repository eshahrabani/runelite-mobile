package javax.swing.text;

import java.awt.event.ActionEvent;
import java.util.Hashtable;

import javax.swing.AbstractAction;
import javax.swing.Action;

/**
 * Hand-written {@code javax.swing.text.TextAction}: the base class of every editor action.
 *
 * <p>Why hand-written: {@link DefaultEditorKit}'s actions (and {@code JTextComponent}'s
 * {@code getActions()}) are built on it, and the shipped client's own text actions subclass it
 * through the action map. The generated stub set contained no such type.
 *
 * <p>As in the JDK, the target component is the action event's source when that is a text
 * component, and the focus owner otherwise.
 */
public abstract class TextAction extends AbstractAction {

    public TextAction(String name) {
        super(name);
    }

    /** The component this action should act on. */
    protected final JTextComponent getTextComponent(ActionEvent e) {
        if (e != null && e.getSource() instanceof JTextComponent) {
            return (JTextComponent) e.getSource();
        }
        return getFocusedComponent();
    }

    /** Merges two action lists by action name (the JDK's helper for {@code getActions}). */
    public static final Action[] augmentList(Action[] list1, Action[] list2) {
        Hashtable<String, Action> merged = new Hashtable<>();
        Action[] first = list1 == null ? new Action[0] : list1;
        Action[] second = list2 == null ? new Action[0] : list2;
        for (int i = 0; i < first.length; i++) {
            Object name = first[i].getValue(Action.NAME);
            merged.put(name != null ? name.toString() : "", first[i]);
        }
        for (int i = 0; i < second.length; i++) {
            Object name = second[i].getValue(Action.NAME);
            merged.put(name != null ? name.toString() : "", second[i]);
        }
        return merged.values().toArray(new Action[0]);
    }

    /** The text component holding the focus, or null. */
    protected final JTextComponent getFocusedComponent() {
        return JTextComponent.getFocusedComponent();
    }
}

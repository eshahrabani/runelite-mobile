package javax.swing.event;

import java.util.EventListener;

/**
 * Hand-written {@code javax.swing.event.CaretListener}: notifies an observer that a text
 * component's caret or selection moved.
 *
 * <p>Why hand-written: {@code javax.swing.text.JTextComponent.addCaretListener} is part of the
 * text component contract this port implements, and the listener it takes has to exist. The
 * generated stub set did not contain this type (no shipped class references it), so it is
 * authored here alongside the {@code javax.swing.text} package that needs it.
 */
public interface CaretListener extends EventListener {

    /** The caret moved (and/or the selection changed). */
    void caretUpdate(CaretEvent e);
}

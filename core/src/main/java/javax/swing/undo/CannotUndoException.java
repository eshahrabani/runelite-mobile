package javax.swing.undo;

/**
 * Hand-written {@code javax.swing.undo.CannotUndoException}: thrown when an edit that cannot
 * be undone is undone.
 *
 * <p>Why hand-written: the port's undo stack throws it for real (undoing past the end of the
 * stack, or undoing an edit whose document range has since moved); the stub had only a
 * no-argument constructor and no cause.
 */
public class CannotUndoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CannotUndoException() {
        super();
    }

    public CannotUndoException(String message) {
        super(message);
    }
}

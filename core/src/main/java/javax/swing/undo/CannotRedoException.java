package javax.swing.undo;

/**
 * Hand-written {@code javax.swing.undo.CannotRedoException}: thrown when an edit that cannot
 * be redone is redone.
 *
 * <p>Why hand-written: {@link UndoManager#redo()} throws it when the stack has nothing to
 * redo, which is what the port's Ctrl+Y path relies on. The generated stub did not exist at
 * all.
 */
public class CannotRedoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CannotRedoException() {
        super();
    }

    public CannotRedoException(String message) {
        super(message);
    }
}

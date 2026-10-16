package javax.swing.undo;

/**
 * Hand-written {@code javax.swing.undo.AbstractUndoableEdit}: the do-nothing base every
 * concrete edit extends.
 *
 * <p>Why hand-written: the port's document changes and spinner edits are real edits that a
 * user can undo, and they are built on this class (see
 * {@code javax.swing.text.AbstractDocument.ContentEdit}). The generated stub was an empty
 * abstract class, so no undo manager could ever undo anything.
 *
 * <p>Deviation from the JDK: this class is not declared {@code abstract} — the JDK's own
 * version is concrete too (it is usable as a no-op edit), and the stub's {@code abstract}
 * modifier was an artefact of the generator, not a member signature.
 */
public class AbstractUndoableEdit implements UndoableEdit, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /** The prefix of the presentation name an edit was never given one. */
    protected static final String UndoName = "Undo";

    /** The prefix of the redo presentation name an edit was never given one. */
    protected static final String RedoName = "Redo";

    private boolean hasBeenDone = true;
    private boolean alive = true;

    @Override
    public void undo() throws CannotUndoException {
        if (!canUndo()) {
            throw new CannotUndoException();
        }
        hasBeenDone = false;
    }

    @Override
    public boolean canUndo() {
        return alive && hasBeenDone;
    }

    @Override
    public void redo() throws CannotRedoException {
        if (!canRedo()) {
            throw new CannotRedoException();
        }
        hasBeenDone = true;
    }

    @Override
    public boolean canRedo() {
        return alive && !hasBeenDone;
    }

    @Override
    public void die() {
        alive = false;
    }

    @Override
    public boolean addEdit(UndoableEdit anEdit) {
        return false;
    }

    @Override
    public boolean replaceEdit(UndoableEdit anEdit) {
        return false;
    }

    @Override
    public boolean isSignificant() {
        return true;
    }

    @Override
    public String getPresentationName() {
        return "";
    }

    @Override
    public String getUndoPresentationName() {
        String name = getPresentationName();
        return name == null || name.isEmpty() ? UndoName : UndoName + " " + name;
    }

    @Override
    public String getRedoPresentationName() {
        String name = getPresentationName();
        return name == null || name.isEmpty() ? RedoName : RedoName + " " + name;
    }

    @Override
    public String toString() {
        return super.toString() + " hasBeenDone: " + hasBeenDone + " alive: " + alive;
    }
}

package javax.swing.undo;

/**
 * Hand-written {@code javax.swing.undo.UndoableEdit}: one reversible model change.
 *
 * <p>Why hand-written: RuneLite's Notes panel installs an {@link UndoManager} on its text
 * area's document and drives Ctrl+Z/Ctrl+Y from it, so edits really are added, undone and
 * redone on this port. The generated stub declared no members, which meant no edit could
 * ever be reversed.
 */
public interface UndoableEdit {

    /** Reverses this edit. */
    void undo() throws CannotUndoException;

    /** True when {@link #undo()} can be called. */
    boolean canUndo();

    /** Re-applies this edit after an undo. */
    void redo() throws CannotRedoException;

    /** True when {@link #redo()} can be called. */
    boolean canRedo();

    /** Tells the edit that it will never be used again. */
    void die();

    /** Absorbs {@code anEdit} (returns true when it was merged into this one). */
    boolean addEdit(UndoableEdit anEdit);

    /** Replaces this edit with {@code anEdit} (returns true when it was). */
    boolean replaceEdit(UndoableEdit anEdit);

    /** True when this edit should appear as its own undo step. */
    boolean isSignificant();

    /** A human-readable description of this edit. */
    String getPresentationName();

    /** The description shown for undoing this edit. */
    String getUndoPresentationName();

    /** The description shown for redoing this edit. */
    String getRedoPresentationName();
}

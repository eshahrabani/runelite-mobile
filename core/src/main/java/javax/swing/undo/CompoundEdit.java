package javax.swing.undo;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written {@code javax.swing.undo.CompoundEdit}: a group of edits that undo and redo as
 * one unit.
 *
 * <p>Why hand-written: it is the superclass of both {@link UndoManager} and
 * {@code javax.swing.text.AbstractDocument.DefaultDocumentEvent} — a document change is a
 * compound edit (its text mutation plus its element changes) that RuneLite's Notes panel
 * undoes as one step. The generated stub stored nothing.
 *
 * <p>Port note: the JDK's {@code inProgress} flag starts out <em>true</em> (edits are added
 * until {@link #end()} is called); the same holds here, so callers that build a compound edit
 * by hand must {@link #end()} it before it can be undone.
 */
public class CompoundEdit extends AbstractUndoableEdit {
    private static final long serialVersionUID = 1L;

    /** The edits of this compound, in the order they were added. */
    protected List<UndoableEdit> edits = new ArrayList<>();

    private boolean inProgress = true;

    public CompoundEdit() {
        super();
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        for (int i = edits.size() - 1; i >= 0; i--) {
            edits.get(i).undo();
        }
    }

    @Override
    public boolean canUndo() {
        return !isInProgress() && !edits.isEmpty() && lastEdit().canUndo();
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        for (int i = 0; i < edits.size(); i++) {
            edits.get(i).redo();
        }
    }

    @Override
    public boolean canRedo() {
        return !isInProgress() && !edits.isEmpty() && lastEdit().canRedo();
    }

    @Override
    public boolean addEdit(UndoableEdit anEdit) {
        if (!inProgress) {
            return false;
        }
        UndoableEdit last = lastEdit();
        if (last == null) {
            edits.add(anEdit);
        } else if (!last.addEdit(anEdit)) {
            edits.add(anEdit);
        }
        return true;
    }

    @Override
    public boolean replaceEdit(UndoableEdit anEdit) {
        return false;
    }

    /** Ends the compound: from now on it can be undone and no longer accepts edits. */
    public void end() {
        inProgress = false;
    }

    /** True while this compound still accepts edits. */
    protected boolean isInProgress() {
        return inProgress;
    }

    @Override
    public boolean isSignificant() {
        for (int i = 0; i < edits.size(); i++) {
            if (edits.get(i).isSignificant()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getPresentationName() {
        UndoableEdit last = lastEdit();
        return last == null ? super.getPresentationName() : last.getPresentationName();
    }

    @Override
    public String getUndoPresentationName() {
        UndoableEdit last = lastEdit();
        return last == null ? super.getUndoPresentationName() : last.getUndoPresentationName();
    }

    @Override
    public String getRedoPresentationName() {
        UndoableEdit last = lastEdit();
        return last == null ? super.getRedoPresentationName() : last.getRedoPresentationName();
    }

    @Override
    public void die() {
        for (int i = 0; i < edits.size(); i++) {
            edits.get(i).die();
        }
        super.die();
    }

    /** The edit added last, or null. */
    protected UndoableEdit lastEdit() {
        return edits.isEmpty() ? null : edits.get(edits.size() - 1);
    }

    @Override
    public String toString() {
        return super.toString() + " edits: " + edits;
    }
}

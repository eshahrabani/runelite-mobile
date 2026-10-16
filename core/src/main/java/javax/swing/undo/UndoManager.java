package javax.swing.undo;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;

/**
 * Hand-written {@code javax.swing.undo.UndoManager}: the edit stack behind Ctrl+Z/Ctrl+Y.
 *
 * <p>Why hand-written: RuneLite's Notes panel registers one on its text area's document and
 * asks it {@code canUndo()}/{@code undo()} from an action bound in the component's
 * InputMap — that is the port's only undo path, and a stub returning false from
 * {@code canUndo()} silently disabled it. The stack, the limit and the presentation names
 * are therefore all real here.
 *
 * <p>Deviation from the JDK: {@code undo}/{@code redo} are not synchronised and the "in
 * progress" streaming mode is the JDK's ({@link CompoundEdit#end()}), nothing more.
 */
public class UndoManager extends CompoundEdit implements UndoableEditListener {
    private static final long serialVersionUID = 1L;

    /** The index of the edit that {@link #undo()} would undo (into {@link #edits}). */
    private int indexOfNextAdd = 0;

    private int limit = 100;

    public UndoManager() {
        super();
        end();
    }

    /** The listener entry point: adds the edit the document just made. */
    @Override
    public void undoableEditHappened(UndoableEditEvent e) {
        if (e != null) {
            addEdit(e.getEdit());
        }
    }

    @Override
    public void undo() throws CannotUndoException {
        if (!canUndo()) {
            throw new CannotUndoException();
        }
        int index = edits.size() - 1;
        while (index >= 0 && !edits.get(index).isSignificant()) {
            index--;
        }
        if (index < 0) {
            throw new CannotUndoException();
        }
        for (int i = edits.size() - 1; i >= index; i--) {
            edits.get(i).undo();
        }
        indexOfNextAdd = index;
    }

    @Override
    public boolean canUndo() {
        return !isInProgress() && getUndoOrRedoIndex(true) >= 0;
    }

    @Override
    public void redo() throws CannotRedoException {
        if (!canRedo()) {
            throw new CannotRedoException();
        }
        int index = indexOfNextAdd;
        int end = index;
        while (end < edits.size() && !edits.get(end).isSignificant()) {
            end++;
        }
        for (int i = index; i <= end && i < edits.size(); i++) {
            edits.get(i).redo();
        }
        indexOfNextAdd = end + 1;
    }

    @Override
    public boolean canRedo() {
        return !isInProgress() && getUndoOrRedoIndex(false) >= 0;
    }

    /**
     * The index {@link #undo()} would start at ({@code undo} true) or {@link #redo()} would
     * start at, or -1 when there is nothing to do.
     */
    private int getUndoOrRedoIndex(boolean undo) {
        if (edits.isEmpty()) {
            return -1;
        }
        if (undo) {
            // Anything left of indexOfNextAdd that can be undone is undoable.
            for (int i = indexOfNextAdd - 1; i >= 0; i--) {
                if (edits.get(i).canUndo()) {
                    return i;
                }
            }
            return -1;
        }
        for (int i = indexOfNextAdd; i < edits.size(); i++) {
            if (edits.get(i).canRedo()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean addEdit(UndoableEdit anEdit) {
        if (anEdit == null) {
            return false;
        }
        // Everything after the cursor is no longer reachable: drop it.
        trimEdits(indexOfNextAdd, edits.size() - 1);
        boolean added;
        UndoableEdit last = edits.isEmpty() ? null : edits.get(edits.size() - 1);
        if (last != null && last.addEdit(anEdit)) {
            added = true;
        } else {
            // Edits that report themselves as insignificant are not separate undo steps but
            // still belong to the stack.
            edits.add(anEdit);
            added = true;
        }
        indexOfNextAdd = edits.size();
        trimForLimit();
        return added;
    }

    /** True when there is anything left to undo or redo. */
    public boolean canUndoOrRedo() {
        return indexOfNextAdd != edits.size();
    }

    /** Throws away every edit (the JDK's {@code discardAllEdits}). */
    public void discardAllEdits() {
        List<UndoableEdit> doomed = new ArrayList<>(edits);
        edits.clear();
        indexOfNextAdd = 0;
        for (int i = 0; i < doomed.size(); i++) {
            doomed.get(i).die();
        }
    }

    /** Removes the edits in the inclusive index range. */
    protected void trimEdits(int from, int to) {
        if (from > to || from < 0) {
            return;
        }
        int last = Math.min(to, edits.size() - 1);
        for (int i = last; i >= from; i--) {
            UndoableEdit edit = edits.remove(i);
            edit.die();
            if (indexOfNextAdd > i) {
                indexOfNextAdd--;
            }
        }
    }

    /** Keeps the stack within {@link #getLimit()}. */
    protected void trimForLimit() {
        if (limit <= 0) {
            return;
        }
        if (edits.size() > limit) {
            trimEdits(0, edits.size() - limit - 1);
        }
    }

    /** The maximum number of edits kept (0 means unlimited). */
    public int getLimit() {
        return limit;
    }

    /** Sets the maximum number of edits kept. */
    public void setLimit(int l) {
        if (l == 0) {
            // The JDK treats 0 as "discard everything", not as "unlimited".
            discardAllEdits();
            return;
        }
        limit = l;
        trimForLimit();
    }

    @Override
    public String getUndoPresentationName() {
        if (!canUndo()) {
            return "";
        }
        UndoableEdit edit = edits.get(getUndoOrRedoIndex(true));
        return edit.getUndoPresentationName();
    }

    @Override
    public String getRedoPresentationName() {
        if (!canRedo()) {
            return "";
        }
        UndoableEdit edit = edits.get(getUndoOrRedoIndex(false));
        return edit.getRedoPresentationName();
    }

    @Override
    public String toString() {
        return super.toString() + " limit: " + limit + " indexOfNextAdd: " + indexOfNextAdd;
    }
}

package javax.swing.event;

/** Swing {@code UndoableEditEvent}: an edit was added to an undoable model. */
public class UndoableEditEvent extends java.util.EventObject {
    private static final long serialVersionUID = 7858040948092863357L;

    private final javax.swing.undo.UndoableEdit edit;

    public UndoableEditEvent(Object source, javax.swing.undo.UndoableEdit edit) {
        super(source == null ? UndoableEditEvent.class : source);
        this.edit = edit;
    }

    public javax.swing.undo.UndoableEdit getEdit() {
        return edit;
    }
}

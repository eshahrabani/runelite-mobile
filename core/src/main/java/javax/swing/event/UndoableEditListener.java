package javax.swing.event;

import java.util.EventListener;

/** Listener for {@link UndoableEditEvent}s. */
public interface UndoableEditListener extends EventListener {
    void undoableEditHappened(UndoableEditEvent e);
}

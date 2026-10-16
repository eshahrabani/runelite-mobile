package javax.swing.event;

import java.util.EventListener;

/** Listener for cell-editor start/stop events. */
public interface CellEditorListener extends EventListener {
    void editingStopped(ChangeEvent e);

    void editingCanceled(ChangeEvent e);
}

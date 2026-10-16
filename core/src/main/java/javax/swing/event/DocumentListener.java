package javax.swing.event;

import java.util.EventListener;

/** Listener for document mutations. */
public interface DocumentListener extends EventListener {
    void insertUpdate(DocumentEvent e);

    void removeUpdate(DocumentEvent e);

    void changedUpdate(DocumentEvent e);
}

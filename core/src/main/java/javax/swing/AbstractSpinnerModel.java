package javax.swing;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Hand-written {@code javax.swing.AbstractSpinnerModel}: the change-listener plumbing a
 * {@link SpinnerModel} implementation shares.
 *
 * <p>Why hand-written: {@link SpinnerNumberModel} (the model every numeric spinner in the
 * client's config UI uses) extends it, and the spinner only reacts to value changes through
 * these listeners. The generated stub had no listener state at all.
 *
 * <p>Deviation from the JDK: listeners live in a plain list instead of an
 * {@code EventListenerList} (the port has no such type), and the generic
 * {@code getListeners(Class)} accessor is therefore absent; {@link #getChangeListeners()}
 * covers it.
 */
public abstract class AbstractSpinnerModel implements SpinnerModel, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final List<ChangeListener> changeListeners = new ArrayList<>(2);
    private transient ChangeEvent changeEvent;

    protected AbstractSpinnerModel() {
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        if (l != null) {
            synchronized (changeListeners) {
                changeListeners.add(l);
            }
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (changeListeners) {
            changeListeners.remove(l);
        }
    }

    /** The registered change listeners. */
    public ChangeListener[] getChangeListeners() {
        synchronized (changeListeners) {
            return changeListeners.toArray(new ChangeListener[0]);
        }
    }

    /** Notifies every listener that the model's value changed. */
    protected void fireStateChanged() {
        List<ChangeListener> snapshot;
        synchronized (changeListeners) {
            snapshot = new ArrayList<>(changeListeners);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            if (changeEvent == null) {
                changeEvent = new ChangeEvent(this);
            }
            snapshot.get(i).stateChanged(changeEvent);
        }
    }
}

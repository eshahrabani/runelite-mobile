package javax.swing;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * Swing {@code AbstractListModel}: the {@link ListModel} listener plumbing shared by
 * {@link DefaultListModel} and {@link DefaultComboBoxModel}.
 *
 * <p>Hand-written because the widgets that live in the port's sidebar fire real
 * {@link ListDataEvent}s (the JDK's {@code EventListenerList} is not an Android class, so
 * the listener store is a plain list). The firing rules match the JDK: exactly one event
 * carries the half-open index range that changed.
 */
public abstract class AbstractListModel<E> implements ListModel<E>, java.io.Serializable {
    private static final long serialVersionUID = 6560430815487181741L;

    private final List<ListDataListener> listenerList = new ArrayList<>();

    @Override
    public void addListDataListener(ListDataListener l) {
        if (l != null) {
            listenerList.add(l);
        }
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        listenerList.remove(l);
    }

    /** The currently registered listeners (JDK-compatible accessor). */
    public ListDataListener[] getListDataListeners() {
        return listenerList.toArray(new ListDataListener[0]);
    }

    /** Fires one {@code CONTENTS_CHANGED} for the inclusive range {@code index0..index1}. */
    protected void fireContentsChanged(Object source, int index0, int index1) {
        ListDataEvent event = null;
        for (ListDataListener l : snapshot()) {
            if (event == null) {
                event = new ListDataEvent(source == null ? this : source,
                        ListDataEvent.CONTENTS_CHANGED, index0, index1);
            }
            l.contentsChanged(event);
        }
    }

    /** Fires one {@code INTERVAL_ADDED} for the inclusive range {@code index0..index1}. */
    protected void fireIntervalAdded(Object source, int index0, int index1) {
        ListDataEvent event = null;
        for (ListDataListener l : snapshot()) {
            if (event == null) {
                event = new ListDataEvent(source == null ? this : source,
                        ListDataEvent.INTERVAL_ADDED, index0, index1);
            }
            l.intervalAdded(event);
        }
    }

    /** Fires one {@code INTERVAL_REMOVED} for the inclusive range {@code index0..index1}. */
    protected void fireIntervalRemoved(Object source, int index0, int index1) {
        ListDataEvent event = null;
        for (ListDataListener l : snapshot()) {
            if (event == null) {
                event = new ListDataEvent(source == null ? this : source,
                        ListDataEvent.INTERVAL_REMOVED, index0, index1);
            }
            l.intervalRemoved(event);
        }
    }

    /** A copy, so a listener may add or remove listeners while the event is being fired. */
    private List<ListDataListener> snapshot() {
        return new ArrayList<>(listenerList);
    }
}

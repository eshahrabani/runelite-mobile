package java.beans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaBeans {@code PropertyChangeSupport}: the notification plumbing Swing components
 * use for their bound properties ({@code JLabel.text}, {@code JComponent.border}, …).
 *
 * <p>Real implementation, because a Swing widget that never fires its property changes
 * breaks the components that listen for them (the configuration UI's text fields, the
 * colour buttons, RuneLite's dimmable panels).
 */
public class PropertyChangeSupport {
    private final Object source;
    private final List<PropertyChangeListener> listeners = new ArrayList<>();
    private Map<String, List<PropertyChangeListener>> namedListeners;

    public PropertyChangeSupport(Object sourceBean) {
        if (sourceBean == null) {
            throw new NullPointerException();
        }
        this.source = sourceBean;
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (namedListeners == null) {
            namedListeners = new HashMap<>();
        }
        namedListeners.computeIfAbsent(propertyName, k -> new ArrayList<>()).add(listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        listeners.remove(listener);
    }

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (namedListeners == null) {
            return;
        }
        List<PropertyChangeListener> named = namedListeners.get(propertyName);
        if (named != null) {
            named.remove(listener);
            if (named.isEmpty()) {
                namedListeners.remove(propertyName);
            }
        }
    }

    public synchronized PropertyChangeListener[] getPropertyChangeListeners() {
        return listeners.toArray(new PropertyChangeListener[0]);
    }

    public synchronized PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        if (namedListeners == null) {
            return new PropertyChangeListener[0];
        }
        List<PropertyChangeListener> named = namedListeners.get(propertyName);
        return named == null ? new PropertyChangeListener[0] : named.toArray(new PropertyChangeListener[0]);
    }

    public boolean hasListeners(String propertyName) {
        synchronized (this) {
            if (!listeners.isEmpty()) {
                return true;
            }
            return namedListeners != null && namedListeners.containsKey(propertyName);
        }
    }

    public void firePropertyChange(PropertyChangeEvent event) {
        PropertyChangeListener[] all;
        PropertyChangeListener[] named;
        String propertyName = event.getPropertyName();
        synchronized (this) {
            all = listeners.toArray(new PropertyChangeListener[0]);
            List<PropertyChangeListener> namedList = namedListeners == null ? null : namedListeners.get(propertyName);
            named = namedList == null ? new PropertyChangeListener[0]
                    : namedList.toArray(new PropertyChangeListener[0]);
        }
        for (PropertyChangeListener l : all) {
            l.propertyChange(event);
        }
        for (PropertyChangeListener l : named) {
            l.propertyChange(event);
        }
    }

    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (oldValue != null && newValue != null && oldValue.equals(newValue)) {
            return;
        }
        firePropertyChange(new PropertyChangeEvent(source, propertyName, oldValue, newValue));
    }

    public void firePropertyChange(String propertyName, int oldValue, int newValue) {
        if (oldValue == newValue) {
            return;
        }
        firePropertyChange(propertyName, Integer.valueOf(oldValue), Integer.valueOf(newValue));
    }

    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        if (oldValue == newValue) {
            return;
        }
        firePropertyChange(propertyName, Boolean.valueOf(oldValue), Boolean.valueOf(newValue));
    }

    public void fireIndexedPropertyChange(String propertyName, int index, Object oldValue, Object newValue) {
        firePropertyChange(new IndexedPropertyChangeEvent(source, propertyName, oldValue, newValue, index));
    }

    public void fireIndexedPropertyChange(String propertyName, int index, int oldValue, int newValue) {
        if (oldValue != newValue) {
            fireIndexedPropertyChange(propertyName, index, Integer.valueOf(oldValue), Integer.valueOf(newValue));
        }
    }

    public void fireIndexedPropertyChange(String propertyName, int index, boolean oldValue, boolean newValue) {
        if (oldValue != newValue) {
            fireIndexedPropertyChange(propertyName, index, Boolean.valueOf(oldValue), Boolean.valueOf(newValue));
        }
    }
}

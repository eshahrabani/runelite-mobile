package javax.swing;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;

/**
 * Swing {@code AbstractAction}: the value map + enabled flag + property change support
 * every {@code Action} subclass (and every RuneLite {@code AbstractAction} lambda
 * wrapper) needs.
 */
public abstract class AbstractAction implements Action {
    private static final String ENABLED_PROPERTY = "enabled";

    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private final Map<String, Object> values = new HashMap<>();
    private boolean enabled = true;

    public AbstractAction() {
    }

    public AbstractAction(String name) {
        putValue(NAME, name);
    }

    public AbstractAction(String name, Icon icon) {
        putValue(NAME, name);
        putValue(SMALL_ICON, icon);
    }

    @Override
    public Object getValue(String key) {
        return values.get(key);
    }

    @Override
    public void putValue(String key, Object newValue) {
        if (key == null) {
            return;
        }
        Object oldValue = values.put(key, newValue);
        if (oldValue == null ? newValue != null : !oldValue.equals(newValue)) {
            firePropertyChange(key, oldValue, newValue);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean newValue) {
        boolean oldValue = this.enabled;
        if (oldValue != newValue) {
            this.enabled = newValue;
            firePropertyChange(ENABLED_PROPERTY, oldValue, newValue);
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return changeSupport.getPropertyChangeListeners();
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        changeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    /**
     * Fires the action's {@code actionPerformed} with a synthesized command string when
     * the caller did not supply an {@code ActionEvent} (the JDK's convenience entry point).
     */
    public void actionPerformed(java.awt.event.ActionEvent e) {
    }

    @Override
    public String toString() {
        Object name = getValue(NAME);
        return name != null ? name.toString() : super.toString();
    }
}

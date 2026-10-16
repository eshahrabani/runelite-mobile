package java.beans;

/**
 * JavaBeans {@code PropertyChangeEvent}: a bound property changed.
 */
public class PropertyChangeEvent extends java.util.EventObject {
    private static final long serialVersionUID = 7041145838872885090L;

    private final String propertyName;
    private final Object oldValue;
    private final Object newValue;
    private Object propagationId;

    public PropertyChangeEvent() {
        // Placeholder for tools/gen_stubs.py's hand-written-supertype model.
        this(null, null, null, null);
    }

    public PropertyChangeEvent(Object source, String propertyName, Object oldValue, Object newValue) {
        super(source == null ? PropertyChangeEvent.class : source);
        this.propertyName = propertyName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Object getNewValue() {
        return newValue;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public void setPropagationId(Object propagationId) {
        this.propagationId = propagationId;
    }

    public Object getPropagationId() {
        return propagationId;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[propertyName=" + propertyName
                + "; oldValue=" + oldValue + "; newValue=" + newValue + "]";
    }
}

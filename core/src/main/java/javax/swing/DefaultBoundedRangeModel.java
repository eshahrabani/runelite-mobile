package javax.swing;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Swing {@code DefaultBoundedRangeModel}: the standard {@link BoundedRangeModel}, the
 * state a {@link JScrollBar} (and the port's sliders/progress widgets) scroll through.
 *
 * <p>Hand-written rather than generated: the port's {@code JScrollBar} needs a real model
 * to own value/extent/minimum/maximum, to enforce the
 * {@code minimum <= value <= value+extent <= maximum} invariant and to notify change
 * listeners; a data-only stub could not scroll anything. Every value change funnels
 * through {@link #setRangeProperties} exactly as the JDK does, so at most one
 * {@code ChangeEvent} is fired per mutation.
 *
 * <p>Port deviation: the JDK keeps its listeners in a {@code javax.swing.event
 * .EventListenerList}; the port has no such class (nothing else needs the typed slot
 * array), so {@link #listenerList} is a plain list, which is the pattern the rest of this
 * hand-written runtime uses (see {@code JViewport}). Observably the two agree.
 */
public class DefaultBoundedRangeModel implements BoundedRangeModel {

    /**
     * The single {@code ChangeEvent} instance reused by every notification: the event's
     * only state is its source, which is always this model.
     */
    protected transient ChangeEvent changeEvent;

    /** The listeners waiting for model changes; the port's stand-in for EventListenerList. */
    protected final List<ChangeListener> listenerList = new ArrayList<>();

    private int value;
    private int extent;
    private int min;
    private int max = 100;
    private boolean isAdjusting;

    /** Creates a model with value 0, extent 0, minimum 0 and maximum 100. */
    public DefaultBoundedRangeModel() {
    }

    /**
     * Creates a model with the given properties.
     *
     * @throws IllegalArgumentException if the arguments do not satisfy
     *         {@code min <= value <= value+extent <= max}
     */
    public DefaultBoundedRangeModel(int value, int extent, int min, int max) {
        if ((max >= min) && (value >= min) && ((value + extent) >= value) && ((value + extent) <= max)) {
            this.value = value;
            this.extent = extent;
            this.min = min;
            this.max = max;
        } else {
            throw new IllegalArgumentException("invalid range properties");
        }
    }

    @Override
    public int getValue() {
        return value;
    }

    @Override
    public int getExtent() {
        return extent;
    }

    @Override
    public int getMinimum() {
        return min;
    }

    @Override
    public int getMaximum() {
        return max;
    }

    @Override
    public void setValue(int n) {
        n = Math.min(n, Integer.MAX_VALUE - extent);

        int newValue = Math.max(n, min);
        if (newValue + extent > max) {
            newValue = max - extent;
        }
        setRangeProperties(newValue, extent, min, max, isAdjusting);
    }

    @Override
    public void setExtent(int n) {
        int newExtent = Math.max(0, n);
        if (value + newExtent > max) {
            newExtent = max - value;
        }
        setRangeProperties(value, newExtent, min, max, isAdjusting);
    }

    @Override
    public void setMinimum(int n) {
        int newMax = Math.max(n, max);
        int newValue = Math.max(n, value);
        int newExtent = Math.min(newMax - newValue, extent);
        setRangeProperties(newValue, newExtent, n, newMax, isAdjusting);
    }

    @Override
    public void setMaximum(int n) {
        int newMin = Math.min(n, min);
        int newExtent = Math.min(n - newMin, extent);
        int newValue = Math.min(n - newExtent, value);
        setRangeProperties(newValue, newExtent, newMin, n, isAdjusting);
    }

    @Override
    public void setValueIsAdjusting(boolean b) {
        setRangeProperties(value, extent, min, max, b);
    }

    @Override
    public boolean getValueIsAdjusting() {
        return isAdjusting;
    }

    @Override
    public void setRangeProperties(int newValue, int newExtent, int newMin, int newMax, boolean adjusting) {
        if (newMin > newMax) {
            newMin = newMax;
        }
        if (newValue > newMax) {
            newMax = newValue;
        }
        if (newValue < newMin) {
            newMin = newValue;
        }

        // The addends are widened to long so that extent can be Integer.MAX_VALUE without
        // overflowing the sum (JDK behaviour).
        if (((long) newExtent + (long) newValue) > newMax) {
            newExtent = newMax - newValue;
        }

        if (newExtent < 0) {
            newExtent = 0;
        }

        boolean isChange = (newValue != value)
                || (newExtent != extent)
                || (newMin != min)
                || (newMax != max)
                || (adjusting != isAdjusting);

        if (isChange) {
            value = newValue;
            extent = newExtent;
            min = newMin;
            max = newMax;
            isAdjusting = adjusting;
            fireStateChanged();
        }
    }

    @Override
    public void addChangeListener(ChangeListener x) {
        if (x != null && !listenerList.contains(x)) {
            listenerList.add(x);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener x) {
        listenerList.remove(x);
    }

    /** The registered change listeners, in registration order. */
    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[0]);
    }

    /**
     * Notifies every listener; the copy means a listener may add/remove listeners (or
     * trigger another model change) while it runs.
     */
    protected void fireStateChanged() {
        if (listenerList.isEmpty()) {
            return;
        }
        if (changeEvent == null) {
            changeEvent = new ChangeEvent(this);
        }
        for (ChangeListener listener : new ArrayList<>(listenerList)) {
            listener.stateChanged(changeEvent);
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + value + "," + extent + "," + min + "," + max + "]";
    }
}

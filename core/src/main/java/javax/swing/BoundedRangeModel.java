package javax.swing;

import javax.swing.event.ChangeListener;

/**
 * Swing {@code BoundedRangeModel}: the value/extent/minimum/maximum state behind a scroll
 * bar (and a slider), with the invariant
 * {@code minimum <= value <= value+extent <= maximum}.
 *
 * <p>Hand-written rather than generated: the shipped jars ask a {@code JScrollBar} for its
 * model and read {@code getExtent()} off it, so the interface has to declare the real
 * member set; the generated file declared {@code getExtent()} as a {@code default} method.
 * The marker sentence is removed so the generator leaves this file alone.
 *
 * <p>Port deviation: none; the JDK declares exactly these members. Where the JDK's
 * behaviour is unspecified (the order of listeners when several are registered), the port
 * follows {@link DefaultBoundedRangeModel}: registration order.
 */
public interface BoundedRangeModel {

    /** The model's current value. */
    int getValue();

    /**
     * Sets the value, clamped so that {@code minimum <= value <= value+extent <= maximum}
     * still holds (only one {@code ChangeEvent} is fired).
     */
    void setValue(int newValue);

    /** True while the value is being changed by an ongoing user gesture. */
    boolean getValueIsAdjusting();

    /** Sets the "adjusting" flag; models typically suppress expensive work while it is set. */
    void setValueIsAdjusting(boolean b);

    int getExtent();

    /** Sets the extent (the visible amount), clamped between 0 and {@code maximum - value}. */
    void setExtent(int newExtent);

    int getMinimum();

    void setMinimum(int newMinimum);

    /** The maximum value; the largest selectable value is {@code maximum - extent}. */
    int getMaximum();

    void setMaximum(int newMaximum);

    /**
     * Sets all four properties plus the adjusting flag at once, forcing the arguments to
     * obey the model's invariant. At most one {@code ChangeEvent} is generated.
     */
    void setRangeProperties(int newValue, int newExtent, int newMin, int newMax, boolean adjusting);

    void addChangeListener(ChangeListener x);

    void removeChangeListener(ChangeListener x);
}

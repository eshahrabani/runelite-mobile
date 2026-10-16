package javax.swing;

import javax.swing.event.ChangeListener;

/**
 * Hand-written {@code javax.swing.SpinnerModel}: the value sequence behind a {@link JSpinner}.
 *
 * <p>Why hand-written: the port's config UI builds spinners for numeric settings
 * ({@code ConfigPanel}, {@code NotificationPanel}, the skill calculator's XP multiplier), and
 * a spinner whose model cannot report its value, step or change notification is a dead
 * widget. The generated stub declared only {@code getValue}.
 *
 * <p>Deviation from the JDK: none of the members are {@code default} methods any more (the
 * generator emitted {@code getValue} as one); a model must implement the whole contract.
 */
public interface SpinnerModel {

    /** The model's current value. */
    Object getValue();

    /** Sets the value; implementations reject values the sequence cannot hold. */
    void setValue(Object value);

    /** The value that follows the current one, or null at the end of the sequence. */
    Object getNextValue();

    /** The value preceding the current one, or null at the start of the sequence. */
    Object getPreviousValue();

    /** Registers a listener for value changes. */
    void addChangeListener(ChangeListener l);

    /** Removes a listener registered with {@link #addChangeListener}. */
    void removeChangeListener(ChangeListener l);
}

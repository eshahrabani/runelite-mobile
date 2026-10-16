package javax.swing;

/**
 * Hand-written {@code javax.swing.SpinnerNumberModel}: the numeric sequence a {@link JSpinner}
 * steps through.
 *
 * <p>Why hand-written: every numeric spinner in the client's config UI is built from it
 * ({@code new SpinnerNumberModel(int value, int min, int max, int stepSize)}), and the
 * spinner's value, its step buttons and the value the setting is written back to all come
 * from this model. The generated stub returned null from every accessor, so the spinner had
 * no value to show and no step to take.
 *
 * <p>Semantics follow the JDK: the value and step size must be non-null {@link Number}s of a
 * compatible type, {@code getNextValue()}/{@code getPreviousValue()} answer null at the
 * bounds (which is what makes the spinner's arrows go dead there), and {@code setValue} does
 * not enforce the bounds (the spinner's editor clamps through the formatter instead).
 */
public class SpinnerNumberModel extends AbstractSpinnerModel implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private Number stepSize;
    private Number value;
    private Comparable<?> minimum;
    private Comparable<?> maximum;

    public SpinnerNumberModel(Number value, Comparable<?> minimum, Comparable<?> maximum, Number stepSize) {
        if (value == null || stepSize == null) {
            throw new IllegalArgumentException("value and stepSize must be non-null");
        }
        if (!((minimum == null || compare(minimum, value) <= 0)
                && (maximum == null || compare(maximum, value) >= 0))) {
            throw new IllegalArgumentException("(minimum <= value <= maximum) is false");
        }
        this.value = value;
        this.minimum = minimum;
        this.maximum = maximum;
        this.stepSize = stepSize;
    }

    public SpinnerNumberModel(int value, int minimum, int maximum, int stepSize) {
        this(Integer.valueOf(value), Integer.valueOf(minimum), Integer.valueOf(maximum),
                Integer.valueOf(stepSize));
    }

    public SpinnerNumberModel(double value, double minimum, double maximum, double stepSize) {
        this(Double.valueOf(value), Double.valueOf(minimum), Double.valueOf(maximum),
                Double.valueOf(stepSize));
    }

    public SpinnerNumberModel() {
        this(Integer.valueOf(0), null, null, Integer.valueOf(1));
    }

    /** The lowest allowed value, or null when there is none. */
    public Comparable<?> getMinimum() {
        return minimum;
    }

    public void setMinimum(Comparable<?> minimum) {
        if (minimum == null ? this.minimum != null : !minimum.equals(this.minimum)) {
            this.minimum = minimum;
            fireStateChanged();
        }
    }

    /** The highest allowed value, or null when there is none. */
    public Comparable<?> getMaximum() {
        return maximum;
    }

    public void setMaximum(Comparable<?> maximum) {
        if (maximum == null ? this.maximum != null : !maximum.equals(this.maximum)) {
            this.maximum = maximum;
            fireStateChanged();
        }
    }

    /** The amount added or subtracted on each step. */
    public Number getStepSize() {
        return stepSize;
    }

    public void setStepSize(Number stepSize) {
        if (stepSize == null) {
            throw new IllegalArgumentException("null stepSize");
        }
        if (!stepSize.equals(this.stepSize)) {
            this.stepSize = stepSize;
            fireStateChanged();
        }
    }

    /** The current value as a number. */
    public Number getNumber() {
        return value;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void setValue(Object value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("illegal value");
        }
        Number number = (Number) value;
        if (!number.equals(this.value)) {
            this.value = number;
            fireStateChanged();
        }
    }

    /** The value {@code dir} steps from the current one, or null when that leaves the bounds. */
    private Number incrValue(int dir) {
        Number newValue;
        if (value instanceof Float || value instanceof Double) {
            double v = value.doubleValue() + stepSize.doubleValue() * (double) dir;
            newValue = value instanceof Double ? (Number) Double.valueOf(v)
                    : (Number) Float.valueOf((float) v);
        } else {
            long v = value.longValue() + stepSize.longValue() * (long) dir;
            if (value instanceof Long) {
                newValue = Long.valueOf(v);
            } else if (value instanceof Integer) {
                newValue = Integer.valueOf((int) v);
            } else if (value instanceof Short) {
                newValue = Short.valueOf((short) v);
            } else {
                newValue = Byte.valueOf((byte) v);
            }
        }
        if (maximum != null && compare(maximum, newValue) < 0) {
            return null;
        }
        if (minimum != null && compare(minimum, newValue) > 0) {
            return null;
        }
        return newValue;
    }

    @Override
    public Object getNextValue() {
        return incrValue(+1);
    }

    @Override
    public Object getPreviousValue() {
        return incrValue(-1);
    }

    /**
     * Compares two numbers of the model's type through {@link Comparable}, tolerating
     * mixed numeric types the way the JDK's casts do not.
     */
    @SuppressWarnings("unchecked")
    private static int compare(Comparable<?> bound, Number candidate) {
        if (bound instanceof Comparable && !((Object) bound).getClass().isInstance(candidate)) {
            // Mixed types (an Integer bound with a Double value): compare numerically.
            return Double.compare(((Number) bound).doubleValue(), candidate.doubleValue());
        }
        return ((Comparable<Object>) bound).compareTo(candidate);
    }

    @Override
    public String toString() {
        return "value=" + value + ", minimum=" + minimum + ", maximum=" + maximum
                + ", stepSize=" + stepSize;
    }
}

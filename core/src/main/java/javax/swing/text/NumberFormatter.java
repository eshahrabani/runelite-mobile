package javax.swing.text;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;


/**
 * Hand-written {@code javax.swing.text.NumberFormatter}: the formatter of every numeric
 * {@link javax.swing.JSpinner} editor and of {@link JFormattedTextField}s holding numbers.
 *
 * <p>Why hand-written: the colour picker and the config panel wrap the spinner editor's
 * formatter factory in RuneLite's {@code UnitFormatterFactory}, whose {@code UnitFormatter}
 * delegate parses and formats through this class — a stub here means the XP multiplier and the
 * notification throttles show and accept nothing. The generated stub set had no such type.
 *
 * <p>Behaviour follows the JDK: the value class decides the type the parsed number is converted
 * back to (an {@code Integer} for a {@code SpinnerNumberModel} built from {@code int}s, so the
 * model's own comparisons keep working), and a default locale pattern is used when no
 * {@link NumberFormat} is supplied.
 */
public class NumberFormatter extends InternationalFormatter {

    private NumberFormat numberFormat;

    public NumberFormatter() {
        this(NumberFormat.getNumberInstance());
    }

    public NumberFormatter(NumberFormat format) {
        setFormat(format);
    }

    /** The pattern this formatter uses (restricted to a {@link NumberFormat}). */
    @Override
    public java.text.Format getFormat() {
        return numberFormat;
    }

    /** The number format this formatter uses, or null. */
    public NumberFormat getNumberFormat() {
        return numberFormat;
    }

    /**
     * Sets the number format.
     *
     * <p>As in the JDK, the argument must be a {@link NumberFormat}: a date or text format would
     * produce values the numeric editors cannot hold.
     */
    @Override
    public void setFormat(java.text.Format format) {
        if (format != null && !(format instanceof NumberFormat)) {
            throw new IllegalArgumentException("format must be a NumberFormat: " + format);
        }
        numberFormat = (NumberFormat) format;
    }

    @Override
    public Object stringToValue(String text) throws ParseException {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Number number = parseNumber(text);
        if (number == null) {
            throw new ParseException("unparseable number: \"" + text + "\"", 0);
        }
        checkBounds(number);
        Class<?> vc = getValueClass();
        if (vc == null || vc.isInstance(number)) {
            return number;
        }
        return convertToValueClass(number.toString(), vc);
    }

    @Override
    public String valueToString(Object value) throws ParseException {
        if (value == null) {
            return "";
        }
        if (!(value instanceof Number)) {
            throw new ParseException("value is not a number: " + value, 0);
        }
        NumberFormat format = numberFormat;
        if (format == null) {
            return value.toString();
        }
        return format.format(value);
    }

    /** Parses with the configured format, falling back to a plain parse of the digits. */
    private Number parseNumber(String text) {
        NumberFormat format = numberFormat;
        if (format != null) {
            java.text.ParsePosition position = new java.text.ParsePosition(0);
            Object parsed = format.parseObject(text, position);
            if (parsed instanceof Number) {
                return (Number) parsed;
            }
        }
        try {
            return new DecimalFormat().parse(text.replace(",", "").trim());
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Rejects values outside the bounds the spinner model set.
     *
     * <p>The comparison is numeric: the model's bounds and the parsed value can be different
     * {@link Number} types (an {@code Integer} bound with a {@code Long} value).
     */
    private void checkBounds(Number number) throws ParseException {
        Comparable<?> minimum = getMinimum();
        Comparable<?> maximum = getMaximum();
        double value = number.doubleValue();
        if (minimum instanceof Number && ((Number) minimum).doubleValue() > value) {
            throw new ParseException("value below minimum: " + number, 0);
        }
        if (maximum instanceof Number && value > ((Number) maximum).doubleValue()) {
            throw new ParseException("value above maximum: " + number, 0);
        }
    }

    /** The minimum, clamped to the configured type. */
    @Override
    public void setMinimum(Comparable<?> minimum) {
        if (minimum != null && !(minimum instanceof Number)) {
            throw new IllegalArgumentException("minimum must be a Number");
        }
        super.setMinimum(minimum);
    }

    /** The maximum, clamped to the configured type. */
    @Override
    public void setMaximum(Comparable<?> maximum) {
        if (maximum != null && !(maximum instanceof Number)) {
            throw new IllegalArgumentException("maximum must be a Number");
        }
        super.setMaximum(maximum);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

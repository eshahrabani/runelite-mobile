package javax.swing.text;

import java.text.AttributedCharacterIterator;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;

/**
 * Hand-written {@code javax.swing.text.InternationalFormatter}: a formatter driven by a
 * {@link Format} (a {@code DecimalFormat}, {@code DateFormat}, …).
 *
 * <p>Why hand-written: the spinner's editor formats and parses its value through one of these
 * ({@link NumberFormatter} extends it), and the client's {@code UnitFormatterFactory} delegates
 * to it. The generated stub set had no such type.
 *
 * <p>Deviations from the JDK: the minimum/maximum clamping happens in
 * {@link #stringToValue(String)} only (the JDK also enforces it through an
 * {@code AttributedCharacterIterator}-based text filter, which this port does not need because
 * its spinners clamp on commit); {@code setFormat} accepts any {@link Format}.
 */
public class InternationalFormatter extends DefaultFormatter {

    private Format format;
    private Comparable<?> minimum;
    private Comparable<?> maximum;

    public InternationalFormatter() {
    }

    public InternationalFormatter(Format format) {
        setFormat(format);
    }

    /** The pattern this formatter formats and parses with. */
    public Format getFormat() {
        return format;
    }

    /** Sets the pattern this formatter uses. */
    public void setFormat(Format format) {
        this.format = format;
    }

    /** The lowest value this formatter accepts. */
    public Comparable<?> getMinimum() {
        return minimum;
    }

    public void setMinimum(Comparable<?> minimum) {
        this.minimum = minimum;
    }

    /** The highest value this formatter accepts. */
    public Comparable<?> getMaximum() {
        return maximum;
    }

    public void setMaximum(Comparable<?> maximum) {
        this.maximum = maximum;
    }

    @Override
    public Object stringToValue(String text) throws ParseException {
        Format f = getFormat();
        if (f == null) {
            return super.stringToValue(text);
        }
        if (text == null || text.isEmpty()) {
            return null;
        }
        Object parsed = parse(text, f);
        Object value = convertParsed(parsed);
        checkBounds(value);
        return value;
    }

    @Override
    public String valueToString(Object value) throws ParseException {
        Format f = getFormat();
        if (f == null) {
            return super.valueToString(value);
        }
        if (value == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        f.format(value, sb, new FieldPosition(0));
        return sb.toString();
    }

    /** Parses the text, tolerating a leading/trailing space and quoted values. */
    private static Object parse(String text, Format format) throws ParseException {
        java.text.ParsePosition position = new java.text.ParsePosition(0);
        Object parsed = format.parseObject(text, position);
        if (parsed == null) {
            throw new ParseException("unparseable text: \"" + text + "\"", 0);
        }
        return parsed;
    }

    /** Converts the format's own result (a Long, a Date, …) to the value class. */
    private Object convertParsed(Object parsed) throws ParseException {
        Class<?> vc = getValueClass();
        if (vc == null || vc.isInstance(parsed)) {
            return parsed;
        }
        if (parsed instanceof Number && Number.class.isAssignableFrom(vc)) {
            return convertToValueClass(parsed.toString(), vc);
        }
        return parsed;
    }

    /** Rejects values outside the configured bounds. */
    private void checkBounds(Object value) throws ParseException {
        if (!(value instanceof Comparable)) {
            return;
        }
        Comparable<?> comparable = (Comparable<?>) value;
        if (minimum != null && compare(minimum, value) > 0) {
            throw new ParseException("value below minimum: " + value, 0);
        }
        if (maximum != null && compare(maximum, value) < 0) {
            throw new ParseException("value above maximum: " + value, 0);
        }
    }

    @SuppressWarnings("unchecked")
    private static int compare(Comparable<?> bound, Object value) {
        if (bound instanceof Number && value instanceof Number
                && !bound.getClass().equals(value.getClass())) {
            return Double.compare(((Number) bound).doubleValue(), ((Number) value).doubleValue());
        }
        return ((Comparable<Object>) bound).compareTo(value);
    }

    /** The attributes of the text the format produced (the JDK's contract; empty here). */
    protected AttributedCharacterIterator getIterator(Object value) {
        Format f = getFormat();
        if (f == null || value == null) {
            return null;
        }
        try {
            return f.formatToCharacterIterator(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

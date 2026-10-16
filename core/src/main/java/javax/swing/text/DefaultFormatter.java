package javax.swing.text;

import java.text.ParseException;

import javax.swing.JFormattedTextField;

/**
 * Hand-written {@code javax.swing.text.DefaultFormatter}: the formatter that converts a value to
 * and from its {@link Object#toString()} form.
 *
 * <p>Why hand-written: it is the base of the numeric formatters the spinner and the client's
 * {@code UnitFormatter} delegate to, and it carries the value class the conversion has to keep
 * (a {@code SpinnerNumberModel} built from {@code int}s requires the parsed value to be an
 * {@code Integer} again). The generated stub set had no such type.
 *
 * <p>Deviations from the JDK: navigation filters, overwrite mode and the invalid-edit beep are
 * not modelled (the port has no {@code NavigationFilter} and the beep adds nothing on Android);
 * {@code allowsInvalid} is stored and honoured by the parse path only.
 */
public class DefaultFormatter extends JFormattedTextField.AbstractFormatter implements Cloneable {

    private Class<?> valueClass;
    private boolean allowsInvalid = true;

    public DefaultFormatter() {
    }

    /** The class the parsed value is converted to. */
    public Class<?> getValueClass() {
        return valueClass;
    }

    /** Sets the class the parsed value is converted to. */
    public void setValueClass(Class<?> valueClass) {
        this.valueClass = valueClass;
    }

    /** True when the field may hold text that does not parse yet. */
    public boolean getAllowsInvalid() {
        return allowsInvalid;
    }

    /** Allows/disallows an intermediate state that does not parse. */
    public void setAllowsInvalid(boolean allowsInvalid) {
        this.allowsInvalid = allowsInvalid;
    }

    /**
     * Converts the field's text to a value of {@link #getValueClass()}.
     *
     * <p>Used by {@link JFormattedTextField#commitEdit()} and by the spinner's editor.
     */
    @Override
    public Object stringToValue(String string) throws ParseException {
        Class<?> vc = getValueClass();
        if (vc == null || string == null) {
            return string;
        }
        if (vc == String.class) {
            return string;
        }
        return convertToValueClass(string, vc);
    }

    /** Converts {@code value} to its display text. */
    @Override
    public String valueToString(Object value) throws ParseException {
        return value == null ? "" : value.toString();
    }

    /**
     * Builds a value of {@code type} from {@code text}: text-constructor first, then
     * {@code valueOf(String)}, as the JDK's reflection-based conversion does.
     */
    protected Object convertToValueClass(String text, Class<?> type) throws ParseException {
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            try {
                return convertNumber(text, type);
            } catch (NumberFormatException e) {
                throw new ParseException("cannot parse \"" + text + "\" as " + type.getSimpleName(), 0);
            }
        }
        try {
            java.lang.reflect.Method valueOf = type.getMethod("valueOf", String.class);
            if (valueOf.getReturnType() == type) {
                return valueOf.invoke(null, text);
            }
        } catch (ReflectiveOperationException ignored) {
            // Falls through to the constructor lookup below.
        }
        try {
            return type.getConstructor(String.class).newInstance(text);
        } catch (ReflectiveOperationException e) {
            throw new ParseException("cannot build a " + type.getSimpleName() + " from \"" + text + "\"", 0);
        }
    }

    private static Number convertNumber(String text, Class<?> type) {
        String trimmed = text.trim();
        if (type == Integer.class || type == int.class) {
            return Integer.valueOf((int) Double.parseDouble(trimmed));
        }
        if (type == Long.class || type == long.class) {
            return Long.valueOf((long) Double.parseDouble(trimmed));
        }
        if (type == Short.class || type == short.class) {
            return Short.valueOf((short) Double.parseDouble(trimmed));
        }
        if (type == Byte.class || type == byte.class) {
            return Byte.valueOf((byte) Double.parseDouble(trimmed));
        }
        if (type == Float.class || type == float.class) {
            return Float.valueOf(Float.parseFloat(trimmed));
        }
        if (type == Double.class || type == double.class) {
            return Double.valueOf(Double.parseDouble(trimmed));
        }
        return new java.math.BigDecimal(trimmed);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

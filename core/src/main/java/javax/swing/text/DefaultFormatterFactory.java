package javax.swing.text;

import javax.swing.JFormattedTextField;

/**
 * Hand-written {@code javax.swing.text.DefaultFormatterFactory}: chooses which
 * {@link JFormattedTextField.AbstractFormatter} a formatted text field uses for the value it
 * currently holds.
 *
 * <p>Why hand-written: RuneLite's {@code UnitFormatterFactory} (shipped in the client jar)
 * <em>extends</em> this class and delegates to the factory the text field already had, so the
 * delegate has to be real — the colour-picker spinner installs
 * {@code new UnitFormatterFactory(textField.getFormatterFactory(), "x")} and would get a null
 * delegate from a stub. The class is part of the port because the generated stub set only had
 * the nesting declared inside {@code JFormattedTextField}.
 *
 * <p>Behaviour follows the JDK: a null value selects the null formatter, otherwise the edit
 * formatter while the field has the focus and the display formatter while it does not, with the
 * default formatter as the fallback.
 */
public class DefaultFormatterFactory extends JFormattedTextField.AbstractFormatterFactory {

    private JFormattedTextField.AbstractFormatter defaultFormat;
    private JFormattedTextField.AbstractFormatter displayFormat;
    private JFormattedTextField.AbstractFormatter editFormat;
    private JFormattedTextField.AbstractFormatter nullFormat;

    public DefaultFormatterFactory() {
    }

    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter defaultFormat) {
        this(defaultFormat, null);
    }

    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter defaultFormat,
                                   JFormattedTextField.AbstractFormatter displayFormat) {
        this(defaultFormat, displayFormat, null);
    }

    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter defaultFormat,
                                   JFormattedTextField.AbstractFormatter displayFormat,
                                   JFormattedTextField.AbstractFormatter editFormat) {
        this(defaultFormat, displayFormat, editFormat, null);
    }

    public DefaultFormatterFactory(JFormattedTextField.AbstractFormatter defaultFormat,
                                   JFormattedTextField.AbstractFormatter displayFormat,
                                   JFormattedTextField.AbstractFormatter editFormat,
                                   JFormattedTextField.AbstractFormatter nullFormat) {
        this.defaultFormat = defaultFormat;
        this.displayFormat = displayFormat;
        this.editFormat = editFormat;
        this.nullFormat = nullFormat;
    }

    public void setDefaultFormatter(JFormattedTextField.AbstractFormatter atf) {
        defaultFormat = atf;
    }

    public JFormattedTextField.AbstractFormatter getDefaultFormatter() {
        return defaultFormat;
    }

    public void setDisplayFormatter(JFormattedTextField.AbstractFormatter atf) {
        displayFormat = atf;
    }

    public JFormattedTextField.AbstractFormatter getDisplayFormatter() {
        return displayFormat;
    }

    public void setEditFormatter(JFormattedTextField.AbstractFormatter atf) {
        editFormat = atf;
    }

    public JFormattedTextField.AbstractFormatter getEditFormatter() {
        return editFormat;
    }

    public void setNullFormatter(JFormattedTextField.AbstractFormatter atf) {
        nullFormat = atf;
    }

    public JFormattedTextField.AbstractFormatter getNullFormatter() {
        return nullFormat;
    }

    @Override
    public JFormattedTextField.AbstractFormatter getFormatter(JFormattedTextField source) {
        if (source == null) {
            return null;
        }
        JFormattedTextField.AbstractFormatter format = null;
        if (source.getValue() == null) {
            format = getNullFormatter();
        }
        if (format == null) {
            format = source.hasFocus() ? getEditFormatter() : getDisplayFormatter();
            if (format == null) {
                format = getDefaultFormatter();
            }
        }
        return format;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[default=" + defaultFormat + ",display=" + displayFormat
                + ",edit=" + editFormat + ",null=" + nullFormat + "]";
    }
}

package javax.swing;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.text.DateFormat;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.InternationalFormatter;
import javax.swing.text.JTextComponent;
import javax.swing.text.NumberFormatter;
import javax.swing.text.TextAction;

/**
 * Hand-written {@code javax.swing.JFormattedTextField}: a text field whose text is formatted
 * from, and parsed back to, a typed value.
 *
 * <p>Why hand-written: it is the editor of every {@link JSpinner} in the client, and RuneLite's
 * {@code UnitFormatterFactory} (shipped in the client jar) subclasses its nested
 * {@link AbstractFormatterFactory} / {@link AbstractFormatter} with exactly the shape the JDK
 * has — the nested types are <em>static</em> classes here, as in the JDK, which is what
 * {@code net.runelite.client.ui.UnitFormatter}'s {@code super()} call expects. A generated stub
 * could not hold a value, a formatter or a focus-lost policy.
 *
 * <p>What is real: the value (typed through the formatter factory), the formatter chain
 * ({@link javax.swing.text.NumberFormatter} for numbers, {@link DefaultFormatter} otherwise),
 * <code>commitEdit()</code> on Enter (through the {@code notify-field-accept} binding) and on
 * focus loss with the JDK's four policies, and the {@code editValid} / {@code value} property
 * changes the spinner's editor listens to.
 *
 * <p>Deviations from the JDK: no navigation filter and no {@code formatterActions} (the port has
 * neither type), and {@code invalidEdit()} beeps through {@link javax.swing.text.DefaultEditorKit}
 * instead of the look-and-feel's error feedback.
 */
public class JFormattedTextField extends JTextField {

    /** Focus loss commits the edited value (and leaves it on failure). */
    public static final int COMMIT = 0;

    /** Focus loss commits the edited value, or reverts it when it does not parse. */
    public static final int COMMIT_OR_REVERT = 1;

    /** Focus loss always reverts the edited text to the value. */
    public static final int REVERT = 2;

    /** Focus loss keeps the edited text (the value is only changed by an explicit commit). */
    public static final int PERSIST = 3;

    private Object value;
    private AbstractFormatterFactory factory;
    private AbstractFormatter format;
    private int focusLostBehavior = COMMIT_OR_REVERT;
    private boolean editValid = true;
    private boolean edited;
    private DocumentListener documentListener;

    public JFormattedTextField() {
        super();
        init();
    }

    public JFormattedTextField(Object value) {
        super();
        init();
        setValue(value);
    }

    public JFormattedTextField(Format format) {
        super();
        init();
        setFormatterFactory(getDefaultFormatterFactory(format));
    }

    public JFormattedTextField(AbstractFormatter formatter) {
        super();
        init();
        setFormatterFactory(new DefaultFormatterFactory(formatter));
    }

    public JFormattedTextField(AbstractFormatterFactory factory) {
        super();
        init();
        setFormatterFactory(factory);
    }

    public JFormattedTextField(AbstractFormatterFactory factory, Object value) {
        super();
        init();
        this.value = value;
        setFormatterFactory(factory);
    }

    private void init() {
        setFocusLostBehavior(COMMIT_OR_REVERT);
        documentListener = new DocumentHandler();
        Document d = getDocument();
        if (d != null) {
            d.addDocumentListener(documentListener);
        }
        // Enter commits the value and then behaves like a normal field (the JDK's CommitAction).
        getActionMap().put(notifyAction, new CommitAction());
        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
            }

            @Override
            public void focusLost(FocusEvent e) {
                handleFocusLost();
            }
        });
    }

    /** The UI delegate's class id (kept for API shape). */
    @Override
    public String getUIClassID() {
        return "FormattedTextFieldUI";
    }

    @Override
    public void setDocument(Document doc) {
        if (documentListener != null && getDocument() != null) {
            getDocument().removeDocumentListener(documentListener);
        }
        super.setDocument(doc);
        if (doc != null) {
            if (documentListener == null) {
                documentListener = new DocumentHandler();
            }
            doc.addDocumentListener(documentListener);
        }
    }

    // --------------------------------------------------------------- value

    /** The typed value, or null. */
    public Object getValue() {
        return value;
    }

    /** Sets the value, deriving a formatter from its type when none is installed. */
    public void setValue(Object value) {
        if (value != null && getFormatterFactory() == null) {
            setFormatterFactory(getDefaultFormatterFactory(value));
        }
        setValue(value, true, true);
    }

    private void setValue(Object value, boolean createFormat, boolean firePropertyChange) {
        Object oldValue = this.value;
        this.value = value;
        if (createFormat) {
            AbstractFormatterFactory f = getFormatterFactory();
            setFormatter(f == null ? null : f.getFormatter(this));
        } else {
            setEditValid(true);
        }
        setEdited(false);
        if (firePropertyChange) {
            firePropertyChange("value", oldValue, value);
        }
        repaint();
    }

    /** Parses the field's text and makes it the value. */
    public void commitEdit() throws ParseException {
        AbstractFormatter f = getFormatter();
        if (f != null) {
            setValue(f.stringToValue(getText()), false, true);
        }
    }

    // ----------------------------------------------------------- formatter

    /** The formatter factory this field uses. */
    public AbstractFormatterFactory getFormatterFactory() {
        return factory;
    }

    /** Installs a formatter factory and re-formats the current value with it. */
    public void setFormatterFactory(AbstractFormatterFactory tf) {
        AbstractFormatterFactory old = factory;
        factory = tf;
        firePropertyChange("formatterFactory", old, tf);
        setValue(getValue(), true, false);
    }

    /** The formatter currently installed by the factory. */
    public AbstractFormatter getFormatter() {
        return format;
    }

    /** Installs (or with null removes) the formatter. */
    protected void setFormatter(AbstractFormatter format) {
        AbstractFormatter oldFormat = this.format;
        if (oldFormat != null) {
            oldFormat.uninstall();
        }
        setEditValid(true);
        this.format = format;
        if (format != null) {
            format.install(this);
        }
        setEdited(false);
        firePropertyChange("textFormatter", oldFormat, format);
    }

    /** The focus-lost policy (see the {@code COMMIT}/{@code REVERT}/{@code PERSIST} constants). */
    public int getFocusLostBehavior() {
        return focusLostBehavior;
    }

    public void setFocusLostBehavior(int behavior) {
        if (behavior != COMMIT && behavior != COMMIT_OR_REVERT && behavior != REVERT
                && behavior != PERSIST) {
            throw new IllegalArgumentException("behavior");
        }
        focusLostBehavior = behavior;
    }

    /** True when the text currently parses. */
    public boolean isEditValid() {
        return editValid;
    }

    private void setEditValid(boolean valid) {
        if (valid != editValid) {
            editValid = valid;
            firePropertyChange("editValid", Boolean.valueOf(!valid), Boolean.valueOf(valid));
        }
    }

    /** Called when the user entered something the formatter cannot parse. */
    protected void invalidEdit() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // A port with no audio still reports the invalid edit through "editValid".
        }
    }

    /**
     * The factory the field builds for a value of an unknown type: numbers get a
     * {@link NumberFormatter} that keeps the value's type, other values a plain
     * {@link DefaultFormatter}.
     */
    private AbstractFormatterFactory getDefaultFormatterFactory(Object type) {
        if (type instanceof DateFormat) {
            // The port has no DateFormatter; a plain formatter keeps the field usable.
            return new DefaultFormatterFactory(new DefaultFormatter());
        }
        if (type instanceof NumberFormat) {
            return new DefaultFormatterFactory(new NumberFormatter((NumberFormat) type));
        }
        if (type instanceof Format) {
            return new DefaultFormatterFactory(new InternationalFormatter((Format) type));
        }
        if (type instanceof Number) {
            NumberFormatter displayFormatter = new NumberFormatter();
            displayFormatter.setValueClass(type.getClass());
            NumberFormatter editFormatter = new NumberFormatter(new java.text.DecimalFormat("#.#"));
            editFormatter.setValueClass(type.getClass());
            return new DefaultFormatterFactory(displayFormatter, displayFormatter, editFormatter);
        }
        return new DefaultFormatterFactory(new DefaultFormatter());
    }

    private void setEdited(boolean edited) {
        this.edited = edited;
    }

    private boolean isEdited() {
        return edited;
    }

    @Override
    public Action[] getActions() {
        return TextAction.augmentList(super.getActions(), new Action[] {new CommitAction(), new CancelAction()});
    }

    // ----------------------------------------------------------- listening

    /** Marks the field as edited while the user types. */
    private class DocumentHandler implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            setEdited(true);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            setEdited(true);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
        }
    }

    /** Applies the focus-lost policy (the JDK's {@code FocusLostHandler}). */
    private void handleFocusLost() {
        int behavior = getFocusLostBehavior();
        if (behavior == COMMIT || behavior == COMMIT_OR_REVERT) {
            try {
                commitEdit();
                setValue(getValue(), true, true);
            } catch (ParseException e) {
                if (behavior == COMMIT_OR_REVERT) {
                    setValue(getValue(), true, true);
                } else {
                    setEditValid(false);
                    invalidEdit();
                }
            }
        } else if (behavior == REVERT) {
            setValue(getValue(), true, true);
        }
    }

    /** Sets the value from the text on Enter, then notifies the field's action listeners. */
    static class CommitAction extends JTextField.NotifyAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target instanceof JFormattedTextField) {
                JFormattedTextField ftf = (JFormattedTextField) target;
                try {
                    ftf.commitEdit();
                } catch (ParseException pe) {
                    ftf.setEditValid(false);
                    ftf.invalidEdit();
                }
            }
            super.actionPerformed(e);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    /** Reverts the text to the current value. */
    private static class CancelAction extends TextAction {

        CancelAction() {
            super("reset-field-edit");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target instanceof JFormattedTextField) {
                JFormattedTextField ftf = (JFormattedTextField) target;
                ftf.setValue(ftf.getValue(), true, false);
            }
        }
    }

    /**
     * A formatter that converts between the field's text and a value.
     *
     * <p>The nesting and the method shapes are the JDK's, because the shipped
     * {@code net.runelite.client.ui.UnitFormatter} extends this class and its constructor calls
     * {@code super()} with no outer instance — the JDK declares it a <em>static</em> nested class
     * and so does this port.
     */
    public abstract static class AbstractFormatter implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private JFormattedTextField ftf;

        protected AbstractFormatter() {
        }

        /** Binds this formatter to a field and formats the field's value into its text. */
        public void install(JFormattedTextField ftf) {
            if (this.ftf != null) {
                uninstall();
            }
            this.ftf = ftf;
            if (ftf == null) {
                return;
            }
            try {
                ftf.setText(valueToString(ftf.getValue()));
            } catch (ParseException pe) {
                ftf.setText("");
                setEditValid(false);
            }
            DocumentFilter filter = getDocumentFilter();
            if (filter != null) {
                Document doc = ftf.getDocument();
                if (doc instanceof AbstractDocument) {
                    ((AbstractDocument) doc).setDocumentFilter(filter);
                }
            }
        }

        /** Detaches this formatter (and its document filter) from its field. */
        public void uninstall() {
            JFormattedTextField ftf = this.ftf;
            this.ftf = null;
            if (ftf == null) {
                return;
            }
            Document doc = ftf.getDocument();
            if (doc instanceof AbstractDocument) {
                ((AbstractDocument) doc).setDocumentFilter(null);
            }
        }

        /** Parses the field's text into a value. */
        public abstract Object stringToValue(String text) throws ParseException;

        /** Formats a value into the field's text. */
        public abstract String valueToString(Object value) throws ParseException;

        /** The field this formatter is installed on, or null. */
        protected JFormattedTextField getFormattedTextField() {
            return ftf;
        }

        /** Reports that the user entered something unparseable. */
        protected void invalidEdit() {
            JFormattedTextField ftf = getFormattedTextField();
            if (ftf != null) {
                ftf.invalidEdit();
            }
        }

        /** Updates the field's "the text parses" property. */
        protected void setEditValid(boolean valid) {
            JFormattedTextField ftf = getFormattedTextField();
            if (ftf != null) {
                ftf.setEditValid(valid);
            }
        }

        /** Actions this formatter contributes to the field (none by default). */
        protected javax.swing.Action[] getActions() {
            return null;
        }

        /** A filter this formatter installs on the field's document, or null. */
        protected DocumentFilter getDocumentFilter() {
            return null;
        }

        protected Object clone() throws CloneNotSupportedException {
            AbstractFormatter formatter = (AbstractFormatter) super.clone();
            formatter.ftf = null;
            return formatter;
        }
    }

    /**
     * Creates the {@link AbstractFormatter} a field should use; nested and static for the same
     * reason {@link AbstractFormatter} is.
     */
    public abstract static class AbstractFormatterFactory {

        protected AbstractFormatterFactory() {
        }

        /** The formatter for {@code tf} ({@code null} means "behave like a plain field"). */
        public abstract AbstractFormatter getFormatter(JFormattedTextField tf);
    }

}

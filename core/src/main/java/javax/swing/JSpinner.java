package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

/**
 * Hand-written {@code javax.swing.JSpinner}: the numeric stepper the config UI uses for
 * bounded integer and double settings (the XP multiplier, notification throttles, …).
 *
 * <p>Why hand-written: the client builds spinners with a {@link SpinnerNumberModel}, casts
 * {@link #getEditor()} to {@link DefaultEditor} to reach the text field and installs RuneLite's
 * {@code UnitFormatterFactory} on the editor's formatter factory. All of that has to work: a stub
 * editor returned null from {@code getTextField()} and the cast to {@code DefaultEditor} threw.
 *
 * <p>What is real: the model wiring (value, next/previous, change listeners), the editor
 * ({@link DefaultEditor}/{@link NumberEditor}) with its {@link JFormattedTextField}, the
 * commit-on-Enter and commit-on-focus-lost path that pushes the typed value into the model, and
 * the two step buttons painted as arrow glyphs in this component's right-hand strip.
 *
 * <p>Deviations from the JDK, all deliberate:
 * <ul>
 *   <li>There is no {@code SpinnerUI}. The JDK's {@code BasicSpinnerUI} builds the arrow buttons
 *       as child components; this port paints them itself and steps on a tap, which is what a
 *       touch target needs (and avoids two more widget classes).</li>
 *   <li>The step buttons act on press rather than on release, so a tap steps immediately.</li>
 *   <li>The editor's text field is <em>editable</em> for every editor (the JDK's
 *       {@code DefaultEditor} makes it read-only). Without it the on-screen keyboard could not
 *       type a value into a spinner at all.</li>
 * </ul>
 */
public class JSpinner extends JComponent implements javax.accessibility.Accessible {

    /** The width of the strip that holds the two step buttons. */
    private static final int BUTTON_WIDTH = 18;

    private SpinnerModel model;
    private JComponent editor;
    private boolean editorExplicitlySet;
    private ChangeListener modelListener;
    private final List<ChangeListener> changeListeners = new ArrayList<>(2);
    private transient ChangeEvent changeEvent;

    /** True while the step strip's upper half is being pressed (for the pressed look). */
    private boolean upPressed;
    private boolean downPressed;

    public JSpinner() {
        this(new SpinnerNumberModel());
    }

    public JSpinner(SpinnerModel model) {
        super();
        setOpaque(true);
        Color background = UIManager.getColor("Spinner.background");
        if (background != null) {
            setBackground(background);
        }
        Color foreground = UIManager.getColor("Spinner.foreground");
        if (foreground != null) {
            setForeground(foreground);
        }
        this.model = model;
        model.addChangeListener(getModelListener());
        this.editor = createEditor(model);
        add(editor);
        addMouseListener(new StepMouseHandler());
    }

    /** The UI delegate's class id (the port paints the spinner itself). */
    public String getUIClassID() {
        return "SpinnerUI";
    }

    // ------------------------------------------------------------- model

    /** The model this spinner steps through. */
    public SpinnerModel getModel() {
        return model;
    }

    public void setModel(SpinnerModel model) {
        if (model == null) {
            throw new IllegalArgumentException("null model");
        }
        if (model.equals(this.model)) {
            return;
        }
        SpinnerModel oldModel = this.model;
        if (oldModel != null) {
            oldModel.removeChangeListener(getModelListener());
        }
        this.model = model;
        model.addChangeListener(getModelListener());
        firePropertyChange("model", oldModel, model);
        if (!editorExplicitlySet) {
            setEditor(createEditor(model));
            editorExplicitlySet = false;
        }
        revalidate();
        repaint();
    }

    /** The model's current value. */
    public Object getValue() {
        return getModel().getValue();
    }

    /** Steps the model to {@code value}. */
    public void setValue(Object value) {
        getModel().setValue(value);
    }

    /** The value one step after the current one, or null at the end of the sequence. */
    public Object getNextValue() {
        return getModel().getNextValue();
    }

    /** The value one step before the current one, or null at the start of the sequence. */
    public Object getPreviousValue() {
        return getModel().getPreviousValue();
    }

    // ------------------------------------------------------------- editor

    /** The component that shows and edits the value. */
    public JComponent getEditor() {
        return editor;
    }

    /** Replaces the editor (it must be a {@link DefaultEditor} on this port). */
    public void setEditor(JComponent editor) {
        if (editor == null) {
            throw new IllegalArgumentException("null editor");
        }
        if (!editor.equals(this.editor)) {
            JComponent oldEditor = this.editor;
            if (oldEditor != null && oldEditor.getParent() == this) {
                remove(oldEditor);
            }
            this.editor = editor;
            if (oldEditor instanceof DefaultEditor) {
                ((DefaultEditor) oldEditor).dismiss(this);
            }
            if (editor.getParent() != this) {
                add(editor);
            }
            editorExplicitlySet = true;
            firePropertyChange("editor", oldEditor, editor);
            revalidate();
            repaint();
        }
    }

    /** Creates the default editor for a model. */
    protected JComponent createEditor(SpinnerModel model) {
        if (model instanceof SpinnerNumberModel) {
            return new NumberEditor(this);
        }
        return new DefaultEditor(this);
    }

    /**
     * Parses the editor's text into the model.
     *
     * <p>Called by Enter (the field's commit action) and by the focus-lost policy; the
     * {@code JFormattedTextField} pushes the parsed value into the model through its "value"
     * property change, which is what {@link DefaultEditor} listens for.
     */
    public void commitEdit() throws ParseException {
        JComponent editor = getEditor();
        if (editor instanceof DefaultEditor) {
            ((DefaultEditor) editor).commitEdit();
        }
    }

    // ---------------------------------------------------------- listeners

    /** Registers a listener for value changes. */
    public void addChangeListener(ChangeListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (changeListeners) {
            changeListeners.add(listener);
        }
    }

    /** Removes a listener registered with {@link #addChangeListener}. */
    public void removeChangeListener(ChangeListener listener) {
        synchronized (changeListeners) {
            changeListeners.remove(listener);
        }
    }

    /** The registered change listeners. */
    public ChangeListener[] getChangeListeners() {
        synchronized (changeListeners) {
            return changeListeners.toArray(new ChangeListener[0]);
        }
    }

    /** Notifies the change listeners (the model changed). */
    protected void fireStateChanged() {
        List<ChangeListener> snapshot;
        synchronized (changeListeners) {
            snapshot = new ArrayList<>(changeListeners);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            if (changeEvent == null) {
                changeEvent = new ChangeEvent(this);
            }
            snapshot.get(i).stateChanged(changeEvent);
        }
    }

    private ChangeListener getModelListener() {
        if (modelListener == null) {
            modelListener = new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    fireStateChanged();
                    repaint();
                }
            };
        }
        return modelListener;
    }

    // ------------------------------------------------------------- paint

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Color background = getBackground();
        if (background != null) {
            Color old = g.getColor();
            g.setColor(background);
            int stripWidth = Math.min(BUTTON_WIDTH, Math.max(getWidth() / 3, 0));
            int stripX = getWidth() - getInsets().right - stripWidth;
            g.fillRect(stripX, getInsets().top, stripWidth, getHeight() - getInsets().top - getInsets().bottom);
            g.setColor(old);
            paintStepButtons(g, stripX, stripWidth);
        }
    }

    /** Paints the two arrow glyphs of the step strip. */
    private void paintStepButtons(Graphics g, int stripX, int stripWidth) {
        Insets insets = getInsets();
        int top = insets.top;
        int height = getHeight() - insets.top - insets.bottom;
        if (height <= 0 || stripWidth <= 4) {
            return;
        }
        int half = height / 2;
        Color old = g.getColor();
        Color glyph = getForeground() != null ? getForeground() : Color.WHITE;
        Color area = new Color(60, 60, 60);
        if (upPressed) {
            g.setColor(area);
            g.fillRect(stripX, top, stripWidth, half);
        }
        if (downPressed) {
            g.setColor(area);
            g.fillRect(stripX, top + half, stripWidth, height - half);
        }
        g.setColor(glyph);
        int centreX = stripX + stripWidth / 2;
        int arrowHalfWidth = Math.max(3, stripWidth / 3);
        // Up arrow: a small triangle pointing up in the upper half.
        int upBaseY = top + half / 2 + Math.max(2, arrowHalfWidth / 2);
        int upTipY = top + half / 2 - Math.max(2, arrowHalfWidth / 2);
        fillTriangle(g, centreX, upTipY, centreX - arrowHalfWidth, upBaseY, centreX + arrowHalfWidth, upBaseY);
        // Down arrow: mirrored in the lower half.
        int downBaseY = top + half + (height - half) / 2 - Math.max(2, arrowHalfWidth / 2);
        int downTipY = top + half + (height - half) / 2 + Math.max(2, arrowHalfWidth / 2);
        fillTriangle(g, centreX, downTipY, centreX - arrowHalfWidth, downBaseY, centreX + arrowHalfWidth, downBaseY);
        g.setColor(old);
    }

    private static void fillTriangle(Graphics g, int x1, int y1, int x2, int y2, int x3, int y3) {
        g.fillPolygon(new int[] {x1, x2, x3}, new int[] {y1, y2, y3}, 3);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        Insets insets = getInsets();
        int stripWidth = Math.min(BUTTON_WIDTH, Math.max(getWidth() / 3, 0));
        if (editor != null) {
            editor.setBounds(insets.left, insets.top,
                    Math.max(0, getWidth() - insets.left - insets.right - stripWidth),
                    Math.max(0, getHeight() - insets.top - insets.bottom));
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Insets insets = getInsets();
        Dimension editorSize = editor == null ? new Dimension(30, 20) : editor.getPreferredSize();
        int lineHeight = getFontMetrics(getFont()).getHeight();
        int height = Math.max(editorSize.height, lineHeight + 6);
        return new Dimension(editorSize.width + BUTTON_WIDTH + insets.left + insets.right,
                height + insets.top + insets.bottom);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    // ------------------------------------------------------------- input

    /** Steps the model when the step strip is tapped. */
    private final class StepMouseHandler implements java.awt.event.MouseListener {
        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            if (!isEnabled()) {
                return;
            }
            int stripWidth = Math.min(BUTTON_WIDTH, Math.max(getWidth() / 3, 0));
            int stripX = getWidth() - getInsets().right - stripWidth;
            if (e.getX() < stripX) {
                // A tap on the value itself: hand the focus to the editor's field.
                if (editor instanceof DefaultEditor) {
                    ((DefaultEditor) editor).getTextField().requestFocus();
                }
                return;
            }
            int middle = getInsets().top + (getHeight() - getInsets().top - getInsets().bottom) / 2;
            if (e.getY() < middle) {
                upPressed = true;
                step(true);
            } else {
                downPressed = true;
                step(false);
            }
            repaint();
        }

        @Override
        public void mouseReleased(java.awt.event.MouseEvent e) {
            upPressed = false;
            downPressed = false;
            repaint();
        }

        @Override
        public void mouseClicked(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mouseEntered(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mouseExited(java.awt.event.MouseEvent e) {
        }
    }

    /** Steps the model one value up or down (a no-op at the ends of the sequence). */
    private void step(boolean up) {
        Object next = up ? getNextValue() : getPreviousValue();
        if (next != null) {
            setValue(next);
        }
    }

    /**
     * The default editor: a panel holding the editor's {@link JFormattedTextField}, which it
     * keeps in step with the spinner's model in both directions.
     *
     * <p>This is where a typed value reaches the model: the text field fires a "value" property
     * change when it commits (Enter, or focus loss under the {@code COMMIT_OR_REVERT} policy) and
     * the editor forwards it to {@link JSpinner#setValue(Object)}. The JDK's equivalent is the
     * same; the difference is that this port's layout manager simply gives the field the whole
     * panel, because the spinner sizes the editor itself.
     */
    public static class DefaultEditor extends JPanel
            implements ChangeListener, PropertyChangeListener, LayoutManager {

        /** The spinner this editor is installed on, or null while it is not. */
        private JSpinner spinner;

        /** The editor without a spinner; kept because the generated stub declared it. */
        public DefaultEditor() {
            this(null);
        }

        public DefaultEditor(JSpinner spinner) {
            super(null);
            this.spinner = spinner;
            JFormattedTextField ftf = new JFormattedTextField();
            ftf.setName("Spinner.formattedTextField");
            ftf.setHorizontalAlignment(JTextField.RIGHT);
            if (spinner != null) {
                ftf.setValue(spinner.getValue());
                String toolTip = spinner.getToolTipText();
                if (toolTip != null) {
                    ftf.setToolTipText(toolTip);
                }
            }
            // The JDK makes this field read-only; on a touch port it is the only way in.
            ftf.setEditable(true);
            ftf.addPropertyChangeListener(this);
            add(ftf);
            setLayout(this);
            if (spinner != null) {
                spinner.addChangeListener(this);
            }
        }

        /** Detaches this editor from a spinner (called when the editor is replaced). */
        public void dismiss(JSpinner spinner) {
            if (spinner != null) {
                spinner.removeChangeListener(this);
            }
            if (this.spinner == spinner) {
                this.spinner = null;
            }
        }

        /**
         * The spinner this editor is installed on: the field's parent chain is walked so the
         * editor keeps working after it has been added to a component that is not a spinner.
         */
        public JSpinner getSpinner() {
            if (spinner != null) {
                return spinner;
            }
            for (Component c = this; c != null; c = c.getParent()) {
                if (c instanceof JSpinner) {
                    return (JSpinner) c;
                }
            }
            return null;
        }

        /** The field this editor edits the value in. */
        public JFormattedTextField getTextField() {
            return (JFormattedTextField) getComponent(0);
        }

        /** Parses the field's text into the value (the spinner's {@code commitEdit}). */
        public void commitEdit() throws ParseException {
            getTextField().commitEdit();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            if (e.getSource() instanceof JSpinner) {
                getTextField().setValue(((JSpinner) e.getSource()).getValue());
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            JSpinner spinner = getSpinner();
            if (spinner == null) {
                return;
            }
            if (e.getSource() instanceof JFormattedTextField && "value".equals(e.getPropertyName())) {
                Object lastValue = spinner.getValue();
                try {
                    spinner.setValue(getTextField().getValue());
                } catch (IllegalArgumentException iae) {
                    // The model rejected the value: put the field back in step with it.
                    getTextField().setValue(lastValue);
                }
            }
        }

        // -------------------------------------------------------- LayoutManager

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            Dimension size = getComponent(0).getPreferredSize();
            return new Dimension(size.width + insets.left + insets.right,
                    size.height + insets.top + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            getComponent(0).setBounds(insets.left, insets.top, width, height);
        }
    }

    /**
     * The editor of a {@link SpinnerNumberModel}: a {@link javax.swing.text.NumberFormatter}
     * bound to the model's value class and bounds, exactly as the JDK's {@code NumberEditor}
     * does — which is what makes RuneLite's {@code UnitFormatterFactory} (installed on top of
     * this factory by the client) delegate to a working formatter.
     */
    public static class NumberEditor extends DefaultEditor {

        public NumberEditor(JSpinner spinner) {
            this(spinner, new DecimalFormat("#.#"));
        }

        public NumberEditor(JSpinner spinner, String decimalFormatPattern) {
            this(spinner, new DecimalFormat(decimalFormatPattern));
        }

        private NumberEditor(JSpinner spinner, DecimalFormat format) {
            super(spinner);
            if (!(spinner.getModel() instanceof SpinnerNumberModel)) {
                throw new IllegalArgumentException("model not a SpinnerNumberModel");
            }
            SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
            NumberFormatter formatter = new NumberEditorFormatter(model, format);
            JFormattedTextField ftf = getTextField();
            ftf.setEditable(true);
            ftf.setFormatterFactory(new DefaultFormatterFactory(formatter));
            ftf.setHorizontalAlignment(JTextField.RIGHT);
        }

        /** The number format this editor formats with. */
        public DecimalFormat getFormat() {
            return (DecimalFormat) ((NumberFormatter) getTextField().getFormatter()).getFormat();
        }

        /** The model this editor edits (always a {@link SpinnerNumberModel}). */
        public SpinnerNumberModel getModel() {
            return (SpinnerNumberModel) getSpinner().getModel();
        }
    }

    /** The formatter the number editor installs: it clamps to the model's own bounds. */
    private static class NumberEditorFormatter extends NumberFormatter {
        private final SpinnerNumberModel model;

        NumberEditorFormatter(SpinnerNumberModel model, java.text.NumberFormat format) {
            this.model = model;
            setValueClass(model.getValue().getClass());
            setFormat(format);
            setMinimum(model.getMinimum());
            setMaximum(model.getMaximum());
        }

        @Override
        public void setMinimum(Comparable<?> minimum) {
            model.setMinimum(minimum);
        }

        @Override
        public Comparable<?> getMinimum() {
            return model.getMinimum();
        }

        @Override
        public void setMaximum(Comparable<?> maximum) {
            model.setMaximum(maximum);
        }

        @Override
        public Comparable<?> getMaximum() {
            return model.getMaximum();
        }
    }

    /** The bounds of the component itself (kept for API shape; nothing queries it here). */
    public Rectangle getEditorBounds() {
        return editor == null ? new Rectangle() : editor.getBounds();
    }
}

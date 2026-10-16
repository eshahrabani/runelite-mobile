package javax.swing;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import javax.swing.text.TextAction;

/**
 * Hand-written {@code javax.swing.JTextField}: the single-line text field the sidebar's inputs
 * are built from.
 *
 * <p>Why hand-written: every text input in the plugin panels is one of these (the color
 * picker's hex field, the profile renamer, {@code IconTextField}'s hidden field, the XP
 * multiplier's editor). They must accept the on-screen keyboard's characters into a real
 * {@link Document}, scroll horizontally when the text is wider than the field, and fire an
 * {@link ActionListener} on Enter — none of which the generated stub could do.
 *
 * <p>What is real here: the document (a {@link PlainDocument} that filters newlines, so a
 * multi-line paste cannot turn a field into a text area), the column count and the preferred
 * width it implies, the horizontal alignment and its effect on painting and hit-testing, and
 * Enter &rarr; {@code postActionEvent()} through the {@code notify-field-accept} binding.
 *
 * <p>Deviations from the JDK: the field does not install a look-and-feel border by itself (the
 * client's own components set their borders, e.g. {@code FlatTextField}'s empty border), and
 * {@code isValidateRoot()} is not modelled.
 */
public class JTextField extends JTextComponent implements SwingConstants {

    /** The action name the Enter key is bound to (the JDK's name for it). */
    public static final String notifyAction = "notify-field-accept";

    private int columns;
    private int columnWidth;
    private int horizontalAlignment = LEADING;

    private List<ActionListener> actionListeners;
    private String actionCommand;

    public JTextField() {
        this(null, null, 0);
    }

    public JTextField(String text) {
        this(null, text, 0);
    }

    public JTextField(int columns) {
        this(null, null, columns);
    }

    public JTextField(String text, int columns) {
        this(null, text, columns);
    }

    public JTextField(Document doc, String text, int columns) {
        super();
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        this.columns = columns;
        setDocument(doc == null ? createDefaultModel() : doc);
        if (text != null) {
            setText(text);
        }
        // Enter accepts the field (a single-line field has no use for a line break).
        getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), notifyAction);
        getActionMap().put(notifyAction, new NotifyAction());
    }

    /** The UI delegate's class id (the port has no look-and-feel UI, kept for API shape). */
    public String getUIClassID() {
        return "TextFieldUI";
    }

    /** A single-line document: newlines typed or pasted in are turned into spaces. */
    protected Document createDefaultModel() {
        return new PlainDocument();
    }

    @Override
    public void setDocument(Document doc) {
        if (doc != null) {
            // The document, not the field, is what filters the newlines (as in the JDK).
            doc.putProperty("filterNewlines", Boolean.TRUE);
        }
        super.setDocument(doc);
    }

    // --------------------------------------------------------------- text

    @Override
    public String getText() {
        return super.getText();
    }

    @Override
    public void setText(String t) {
        super.setText(t);
    }

    @Override
    public boolean isEditable() {
        return super.isEditable();
    }

    @Override
    public void setEditable(boolean b) {
        super.setEditable(b);
    }

    // --------------------------------------------------------------- size

    /** The number of columns the field's preferred width is based on. */
    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        int old = this.columns;
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        if (columns != old) {
            this.columns = columns;
            invalidate();
            revalidate();
            repaint();
        }
    }

    /** The width of one 'm' in the field's font. */
    protected int getColumnWidth() {
        if (columnWidth == 0) {
            FontMetrics metrics = getFontMetrics(getFont());
            columnWidth = metrics.stringWidth("m");
        }
        return columnWidth;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (columns != 0) {
            Insets insets = getInsets();
            size.width = Math.max(size.width, columns * getColumnWidth()
                    + insets.left + insets.right);
        }
        return size;
    }

    @Override
    public void setFont(java.awt.Font f) {
        super.setFont(f);
        columnWidth = 0;
    }

    // ------------------------------------------------------------- layout

    /** The horizontal alignment of the text inside the field. */
    public int getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public void setHorizontalAlignment(int alignment) {
        if (alignment == horizontalAlignment) {
            return;
        }
        int old = horizontalAlignment;
        if (alignment == LEFT || alignment == CENTER || alignment == RIGHT
                || alignment == LEADING || alignment == TRAILING) {
            horizontalAlignment = alignment;
        } else {
            throw new IllegalArgumentException("horizontalAlignment");
        }
        firePropertyChange("horizontalAlignment", old, horizontalAlignment);
        invalidateLines();
        repaint();
    }

    /** Shifts the text for {@link #getHorizontalAlignment()} (and flips LEADING/TRAILING). */
    @Override
    protected int lineOriginX(Line line) {
        int base = super.lineOriginX(line);
        int alignment = horizontalAlignment;
        if (alignment == LEADING) {
            alignment = LEFT;
        } else if (alignment == TRAILING) {
            alignment = RIGHT;
        }
        Insets insets = getInsets();
        Insets margin = getMargin();
        int available = getWidth() - insets.left - insets.right - margin.left - margin.right;
        int slack = available - line.width;
        if (slack <= 0) {
            return base;
        }
        if (alignment == CENTER) {
            return base + slack / 2;
        }
        if (alignment == RIGHT) {
            return base + slack;
        }
        return base;
    }

    @Override
    protected boolean isMultiLine() {
        return false;
    }

    // ------------------------------------------------------------- actions

    /** Registers a listener for the action the field fires on Enter. */
    public void addActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        synchronized (this) {
            if (actionListeners == null) {
                actionListeners = new ArrayList<>(2);
            }
            actionListeners.add(l);
        }
    }

    /** Removes a listener registered with {@link #addActionListener}. */
    public void removeActionListener(ActionListener l) {
        synchronized (this) {
            if (actionListeners != null) {
                actionListeners.remove(l);
            }
        }
    }

    /** The registered action listeners. */
    public ActionListener[] getActionListeners() {
        synchronized (this) {
            return actionListeners == null ? new ActionListener[0]
                    : actionListeners.toArray(new ActionListener[0]);
        }
    }

    /** True when the field has at least one action listener. */
    public boolean hasActionListener() {
        synchronized (this) {
            return actionListeners != null && !actionListeners.isEmpty();
        }
    }

    /** The command string the field's {@link ActionEvent} carries. */
    public String getActionCommand() {
        return actionCommand;
    }

    /** Sets the command string the field's action events carry. */
    public void setActionCommand(String command) {
        this.actionCommand = command;
    }

    /** Fires the field's action event (Enter, or an explicit {@link #postActionEvent()}). */
    protected void fireActionPerformed() {
        List<ActionListener> snapshot;
        synchronized (this) {
            if (actionListeners == null) {
                return;
            }
            snapshot = new ArrayList<>(actionListeners);
        }
        String command = actionCommand != null ? actionCommand : getText();
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command);
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).actionPerformed(event);
        }
    }

    /** The JDK's entry point for the field's "accept" action. */
    public void postActionEvent() {
        fireActionPerformed();
    }

    @Override
    public javax.swing.Action[] getActions() {
        return TextAction.augmentList(super.getActions(), new javax.swing.Action[] {new NotifyAction()});
    }

    /** The action behind the Enter key: accept the field. */
    static class NotifyAction extends TextAction {

        NotifyAction() {
            super(notifyAction);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target instanceof JTextField) {
                ((JTextField) target).postActionEvent();
            }
        }

        @Override
        public boolean isEnabled() {
            JTextComponent target = getFocusedComponent();
            return target instanceof JTextField && ((JTextField) target).hasActionListener();
        }
    }

}

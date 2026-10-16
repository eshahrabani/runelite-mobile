package javax.swing.text;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.UIManager;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;

/**
 * Hand-written {@code javax.swing.text.JTextComponent}: the base of every text widget on this
 * port — {@code JTextField}, {@code JPasswordField}, {@code JTextArea},
 * {@code JFormattedTextField} and {@code JEditorPane}.
 *
 * <p>Why hand-written: the sidebar's own text fields are load-bearing. The color picker's hex
 * field rejects input with a {@link DocumentFilter}, Notes keeps an undo stack, the profile
 * renamer validates as you type, {@code FlatTextField} needs the text to scroll horizontally
 * inside a fixed-width box, and the keyboard bar types through {@link #replaceSelection}. The
 * generated stub could not store a document, a caret or a selection.
 *
 * <p>What is real here: the document wiring and the editable operations (insert, delete,
 * select, cut/copy/paste through the system clipboard), the caret and its listeners, the key
 * handling (the JDK's default text bindings installed by {@link DefaultEditorKit}, plus typed
 * characters), the mouse gesture that focuses the field and moves the caret, and the painting
 * (background, text, selection behind the run, a one-pixel caret when the component has
 * focus) with the internal scrolling a single-line field needs.
 *
 * <p>Deviations from the JDK, all documented in place:
 * <ul>
 *   <li><b>No {@code TextUI} and no {@link View} tree.</b> The JDK delegates layout and painting
 *       to a UI delegate that builds views; this port lays the text out in
 *       {@link #computeLines()} (one line for a field, wrapped lines for a text area) and paints
 *       it directly. {@link #modelToView(int)}/{@link #viewToModel(Point)} are the interface to
 *       that layout.</li>
 *   <li><b>Caret blinking is off.</b> {@link DefaultCaret#setBlinkRate(int)} only stores the
 *       rate.</li>
 *   <li><b>{@code getFocusedComponent()} answers the focus owner</b> when it is a text
 *       component (the JDK remembers the last one through an {@code AppContext}).</li>
 * </ul>
 */
public abstract class JTextComponent extends JComponent implements Scrollable,
        javax.accessibility.Accessible {

    /** Client property key holding the last text component to hold the focus. */
    public static final String FOCUSED_COMPONENT = "FOCUSED_COMPONENT";

    /** The name of the JDK's default keymap (kept for API compatibility). */
    public static final String DEFAULT_KEYMAP = "default";

    private Document doc;
    private Caret caret;
    private Highlighter highlighter;
    private boolean editable = true;
    private Insets margin;
    private Color selectionColor;
    private Color selectedTextColor;
    private Color caretColor;
    private Color disabledTextColor;

    /** Internal scroll offsets of the text inside the component. */
    private int scrollX;
    private int scrollY;

    private final java.util.List<CaretListener> caretListeners = new java.util.ArrayList<>(1);
    private transient CaretPositionEvent caretEvent;

    /** Owned and installed on the document, so Ctrl+Z/Ctrl+Y work on every text field. */
    private UndoManager undoManager;
    private Action undoAction;
    private Action redoAction;

    private java.util.List<Line> lines;
    private int linesDocLength = -1;
    private int linesWidth = -1;

    protected JTextComponent() {
        super();
        setOpaque(true);
        setFocusable(true);
        setFont(UIManager.getFont("TextField.font"));
        Color background = UIManager.getColor("TextField.background");
        if (background != null) {
            setBackground(background);
        }
        setForeground(UIManager.getColor("TextField.foreground"));
        setCaretColor(UIManager.getColor("TextField.caretForeground"));
        setSelectionColor(UIManager.getColor("TextField.selectionBackground"));
        setSelectedTextColor(UIManager.getColor("TextField.selectionForeground"));
        setDisabledTextColor(defaultDisabledTextColor());
        Object marginDefault = UIManager.get("TextField.margin");
        setMargin(marginDefault instanceof Insets ? (Insets) marginDefault : new Insets(0, 0, 0, 0));
        Color caret = UIManager.getColor("TextField.caretForeground");
        setCaretColor(caret != null ? caret : getForeground());
        setDocument(createDefaultModel());
        installDefaultKeyBindings();
        addMouseListener(new MouseHandler());
        addMouseMotionListener(new MouseMotionHandler());
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    // --------------------------------------------------------- component

    /** The component that currently holds text focus, or null. */
    public static JTextComponent getFocusedComponent() {
        Component owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner instanceof JTextComponent ? (JTextComponent) owner : null;
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean old = isEnabled();
        super.setEnabled(enabled);
        if (old != enabled) {
            repaint();
        }
    }

    // ---------------------------------------------------------- document

    /** A fresh document for this component (a {@link PlainDocument} by default). */
    protected Document createDefaultModel() {
        return new PlainDocument();
    }

    /** The document this component edits. */
    public Document getDocument() {
        return doc;
    }

    /**
     * Replaces the document.
     *
     * <p>The caret's listeners move to the new document, and this component's undo manager is
     * registered on it (so Ctrl+Z works without the caller doing anything).
     */
    public void setDocument(Document doc) {
        if (this.doc == doc) {
            return;
        }
        Document old = this.doc;
        if (old != null) {
            if (undoManager != null) {
                old.removeUndoableEditListener(undoManager);
            }
            if (documentListener != null) {
                old.removeDocumentListener(documentListener);
            }
        }
        this.doc = doc;
        if (doc != null) {
            if (undoManager == null) {
                undoManager = new UndoManager();
            }
            doc.addUndoableEditListener(undoManager);
            if (caret == null) {
                setCaret(new DefaultCaret());
            } else {
                caret.install(this);
            }
        }
        installDocumentListener(doc);
        lines = null;
        linesDocLength = -1;
        firePropertyChange("document", old, doc);
        invalidate();
        revalidate();
        repaint();
    }

    /** The text of the whole document, or null when there is no document. */
    public String getText() {
        Document d = getDocument();
        if (d == null) {
            return null;
        }
        try {
            return d.getText(0, d.getLength());
        } catch (BadLocationException e) {
            return null;
        }
    }

    /** The text of a range of the document. */
    public String getText(int offs, int len) throws BadLocationException {
        Document d = getDocument();
        return d == null ? null : d.getText(offs, len);
    }

    /** Replaces the whole content with {@code t}. */
    public void setText(String t) {
        Document d = getDocument();
        if (d == null) {
            return;
        }
        try {
            d.remove(0, d.getLength());
            if (t != null && !t.isEmpty()) {
                d.insertString(0, t, null);
            }
            caret.setDot(0);
        } catch (BadLocationException e) {
            // Nothing sensible to do: the document rejected a change it just produced.
        }
        firePropertyChange("text", null, t);
        repaint();
    }

    /** True when the user may type into this component. */
    public boolean isEditable() {
        return editable;
    }

    /** Enables/disables typing (and the clipboard actions). */
    public void setEditable(boolean b) {
        boolean old = editable;
        editable = b;
        if (old != b) {
            firePropertyChange("editable", old, b);
            repaint();
        }
    }

    // ------------------------------------------------------------- caret

    /** This component's caret. */
    public Caret getCaret() {
        if (caret == null) {
            setCaret(new DefaultCaret());
        }
        return caret;
    }

    /** Replaces the caret ({@code null} installs the default one). */
    public void setCaret(Caret c) {
        if (caret != null) {
            caret.removeChangeListener(getCaretChangeListener());
            caret.deinstall(this);
        }
        Caret old = caret;
        caret = c == null ? new DefaultCaret() : c;
        caret.install(this);
        caret.addChangeListener(getCaretChangeListener());
        firePropertyChange("caret", old, caret);
    }

    private CaretChangeListener caretChangeListener;

    private ChangeListener getCaretChangeListener() {
        if (caretChangeListener == null) {
            caretChangeListener = new CaretChangeListener();
        }
        return caretChangeListener;
    }

    /** The caret's own listener: turns caret movement into {@code CaretEvent}s. */
    private final class CaretChangeListener implements ChangeListener {
        @Override
        public void stateChanged(ChangeEvent e) {
            if (caretEvent == null) {
                caretEvent = new CaretPositionEvent(JTextComponent.this);
            }
            caretEvent.setPositions(getCaret().getDot(), getCaret().getMark());
            fireCaretUpdate(caretEvent);
            scrollToCaret();
            repaint();
        }
    }

    /** The caret position ("dot"). */
    public int getCaretPosition() {
        return getCaret().getDot();
    }

    /** Moves the caret, dropping the selection. */
    public void setCaretPosition(int position) {
        getCaret().setDot(position);
    }

    /** Extends the selection from the mark to {@code position}. */
    public void moveCaretPosition(int position) {
        getCaret().moveDot(position);
    }

    /** Registers a listener for caret/selection changes. */
    public void addCaretListener(CaretListener listener) {
        if (listener != null) {
            synchronized (caretListeners) {
                caretListeners.add(listener);
            }
        }
    }

    /** Removes a listener registered with {@link #addCaretListener}. */
    public void removeCaretListener(CaretListener listener) {
        synchronized (caretListeners) {
            caretListeners.remove(listener);
        }
    }

    /** The registered caret listeners. */
    public CaretListener[] getCaretListeners() {
        synchronized (caretListeners) {
            return caretListeners.toArray(new CaretListener[0]);
        }
    }

    /** Notifies the {@link CaretListener}s. */
    protected void fireCaretUpdate(CaretEvent e) {
        java.util.List<CaretListener> snapshot;
        synchronized (caretListeners) {
            snapshot = new java.util.ArrayList<>(caretListeners);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).caretUpdate(e);
        }
    }

    // --------------------------------------------------------- selection

    /** The lower end of the selection. */
    public int getSelectionStart() {
        return Math.min(getCaret().getDot(), getCaret().getMark());
    }

    /** The upper end of the selection. */
    public int getSelectionEnd() {
        return Math.max(getCaret().getDot(), getCaret().getMark());
    }

    /** Sets the selection starting end. */
    public void setSelectionStart(int selectionStart) {
        select(selectionStart, getSelectionEnd());
    }

    /** Sets the selection ending end. */
    public void setSelectionEnd(int selectionEnd) {
        select(getSelectionStart(), selectionEnd);
    }

    /** Selects the given range (either end may be the smaller one). */
    public void select(int selectionStart, int selectionEnd) {
        Document d = getDocument();
        if (d == null) {
            return;
        }
        if (selectionStart < 0) {
            selectionStart = 0;
        }
        if (selectionEnd > d.getLength()) {
            selectionEnd = d.getLength();
        }
        if (selectionStart > selectionEnd) {
            int tmp = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = tmp;
        }
        setCaretPosition(selectionStart);
        moveCaretPosition(selectionEnd);
    }

    /** Selects the whole document. */
    public void selectAll() {
        Document d = getDocument();
        if (d != null) {
            select(0, d.getLength());
        }
    }

    /** The selected text, or null when nothing is selected. */
    public String getSelectedText() {
        Document d = getDocument();
        if (d == null) {
            return null;
        }
        int p0 = getSelectionStart();
        int p1 = getSelectionEnd();
        if (p0 == p1) {
            return null;
        }
        try {
            return d.getText(p0, p1 - p0);
        } catch (BadLocationException e) {
            return null;
        }
    }

    /**
     * Replaces the selection (or inserts at the caret when there is none) with {@code content}
     * and leaves the caret after the inserted text.
     */
    public void replaceSelection(String content) {
        Document d = getDocument();
        if (d == null) {
            return;
        }
        int p0 = Math.min(getCaret().getDot(), getCaret().getMark());
        int p1 = Math.max(getCaret().getDot(), getCaret().getMark());
        try {
            if (p0 != p1) {
                d.remove(p0, p1 - p0);
            }
            if (content != null && !content.isEmpty()) {
                d.insertString(p0, content, null);
            }
            getCaret().setDot(p0 + (content == null ? 0 : content.length()));
        } catch (BadLocationException e) {
            DefaultEditorKit.beep();
        }
    }

    // -------------------------------------------------------- clipboard

    /** Copies the selection to the system clipboard. */
    public void copy() {
        String selected = getSelectedText();
        if (selected == null) {
            return;
        }
        try {
            Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard != null) {
                clipboard.setContents(new StringSelection(selected), null);
            }
        } catch (Throwable ignored) {
            // A port without a clipboard service still edits text.
        }
    }

    /** Copies the selection to the clipboard and removes it. */
    public void cut() {
        if (!isEditable()) {
            return;
        }
        String selected = getSelectedText();
        if (selected == null) {
            return;
        }
        copy();
        try {
            Document d = getDocument();
            if (d != null) {
                d.remove(getSelectionStart(), getSelectionEnd() - getSelectionStart());
            }
        } catch (BadLocationException e) {
            DefaultEditorKit.beep();
        }
    }

    /** Inserts the clipboard's text at the caret. */
    public void paste() {
        if (!isEditable()) {
            return;
        }
        String text = clipboardText();
        if (text != null) {
            replaceSelection(text);
        }
    }

    /** The clipboard's string content, or null when there is none. */
    private String clipboardText() {
        try {
            Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard == null) {
                return null;
            }
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }
            Object data = clipboard.getData(DataFlavor.stringFlavor);
            return data instanceof String ? (String) data : null;
        } catch (Throwable e) {
            return null;
        }
    }

    // ----------------------------------------------------------- colors

    /** The colour of the selection background. */
    public Color getSelectionColor() {
        return selectionColor != null ? selectionColor : new Color(77, 77, 77);
    }

    public void setSelectionColor(Color c) {
        Color old = selectionColor;
        selectionColor = c;
        firePropertyChange("selectionColor", old, c);
        repaint();
    }

    /** The colour of selected text. */
    public Color getSelectedTextColor() {
        return selectedTextColor != null ? selectedTextColor : Color.WHITE;
    }

    public void setSelectedTextColor(Color c) {
        Color old = selectedTextColor;
        selectedTextColor = c;
        firePropertyChange("selectedTextColor", old, c);
        repaint();
    }

    /** The colour of the caret. */
    public Color getCaretColor() {
        if (caretColor != null) {
            return caretColor;
        }
        return getForeground() != null ? getForeground() : Color.WHITE;
    }

    public void setCaretColor(Color c) {
        Color old = caretColor;
        caretColor = c;
        firePropertyChange("caretColor", old, c);
        repaint();
    }

    /** The colour of the text when the component is disabled. */
    public Color getDisabledTextColor() {
        return disabledTextColor != null ? disabledTextColor : defaultDisabledTextColor();
    }

    private static Color defaultDisabledTextColor() {
        Color c = UIManager.getColor("TextField.inactiveForeground");
        if (c != null) {
            return c;
        }
        Color foreground = UIManager.getColor("TextField.foreground");
        if (foreground != null) {
            return new Color(foreground.getRed() / 2, foreground.getGreen() / 2, foreground.getBlue() / 2);
        }
        return new Color(120, 120, 120);
    }

    public void setDisabledTextColor(Color c) {
        Color old = disabledTextColor;
        disabledTextColor = c;
        firePropertyChange("disabledTextColor", old, c);
        repaint();
    }

    /** The margin between the border and the text. */
    public Insets getMargin() {
        return margin == null ? new Insets(0, 0, 0, 0) : margin;
    }

    public void setMargin(Insets m) {
        this.margin = m;
        lines = null;
        revalidate();
        repaint();
    }

    // ------------------------------------------------------- highlighter

    /** The highlighter that draws behind the text. */
    public Highlighter getHighlighter() {
        if (highlighter == null) {
            highlighter = new DefaultHighlighter();
            highlighter.install(this);
        }
        return highlighter;
    }

    /**
     * Replaces the highlighter.
     *
     * <p>Port deviation: passing {@code null} keeps the current (non-null) highlighter instead
     * of removing it — RuneLite's {@code JRichTextPane} calls {@code setHighlighter(null)} to
     * switch the desktop selection highlight off, and this port has no highlight to switch off,
     * so the call is harmless either way.
     */
    public void setHighlighter(Highlighter h) {
        if (h == null) {
            highlighter = null;
            return;
        }
        if (highlighter != null) {
            highlighter.deinstall(this);
        }
        highlighter = h;
        highlighter.install(this);
        repaint();
    }

    // ---------------------------------------------------------- layout

    /**
     * One laid-out line of text.
     *
     * <p>{@code x} holds the x offset (relative to the line's left edge) of every offset in
     * {@code [start, end]}, so a tab (which advances to the next tab stop) and the password
     * echo characters are accounted for once, in both painting and hit-testing.
     */
    protected static final class Line {
        /** First document offset of the line. */
        public int start;

        /** Offset just past the line's last character. */
        public int end;

        /** The x offset of each offset in {@code [start, end]}. */
        public int[] x;

        /** The line's display text (what is drawn). */
        public String text;

        /** The width of the line. */
        public int width;
    }

    /** The height of one text line. */
    protected int getLineHeight() {
        return getFontMetrics(getFont()).getHeight();
    }

    /** The number of lines that fit into the component's height. */
    int visibleLineCount() {
        int height = getHeight() - getInsets().top - getInsets().bottom - getMargin().top - getMargin().bottom;
        return Math.max(1, height / Math.max(1, getLineHeight()));
    }

    /** The width available to the text. */
    protected int getAvailableTextWidth() {
        Insets insets = getInsets();
        Insets m = getMargin();
        int width = getWidth() - insets.left - insets.right - m.left - m.right;
        return Math.max(width, 1);
    }

    /**
     * The y coordinate the first line's text starts at.
     *
     * <p>A single-line component centres its text vertically when it is taller than one line
     * (the JDK's field view does the same); a multi-line component starts at the top.
     */
    protected int verticalOrigin() {
        Insets insets = getInsets();
        Insets m = getMargin();
        int top = m.top;
        if (isMultiLine() || getHeight() <= 0) {
            return top;
        }
        int available = getHeight() - insets.top - insets.bottom - m.top - m.bottom;
        int content = layoutLines().size() * getLineHeight();
        return top + Math.max(0, (available - content) / 2);
    }

    /**
     * The x coordinate the line's text starts at.
     *
     * <p>{@link javax.swing.JTextField} shifts this for its horizontal alignment, which keeps
     * painting, hit-testing and the caret in agreement.
     */
    protected int lineOriginX(Line line) {
        return getMargin().left - scrollX;
    }

    /** The x of {@code screenX} inside the line's own coordinate space. */
    private int xWithinLine(Line line, int screenX) {
        return screenX - lineOriginX(line);
    }

    /**
     * Lays the document out into lines.
     *
     * <p>The base implementation is a single line (a field shows its text on one line);
     * {@link javax.swing.JTextArea} overrides this to split on newlines and, when line wrap is
     * on, to wrap to the available width.
     */
    protected java.util.List<Line> computeLines() {
        java.util.List<Line> result = new java.util.ArrayList<>(1);
        int length = getDocument() == null ? 0 : getDocument().getLength();
        result.add(buildLine(0, length, false));
        return result;
    }

    /**
     * Builds one line for {@code [start, end)}.
     *
     * @param expandTabs true to advance a tab to the next tab stop, false to treat it as one
     *                   character (a field shows tabs as a single advance)
     */
    protected Line buildLine(int start, int end, boolean expandTabs) {
        Line line = new Line();
        line.start = start;
        line.end = end;
        FontMetrics fm = getFontMetrics(getFont());
        String display = getDisplayText(start, end);
        line.text = display;
        line.x = new int[Math.max(display.length(), 0) + 1];
        int pen = 0;
        int tabSize = tabSize();
        for (int i = 0; i < display.length(); i++) {
            line.x[i] = pen;
            char c = display.charAt(i);
            if (c == '\t' && expandTabs) {
                int advance = fm.stringWidth(" ");
                int next = ((pen / (advance * tabSize)) + 1) * advance * tabSize;
                pen = next;
            } else {
                pen += fm.stringWidth(String.valueOf(c));
            }
        }
        line.x[display.length()] = pen;
        line.width = pen;
        return line;
    }

    /** The characters of a range as they are displayed (a password field echoes them). */
    protected String getDisplayText(int start, int end) {
        Document d = getDocument();
        if (d == null || end <= start) {
            return "";
        }
        try {
            return d.getText(start, end - start);
        } catch (BadLocationException e) {
            return "";
        }
    }

    /** The tab size the layout expands a tab to (a field treats a tab as one character). */
    protected int tabSize() {
        return 8;
    }

    private java.util.List<Line> layoutLines() {
        int length = getDocument() == null ? 0 : getDocument().getLength();
        int width = getAvailableTextWidth();
        if (lines == null || linesDocLength != length || linesWidth != width) {
            lines = computeLines();
            linesDocLength = length;
            linesWidth = width;
        }
        return lines;
    }

    /** Drops the cached layout (after an edit, a resize or a font change). */
    protected void invalidateLines() {
        lines = null;
        linesDocLength = -1;
    }

    /** The line index holding {@code pos} (clamped into the layout). */
    private int lineIndexFor(int pos) {
        java.util.List<Line> ls = layoutLines();
        for (int i = 0; i < ls.size(); i++) {
            Line line = ls.get(i);
            if (pos < line.end || i == ls.size() - 1) {
                return i;
            }
        }
        return Math.max(0, ls.size() - 1);
    }

    /** The rectangle the character at {@code pos} occupies (the caret position's rectangle). */
    public Rectangle modelToView(int pos) {
        java.util.List<Line> ls = layoutLines();
        if (ls.isEmpty()) {
            return new Rectangle(getMargin().left, getMargin().top, 1, getLineHeight());
        }
        int index = lineIndexFor(pos);
        Line line = ls.get(index);
        int offsetInLine = Math.max(0, Math.min(pos - line.start, line.text.length()));
        int x = lineOriginX(line) + line.x[offsetInLine];
        int y = verticalOrigin() + index * getLineHeight() - scrollY;
        return new Rectangle(x, y, 1, getLineHeight());
    }

    /** The document offset closest to the point {@code pt}. */
    public int viewToModel(Point pt) {
        java.util.List<Line> ls = layoutLines();
        if (ls.isEmpty()) {
            return 0;
        }
        int lineHeight = Math.max(1, getLineHeight());
        int index = (pt.y - verticalOrigin() + scrollY) / lineHeight;
        if (index < 0) {
            index = 0;
        } else if (index >= ls.size()) {
            index = ls.size() - 1;
        }
        Line line = ls.get(index);
        int x = xWithinLine(line, pt.x);
        int offsetInLine = line.text.length();
        for (int i = 0; i < line.text.length(); i++) {
            // Half a character decides whether the caret goes before or after it.
            if (x < (line.x[i] + line.x[i + 1]) / 2) {
                offsetInLine = i;
                break;
            }
        }
        return line.start + offsetInLine;
    }

    // -------------------------------------------------------- geometry helpers

    /** The start offset of the logical line (the line between newlines) containing {@code pos}. */
    int lineStart(int pos) {
        String text = getText();
        if (text == null || text.isEmpty()) {
            return 0;
        }
        pos = Math.max(0, Math.min(pos, text.length()));
        int index = text.lastIndexOf('\n', Math.max(pos - 1, 0));
        return index < 0 ? 0 : index + 1;
    }

    /** The offset just before the logical line's newline (or the document end). */
    int lineEnd(int pos) {
        String text = getText();
        if (text == null || text.isEmpty()) {
            return 0;
        }
        pos = Math.max(0, Math.min(pos, text.length()));
        int index = text.indexOf('\n', pos);
        return index < 0 ? text.length() : index;
    }

    /** The start offset of the visual (wrapped) line containing {@code pos}. */
    int visualLineStart(int pos) {
        java.util.List<Line> ls = layoutLines();
        Line line = ls.get(lineIndexFor(pos));
        return line.start;
    }

    /** The end offset of the visual (wrapped) line containing {@code pos}. */
    int visualLineEnd(int pos) {
        java.util.List<Line> ls = layoutLines();
        Line line = ls.get(lineIndexFor(pos));
        return line.end;
    }

    /** The offset one line above {@code pos}, keeping the column where it can. */
    int offsetAbove(int pos) {
        return offsetAbove(pos, 1);
    }

    /** The offset {@code count} lines above {@code pos}, keeping the column where it can. */
    int offsetAbove(int pos, int count) {
        java.util.List<Line> ls = layoutLines();
        int index = lineIndexFor(pos);
        if (index == 0) {
            return 0;
        }
        Rectangle r = modelToView(pos);
        int column = r.x;
        Line line = ls.get(Math.max(0, index - count));
        return line.start + offsetForColumn(line, xWithinLine(line, column));
    }

    /** The offset one line below {@code pos}, keeping the column where it can. */
    int offsetBelow(int pos) {
        return offsetBelow(pos, 1);
    }

    /** The offset {@code count} lines below {@code pos}, keeping the column where it can. */
    int offsetBelow(int pos, int count) {
        java.util.List<Line> ls = layoutLines();
        int index = lineIndexFor(pos);
        if (index >= ls.size() - 1) {
            return getDocument() == null ? 0 : getDocument().getLength();
        }
        Rectangle r = modelToView(pos);
        int column = r.x;
        Line line = ls.get(Math.min(ls.size() - 1, index + count));
        return line.start + offsetForColumn(line, xWithinLine(line, column));
    }

    private static int offsetForColumn(Line line, int column) {
        for (int i = 0; i < line.text.length(); i++) {
            if (column < (line.x[i] + line.x[i + 1]) / 2) {
                return i;
            }
        }
        return line.text.length();
    }

    /** The offset the caret moves to when it goes to the start of the word at {@code pos}. */
    int wordStart(int pos) {
        String text = getText();
        if (text == null || text.isEmpty()) {
            return 0;
        }
        pos = Math.max(0, Math.min(pos, text.length()));
        int i = pos;
        while (i > 0 && !isWordChar(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && isWordChar(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /** The offset the caret moves to when it goes past the word at {@code pos}. */
    int wordEnd(int pos) {
        String text = getText();
        if (text == null || text.isEmpty()) {
            return 0;
        }
        pos = Math.max(0, Math.min(pos, text.length()));
        int i = pos;
        if (i < text.length() && isWordChar(text.charAt(i))) {
            while (i < text.length() && isWordChar(text.charAt(i))) {
                i++;
            }
        } else {
            while (i < text.length() && !isWordChar(text.charAt(i))) {
                i++;
            }
        }
        return i;
    }

    /** The start of the word before {@code pos}. */
    int previousWord(int pos) {
        return wordStart(pos);
    }

    /** The start of the word after {@code pos}. */
    int nextWord(int pos) {
        String text = getText();
        if (text == null) {
            return 0;
        }
        pos = Math.max(0, Math.min(pos, text.length()));
        int i = pos;
        while (i < text.length() && isWordChar(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && !isWordChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Moves the caret, extending the selection when {@code select} is true. */
    void moveCaretTo(int pos, boolean select) {
        Document d = getDocument();
        if (d == null) {
            return;
        }
        int length = d.getLength();
        if (pos < 0) {
            pos = 0;
        } else if (pos > length) {
            pos = length;
        }
        if (select) {
            getCaret().moveDot(pos);
        } else {
            getCaret().setDot(pos);
        }
        scrollToCaret();
    }

    // -------------------------------------------------------- paint

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        java.util.List<Line> ls = layoutLines();
        int lineHeight = getLineHeight();
        FontMetrics fm = getFontMetrics(getFont());
        Color textColor = isEnabled() ? getForeground() : getDisabledTextColor();
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        boolean hasSelection = selEnd > selStart && getCaret().isSelectionVisible();

        int originY = verticalOrigin();
        for (int i = 0; i < ls.size(); i++) {
            Line line = ls.get(i);
            int baseline = originY + i * lineHeight + fm.getAscent() - scrollY;
            int left = lineOriginX(line);
            if (baseline < -lineHeight || baseline > getHeight() + lineHeight) {
                continue; // outside the component: nothing to draw
            }

            if (hasSelection) {
                int from = Math.max(selStart, line.start);
                int to = Math.min(selEnd, line.end);
                if (to > from) {
                    int x0 = left + xAt(line, from);
                    int x1 = left + xAt(line, to);
                    Color old = g.getColor();
                    g.setColor(getSelectionColor());
                    g.fillRect(x0, baseline - fm.getAscent(), Math.max(x1 - x0, 1), lineHeight);
                    g.setColor(old);
                }
            }

            Color old = g.getColor();
            g.setFont(getFont());
            if (hasSelection && selEnd > line.start && selStart < line.end) {
                // Draw the line in up to three runs so the selected text can use its own
                // colour (the JDK paints the selection's text with the selection foreground).
                int a = Math.max(selStart, line.start) - line.start;
                int b = Math.min(selEnd, line.end) - line.start;
                g.setColor(textColor);
                drawRange(g, line, 0, a, left, baseline);
                g.setColor(getSelectedTextColor());
                drawRange(g, line, a, b, left, baseline);
                g.setColor(textColor);
                drawRange(g, line, b, line.text.length(), left, baseline);
            } else {
                g.setColor(textColor);
                drawRange(g, line, 0, line.text.length(), left, baseline);
            }
            g.setColor(old);
        }

        // The caret paints itself (a one-pixel line) while it is visible.
        if (getCaret() != null) {
            getCaret().paint(g);
        }
    }

    /**
     * Draws the line's characters in {@code [from, to)} (offsets within the line), splitting
     * the run around tab characters so the pen jumps to the tab stops the layout computed.
     */
    private void drawRange(Graphics g, Line line, int from, int to, int left, int baseline) {
        if (to <= from || from < 0) {
            return;
        }
        String text = line.text;
        if (to > text.length()) {
            to = text.length();
        }
        if (text.indexOf('\t') < 0) {
            g.drawString(text.substring(from, to), left + line.x[from], baseline);
            return;
        }
        int runStart = from;
        for (int i = from; i <= to; i++) {
            if (i == to || text.charAt(i) == '\t') {
                if (i > runStart) {
                    g.drawString(text.substring(runStart, i), left + line.x[runStart], baseline);
                }
                runStart = i + 1;
            }
        }
    }

    private static int xAt(Line line, int pos) {
        int offsetInLine = pos - line.start;
        if (offsetInLine < 0) {
            offsetInLine = 0;
        } else if (offsetInLine > line.text.length()) {
            offsetInLine = line.text.length();
        }
        return line.x[offsetInLine];
    }

    /** Keeps the caret inside the visible area, scrolling the text when it is not. */
    private void scrollToCaret() {
        if (getDocument() == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        java.util.List<Line> ls = layoutLines();
        if (ls.isEmpty()) {
            return;
        }
        int lineHeight = Math.max(1, getLineHeight());
        Insets insets = getInsets();
        Insets m = getMargin();
        int caretIndex = lineIndexFor(getCaret().getDot());
        Line line = ls.get(caretIndex);
        int caretX = line.x[Math.max(0, Math.min(getCaret().getDot() - line.start, line.text.length()))];

        int visibleWidth = Math.max(1, getWidth() - insets.left - insets.right - m.left - m.right);
        if (caretX - scrollX > visibleWidth - 1) {
            scrollX = caretX - visibleWidth + 1;
        }
        if (caretX - scrollX < 0) {
            scrollX = caretX;
        }
        if (scrollX < 0) {
            scrollX = 0;
        }

        int visibleHeight = Math.max(1, getHeight() - insets.top - insets.bottom - m.top - m.bottom);
        int caretY = verticalOrigin() + caretIndex * lineHeight - getMargin().top;
        if (caretY - scrollY > visibleHeight - lineHeight) {
            scrollY = caretY - visibleHeight + lineHeight;
        }
        if (caretY - scrollY < 0) {
            scrollY = caretY;
        }
        if (scrollY < 0) {
            scrollY = 0;
        }
    }

    @Override
    public void setFont(java.awt.Font f) {
        super.setFont(f);
        invalidateLines();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        invalidateLines();
    }

    // -------------------------------------------------------- text events

    private DocumentListener documentListener;

    /** Subscribes to a document so edits invalidate the layout and repaint the component. */
    private void installDocumentListener(Document d) {
        if (d == null) {
            return;
        }
        if (documentListener == null) {
            documentListener = new TextDocumentListener();
        }
        d.addDocumentListener(documentListener);
    }

    /** Repaints (and re-lays out) when the document changes. */
    private final class TextDocumentListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            documentChanged();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            documentChanged();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            documentChanged();
        }

        private void documentChanged() {
            invalidateLines();
            invalidate();
            revalidate();
            scrollToCaret();
            repaint();
        }
    }

    // ------------------------------------------------------ key handling

    /**
     * Installs the JDK's default text key bindings plus this port's undo/redo bindings.
     *
     * <p>Ctrl+Z/Ctrl+Y are bound through the {@code InputMap} to actions that drive the undo
     * manager the component registers on its document (see
     * {@link #setDocument(Document)}) — the document's own edit stack, so a plugin that
     * installs its own manager still wins by rebinding the same stroke.
     */
    protected void installDefaultKeyBindings() {
        DefaultEditorKit.installDefaultBindings(this, isMultiLine());
        InputMap im = getInputMap();
        ActionMap am = getActionMap();
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        int shift = InputEvent.SHIFT_DOWN_MASK;
        undoAction = new UndoAction(true);
        redoAction = new UndoAction(false);
        am.put("undo", undoAction);
        am.put("redo", redoAction);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrl), "undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ctrl), "redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrl | shift), "redo");
    }

    /** True for a component that may hold more than one line of text. */
    protected boolean isMultiLine() {
        return false;
    }

    /** The actions this component exposes to its input map. */
    public Action[] getActions() {
        return new DefaultEditorKit().getActions();
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            if (c == KeyEvent.CHAR_UNDEFINED) {
                return;
            }
            KeyStroke ks = KeyStroke.getKeyStrokeForEvent(e);
            if (processKeyBinding(ks, e, WHEN_FOCUSED, true)) {
                e.consume();
                return;
            }
            // A printable character types into the document (the JDK's default-typed action).
            // Control characters (BACK_SPACE, ESCAPE, …) arrive here too: the port's on-screen
            // keyboard repeats the code as the typed char, and their KeyPress bindings have
            // already handled them by the time the typed event arrives.
            if (isEditable() && isEnabled() && c >= 0x20 && c != 0x7F
                    && !e.isControlDown() && !e.isAltDown()) {
                replaceSelection(String.valueOf(c));
                e.consume();
                return;
            }
            super.processKeyEvent(e);
            return;
        }
        // KEY_PRESSED/KEY_RELEASED: JComponent runs the InputMap bindings first, then the
        // component's key listeners (which is what plugins register on).
        super.processKeyEvent(e);
    }

    /** The action behind Ctrl+Z and Ctrl+Y. */
    private final class UndoAction extends AbstractAction {
        private final boolean undo;

        UndoAction(boolean undo) {
            super(undo ? "undo" : "redo");
            this.undo = undo;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            UndoManager manager = undoManager;
            if (manager == null) {
                return;
            }
            if (undo) {
                if (manager.canUndo()) {
                    manager.undo();
                }
            } else if (manager.canRedo()) {
                manager.redo();
            }
        }
    }

    // ------------------------------------------------------ mouse handling

    /** Focuses the field and moves the caret to the tapped position (the JDK's caret does this). */
    private final class MouseHandler implements java.awt.event.MouseListener {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() >= 2) {
                selectAll();
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (!isEnabled()) {
                return;
            }
            requestFocus();
            if (e.getButton() == MouseEvent.BUTTON1) {
                moveCaretTo(viewToModel(e.getPoint()), e.isShiftDown());
                dragging = true;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragging = false;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }
    }

    private boolean dragging;

    /** Extends the selection while the pointer is dragged over the text. */
    private final class MouseMotionHandler implements java.awt.event.MouseMotionListener {
        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragging) {
                moveCaretTo(viewToModel(e.getPoint()), true);
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
        }
    }

    // -------------------------------------------------------- Scrollable

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return getLineHeight();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(1, visibleRect.height - getLineHeight());
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // ------------------------------------------------------ size

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Insets insets = getInsets();
        Insets m = getMargin();
        int width = 0;
        int height = 0;
        java.util.List<Line> ls = layoutLines();
        for (int i = 0; i < ls.size(); i++) {
            width = Math.max(width, ls.get(i).width);
            height += getLineHeight();
        }
        return new Dimension(width + insets.left + insets.right + m.left + m.right,
                height + insets.top + insets.bottom + m.top + m.bottom);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    /** The caret event the component fires; it is mutable so one instance can be reused. */
    private static final class CaretPositionEvent extends CaretEvent {
        private static final long serialVersionUID = 1L;

        CaretPositionEvent(Object source) {
            super(source);
        }

        void setPositions(int dot, int mark) {
            this.dot = dot;
            this.mark = mark;
        }
    }
}

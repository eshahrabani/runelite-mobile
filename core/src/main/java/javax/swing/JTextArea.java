package javax.swing;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;

/**
 * Hand-written {@code javax.swing.JTextArea}: the multi-line text widget the config panels and
 * Notes use.
 *
 * <p>Why hand-written: Notes keeps its whole content in one of these, the config UI shows long
 * descriptions in them, and they are the widget the line-wrap, row/column and line-offset API
 * exists for. The generated stub could not even report its line count.
 *
 * <p>What is real here: the row/column-based preferred size, line wrap (character or word
 * based) with the {@link Scrollable} answers that go with it ({@code getLineWrap()} tracks the
 * viewport width), the JDK's line queries built on the document's element tree, {@code append}/
 * {@code insert}/{@code replaceRange}, the tab size (stored on the document as the JDK does)
 * and the multiline key bindings (Enter inserts a line break, up/down move by line).
 *
 * <p>Deviations from the JDK: the wrapped lines are computed here (from the font metrics) rather
 * than by a {@code View} tree, so a line's visual extent is a row of this component's own
 * layout; and the text area scrolls itself vertically when it is smaller than its content,
 * which is what a border-hugging text area in a fixed-size panel needs.
 */
public class JTextArea extends JTextComponent {

    private int rows;
    private int columns;
    private int columnWidth;
    private int rowHeight;
    private boolean wrap;
    private boolean word;

    public JTextArea() {
        this(null, null, 0, 0);
    }

    public JTextArea(String text) {
        this(null, text, 0, 0);
    }

    public JTextArea(int rows, int columns) {
        this(null, null, rows, columns);
    }

    public JTextArea(String text, int rows, int columns) {
        this(null, text, rows, columns);
    }

    public JTextArea(Document doc) {
        this(doc, null, 0, 0);
    }

    public JTextArea(Document doc, String text, int rows, int columns) {
        super();
        if (rows < 0) {
            throw new IllegalArgumentException("rows: " + rows);
        }
        if (columns < 0) {
            throw new IllegalArgumentException("columns: " + columns);
        }
        this.rows = rows;
        this.columns = columns;
        setDocument(doc == null ? createDefaultModel() : doc);
        if (text != null) {
            setText(text);
            select(0, 0);
        }
    }

    /** The UI delegate's class id (kept for API shape). */
    public String getUIClassID() {
        return "TextAreaUI";
    }

    @Override
    protected Document createDefaultModel() {
        return new PlainDocument();
    }

    @Override
    protected boolean isMultiLine() {
        return true;
    }

    // ------------------------------------------------------------ wrapping

    /** True when long lines are wrapped instead of scrolled. */
    public boolean getLineWrap() {
        return wrap;
    }

    public void setLineWrap(boolean wrap) {
        boolean old = this.wrap;
        this.wrap = wrap;
        firePropertyChange("lineWrap", old, wrap);
        invalidateLines();
        revalidate();
        repaint();
    }

    /** True when wrapping breaks at word boundaries. */
    public boolean getWrapStyleWord() {
        return word;
    }

    public void setWrapStyleWord(boolean word) {
        boolean old = this.word;
        this.word = word;
        firePropertyChange("wrapStyleWord", old, word);
        invalidateLines();
        repaint();
    }

    /** The number of characters a tab is expanded to. */
    public int getTabSize() {
        int size = 8;
        Document doc = getDocument();
        if (doc != null) {
            Object value = doc.getProperty(PlainDocument.tabSizeAttribute);
            if (value instanceof Number) {
                size = ((Number) value).intValue();
            }
        }
        return size;
    }

    /** Stores the tab size on the document (as the JDK does) and re-lays out the text. */
    public void setTabSize(int size) {
        Document doc = getDocument();
        if (doc != null) {
            int old = getTabSize();
            doc.putProperty(PlainDocument.tabSizeAttribute, Integer.valueOf(size));
            firePropertyChange("tabSize", old, size);
            invalidateLines();
            repaint();
        }
    }

    /** The tab size the layout uses (the document property set by {@link #setTabSize}). */
    @Override
    protected int tabSize() {
        return getTabSize();
    }

    @Override
    protected List<Line> computeLines() {
        List<Line> result = new ArrayList<>();
        String text = getText();
        if (text == null) {
            text = "";
        }
        int length = text.length();
        // Before the first layout pass (width 0) wrapping is meaningless: lay the text out
        // unwrapped so the preferred size stays sane, then re-wrap once there is a width.
        if (!wrap || getWidth() <= 0) {
            int start = 0;
            for (int i = 0; i < length; i++) {
                if (text.charAt(i) == '\n') {
                    result.add(buildLine(start, i, true));
                    start = i + 1;
                }
            }
            result.add(buildLine(start, length, true));
            return result;
        }
        int available = getAvailableTextWidth();
        int p = 0;
        while (true) {
            int newline = text.indexOf('\n', p);
            int logicalEnd = newline < 0 ? length : newline;
            int q = p;
            while (q < logicalEnd) {
                int brk = wrapOffset(text, q, logicalEnd, available);
                if (brk <= q) {
                    brk = q + 1; // always make progress
                }
                result.add(buildLine(q, Math.min(brk, logicalEnd), true));
                q = brk;
            }
            if (q == p) {
                // An empty logical line still occupies a row.
                result.add(buildLine(p, p, true));
            }
            if (newline < 0) {
                break;
            }
            p = newline + 1;
            if (p == length) {
                result.add(buildLine(p, p, true));
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(buildLine(0, 0, true));
        }
        return result;
    }

    /** The offset the line starting at {@code start} should break at to fit {@code available}. */
    private int wrapOffset(String text, int start, int end, int available) {
        FontMetrics fm = getFontMetrics(getFont());
        int tabSize = tabSize();
        int space = Math.max(1, fm.stringWidth(" "));
        int pen = 0;
        int lastSpace = -1;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            int advance;
            if (c == '\t') {
                advance = ((pen / (space * tabSize)) + 1) * space * tabSize - pen;
            } else {
                advance = fm.stringWidth(String.valueOf(c));
            }
            if (pen + advance > available && i > start) {
                return word && lastSpace > start ? lastSpace : i;
            }
            pen += advance;
            if (c == ' ') {
                lastSpace = i + 1;
            }
        }
        return end;
    }

    // ------------------------------------------------------- line queries

    /** The line (logical, newline separated) that contains {@code offset}. */
    public int getLineOfOffset(int offset) throws BadLocationException {
        Document doc = getDocument();
        if (doc == null) {
            throw new BadLocationException("No document", offset);
        }
        if (offset < 0) {
            throw new BadLocationException("Can't translate offset to line", -1);
        }
        if (offset > doc.getLength()) {
            throw new BadLocationException("Can't translate offset to line", doc.getLength() + 1);
        }
        return doc.getDefaultRootElement().getElementIndex(offset);
    }

    /** The number of logical lines. */
    public int getLineCount() {
        Document doc = getDocument();
        return doc == null ? 0 : doc.getDefaultRootElement().getElementCount();
    }

    /** The offset the line starts at. */
    public int getLineStartOffset(int line) throws BadLocationException {
        return lineElement(line).getStartOffset();
    }

    /**
     * The offset the line ends at (including its newline; the port's documents have no implicit
     * trailing break, so the last line ends at the document's end).
     */
    public int getLineEndOffset(int line) throws BadLocationException {
        return lineElement(line).getEndOffset();
    }

    private Element lineElement(int line) throws BadLocationException {
        int count = getLineCount();
        if (line < 0) {
            throw new BadLocationException("Negative line", -1);
        }
        if (line >= count) {
            throw new BadLocationException("No such line", getDocument().getLength() + 1);
        }
        return getDocument().getDefaultRootElement().getElement(line);
    }

    // ------------------------------------------------------- editing

    /** Inserts {@code str} at {@code pos} (the {@code java.awt.TextArea} API). */
    public void insert(String str, int pos) {
        Document doc = getDocument();
        if (doc == null) {
            return;
        }
        try {
            doc.insertString(pos, str, null);
        } catch (BadLocationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /** Appends {@code str} at the end of the document. */
    public void append(String str) {
        Document doc = getDocument();
        if (doc == null) {
            return;
        }
        try {
            doc.insertString(doc.getLength(), str, null);
        } catch (BadLocationException e) {
            // The document rejected an append at its own end: nothing sensible to do.
        }
    }

    /** Replaces the range with {@code str} as one undoable edit. */
    public void replaceRange(String str, int start, int end) {
        if (end < start) {
            throw new IllegalArgumentException("end before start");
        }
        Document doc = getDocument();
        if (doc == null) {
            return;
        }
        try {
            if (doc instanceof javax.swing.text.AbstractDocument) {
                ((javax.swing.text.AbstractDocument) doc).replace(start, end - start, str, null);
            } else {
                doc.remove(start, end - start);
                doc.insertString(start, str, null);
            }
        } catch (BadLocationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    // -------------------------------------------------------------- size

    /** The preferred height in rows. */
    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        int old = this.rows;
        if (rows < 0) {
            throw new IllegalArgumentException("rows less than zero.");
        }
        if (rows != old) {
            this.rows = rows;
            invalidate();
            revalidate();
            repaint();
        }
    }

    /** The preferred width in columns. */
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

    /** The height of one text row. */
    protected int getRowHeight() {
        if (rowHeight == 0) {
            FontMetrics metrics = getFontMetrics(getFont());
            rowHeight = metrics.getHeight();
        }
        return rowHeight;
    }

    /** The width of one 'm' in the text area's font. */
    protected int getColumnWidth() {
        if (columnWidth == 0) {
            FontMetrics metrics = getFontMetrics(getFont());
            columnWidth = metrics.stringWidth("m");
        }
        return columnWidth;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        Insets insets = getInsets();
        if (columns != 0) {
            d.width = Math.max(d.width, columns * getColumnWidth() + insets.left + insets.right);
        }
        if (rows != 0) {
            d.height = Math.max(d.height, rows * getRowHeight() + insets.top + insets.bottom);
        }
        return d;
    }

    @Override
    public void setFont(Font f) {
        super.setFont(f);
        rowHeight = 0;
        columnWidth = 0;
    }

    // ---------------------------------------------------------- Scrollable

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return wrap || super.getScrollableTracksViewportWidth();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        Insets insets = getInsets();
        if (columns != 0) {
            size.width = columns * getColumnWidth() + insets.left + insets.right;
        }
        if (rows != 0) {
            size.height = rows * getRowHeight() + insets.top + insets.bottom;
        }
        return size;
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (orientation == SwingConstants.VERTICAL) {
            return getRowHeight();
        }
        return getColumnWidth();
    }
}

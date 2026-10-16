package javax.swing.text;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.DocumentEvent;

/**
 * Hand-written {@code javax.swing.text.PlainDocument}: the document a plain
 * {@link javax.swing.JTextField}/{@link javax.swing.JTextArea} edits, with the JDK's
 * one-line-element-per-newline element tree.
 *
 * <p>Why hand-written: the port's text fields have to be editable and reportable — Notes
 * subscribes document listeners, the color picker's filter vetoes keystrokes, and
 * {@code JTextArea}'s line-aware API ({@code getLineCount}, {@code getLineStartOffset},
 * {@code append}) is defined in terms of this document's root element. The generated stub
 * set did not contain this class at all.
 *
 * <p>Deviations from the JDK: the line elements are rebuilt from the content whenever the
 * text changed instead of being patched incrementally, and each document event reports one
 * {@link AbstractDocument.ElementEdit} — the lines that appeared (insert) or disappeared
 * (remove) — rather than a single merged edit carrying both sides. Element identity is
 * therefore not stable across edits, which nothing on this port observes (only the offsets
 * and the counts are).
 */
public class PlainDocument extends AbstractDocument {
    private static final long serialVersionUID = 1L;

    /** The property that limits the number of lines (0 or absent means unlimited). */
    public static final String lineLimitAttribute = "lineLimit";

    /** The property that holds the tab size. */
    public static final String tabSizeAttribute = "tabSize";

    private final RootElement root = new RootElement();
    private int cachedLength = -1;

    public PlainDocument() {
        this(new StringContent());
    }

    public PlainDocument(AbstractDocument.Content c) {
        super(c);
    }

    public PlainDocument(AbstractDocument.Content c, AbstractDocument.AttributeContext a) {
        super(c, a);
    }

    @Override
    public Element getDefaultRootElement() {
        return root;
    }

    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        if (str == null) {
            return;
        }
        // A single-line field sets "filterNewlines" so that a paste cannot turn it into a
        // multi-line one (the JDK filters here for the same reason).
        Object filterNewlines = getProperty("filterNewlines");
        if (Boolean.TRUE.equals(filterNewlines) && str.indexOf('\n') >= 0) {
            str = str.replace('\n', ' ');
        }
        Object limitProperty = getProperty(lineLimitAttribute);
        if (limitProperty instanceof Number) {
            int limit = ((Number) limitProperty).intValue();
            if (limit > 0) {
                str = applyLineLimit(offs, str, limit);
                if (str == null) {
                    return;
                }
            }
        }
        super.insertString(offs, str, a);
    }

    /** Truncates {@code str} so at most {@code limit} lines exist afterwards. */
    private String applyLineLimit(int offs, String str, int limit) {
        int line = root.getElementIndex(offs);
        int allowed = limit - line - 1;
        if (allowed < 0) {
            return null;
        }
        int newlines = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n' && ++newlines > allowed) {
                return null;
            }
        }
        return str;
    }

    @Override
    protected void insertUpdate(DefaultDocumentEvent chng, AttributeSet attr) {
        int offset = chng.getOffset();
        int length = chng.getLength();
        int firstLine = root.getElementIndex(offset);
        invalidateLines();
        // The lines the insertion produced, from the line containing the insertion point to
        // the line containing the end of the inserted text.
        int lastLine = root.getElementIndex(offset + length);
        Element[] added = root.copyChildren(firstLine, lastLine - firstLine + 1);
        chng.addChange(new ElementEdit(root, firstLine, null, added));
    }

    @Override
    protected void removeUpdate(DefaultDocumentEvent chng) {
        // Called before the text goes away (JDK ordering), so the removed lines can still be
        // read out of the content.
        int offset = chng.getOffset();
        int last = offset + chng.getLength();
        int firstLine = root.getElementIndex(offset);
        int lastLine = root.getElementIndex(last);
        Element[] removed = root.copyChildren(firstLine, lastLine - firstLine + 1);
        invalidateLines();
        chng.addChange(new ElementEdit(root, firstLine, removed, null));
    }

    /** The line element containing {@code pos} (clamped into the document). */
    public Element getParagraphElement(int pos) {
        if (pos < 0) {
            return null;
        }
        return root.getElement(root.getElementIndex(pos));
    }

    private void invalidateLines() {
        cachedLength = -1;
    }

    /**
     * The document's line partition: one {@link LineElement} per line, each ending at its
     * newline (the last one at the end of the document).
     */
    protected class RootElement extends AbstractDocument.BranchElement {
        private final List<Element> lines = new ArrayList<>();

        RootElement() {
            super(null, null);
        }

        @Override
        public Document getDocument() {
            return PlainDocument.this;
        }

        @Override
        public int getStartOffset() {
            return 0;
        }

        @Override
        public int getEndOffset() {
            ensureLines();
            return getLength();
        }

        @Override
        public int getElementCount() {
            ensureLines();
            return lines.size();
        }

        @Override
        public Element getElement(int index) {
            ensureLines();
            return index >= 0 && index < lines.size() ? lines.get(index) : null;
        }

        @Override
        public int getElementIndex(int offset) {
            ensureLines();
            int low = 0;
            int high = lines.size() - 1;
            if (high < 0) {
                return -1;
            }
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int start = lines.get(mid).getStartOffset();
                int end = lines.get(mid).getEndOffset();
                if (offset < start) {
                    high = mid - 1;
                } else if (offset >= end) {
                    low = mid + 1;
                } else {
                    return mid;
                }
            }
            return Math.max(0, Math.min(lines.size() - 1, low));
        }

        Element[] copyChildren(int index, int count) {
            ensureLines();
            if (count <= 0 || index < 0 || index >= lines.size()) {
                return new Element[0];
            }
            int n = Math.min(count, lines.size() - index);
            return lines.subList(index, index + n).toArray(new Element[0]);
        }

        private void ensureLines() {
            if (cachedLength == getLength()) {
                return;
            }
            lines.clear();
            int length = getLength();
            String text;
            try {
                text = getText(0, length);
            } catch (BadLocationException e) {
                text = "";
                length = 0;
            }
            int start = 0;
            for (int i = 0; i < length; i++) {
                if (text.charAt(i) == '\n') {
                    lines.add(new LineElement(this, start, i + 1));
                    start = i + 1;
                }
            }
            lines.add(new LineElement(this, start, length));
            cachedLength = length;
        }
    }

    /** One line of a {@link PlainDocument}: a leaf whose range includes its newline. */
    protected class LineElement extends AbstractDocument.LeafElement {

        LineElement(Element parent, int start, int end) {
            super(parent, null, start, end);
        }

        @Override
        public Document getDocument() {
            return PlainDocument.this;
        }

        @Override
        public String getName() {
            return ParagraphElementName;
        }
    }
}

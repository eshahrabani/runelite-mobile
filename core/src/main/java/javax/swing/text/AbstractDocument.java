package javax.swing.text;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Hashtable;
import java.util.List;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;

/**
 * Hand-written {@code javax.swing.text.AbstractDocument}: the real character buffer, element
 * tree, filter chain and event source behind every {@link Document} on this port.
 *
 * <p>Why hand-written: the port's plugin panels edit documents for real. RuneLite's color
 * picker installs a {@link DocumentFilter} that throws {@link BadLocationException} to reject
 * a bad hex value, the profile renamer installs one that rewrites illegal filename
 * characters, {@code IconTextField}/{@code PluginListPanel} subscribe with a
 * {@link DocumentListener} to react while the user types, and Notes drives an
 * {@link javax.swing.undo.UndoManager} from the undoable-edit events this class fires. A
 * generated data-only stub stored no text and fired nothing, so none of those behaviours
 * could exist.
 *
 * <p>What it implements: the content buffer ({@link StringContent}), insert/remove/replace
 * with the filter honoured through {@link DocumentFilter.FilterBypass}, insert/remove/change
 * {@link DocumentEvent}s (with {@link ElementChange} for the affected line elements),
 * undoable-edit events whose edit undoes the change, document properties, {@link Position}s
 * that track edits, and the element tree types ({@link AbstractElement},
 * {@link BranchElement}, {@link LeafElement}).
 *
 * <p>Deviations from the JDK, all deliberate:
 * <ul>
 *   <li>There is no separate model thread: {@code readLock}/{@code writeLock} take an
 *       ordinary monitor, and {@link #render(Runnable)} runs the runnable inline.</li>
 *   <li>A change reports its element change as <em>one</em> {@link ElementEdit} per event —
 *       an insertion reports the lines added, a removal the lines taken away — instead of the
 *       JDK's merged edit whose children array holds both sides. Both arrays are always
 *       non-null, and {@code getChange(element)} returns the edit for the element it names.</li>
 *   <li>The undoable-edit event carries a {@link DefaultDocumentEvent} that has been
 *       {@link DefaultDocumentEvent#end() ended} before it is fired, which is what makes it
 *       undoable through {@link javax.swing.undo.UndoManager} (see that method).</li>
 * </ul>
 */
public abstract class AbstractDocument implements Document, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /** The name of a paragraph-level element. */
    public static final String ParagraphElementName = "paragraph";

    /** The name of a leaf element that carries content. */
    public static final String ContentElementName = "content";

    /** The name of a section element. */
    public static final String SectionElementName = "section";

    /** The name of an element that carries a bidi level. */
    public static final String BidiElementName = "bidi level";

    /** The attribute name an element's name is stored under. */
    public static final String ElementNameAttribute = "$ename";

    private final Content data;
    private final AttributeContext context;
    private final Dictionary<Object, Object> properties = new Hashtable<>();
    private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
    private final List<StickyPosition> positions = new ArrayList<>();

    private DocumentFilter documentFilter;
    private List<DocumentListener> documentListeners;
    private List<UndoableEditListener> undoListeners;

    /** For a document built without a supplied buffer; {@link PlainDocument} does this. */
    protected AbstractDocument() {
        this(new StringContent());
    }

    protected AbstractDocument(Content data) {
        this(data, null);
    }

    protected AbstractDocument(Content data, AttributeContext context) {
        this.data = data;
        this.context = context;
    }

    // ------------------------------------------------------------ content

    /** The content buffer holding this document's characters. */
    public final Content getContent() {
        return data;
    }

    /** The attribute factory of this document, or null when it has none. */
    protected final AttributeContext getAttributeContext() {
        return context;
    }

    /** The lock object guarding this document's model. */
    protected final Object getLock() {
        return lock;
    }

    /**
     * Takes the model lock.
     *
     * <p>Port deviation from the JDK's read/write lock pair: one reentrant lock serves both,
     * because there is no separate model thread on Android and every mutation here is made
     * from the Swing thread.
     */
    protected final void writeLock() {
        lock.lock();
    }

    protected final void writeUnlock() {
        lock.unlock();
    }

    protected final void readLock() {
        lock.lock();
    }

    protected final void readUnlock() {
        lock.unlock();
    }

    public final int getLength() {
        return data.length();
    }

    // ------------------------------------------------------- construction

    public Dictionary<Object, Object> getDocumentProperties() {
        return properties;
    }

    public void setDocumentProperties(Dictionary<Object, Object> x) {
        if (x == null) {
            return;
        }
        Enumeration<Object> keys = x.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            properties.put(key, x.get(key));
        }
    }

    public final Object getProperty(Object key) {
        return properties.get(key);
    }

    public void putProperty(Object key, Object value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }

    // ------------------------------------------------------------- filter

    /** The filter installed with {@link #setDocumentFilter}, or null. */
    public DocumentFilter getDocumentFilter() {
        return documentFilter;
    }

    /** Installs (or with null removes) the filter consulted before every mutation. */
    public void setDocumentFilter(DocumentFilter filter) {
        documentFilter = filter;
    }

    private final class Bypass extends DocumentFilter.FilterBypass {
        @Override
        public Document getDocument() {
            return AbstractDocument.this;
        }

        @Override
        public void remove(int offset, int length) throws BadLocationException {
            handleRemove(offset, length);
        }

        @Override
        public void insertString(int offset, String string, AttributeSet attr) throws BadLocationException {
            handleInsertString(offset, string, attr);
        }

        @Override
        public void replace(int offset, int length, String string, AttributeSet attrs)
                throws BadLocationException {
            replace(offset, length, string, attrs);
        }
    }

    private DocumentFilter.FilterBypass getFilterBypass() {
        return new Bypass();
    }

    // ------------------------------------------------------------ editing

    @Override
    public void insertString(int offset, String str, AttributeSet a) throws BadLocationException {
        DocumentFilter filter = getDocumentFilter();
        writeLock();
        try {
            if (filter != null) {
                filter.insertString(getFilterBypass(), offset, str, a);
            } else {
                handleInsertString(offset, str, a);
            }
        } finally {
            writeUnlock();
        }
    }

    @Override
    public void remove(int offs, int len) throws BadLocationException {
        DocumentFilter filter = getDocumentFilter();
        writeLock();
        try {
            if (filter != null) {
                filter.remove(getFilterBypass(), offs, len);
            } else {
                handleRemove(offs, len);
            }
        } finally {
            writeUnlock();
        }
    }

    /**
     * Replaces a range with new text (or, with empty text, changes only the attributes).
     *
     * <p>Not part of the {@link Document} interface in the JDK either; it exists because
     * {@link DocumentFilter.FilterBypass#replace} and the attribute-change event need it.
     */
    public void replace(int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        writeLock();
        try {
            replaceInternal(offset, length, text, attrs);
        } finally {
            writeUnlock();
        }
    }

    private void replaceInternal(int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        if (length == 0 && (text == null || text.isEmpty())) {
            DefaultDocumentEvent chng = new DefaultDocumentEvent(offset, 0, DocumentEvent.EventType.CHANGE);
            chng.end();
            fireChangedUpdate(chng);
            fireUndoableEditUpdate(new UndoableEditEvent(this, chng));
            return;
        }
        if (length > 0) {
            // Both sides go through the filter once: a filter re-entering via the bypass
            // does not see its own edits.
            DocumentFilter filter = getDocumentFilter();
            if (filter == null) {
                handleRemove(offset, length);
                handleInsertString(offset, text, attrs);
            } else {
                filter.replace(getFilterBypass(), offset, length, text, attrs);
            }
            return;
        }
        insertString(offset, text, attrs);
    }

    /** Inserts without consulting the filter. */
    protected void handleInsertString(int offset, String str, AttributeSet a) throws BadLocationException {
        if (str == null || str.isEmpty()) {
            return;
        }
        data.insertString(offset, str);
        shiftPositionsForInsert(offset, str.length());

        DefaultDocumentEvent chng = new DefaultDocumentEvent(offset, str.length(),
                DocumentEvent.EventType.INSERT);
        UndoableEdit edit = new ContentEdit(offset, str);
        chng.addEdit(edit);
        insertUpdate(chng, a);
        fireInsertUpdate(chng);
        // Hand the edit to the undo managers only once it is complete: an edit still in
        // progress cannot be undone, and every listener here is expected to be able to.
        chng.end();
        fireUndoableEditUpdate(new UndoableEditEvent(this, chng));
    }

    /** Removes without consulting the filter. */
    protected void handleRemove(int offs, int len) throws BadLocationException {
        if (len == 0) {
            return;
        }
        String removed = data.getString(offs, len);
        DefaultDocumentEvent chng = new DefaultDocumentEvent(offs, len, DocumentEvent.EventType.REMOVE);
        removeUpdate(chng);
        data.remove(offs, len);
        shiftPositionsForRemove(offs, len);
        chng.addEdit(new ContentEdit(offs, removed));
        fireRemoveUpdate(chng);
        chng.end();
        fireUndoableEditUpdate(new UndoableEditEvent(this, chng));
    }

    // --------------------------------------------------------- positions

    @Override
    public Position getStartPosition() {
        return new StickyPosition(0, Position.Bias.Backward);
    }

    @Override
    public Position getEndPosition() {
        return new StickyPosition(data.length(), Position.Bias.Forward);
    }

    /** A position that moves with the text around it (see {@link StickyPosition}). */
    public Position createPosition(int offs) throws BadLocationException {
        if (offs < 0 || offs > data.length()) {
            throw new BadLocationException("Position out of range", offs);
        }
        StickyPosition p = new StickyPosition(offs, Position.Bias.Forward);
        synchronized (lock) {
            positions.add(p);
        }
        return p;
    }

    private void shiftPositionsForInsert(int where, int length) {
        synchronized (lock) {
            for (int i = 0; i < positions.size(); i++) {
                positions.get(i).afterInsert(where, length);
            }
        }
    }

    private void shiftPositionsForRemove(int where, int length) {
        synchronized (lock) {
            for (int i = 0; i < positions.size(); i++) {
                positions.get(i).afterRemove(where, length);
            }
        }
    }

    /** A {@link Position} whose offset is bumped whenever text is inserted before it. */
    private static final class StickyPosition implements Position {
        private int offset;
        private final Position.Bias bias;
        private boolean valid = true;

        StickyPosition(int offset, Position.Bias bias) {
            this.offset = offset;
            this.bias = bias;
        }

        @Override
        public int getOffset() {
            return valid ? offset : -1;
        }

        void afterInsert(int where, int length) {
            if (!valid) {
                return;
            }
            if (offset > where || (offset == where && bias == Position.Bias.Forward)) {
                offset += length;
            }
        }

        void afterRemove(int where, int length) {
            if (!valid) {
                return;
            }
            if (offset >= where + length) {
                offset -= length;
            } else if (offset > where) {
                offset = where;
            }
        }
    }

    // -------------------------------------------------------------- text

    /** This document's root elements: a text document has exactly one. */
    @Override
    public Element[] getRootElements() {
        return new Element[] {getDefaultRootElement()};
    }

    @Override
    public String getText(int offset, int length) throws BadLocationException {
        return data.getString(offset, length);
    }

    @Override
    public void getText(int offset, int length, Segment txt) throws BadLocationException {
        data.getChars(offset, length, txt);
    }

    // ------------------------------------------------------------ render

    /**
     * Runs {@code r} with the model locked.
     *
     * <p>Port deviation: the runnable runs inline. The JDK serialises model mutations on a
     * separate thread; this port's Swing thread is the single writer, so a nested run is
     * always safe and avoids a second thread that Android would only have to idle.
     */
    @Override
    public void render(Runnable r) {
        if (r == null) {
            return;
        }
        readLock();
        try {
            r.run();
        } finally {
            readUnlock();
        }
    }

    // ----------------------------------------------------------- events

    @Override
    public void addDocumentListener(DocumentListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (lock) {
            if (documentListeners == null) {
                documentListeners = new ArrayList<>(2);
            }
            documentListeners.add(listener);
        }
    }

    @Override
    public void removeDocumentListener(DocumentListener listener) {
        synchronized (lock) {
            if (documentListeners != null) {
                documentListeners.remove(listener);
            }
        }
    }

    @Override
    public void addUndoableEditListener(UndoableEditListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (lock) {
            if (undoListeners == null) {
                undoListeners = new ArrayList<>(2);
            }
            undoListeners.add(listener);
        }
    }

    @Override
    public void removeUndoableEditListener(UndoableEditListener listener) {
        synchronized (lock) {
            if (undoListeners != null) {
                undoListeners.remove(listener);
            }
        }
    }

    /** The registered listeners of the given type (the JDK's {@code getListeners} contract). */
    public EventListener[] getListeners(Class<?> listenerType) {
        List<EventListener> out = new ArrayList<>();
        synchronized (lock) {
            if (listenerType == DocumentListener.class && documentListeners != null) {
                out.addAll(documentListeners);
            } else if (listenerType == UndoableEditListener.class && undoListeners != null) {
                out.addAll(undoListeners);
            }
        }
        return out.toArray(new EventListener[0]);
    }

    protected void fireInsertUpdate(DocumentEvent e) {
        for (DocumentListener l : snapshotDocumentListeners()) {
            l.insertUpdate(e);
        }
    }

    protected void fireRemoveUpdate(DocumentEvent e) {
        for (DocumentListener l : snapshotDocumentListeners()) {
            l.removeUpdate(e);
        }
    }

    protected void fireChangedUpdate(DocumentEvent e) {
        for (DocumentListener l : snapshotDocumentListeners()) {
            l.changedUpdate(e);
        }
    }

    protected void fireUndoableEditUpdate(UndoableEditEvent e) {
        List<UndoableEditListener> listeners;
        synchronized (lock) {
            listeners = undoListeners == null ? null : new ArrayList<>(undoListeners);
        }
        if (listeners == null) {
            return;
        }
        for (UndoableEditListener l : listeners) {
            l.undoableEditHappened(e);
        }
    }

    private List<DocumentListener> snapshotDocumentListeners() {
        synchronized (lock) {
            return documentListeners == null ? Collections.<DocumentListener>emptyList()
                    : new ArrayList<>(documentListeners);
        }
    }

    // -------------------------------------------------------- element hooks

    /**
     * Called after an insertion so a subclass can update its element tree.
     *
     * <p>The base implementation does nothing: a document with no line structure has no
     * elements to update.
     */
    protected void insertUpdate(DefaultDocumentEvent chng, AttributeSet attr) {
    }

    /**
     * Called <em>before</em> a removal takes effect (as in the JDK), so the subclass can
     * still see the text that is about to disappear when it computes the removed elements.
     */
    protected void removeUpdate(DefaultDocumentEvent chng) {
    }

    // ------------------------------------------------------------ debug

    /** Dumps the element tree to {@code out} (the JDK's diagnostic helper). */
    public void dump(PrintStream out) {
        Element root = getDefaultRootElement();
        dumpElement(out, root, 0);
    }

    private void dumpElement(PrintStream out, Element element, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }
        out.println(indent + element.getName() + " [" + element.getStartOffset() + ","
                + element.getEndOffset() + "]");
        for (int i = 0; i < element.getElementCount(); i++) {
            dumpElement(out, element.getElement(i), depth + 1);
        }
    }

    // ------------------------------------------------------- inner types

    /**
     * The character buffer contract of a document.
     *
     * <p>{@link StringContent} is the only implementation on this port; the JDK's gap buffer
     * exists for a model thread this port does not have.
     */
    public interface Content {
        /** The number of characters held. */
        int length();

        /** Inserts {@code str} at {@code where}. */
        void insertString(int where, String str) throws BadLocationException;

        /** Removes {@code nitems} characters at {@code where}. */
        void remove(int where, int nitems) throws BadLocationException;

        /** The text of the given range. */
        String getString(int where, int len) throws BadLocationException;

        /** Copies the given range into {@code txt}. */
        void getChars(int where, int len, Segment txt) throws BadLocationException;
    }

    /** The attribute factory a document uses to merge attribute sets. */
    public interface AttributeContext {
        /** Adds an attribute, returning the (possibly new) set. */
        AttributeSet addAttribute(AttributeSet old, Object name, Object value);

        /** Adds every attribute of {@code attr}. */
        AttributeSet addAttributes(AttributeSet old, AttributeSet attr);

        /** Removes an attribute. */
        AttributeSet removeAttribute(AttributeSet old, Object name);

        /** Removes the named attributes. */
        AttributeSet removeAttributes(AttributeSet old, Enumeration<?> names);

        /** Removes every attribute of {@code attrs}. */
        AttributeSet removeAttributes(AttributeSet old, AttributeSet attrs);

        /** The shared empty attribute set. */
        AttributeSet getEmptySet();

        /** Returns a set that is no longer needed. */
        void reclaim(AttributeSet a);
    }

    /** An element with no children. */
    public static class LeafElement extends AbstractElement {

        public LeafElement(Element parent, AttributeSet a, int offs0, int offs1) {
            super(parent, a, offs0, offs1);
        }

        @Override
        public String getName() {
            return ContentElementName;
        }

        @Override
        public int getElementIndex(int offset) {
            return -1;
        }

        @Override
        public int getElementCount() {
            return 0;
        }

        @Override
        public Element getElement(int index) {
            return null;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }
    }

    /** An element with children. */
    public static class BranchElement extends AbstractElement {

        public BranchElement(Element parent, AttributeSet a) {
            super(parent, a, 0, 0);
        }

        @Override
        public String getName() {
            Object name = getAttribute(ElementNameAttribute);
            return name == null ? ParagraphElementName : name.toString();
        }

        @Override
        public boolean isLeaf() {
            return false;
        }
    }

    /**
     * The shared implementation of {@link Element} and {@link MutableAttributeSet}.
     *
     * <p>Implements the child-storage and offset-fixing half of the JDK class of the same
     * name; the JDK's {@code AttributeOwner} bookkeeping is not modelled.
     */
    public abstract static class AbstractElement extends SimpleAttributeSet implements Element {
        private final Element parent;
        private final Document document;
        private int startOffset;
        private int endOffset;
        private Element[] children;

        protected AbstractElement(Element parent, AttributeSet a, int offs0, int offs1) {
            super(a);
            this.parent = parent;
            this.document = parent == null ? null : parent.getDocument();
            this.startOffset = offs0;
            this.endOffset = offs1;
        }

        @Override
        public Document getDocument() {
            return document;
        }

        @Override
        public Element getParentElement() {
            return parent;
        }

        @Override
        public AttributeSet getAttributes() {
            return this;
        }

        @Override
        public int getStartOffset() {
            return startOffset;
        }

        @Override
        public int getEndOffset() {
            return endOffset;
        }

        /** Moves this element's range. */
        protected void setOffsets(int start, int end) {
            this.startOffset = start;
            this.endOffset = end;
        }

        /** The child over {@code offset}, or the closest child when {@code offset} is outside. */
        @Override
        public int getElementIndex(int offset) {
            Element[] kids = getChildren();
            int low = 0;
            int high = kids.length - 1;
            if (high < 0) {
                return -1;
            }
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int start = kids[mid].getStartOffset();
                int end = kids[mid].getEndOffset();
                if (offset < start) {
                    high = mid - 1;
                } else if (offset >= end) {
                    low = mid + 1;
                } else {
                    return mid;
                }
            }
            return Math.max(0, Math.min(kids.length - 1, low));
        }

        @Override
        public int getElementCount() {
            return getChildren().length;
        }

        @Override
        public Element getElement(int index) {
            Element[] kids = getChildren();
            return index >= 0 && index < kids.length ? kids[index] : null;
        }

        @Override
        public abstract boolean isLeaf();

        private Element[] getChildren() {
            return children == null ? EMPTY_ELEMENTS : children;
        }

        /** Replaces this element's children (the JDK's {@code replace}). */
        public void replace(int offset, int length, Element[] added) {
            Element[] kids = getChildren();
            Element[] updated = new Element[kids.length - length + (added == null ? 0 : added.length)];
            System.arraycopy(kids, 0, updated, 0, offset);
            if (added != null && added.length > 0) {
                System.arraycopy(added, 0, updated, offset, added.length);
            }
            System.arraycopy(kids, offset + length, updated, offset + (added == null ? 0 : added.length),
                    kids.length - offset - length);
            children = updated;
        }

        @Override
        public String toString() {
            return getName() + "[" + startOffset + "," + endOffset + "]";
        }
    }

    private static final Element[] EMPTY_ELEMENTS = new Element[0];

    // ------------------------------------------------------- undo plumbing

    /** Undoes/redoes one content mutation by re-inserting or removing the same text. */
    protected class ContentEdit extends AbstractUndoableEdit {
        private final int offset;
        private final String text;

        ContentEdit(int offset, String text) {
            this.offset = offset;
            this.text = text;
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            try {
                data.remove(offset, text.length());
                shiftPositionsForRemove(offset, text.length());
            } catch (BadLocationException e) {
                throw new CannotUndoException();
            }
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            try {
                data.insertString(offset, text);
                shiftPositionsForInsert(offset, text.length());
            } catch (BadLocationException e) {
                throw new CannotRedoException();
            }
        }

        @Override
        public String getPresentationName() {
            return "change";
        }
    }

    /**
     * The event a mutation hands to its listeners, which is also the undoable edit that
     * reverses it.
     *
     * <p>Deviation worth knowing about: this edit is {@link CompoundEdit compound}, so the
     * JDK's {@code canUndo()} answers false while {@code inProgress} — the JDK relies on
     * that state clearing somewhere in the model thread. Here the caller clears it
     * explicitly with {@link #end()} before the edit is published to listeners, so an undo
     * manager that is registered on the document can immediately undo what it was handed.
     */
    public class DefaultDocumentEvent extends CompoundEdit implements DocumentEvent {
        private final int offset;
        private final int length;
        private final EventType type;
        private final List<ElementChange> changes = new ArrayList<>(1);

        public DefaultDocumentEvent(int offs, int len, EventType type) {
            this.offset = offs;
            this.length = len;
            this.type = type;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public Document getDocument() {
            return AbstractDocument.this;
        }

        @Override
        public EventType getType() {
            return type;
        }

        /** Records an element change for {@link #getChange(Element)}. */
        public void addChange(ElementChange change) {
            if (change != null) {
                changes.add(change);
            }
        }

        @Override
        public ElementChange getChange(Element elem) {
            for (int i = changes.size() - 1; i >= 0; i--) {
                ElementChange change = changes.get(i);
                if (change.getElement() == elem) {
                    return change;
                }
            }
            return null;
        }

        /** Marks the edit complete so it can be undone. */
        public void end() {
            super.end();
        }

        @Override
        public String getPresentationName() {
            return String.valueOf(type);
        }

        @Override
        public String getUndoPresentationName() {
            return "Undo " + getPresentationName();
        }

        @Override
        public String getRedoPresentationName() {
            return "Redo " + getPresentationName();
        }
    }

    /**
     * The elements added to or removed from a parent element by a {@link DocumentEvent}.
     *
     * <p>One side of the pair is always empty here (see the class comment's deviation list).
     */
    public static class ElementEdit extends AbstractUndoableEdit implements DocumentEvent.ElementChange {
        private final Element element;
        private final int index;
        private final Element[] removed;
        private final Element[] added;

        public ElementEdit(Element element, int index, Element[] removed, Element[] added) {
            this.element = element;
            this.index = index;
            this.removed = removed == null ? EMPTY_ELEMENTS : removed;
            this.added = added == null ? EMPTY_ELEMENTS : added;
        }

        @Override
        public Element getElement() {
            return element;
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public Element[] getChildrenRemoved() {
            return removed.clone();
        }

        @Override
        public Element[] getChildrenAdded() {
            return added.clone();
        }

        /** An element edit cannot be undone on its own: its document event owns the text. */
        @Override
        public boolean canUndo() {
            return false;
        }
    }
}

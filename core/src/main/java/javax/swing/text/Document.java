package javax.swing.text;

import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditListener;

/**
 * Hand-written {@code javax.swing.text.Document}: the editable character container a
 * {@link JTextComponent} shows.
 *
 * <p>Why hand-written: the write side matters on this port — the plugin panels install
 * {@link DocumentFilter}s on their fields, register document listeners for live validation
 * and undo, and call {@code insertString}/{@code remove}/{@code replace} through the
 * document. The generated stub answered with nulls and never fired an event, so nothing
 * downstream (the color picker's hex field, Notes, the profile renamer) could work.
 *
 * <p>Deviation from the JDK: {@link #render(Runnable)} runs the runnable inline instead of
 * serialising model mutations on the event thread, because every document mutation on this
 * port is already made from the Swing thread (or from a plugin that the port serialises
 * there).
 */
public interface Document {

    /** The property key of the {@link java.io.Reader} the document was read from. */
    String StreamDescriptionProperty = "stream";

    /** The property key of the document's title. */
    String TitleProperty = "title";

    /** The number of characters in the document. */
    int getLength();

    /** Registers a listener for insert/remove/attribute events. */
    void addDocumentListener(DocumentListener listener);

    /** Removes a listener registered with {@link #addDocumentListener}. */
    void removeDocumentListener(DocumentListener listener);

    /** Registers a listener for undoable edits (an {@link javax.swing.undo.UndoManager}). */
    void addUndoableEditListener(UndoableEditListener listener);

    /** Removes a listener registered with {@link #addUndoableEditListener}. */
    void removeUndoableEditListener(UndoableEditListener listener);

    /** A document property, or null. */
    Object getProperty(Object key);

    /** Sets a document property ({@code null} removes it). */
    void putProperty(Object key, Object value);

    /** Removes {@code len} characters starting at {@code offs}. */
    void remove(int offs, int len) throws BadLocationException;

    /** Inserts {@code str} at {@code offset} with the given attributes. */
    void insertString(int offset, String str, AttributeSet a) throws BadLocationException;

    /** The text of the given range. */
    String getText(int offset, int length) throws BadLocationException;

    /** Copies the text of the given range into {@code txt}. */
    void getText(int offset, int length, Segment txt) throws BadLocationException;

    /** A position at the start of the document that follows edits. */
    Position getStartPosition();

    /** A position at the end of the document that follows edits. */
    Position getEndPosition();

    /** The root of this document's element tree (the paragraph level for text documents). */
    Element getDefaultRootElement();

    /** This document's root elements (a text document has exactly one). */
    Element[] getRootElements();

    /**
     * Runs {@code r} with the document's model locked.
     *
     * <p>Port deviation: the runnable runs inline; there is no separate model thread.
     */
    void render(Runnable r);
}

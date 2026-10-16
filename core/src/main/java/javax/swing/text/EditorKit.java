package javax.swing.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import javax.swing.Action;
import javax.swing.JEditorPane;

/**
 * Hand-written {@code javax.swing.text.EditorKit}: the document/caret/view factory a text
 * component's content type selects.
 *
 * <p>Why hand-written: {@code JEditorPane} (the info panel's rich-text view and RuneLite's
 * {@code JRichTextPane}) asks its kit for the caret, the default document and the actions, and
 * the client jar references {@code javax.swing.text.EditorKit} directly. The generated stub
 * declared no members at all, so those answers were impossible.
 *
 * <p>Deviation from the JDK: {@code install}/{@code deinstall} do nothing (there is no
 * {@code TextUI} to bind to), and the read/write pairs are implemented in the kit rather than
 * in a per-content-type subclass.
 */
public abstract class EditorKit implements Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public EditorKit() {
    }

    /** Kits are stateless on this port, so a shallow copy is enough. */
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /** Called when the kit is installed on a component (no-op on this port). */
    public void install(JEditorPane c) {
    }

    /** Called when the kit is removed from a component (no-op on this port). */
    public void deinstall(JEditorPane c) {
    }

    /** The MIME type of the content this kit edits, e.g. {@code text/plain}. */
    public abstract String getContentType();

    /** The factory that builds views for this kit's documents, or null. */
    public abstract ViewFactory getViewFactory();

    /** The actions this kit provides (bound through the component's InputMap). */
    public abstract Action[] getActions();

    /** The caret the component should use, or null for the component's default. */
    public abstract Caret createCaret();

    /** A new empty document for this content type. */
    public abstract Document createDefaultDocument();

    /** Reads a character stream into the document. */
    public abstract void read(InputStream in, Document doc, int pos)
            throws IOException, BadLocationException;

    /** Writes part of the document to a byte stream. */
    public abstract void write(OutputStream out, Document doc, int pos, int len)
            throws IOException, BadLocationException;

    /** Reads a character stream into the document. */
    public abstract void read(Reader in, Document doc, int pos)
            throws IOException, BadLocationException;

    /** Writes part of the document to a character stream. */
    public abstract void write(Writer out, Document doc, int pos, int len)
            throws IOException, BadLocationException;
}

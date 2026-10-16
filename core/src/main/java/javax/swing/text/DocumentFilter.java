package javax.swing.text;

/**
 * Hand-written {@code javax.swing.text.DocumentFilter}: intercepts the mutations that a
 * {@link Document} is about to make.
 *
 * <p>Why hand-written: this is the one part of the text stack the shipped RuneLite panels
 * use directly — the color picker's hex field installs a filter that <em>throws</em>
 * {@link BadLocationException} to reject what was typed, and the profile renamer installs
 * one that silently drops illegal filename characters (it overrides
 * {@code insertString}/{@code replace} and calls the super implementation with the
 * sanitised text). A stub cannot do either, so the bypass contract is real here.
 *
 * <p>Deviation from the JDK: {@link FilterBypass#getDocument()} is abstract (as in the
 * JDK); nothing in the port subclasses {@code FilterBypass}.
 */
public class DocumentFilter {

    public DocumentFilter() {
    }

    /**
     * Removes {@code length} characters at {@code offset}.
     *
     * <p>The default implementation forwards to {@code fb}. A filter that wants to block the
     * edit simply returns without calling it; one that wants to reject it throws
     * {@link BadLocationException}.
     */
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        fb.remove(offset, length);
    }

    /** Inserts {@code string} at {@code offset}; the default forwards to {@code fb}. */
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
            throws BadLocationException {
        fb.insertString(offset, string, attr);
    }

    /**
     * Replaces {@code length} characters at {@code offset} with {@code text}; the default
     * forwards to {@code fb}.
     */
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        fb.replace(offset, length, text, attrs);
    }

    /**
     * The handle a filter uses to perform the mutation it was asked to filter. Re-entering
     * the document through the bypass is what stops the filter from being consulted twice.
     */
    public abstract static class FilterBypass {

        /** The document being mutated. */
        public abstract Document getDocument();

        /** Removes {@code length} characters at {@code offset} without consulting a filter. */
        public abstract void remove(int offset, int length) throws BadLocationException;

        /** Inserts {@code string} at {@code offset} without consulting a filter. */
        public abstract void insertString(int offset, String string, AttributeSet attr)
                throws BadLocationException;

        /** Replaces a range without consulting a filter. */
        public abstract void replace(int offset, int length, String string, AttributeSet attrs)
                throws BadLocationException;
    }
}

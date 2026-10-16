package javax.swing.text;

/**
 * Hand-written {@code javax.swing.text.BadLocationException}: the checked exception every
 * {@link Document} mutation declares.
 *
 * <p>Why hand-written: it is thrown and caught for real on this port (the color picker's
 * hex filter rejects input by throwing it, RuneLite catches it around document edits), and
 * the generated stub carried no offset.
 */
public class BadLocationException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int offs;

    public BadLocationException() {
        this(null, -1);
    }

    public BadLocationException(String s, int offs) {
        super(s);
        this.offs = offs;
    }

    /** The offset the caller asked for; -1 when it was not recorded. */
    public int offsetRequested() {
        return offs;
    }
}

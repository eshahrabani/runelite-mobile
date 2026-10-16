package javax.swing.text;

/**
 * Hand-written {@code javax.swing.text.StringContent}: the character buffer a document holds
 * its text in.
 *
 * <p>Why hand-written: {@link AbstractDocument.Content} has to store characters for real (the
 * port's text fields are edited by hand and by the plugin panels), and the JDK's own
 * {@code StringContent} is not part of the desktop surface the shipped jars could reference.
 *
 * <p>Deviation from the JDK: the buffer is a {@link StringBuilder} rather than a gap buffer.
 * The gap buffer exists to make large edits cheap on the model thread; this port's documents
 * are plugin-panel sized and every edit already allocates the event that describes it.
 */
class StringContent implements AbstractDocument.Content, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final StringBuilder buffer;

    StringContent() {
        this(64);
    }

    StringContent(int initialCapacity) {
        buffer = new StringBuilder(initialCapacity);
    }

    @Override
    public int length() {
        return buffer.length();
    }

    @Override
    public void insertString(int where, String str) throws BadLocationException {
        if (str == null) {
            return;
        }
        if (where < 0 || where > buffer.length()) {
            throw new BadLocationException("Invalid insert", where);
        }
        buffer.insert(where, str);
    }

    @Override
    public void remove(int where, int nitems) throws BadLocationException {
        if (nitems < 0 || where < 0 || where + nitems > buffer.length()) {
            throw new BadLocationException("Invalid remove", where);
        }
        buffer.delete(where, where + nitems);
    }

    @Override
    public String getString(int where, int len) throws BadLocationException {
        if (len < 0 || where < 0 || where + len > buffer.length()) {
            throw new BadLocationException("Invalid range", where);
        }
        return buffer.substring(where, where + len);
    }

    @Override
    public void getChars(int where, int len, Segment txt) throws BadLocationException {
        if (len < 0 || where < 0 || where + len > buffer.length()) {
            throw new BadLocationException("Invalid range", where);
        }
        if (txt.array == null || txt.array.length < len) {
            txt.array = new char[len];
        }
        buffer.getChars(where, where + len, txt.array, 0);
        txt.offset = 0;
        txt.count = len;
    }

    @Override
    public String toString() {
        return buffer.toString();
    }
}

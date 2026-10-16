package javax.swing.text;

/**
 * Hand-written {@code javax.swing.text.Position}: a location in a {@link Document} that
 * follows the text as it is edited.
 *
 * <p>Why hand-written: {@link AbstractDocument#createPosition(int)} is part of the real
 * document contract (a caret or a filter keeps a position to stay anchored across edits);
 * the generated stub could not even return an offset.
 */
public interface Position {

    /** The current offset of this position, or -1 once the document removed it. */
    int getOffset();

    /**
     * How a position behaves at an insertion point.
     *
     * <p>{@link #Forward} sticks to the text after the insertion (offset grows),
     * {@link #Backward} stays before it.
     */
    final class Bias {
        /** The position follows the text after an insertion at its offset. */
        public static final Bias Forward = new Bias("Forward");

        /** The position stays before text inserted at its offset. */
        public static final Bias Backward = new Bias("Backward");

        private final String name;

        private Bias(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        /** Returns the bias constant with the given name, or null. */
        public static Bias valueOf(String name) {
            if ("Forward".equals(name)) {
                return Forward;
            }
            if ("Backward".equals(name)) {
                return Backward;
            }
            return null;
        }
    }
}

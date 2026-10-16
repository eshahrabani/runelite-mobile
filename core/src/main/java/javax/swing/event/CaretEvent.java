package javax.swing.event;

/**
 * Hand-written {@code javax.swing.event.CaretEvent}: the dot and mark a caret moved to.
 *
 * <p>Why hand-written: it is the argument of {@link CaretListener#caretUpdate}, which
 * {@code JTextComponent.addCaretListener} delivers on this port. The generated stub set did not
 * contain this type.
 *
 * <p>As in the JDK this class is abstract; the port's own event subclass carries the offsets.
 */
public abstract class CaretEvent extends java.util.EventObject {
    private static final long serialVersionUID = 1L;

    /** The new dot. */
    protected int dot;

    /** The new mark. */
    protected int mark;

    public CaretEvent(Object source) {
        this(source, 0, 0);
    }

    public CaretEvent(Object source, int dot, int mark) {
        super(source == null ? CaretEvent.class : source);
        this.dot = dot;
        this.mark = mark;
    }

    /** The position of the caret after the change. */
    public int getDot() {
        return dot;
    }

    /** The other end of the selection after the change. */
    public int getMark() {
        return mark;
    }

    /** The concrete event a {@code JTextComponent} fires. */
    public static class CaretPositionEvent extends CaretEvent {
        private static final long serialVersionUID = 1L;

        public CaretPositionEvent(Object source, int dot, int mark) {
            super(source, dot, mark);
        }
    }
}

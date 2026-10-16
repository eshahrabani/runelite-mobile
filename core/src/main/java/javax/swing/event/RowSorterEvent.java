package javax.swing.event;

/** Swing {@code RowSorterEvent}: a row sorter re-sorted or changed. */
public class RowSorterEvent extends java.util.EventObject {
    private static final long serialVersionUID = 4164377688258829434L;

    public enum Type {
        SORT_ORDER_CHANGED,
        SORTED
    }

    private final Type type;

    public RowSorterEvent(Object source) {
        this(source, Type.SORT_ORDER_CHANGED, null);
    }

    public RowSorterEvent(Object source, Type type, int[] previousRowIndexToModel) {
        super(source == null ? RowSorterEvent.class : source);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}

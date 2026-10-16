package javax.swing.event;

/** Swing {@code TableColumnModelEvent}: a table column was added, removed or moved. */
public class TableColumnModelEvent extends java.util.EventObject {
    private static final long serialVersionUID = -2068586520966402287L;

    protected int fromIndex;
    protected int toIndex;

    public TableColumnModelEvent(Object source, int from, int to) {
        super(source == null ? TableColumnModelEvent.class : source);
        this.fromIndex = from;
        this.toIndex = to;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getToIndex() {
        return toIndex;
    }
}

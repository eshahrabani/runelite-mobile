package javax.swing.event;

/**
 * Swing {@code ListSelectionEvent}: a list/tree/table selection changed.
 */
public class ListSelectionEvent extends java.util.EventObject {
    private static final long serialVersionUID = -4298474751201349152L;

    private final int firstIndex;
    private final int lastIndex;
    private final boolean isAdjusting;

    public ListSelectionEvent(Object source, int firstIndex, int lastIndex, boolean isAdjusting) {
        super(source == null ? ListSelectionEvent.class : source);
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.isAdjusting = isAdjusting;
    }

    public int getFirstIndex() {
        return firstIndex;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public boolean getValueIsAdjusting() {
        return isAdjusting;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[firstIndex=" + firstIndex + ",lastIndex=" + lastIndex
                + ",isAdjusting=" + isAdjusting + "]";
    }
}

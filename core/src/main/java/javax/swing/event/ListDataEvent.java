package javax.swing.event;

/** Swing {@code ListDataEvent}: a {@code ListModel} changed. */
public class ListDataEvent extends java.util.EventObject {
    private static final long serialVersionUID = -3794740411531421048L;

    public static final int CONTENTS_CHANGED = 0;
    public static final int INTERVAL_ADDED = 1;
    public static final int INTERVAL_REMOVED = 2;

    private final int type;
    private final int index0;
    private final int index1;

    public ListDataEvent(Object source, int type, int index0, int index1) {
        super(source == null ? ListDataEvent.class : source);
        this.type = type;
        this.index0 = index0;
        this.index1 = index1;
    }

    public int getType() {
        return type;
    }

    public int getIndex0() {
        return index0;
    }

    public int getIndex1() {
        return index1;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[type=" + type + ",index0=" + index0 + ",index1=" + index1 + "]";
    }
}

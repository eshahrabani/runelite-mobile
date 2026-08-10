package java.awt.event;

import java.awt.Component;

public class HierarchyEvent {
    public static final int HIERARCHY_CHANGED = 1400;
    public static final int ANCESTOR_MOVED = 1401;
    public static final int ANCESTOR_RESIZED = 1402;

    private Component source;
    private int id;

    public HierarchyEvent(Component source, int id, Component changed, java.awt.Container changedParent) {
        this.source = source;
        this.id = id;
    }

    public Component getSource() { return source; }
    public int getID() { return id; }

    public long getChangeFlags() { return 0; }
}

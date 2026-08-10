package java.awt.event;

import java.awt.Component;

public class ComponentEvent {
    public static final int COMPONENT_MOVED = 100;
    public static final int COMPONENT_RESIZED = 101;
    public static final int COMPONENT_SHOWN = 102;
    public static final int COMPONENT_HIDDEN = 103;

    private int id;
    private Component component;

    public ComponentEvent(Component source, int id) {
        this.component = source;
        this.id = id;
    }

    public Component getComponent() { return component; }
    public int getID() { return id; }
}

package java.awt.event;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;

/** AWT {@code ContainerEvent}: a child was added to or removed from a container. */
public class ContainerEvent extends AWTEvent {
    public static final int CONTAINER_FIRST = 300;
    public static final int COMPONENT_ADDED = 300;
    public static final int COMPONENT_REMOVED = 301;
    public static final int CONTAINER_LAST = 301;

    private final Component child;

    public ContainerEvent(Container source, int id, Component child) {
        super(source, id);
        this.child = child;
    }

    public Container getContainer() {
        return (Container) getSource();
    }

    public Component getChild() {
        return child;
    }

    @Override
    public String paramString() {
        return (getID() == COMPONENT_ADDED ? "COMPONENT_ADDED" : "COMPONENT_REMOVED") + " child=" + child;
    }
}

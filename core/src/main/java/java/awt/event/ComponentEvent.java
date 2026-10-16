package java.awt.event;

import java.awt.AWTEvent;
import java.awt.Component;

/**
 * AWT {@code ComponentEvent} on the real {@link AWTEvent} hierarchy: the dispatch
 * machinery of the port's Swing runtime keys off {@code instanceof ComponentEvent}, and
 * the game client constructs these from its own UI code.
 */
public class ComponentEvent extends AWTEvent {
    public static final int COMPONENT_FIRST = 100;
    public static final int COMPONENT_MOVED = 100;
    public static final int COMPONENT_RESIZED = 101;
    public static final int COMPONENT_SHOWN = 102;
    public static final int COMPONENT_HIDDEN = 103;
    public static final int COMPONENT_LAST = 103;

    /** Placeholder for the generator's hand-written-supertype model; never dispatched. */
    public ComponentEvent() {
        this(null, COMPONENT_MOVED);
    }

    public ComponentEvent(Component source, int id) {
        super(source, id);
    }

    public Component getComponent() {
        return (Component) getSource();
    }

    @Override
    public String paramString() {
        switch (getID()) {
            case COMPONENT_MOVED:
                return "COMPONENT_MOVED";
            case COMPONENT_RESIZED:
                return "COMPONENT_RESIZED";
            case COMPONENT_SHOWN:
                return "COMPONENT_SHOWN";
            case COMPONENT_HIDDEN:
                return "COMPONENT_HIDDEN";
            default:
                return "unknown type";
        }
    }
}

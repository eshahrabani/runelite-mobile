package java.awt.event;

import java.awt.AWTEvent;
import java.awt.Component;

/**
 * AWT {@code FocusEvent}. Focus changes are synthesized by the port's
 * {@code KeyboardFocusManager} (there is no native focus owner) and delivered to the
 * focus listeners of the component involved.
 */
public class FocusEvent extends AWTEvent {
    public static final int FOCUS_FIRST = 1004;
    public static final int FOCUS_GAINED = 1004;
    public static final int FOCUS_LOST = 1005;
    public static final int FOCUS_LAST = 1005;

    private final boolean temporary;
    private final Component opposite;

    /** Placeholder for the generator's hand-written-supertype model; never dispatched. */
    public FocusEvent() {
        this(null, FOCUS_GAINED);
    }

    public FocusEvent(Component source, int id) {
        this(source, id, false);
    }

    public FocusEvent(Component source, int id, boolean temporary) {
        this(source, id, temporary, null);
    }

    public FocusEvent(Component source, int id, boolean temporary, Component opposite) {
        super(source, id);
        this.temporary = temporary;
        this.opposite = opposite;
    }

    public Component getComponent() {
        return (Component) getSource();
    }

    public boolean isTemporary() {
        return temporary;
    }

    public Component getOppositeComponent() {
        return opposite;
    }

    @Override
    public String paramString() {
        return (getID() == FOCUS_GAINED ? "FOCUS_GAINED" : "FOCUS_LOST")
                + (temporary ? ",temporary" : "");
    }
}

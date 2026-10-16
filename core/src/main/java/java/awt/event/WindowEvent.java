package java.awt.event;

import java.awt.AWTEvent;
import java.awt.Window;

/** AWT {@code WindowEvent}: window open/close/activate/state transitions of the port's virtual windows. */
public class WindowEvent extends AWTEvent {
    public static final int WINDOW_FIRST = 200;
    public static final int WINDOW_OPENED = 200;
    public static final int WINDOW_CLOSING = 201;
    public static final int WINDOW_CLOSED = 202;
    public static final int WINDOW_ICONIFIED = 203;
    public static final int WINDOW_DEICONIFIED = 204;
    public static final int WINDOW_ACTIVATED = 205;
    public static final int WINDOW_DEACTIVATED = 206;
    public static final int WINDOW_GAINED_FOCUS = 207;
    public static final int WINDOW_LOST_FOCUS = 208;
    public static final int WINDOW_STATE_CHANGED = 209;
    public static final int WINDOW_LAST = 209;

    private final Window opposite;
    private final int oldState;
    private final int newState;

    /** Placeholder for the generator's hand-written-supertype model; never dispatched. */
    public WindowEvent() {
        this(null, WINDOW_OPENED);
    }

    public WindowEvent(Window source, int id) {
        this(source, id, null);
    }

    public WindowEvent(Window source, int id, Window opposite) {
        this(source, id, opposite, 0, 0);
    }

    public WindowEvent(Window source, int id, int oldState, int newState) {
        this(source, id, null, oldState, newState);
    }

    public WindowEvent(Window source, int id, Window opposite, int oldState, int newState) {
        super(source, id);
        this.opposite = opposite;
        this.oldState = oldState;
        this.newState = newState;
    }

    public Window getWindow() {
        return (Window) getSource();
    }

    @Override
    public Window getSource() {
        return (Window) super.getSource();
    }

    public Window getOppositeWindow() {
        return opposite;
    }

    public int getOldState() {
        return oldState;
    }

    public int getNewState() {
        return newState;
    }
}

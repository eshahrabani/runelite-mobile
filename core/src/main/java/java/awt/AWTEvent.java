package java.awt;

import java.util.EventObject;

/**
 * Base class of the AWT event hierarchy the game client uses.
 *
 * <p>Hand-written rather than generated: the client jar never names a member of this
 * class directly (its call sites go through {@code ActionEvent} &co.), but the core
 * event stubs extend it and call {@code super(source, id)}, so the constructor and the
 * consumed flag have to exist for real. Getting this wrong is invisible at build time
 * and fatal at runtime: the game's own UI event dispatch creates {@code ActionEvent}s on
 * the client thread, and a missing {@code AWTEvent(Object,int)} stops that thread with a
 * {@code NoSuchMethodError} (the frame freezes).
 */
public class AWTEvent extends EventObject {

    public static final long COMPONENT_EVENT_MASK = 0x1L;
    public static final long CONTAINER_EVENT_MASK = 0x2L;
    public static final long FOCUS_EVENT_MASK = 0x4L;
    public static final long KEY_EVENT_MASK = 0x8L;
    public static final long MOUSE_EVENT_MASK = 0x10L;
    public static final long MOUSE_MOTION_EVENT_MASK = 0x20L;
    public static final long WINDOW_EVENT_MASK = 0x40L;
    public static final long ACTION_EVENT_MASK = 0x80L;
    public static final long ADJUSTMENT_EVENT_MASK = 0x100L;
    public static final long ITEM_EVENT_MASK = 0x200L;
    public static final long TEXT_EVENT_MASK = 0x400L;
    public static final long INPUT_METHOD_EVENT_MASK = 0x800L;
    public static final long INVOCATION_EVENT_MASK = 0x1000L;
    public static final long HIERARCHY_EVENT_MASK = 0x2000L;
    public static final long HIERARCHY_BOUNDS_EVENT_MASK = 0x4000L;
    public static final long MOUSE_WHEEL_EVENT_MASK = 0x20000L;
    public static final long WINDOW_STATE_EVENT_MASK = 0x40000L;
    public static final long WINDOW_FOCUS_EVENT_MASK = 0x80000L;

    /** Reserved range for AWT event ids, matching the JDK. */
    public static final int RESERVED_ID_MAX = 1999;

    private int id;
    private boolean consumed;

    public AWTEvent(Object source, int id) {
        super(source);
        this.id = id;
    }

    public AWTEvent(int id) {
        this(null, id);
    }

    public int getID() {
        return id;
    }

    public void consume() {
        consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public String paramString() {
        return "id=" + id;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + paramString() + "] on " + getSource();
    }
}

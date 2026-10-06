package java.awt.event;

/**
 * AWT MouseEvent compatibility stub for Android runtime.
 */
public class MouseEvent extends InputEvent {
    public static final int MOUSE_CLICKED = 500;
    public static final int MOUSE_PRESSED = 501;
    public static final int MOUSE_RELEASED = 502;
    public static final int MOUSE_MOVED = 503;
    public static final int MOUSE_ENTERED = 504;
    public static final int MOUSE_EXITED = 505;
    public static final int MOUSE_DRAGGED = 506;
    public static final int MOUSE_WHEEL = 507;

    public static final int BUTTON1 = 1;
    /** Middle button — the client's camera-drag button (button code 4). */
    public static final int BUTTON2 = 2;
    public static final int BUTTON3 = 3;

    private final int id;
    private final int x;
    private final int y;
    private final int button;
    private final int clickCount;
    private final boolean popupTrigger;
    private final Object source;

    public MouseEvent(Object source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popupTrigger, int button) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = clickCount;
        this.popupTrigger = popupTrigger;
        this.source = source;
        this.when = when;
        this.modifiers = modifiers;
    }

    public Object getSource() { return source; }
    public int getID() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getButton() { return button; }
    public int getClickCount() { return clickCount; }
    public boolean isPopupTrigger() { return popupTrigger; }
}

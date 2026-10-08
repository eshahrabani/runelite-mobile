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

    /** No button (the JDK's value); MouseWheelEvent passes it to this constructor. */
    public static final int NOBUTTON = 0;
    public static final int BUTTON1 = 1;
    /** Middle button — the client's camera-drag button (button code 4). */
    public static final int BUTTON2 = 2;
    public static final int BUTTON3 = 3;

    private final int x;
    private final int y;
    private final int button;
    private final int clickCount;
    private final boolean popupTrigger;

    public MouseEvent(java.awt.Component source, int id, long when, int modifiers,
                      int x, int y, int clickCount, boolean popupTrigger, int button) {
        super(source, id, when, modifiers);
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = clickCount;
        this.popupTrigger = popupTrigger;
    }

    public java.awt.Component getComponent() { return (java.awt.Component) getSource(); }

    public java.awt.Point getPoint() { return new java.awt.Point(x, y); }

    public int getX() { return x; }

    public int getY() { return y; }

    public int getButton() { return button; }

    public int getClickCount() { return clickCount; }

    public boolean isPopupTrigger() { return popupTrigger; }

    /**
     * The port dispatches presses with {@code modifiers == 0} (a synthesized event has no
     * real modifier state), so the pressed button's down-mask is the only extended-state
     * bit the client can see — same as a desktop AWT press.
     */
    @Override
    public int getModifiersEx() {
        int mods = super.getModifiersEx();
        if (getID() == MOUSE_PRESSED || getID() == MOUSE_DRAGGED) {
            switch (button) {
                case BUTTON1:
                    mods |= BUTTON1_DOWN_MASK;
                    break;
                case BUTTON2:
                    mods |= BUTTON2_DOWN_MASK;
                    break;
                case BUTTON3:
                    mods |= BUTTON3_DOWN_MASK;
                    break;
                default:
                    break;
            }
        }
        return mods;
    }
}

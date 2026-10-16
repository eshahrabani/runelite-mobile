package java.awt;

/**
 * AWT {@code Cursor}. The port has no cursor sprite (touches, not a pointer), so every
 * cursor is the default one; {@code setCursor} calls are accepted and ignored, which is
 * what RuneLite's "hide the cursor over the canvas" code needs.
 */
public class Cursor {
    public static final int DEFAULT_CURSOR = 0;
    public static final int CROSSHAIR_CURSOR = 1;
    public static final int TEXT_CURSOR = 2;
    public static final int WAIT_CURSOR = 3;
    public static final int SW_RESIZE_CURSOR = 4;
    public static final int SE_RESIZE_CURSOR = 5;
    public static final int NW_RESIZE_CURSOR = 6;
    public static final int NE_RESIZE_CURSOR = 7;
    public static final int N_RESIZE_CURSOR = 8;
    public static final int S_RESIZE_CURSOR = 9;
    public static final int W_RESIZE_CURSOR = 10;
    public static final int E_RESIZE_CURSOR = 11;
    public static final int HAND_CURSOR = 12;
    public static final int MOVE_CURSOR = 13;

    /** The shared default cursor instance. */
    private static final Cursor DEFAULT = new Cursor(DEFAULT_CURSOR);

    protected int type;

    public Cursor(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static Cursor getPredefinedCursor(int type) {
        return type == DEFAULT_CURSOR ? DEFAULT : new Cursor(type);
    }

    public static Cursor getDefaultCursor() {
        return DEFAULT;
    }

    public static Cursor getSystemCustomCursor(String name) {
        return DEFAULT;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + type + "]";
    }
}

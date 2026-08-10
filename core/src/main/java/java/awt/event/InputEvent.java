package java.awt.event;

/**
 * AWT InputEvent compatibility stub for Android runtime.
 */
public class InputEvent {
    public static final int BUTTON1_DOWN_MASK = 1024;
    public static final int BUTTON2_DOWN_MASK = 2048;
    public static final int BUTTON3_DOWN_MASK = 4096;
    public static final int SHIFT_DOWN_MASK = 64;
    public static final int CTRL_DOWN_MASK = 128;
    public static final int META_DOWN_MASK = 256;
    public static final int ALT_DOWN_MASK = 512;

    protected long when;
    protected int modifiers;
    protected boolean consumed;

    public long getWhen() { return when; }
    public int getModifiers() { return modifiers; }

    public boolean isConsumed() { return consumed; }

    public void consume() { consumed = true; }

    public boolean isControlDown() { return (modifiers & CTRL_DOWN_MASK) != 0; }

    public boolean isAltDown() { return (modifiers & ALT_DOWN_MASK) != 0; }

    public boolean isShiftDown() { return (modifiers & SHIFT_DOWN_MASK) != 0; }

    public boolean isMetaDown() { return (modifiers & META_DOWN_MASK) != 0; }
}

package java.awt.event;

/**
 * AWT InputEvent compatibility stub for Android runtime.
 *
 * <p>Rebased on {@link java.awt.AWTEvent} (and through it {@code java.util.EventObject})
 * so the client's {@code getSource()}, {@code getID()}, {@code consume()},
 * {@code isConsumed()} and {@code paramString()} calls resolve on the real hierarchy
 * instead of being redeclared here or absent.
 */
public class InputEvent extends java.awt.AWTEvent {
    public static final int BUTTON1_DOWN_MASK = 1024;
    public static final int BUTTON2_DOWN_MASK = 2048;
    public static final int BUTTON3_DOWN_MASK = 4096;
    public static final int SHIFT_DOWN_MASK = 64;
    public static final int CTRL_DOWN_MASK = 128;
    public static final int META_DOWN_MASK = 256;
    public static final int ALT_DOWN_MASK = 512;

    protected long when;
    protected int modifiers;

    public InputEvent(Object source, int id, long when, int modifiers) {
        super(source, id);
        this.when = when;
        this.modifiers = modifiers;
    }

    public long getWhen() { return when; }

    public int getModifiers() { return modifiers; }

    public int getModifiersEx() { return modifiers; }

    /** {@code "Shift, Ctrl, Button1"} style text the client uses for its own messages. */
    public static String getModifiersExText(int modifiers) {
        StringBuilder sb = new StringBuilder();
        appendMask(sb, modifiers, SHIFT_DOWN_MASK, "Shift");
        appendMask(sb, modifiers, CTRL_DOWN_MASK, "Ctrl");
        appendMask(sb, modifiers, META_DOWN_MASK, "Meta");
        appendMask(sb, modifiers, ALT_DOWN_MASK, "Alt");
        appendMask(sb, modifiers, BUTTON1_DOWN_MASK, "Button1");
        appendMask(sb, modifiers, BUTTON2_DOWN_MASK, "Button2");
        appendMask(sb, modifiers, BUTTON3_DOWN_MASK, "Button3");
        return sb.toString();
    }

    private static void appendMask(StringBuilder sb, int modifiers, int mask, String name) {
        if ((modifiers & mask) == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(name);
    }

    public boolean isControlDown() { return (modifiers & CTRL_DOWN_MASK) != 0; }

    public boolean isAltDown() { return (modifiers & ALT_DOWN_MASK) != 0; }

    public boolean isShiftDown() { return (modifiers & SHIFT_DOWN_MASK) != 0; }

    public boolean isMetaDown() { return (modifiers & META_DOWN_MASK) != 0; }
}

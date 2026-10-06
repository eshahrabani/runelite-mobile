package java.awt.event;

/**
 * AWT KeyEvent compatibility stub for Android runtime.
 */
public class KeyEvent extends InputEvent {
    public static final int KEY_PRESSED = 401;
    public static final int KEY_RELEASED = 402;
    public static final int KEY_TYPED = 400;

    public static final int VK_BACK_SPACE = 8;
    public static final int VK_TAB = 9;
    public static final int VK_ENTER = 10;
    public static final int VK_CLEAR = 12;
    public static final int VK_SHIFT = 16;
    public static final int VK_CONTROL = 17;
    public static final int VK_ESCAPE = 27;
    public static final int VK_SPACE = 32;
    public static final int VK_DELETE = 127;

    private final int id;
    private final char keyChar;
    private final int keyCode;

    public KeyEvent(Object source, int id, long when, int modifiers, int keyCode, char keyChar) {
        this.id = id;
        this.when = when;
        this.modifiers = modifiers;
        this.keyCode = keyCode;
        this.keyChar = keyChar;
    }

    public int getID() { return id; }
    public int getKeyCode() { return keyCode; }
    public char getKeyChar() { return keyChar; }
}

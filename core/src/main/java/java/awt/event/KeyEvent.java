package java.awt.event;

/**
 * AWT KeyEvent compatibility stub for Android runtime.
 */
public class KeyEvent extends InputEvent {
    public static final int KEY_PRESSED = 401;
    public static final int KEY_RELEASED = 402;
    public static final int KEY_TYPED = 400;

    public static final int VK_ENTER = 10;
    public static final int VK_ESCAPE = 27;

    private final int id;
    private final char keyChar;
    private final int keyCode;

    public KeyEvent(Object source, int id, long when, int modifiers, int keyCode, char keyChar) {
        this.id = id;
        this.keyCode = keyCode;
        this.keyChar = keyChar;
    }

    public int getID() { return id; }
    public int getKeyCode() { return keyCode; }
    public char getKeyChar() { return keyChar; }
}

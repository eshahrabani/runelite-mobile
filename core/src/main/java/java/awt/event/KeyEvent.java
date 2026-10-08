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

    private int keyCode;
    private char keyChar;

    public KeyEvent(java.awt.Component source, int id, long when, int modifiers,
                    int keyCode, char keyChar) {
        super(source, id, when, modifiers);
        this.keyCode = keyCode;
        this.keyChar = keyChar;
    }

    public int getKeyCode() { return keyCode; }

    public char getKeyChar() { return keyChar; }

    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    public void setKeyChar(char keyChar) { this.keyChar = keyChar; }

    /** Without a native keyboard mapping the extended code is the key code itself. */
    public int getExtendedKeyCode() { return keyCode; }

    /** {@code "Enter"}, {@code "Space"}, … for the VK_* constants declared here. */
    public static String getKeyText(int keyCode) {
        switch (keyCode) {
            case VK_ENTER:
                return "Enter";
            case VK_ESCAPE:
                return "Escape";
            case VK_SPACE:
                return "Space";
            case VK_TAB:
                return "Tab";
            case VK_BACK_SPACE:
                return "Backspace";
            case VK_CLEAR:
                return "Clear";
            case VK_DELETE:
                return "Delete";
            case VK_SHIFT:
                return "Shift";
            case VK_CONTROL:
                return "Ctrl";
            default:
                return "Key " + keyCode;
        }
    }

    @Override
    public String paramString() {
        return super.paramString() + ",keyCode=" + keyCode + ",keyChar=" + keyChar;
    }
}

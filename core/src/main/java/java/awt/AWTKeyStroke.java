package java.awt;

import java.awt.event.KeyEvent;

/**
 * AWT {@code AWTKeyStroke}: an immutable key + modifier combination.
 *
 * <p>Real implementation, because {@code JComponent}'s InputMap/ActionMap key bindings
 * (Swing's Ctrl+Z etc.) and RuneLite's keyboard handling both key off equality of
 * strokes. Parsing follows the JDK's {@code "ctrl shift Z"} grammar.
 */
public class AWTKeyStroke implements java.io.Serializable {
    private static final long serialVersionUID = -6430539691155161871L;

    protected char keyChar;
    protected int keyCode;
    protected int modifiers;
    protected boolean onKeyRelease;

    protected AWTKeyStroke() {
        this.keyChar = KeyEvent.CHAR_UNDEFINED;
        this.keyCode = KeyEvent.VK_UNDEFINED;
        this.modifiers = 0;
        this.onKeyRelease = false;
    }

    protected AWTKeyStroke(char keyChar) {
        this(keyChar, KeyEvent.VK_UNDEFINED, 0, false);
    }

    protected AWTKeyStroke(char keyChar, int keyCode, int modifiers, boolean onKeyRelease) {
        this.keyChar = keyChar;
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        this.onKeyRelease = onKeyRelease;
    }

    public static AWTKeyStroke getAWTKeyStroke(char keyChar) {
        return new AWTKeyStroke(keyChar);
    }

    public static AWTKeyStroke getAWTKeyStroke(int keyCode, int modifiers) {
        return new AWTKeyStroke(KeyEvent.CHAR_UNDEFINED, keyCode, modifiers, false);
    }

    public static AWTKeyStroke getAWTKeyStroke(int keyCode, int modifiers, boolean onKeyRelease) {
        return new AWTKeyStroke(KeyEvent.CHAR_UNDEFINED, keyCode, modifiers, onKeyRelease);
    }

    public static AWTKeyStroke getAWTKeyStrokeForEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_TYPED) {
            return new AWTKeyStroke(event.getKeyChar());
        }
        return new AWTKeyStroke(KeyEvent.CHAR_UNDEFINED, event.getKeyCode(),
                event.getModifiersEx() & KeyEvent.KEY_MODIFIERS_MASK, event.getID() == KeyEvent.KEY_RELEASED);
    }

    /** Parses the JDK's textual form, e.g. {@code "control shift released Z"}. */
    public static AWTKeyStroke getAWTKeyStroke(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        int modifiers = 0;
        boolean release = false;
        String text = s;
        // Split the trailing key token from the modifier words.
        while (true) {
            int space = text.indexOf(' ');
            if (space < 0) {
                break;
            }
            String word = text.substring(0, space).toLowerCase();
            text = text.substring(space + 1).trim();
            switch (word) {
                case "shift":
                    modifiers |= KeyEvent.SHIFT_DOWN_MASK;
                    break;
                case "control":
                case "ctrl":
                    modifiers |= KeyEvent.CTRL_DOWN_MASK;
                    break;
                case "alt":
                    modifiers |= KeyEvent.ALT_DOWN_MASK;
                    break;
                case "meta":
                    modifiers |= KeyEvent.META_DOWN_MASK;
                    break;
                case "altgraph":
                    modifiers |= KeyEvent.ALT_GRAPH_DOWN_MASK;
                    break;
                case "released":
                    release = true;
                    break;
                case "pressed":
                    break;
                default:
                    return null;
            }
        }
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() == 1) {
            return new AWTKeyStroke(text.charAt(0), KeyEvent.VK_UNDEFINED, modifiers, release);
        }
        if (text.equalsIgnoreCase("typed")) {
            return null;
        }
        int keyCode = keyCodeOf(text);
        return keyCode == KeyEvent.VK_UNDEFINED ? null
                : new AWTKeyStroke(KeyEvent.CHAR_UNDEFINED, keyCode, modifiers, release);
    }

    private static int keyCodeOf(String name) {
        String n = name.toUpperCase();
        if (n.startsWith("VK_")) {
            n = n.substring(3);
        }
        if (n.length() == 1) {
            char c = Character.toUpperCase(n.charAt(0));
            if (c >= 'A' && c <= 'Z') {
                return KeyEvent.VK_A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return KeyEvent.VK_0 + (c - '0');
            }
        }
        switch (n) {
            case "ENTER":
                return KeyEvent.VK_ENTER;
            case "TAB":
                return KeyEvent.VK_TAB;
            case "SPACE":
                return KeyEvent.VK_SPACE;
            case "ESCAPE":
            case "ESC":
                return KeyEvent.VK_ESCAPE;
            case "BACK_SPACE":
            case "BACKSPACE":
                return KeyEvent.VK_BACK_SPACE;
            case "DELETE":
                return KeyEvent.VK_DELETE;
            case "UP":
                return KeyEvent.VK_UP;
            case "DOWN":
                return KeyEvent.VK_DOWN;
            case "LEFT":
                return KeyEvent.VK_LEFT;
            case "RIGHT":
                return KeyEvent.VK_RIGHT;
            case "HOME":
                return KeyEvent.VK_HOME;
            case "END":
                return KeyEvent.VK_END;
            case "PAGE_UP":
                return KeyEvent.VK_PAGE_UP;
            case "PAGE_DOWN":
                return KeyEvent.VK_PAGE_DOWN;
            case "SHIFT":
                return KeyEvent.VK_SHIFT;
            case "CONTROL":
            case "CTRL":
                return KeyEvent.VK_CONTROL;
            case "ALT":
                return KeyEvent.VK_ALT;
            case "F1":
            case "F2":
            case "F3":
            case "F4":
            case "F5":
            case "F6":
            case "F7":
            case "F8":
            case "F9":
            case "F10":
            case "F11":
            case "F12":
                return KeyEvent.VK_F1 + (n.charAt(1) - '1') + (n.length() > 2 ? 9 * (n.charAt(2) - '0') : 0);
            default:
                return KeyEvent.VK_UNDEFINED;
        }
    }

    public char getKeyChar() {
        return keyChar;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isOnKeyRelease() {
        return onKeyRelease;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AWTKeyStroke)) {
            return false;
        }
        AWTKeyStroke k = (AWTKeyStroke) o;
        return k.keyChar == keyChar && k.keyCode == keyCode && k.modifiers == modifiers
                && k.onKeyRelease == onKeyRelease;
    }

    @Override
    public int hashCode() {
        return (keyChar << 16) ^ keyCode ^ modifiers ^ (onKeyRelease ? 1 : 0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("shift ");
        }
        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("ctrl ");
        }
        if ((modifiers & KeyEvent.META_DOWN_MASK) != 0) {
            sb.append("meta ");
        }
        if ((modifiers & KeyEvent.ALT_DOWN_MASK) != 0) {
            sb.append("alt ");
        }
        if (onKeyRelease) {
            sb.append("released ");
        }
        if (keyCode != KeyEvent.VK_UNDEFINED) {
            sb.append(KeyEvent.getKeyText(keyCode));
        } else {
            sb.append(keyChar);
        }
        return sb.toString();
    }
}

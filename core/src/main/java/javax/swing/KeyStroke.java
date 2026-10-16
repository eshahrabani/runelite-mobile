package javax.swing;

import java.awt.AWTKeyStroke;
import java.awt.event.KeyEvent;

/**
 * Swing {@code KeyStroke}: the key description used as the key type of
 * {@link InputMap}/{@link ActionMap} bindings (menu accelerators, {@code Ctrl+Z}, …).
 */
public class KeyStroke extends AWTKeyStroke {
    private static final long serialVersionUID = -9060180771037902530L;

    private KeyStroke() {
    }

    private KeyStroke(char keyChar) {
        super(keyChar);
    }

    private KeyStroke(char keyChar, int keyCode, int modifiers, boolean onKeyRelease) {
        super(keyChar, keyCode, modifiers, onKeyRelease);
    }

    public static KeyStroke getKeyStroke(char keyChar) {
        return new KeyStroke(keyChar);
    }

    public static KeyStroke getKeyStroke(char keyChar, boolean onKeyRelease) {
        return new KeyStroke(keyChar, KeyEvent.VK_UNDEFINED, 0, onKeyRelease);
    }

    public static KeyStroke getKeyStroke(int keyCode, int modifiers) {
        return new KeyStroke(KeyEvent.CHAR_UNDEFINED, keyCode, modifiers, false);
    }

    public static KeyStroke getKeyStroke(int keyCode, int modifiers, boolean onKeyRelease) {
        return new KeyStroke(KeyEvent.CHAR_UNDEFINED, keyCode, modifiers, onKeyRelease);
    }

    public static KeyStroke getKeyStrokeForEvent(KeyEvent event) {
        AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStrokeForEvent(event);
        if (stroke == null) {
            return null;
        }
        return new KeyStroke(stroke.getKeyChar(), stroke.getKeyCode(), stroke.getModifiers(),
                stroke.isOnKeyRelease());
    }

    /** Parses the JDK's textual form; returns null when it cannot be parsed (as the JDK does). */
    public static KeyStroke getKeyStroke(String s) {
        AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStroke(s);
        if (stroke == null) {
            return null;
        }
        return new KeyStroke(stroke.getKeyChar(), stroke.getKeyCode(), stroke.getModifiers(),
                stroke.isOnKeyRelease());
    }
}

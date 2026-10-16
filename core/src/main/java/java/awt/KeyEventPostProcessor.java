package java.awt;

import java.awt.event.KeyEvent;

/** Post-focus key hook: sees keys no component consumed. */
public interface KeyEventPostProcessor {
    boolean postProcessKeyEvent(KeyEvent e);
}

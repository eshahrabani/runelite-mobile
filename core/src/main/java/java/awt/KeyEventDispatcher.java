package java.awt;

import java.awt.event.KeyEvent;

/** Pre-focus key hook: a dispatcher that returns true swallows the key event. */
public interface KeyEventDispatcher {
    boolean dispatchKeyEvent(KeyEvent e);
}

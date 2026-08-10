package javax.swing;

import java.awt.Component;
import java.awt.Frame;

/**
 * Swing JFrame compatibility stub for Android runtime.
 */
public class JFrame extends Frame {
    public static final int EXIT_ON_CLOSE = 3;

    public JFrame() {
        super();
    }

    public JFrame(String title) {
        super();
    }

    public Component add(Component comp) {
        return comp;
    }

    public void setDefaultCloseOperation(int operation) {}

    public void setVisible(boolean b) {}
}

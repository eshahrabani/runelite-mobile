package javax.swing;

import java.awt.Component;

/**
 * Swing JOptionPane compatibility stub for Android runtime.
 */
public class JOptionPane {
    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;

    public static void showMessageDialog(Component parentComponent, Object message, String title, int messageType) {
        System.err.println("JOptionPane.showMessageDialog called: [" + title + "] " + message);
    }
}

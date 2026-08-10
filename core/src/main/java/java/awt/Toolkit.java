package java.awt;

/**
 * AWT Toolkit compatibility stub for Android runtime.
 */
public class Toolkit {
    private static final Toolkit defaultToolkit = new Toolkit();

    private final java.awt.datatransfer.Clipboard systemClipboard = new java.awt.datatransfer.Clipboard("System");

    public static Toolkit getDefaultToolkit() {
        return defaultToolkit;
    }

    public java.awt.datatransfer.Clipboard getSystemClipboard() {
        return systemClipboard;
    }

    public EventQueue getSystemEventQueue() {
        return new EventQueue();
    }
}

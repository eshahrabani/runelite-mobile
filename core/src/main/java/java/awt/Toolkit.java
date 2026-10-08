package java.awt;

/**
 * AWT Toolkit compatibility stub for Android runtime.
 */
public class Toolkit {
    private static final Toolkit defaultToolkit = new Toolkit();

    private final java.awt.datatransfer.Clipboard systemClipboard = new java.awt.datatransfer.Clipboard("System");

    /**
     * Listeners registered for global AWT events. No AWT event stream exists on this
     * port, so they are retained but never invoked.
     */
    private final java.util.List<java.awt.event.AWTEventListener> awtEventListeners = new java.util.ArrayList<>();

    public static Toolkit getDefaultToolkit() {
        return defaultToolkit;
    }

    public java.awt.datatransfer.Clipboard getSystemClipboard() {
        return systemClipboard;
    }

    public EventQueue getSystemEventQueue() {
        return new EventQueue();
    }

    public void beep() {
        // ART has no system beep to ring on this port.
    }

    public void addAWTEventListener(java.awt.event.AWTEventListener listener, long eventMask) {
        // Retained for a future AWT event stream; none exists on this port, so the
        // listener is never invoked (DevToolsPlugin, the only caller, is excluded).
        if (listener != null) {
            awtEventListeners.add(listener);
        }
    }

    public void removeAWTEventListener(java.awt.event.AWTEventListener listener) {
        awtEventListeners.remove(listener);
    }
}

package java.awt;

import org.runelite.mobile.bridge.UiBridge;

/**
 * AWT {@code Toolkit}: the single toolkit of the port.
 *
 * <p>The screen size is the app's display size ({@code UiBridge.setScreenSize}) and there
 * are no screen insets, no custom cursors and no system beep — but the methods that Swing
 * and RuneLite call must answer with real values, because window centring, popup placement
 * and cursor-neutralising code all read them.
 */
public class Toolkit {
    private static final Toolkit DEFAULT = new Toolkit();

    private final EventQueue eventQueue = new EventQueue();
    private final java.awt.datatransfer.Clipboard systemClipboard =
            new java.awt.datatransfer.Clipboard("System");

    /** Listeners registered for global AWT events; there is no AWT event stream here. */
    private final java.util.List<java.awt.event.AWTEventListener> awtEventListeners = new java.util.ArrayList<>();

    public static Toolkit getDefaultToolkit() {
        return DEFAULT;
    }

    public java.awt.datatransfer.Clipboard getSystemClipboard() {
        return systemClipboard;
    }

    /** The port's single event queue, shared with the Swing dispatch thread. */
    public EventQueue getSystemEventQueue() {
        return eventQueue;
    }

    public Dimension getScreenSize() {
        return new Dimension(UiBridge.screenWidth(), UiBridge.screenHeight());
    }

    public Insets getScreenInsets(GraphicsConfiguration gc) {
        // The app letterboxes and insets the game surface itself; the Swing stage gets
        // the full window it is given.
        return new Insets(0, 0, 0, 0);
    }

    public int getScreenResolution() {
        return Math.round(UiBridge.density() * 160f);
    }

    public void beep() {
        // ART has no system beep to ring on this port.
    }

    public Cursor createCustomCursor(java.awt.Image cursor, Point hotSpot, String name) {
        // No cursor sprite exists on a touch device; the default cursor stands in.
        return Cursor.getDefaultCursor();
    }

    public Dimension getBestCursorSize(int preferredWidth, int preferredHeight) {
        return new Dimension(0, 0);
    }

    public int getMaximumCursorColors() {
        return 0;
    }

    public boolean isAlwaysOnTopSupported() {
        return false;
    }

    public void addAWTEventListener(java.awt.event.AWTEventListener listener, long eventMask) {
        if (listener != null) {
            awtEventListeners.add(listener);
        }
    }

    public void removeAWTEventListener(java.awt.event.AWTEventListener listener) {
        awtEventListeners.remove(listener);
    }

    public java.awt.event.AWTEventListener[] getAWTEventListeners() {
        return awtEventListeners.toArray(new java.awt.event.AWTEventListener[0]);
    }

    public Object getDesktopProperty(String key) {
        return null;
    }

    public void sync() {
    }
}

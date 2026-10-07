package javax.swing;

import java.awt.Component;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;

import org.runelite.mobile.bridge.AWTBridge;

/**
 * Swing thread/geometry helpers.
 *
 * <p>The port has no event dispatch thread: {@code MainActivity.onCreate} registers the
 * Android main thread as the UI thread with {@link AWTBridge}. RuneLite's contract is
 * that plugins start and stop on the EDT ({@code PluginManager.startPlugin} asserts it),
 * so {@link #invokeLater} and {@link #invokeAndWait} route to the main thread and
 * {@link #isEventDispatchThread} answers "am I on it". Every other method here is the
 * identity/no-op form of the desktop behaviour, because the port never lays out or
 * repaints Swing components.
 */
public class SwingUtilities {

    public static void invokeLater(Runnable doRun) {
        AWTBridge.post(doRun);
    }

    public static void invokeAndWait(Runnable doRun) throws InterruptedException {
        AWTBridge.invokeAndWait(doRun);
    }

    public static boolean isEventDispatchThread() {
        return AWTBridge.isUiThread();
    }

    public static boolean isLeftMouseButton(MouseEvent anEvent) {
        return anEvent != null && anEvent.getButton() == MouseEvent.BUTTON1;
    }

    public static boolean isMiddleMouseButton(MouseEvent anEvent) {
        return anEvent != null && anEvent.getButton() == MouseEvent.BUTTON2;
    }

    public static boolean isRightMouseButton(MouseEvent anEvent) {
        return anEvent != null && anEvent.getButton() == MouseEvent.BUTTON3;
    }

    public static boolean isMenuShortcutKeyDown(java.awt.event.InputEvent event) {
        return false;
    }

    /** Components live in one coordinate space on this port, so points pass through. */
    public static Point convertPoint(Component source, int x, int y, Component destination) {
        return new Point(x, y);
    }

    public static Point convertPoint(Component source, Point aPoint, Component destination) {
        return aPoint == null ? null : new Point(aPoint.x, aPoint.y);
    }

    public static void convertPointFromScreen(Point p, Component c) {
    }

    public static void convertPointToScreen(Point p, Component c) {
    }

    public static MouseEvent convertMouseEvent(Component source, MouseEvent sourceEvent, Component destination) {
        return sourceEvent;
    }

    /** No component tree exists; the caller only ever null-checks the result. */
    public static Window windowForComponent(Component c) {
        return null;
    }

    public static Window getWindowAncestor(Component c) {
        return null;
    }

    public static Component getRoot(Component c) {
        return c;
    }

    public static void updateComponentTreeUI(Component c) {
    }

    public static void updateComponentTreeUI(Window w) {
    }

    public static String layoutCompoundLabel(javax.swing.JLabel label, java.awt.FontMetrics fm, String text,
                                             javax.swing.Icon icon, int verticalAlignment, int horizontalAlignment,
                                             int verticalTextPosition, int horizontalTextPosition, java.awt.Rectangle viewR,
                                             java.awt.Rectangle iconR, java.awt.Rectangle textR, int gap) {
        return text;
    }

    public static void paintComponent(java.awt.Graphics g, Component c, java.awt.Container p, int x, int y, int w, int h) {
    }
}

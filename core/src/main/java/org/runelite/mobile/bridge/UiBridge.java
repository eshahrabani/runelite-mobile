package org.runelite.mobile.bridge;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JComponent;

/**
 * The core ↔ app contract for the Swing stage.
 *
 * <p>The core owns RuneLite's real Swing sidebar and its virtual windows; the app owns
 * the pixels. This class is the meeting point:
 * <ul>
 * <li>the app tells the core how big the stage is and in which scale it wants it
 *     ({@link #setSidebarBounds});</li>
 * <li>the app asks the core to paint into a device-sized ARGB array
 *     ({@link #paintSidebar}/{@link #paintWindow}, dispatch-thread only);</li>
 * <li>the core asks the app to blit that array ({@link Host#sidebarDirty()});</li>
 * <li>the app forwards touches and keys ({@link #dispatchMouse}/{@link #dispatchWheel}/
 *     {@link #dispatchKey}).</li>
 * </ul>
 *
 * <p><b>Coordinate spaces.</b> Both layers are painted in <em>logical</em> units and
 * scaled to the device by {@code scale}. Coordinates dispatched for the sidebar are
 * relative to the sidebar root's top-left; coordinates dispatched for a window are
 * relative to the window's top-left <em>including</em> the core-drawn title bar of
 * {@link #WINDOW_TITLE_HEIGHT} logical pixels. {@link #dispatchMouse} picks the layer
 * automatically: while a visible window exists, input belongs to it (that is also the
 * modality gate — a modal dialog swallows everything).
 *
 * <p>Every mutator and every paint runs on the Swing dispatch thread; the app-facing
 * entry points ({@link #dispatchMouse}/{@link #dispatchWheel}/{@link #dispatchKey},
 * {@link #setSidebarBounds}, {@link #paintSidebar}, {@link #paintWindow}) can be called
 * from any thread and marshal themselves.
 */
public final class UiBridge {

    /** Height of the title bar the core draws for a virtual window, in logical pixels. */
    public static final int WINDOW_TITLE_HEIGHT = 22;

    /** Background behind the sidebar root (the root paints over it when it is opaque). */
    private static final int STAGE_BACKGROUND = 0xFF282828;

    /** Debug: the biggest oversized opaque fills seen since the last report. */
    private static String oversizedSummary() {
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicInteger>> entries =
            new java.util.ArrayList<>(java.awt.Component.OVERSIZED_FILLS.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, entries.size()); i++) {
            sb.append(' ').append(entries.get(i).getKey()).append('=').append(entries.get(i).getValue());
        }
        java.awt.Component.OVERSIZED_FILLS.clear();
        return sb.toString();
    }

    /** Debug: how many component paints the last frame performed, by class. */
    private static String paintCallsSummary() {
        int total = 0;
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicInteger>> entries =
            new java.util.ArrayList<>(java.awt.Component.PAINT_CALLS.entrySet());
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicInteger> entry : entries) {
            total += entry.getValue().get();
        }
        entries.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder sb = new StringBuilder("paints=").append(total);
        for (int i = 0; i < Math.min(4, entries.size()); i++) {
            sb.append(' ').append(entries.get(i).getKey()).append('=').append(entries.get(i).getValue());
        }
        java.awt.Component.PAINT_CALLS.clear();
        return sb.toString();
    }

    /** Device profiling of the sidebar paint path; off in shipped builds. */
    private static final boolean DEBUG_PAINT = false;

    /**
     * True while a pointer event is being dispatched. The keyboard bar is only raised for a
     * focus change the <em>user</em> caused (tapping a text field): RuneLite's panels focus
     * their search field when they are activated, and on a touch device that would pop the
     * keyboard up by itself.
     */
    private static boolean pointerDispatch;

    /** Stage coordinates of the last press, used to tell a tap on a text field from a
     *  programmatic focus change that a panel performs while it is being activated. */
    private static int lastPressX;
    private static int lastPressY;

    /** Debug counter for the throttled paint profiling line. */
    private static int SIDEBAR_PAINTS;

    /** Implemented in the app dex (SwingSidebarView / SwingWindowLayer). */
    public interface Host {
        /** The sidebar needs to be re-rendered and blitted. */
        void sidebarDirty();

        /** The window layer needs to be re-rendered and blitted. */
        void windowDirty();

        /** A text component took or lost the focus: show/hide the keyboard bar. */
        void textInputRequested(boolean show);

        /** A one-line message for the user (tooltip or an unsupported-feature notice). */
        void toast(String message);

        /** RuneLite's own sidebar toggle was clicked: close the drawer. */
        void closeSidebarRequested();
    }

    /** Implemented by the asset-dex sidebar host (the {@code ClientUI} replacement). */
    public interface SidebarActivation {
        /** Activates/deactivates the selected panel (fires {@code Activatable}). */
        void sidebarVisibilityChanged(boolean visible);
    }

    private static volatile Host host;
    private static volatile SidebarActivation sidebarActivation;

    private static volatile int screenWidth = 1080;
    private static volatile int screenHeight = 1920;
    private static volatile float screenDensity = 1f;

    private static volatile JComponent sidebarRoot;
    private static volatile int sidebarLogicalW;
    private static volatile int sidebarLogicalH;
    private static volatile float sidebarScale = 1f;
    private static volatile boolean sidebarVisible;

    private static volatile int windowLogicalW;
    private static volatile int windowLogicalH;
    private static volatile float windowScale = 1f;

    /** The stage target of the most recent paint, reused by {@code paintImmediately}. */
    private static int[] stagePixels;
    private static int stageWidth;
    private static int stageHeight;
    private static float stageScale = 1f;
    private static boolean stageIsWindow;

    private static Component hoverTarget;
    private static final Set<String> REPORTED_UNSUPPORTED = ConcurrentHashMap.newKeySet();

    private UiBridge() {
    }

    public static void setHost(Host h) {
        host = h;
    }

    public static void setSidebarActivation(SidebarActivation activation) {
        sidebarActivation = activation;
    }

    public static void setScreenSize(int w, int h, float density) {
        screenWidth = w;
        screenHeight = h;
        screenDensity = density <= 0f ? 1f : density;
    }

    public static int screenWidth() {
        return screenWidth;
    }

    public static int screenHeight() {
        return screenHeight;
    }

    public static float density() {
        return screenDensity;
    }

    // ------------------------------------------------------------ sidebar

    /** Publishes the sidebar's component tree (called by the asset-dex ClientUI host). */
    public static void setSidebarRoot(JComponent root) {
        sidebarRoot = root;
        sidebarDirty();
    }

    public static JComponent sidebarRoot() {
        return sidebarRoot;
    }

    public static boolean hasSidebar() {
        return sidebarRoot != null;
    }

    public static boolean isSidebarRoot(Component c) {
        return c != null && c == sidebarRoot;
    }

    public static float sidebarScale() {
        return sidebarScale;
    }

    /** Stage geometry in logical units, as computed by the app from the drawer size. */
    public static void setSidebarBounds(int logicalW, int logicalH, float scale) {
        if (logicalW <= 0 || logicalH <= 0 || scale <= 0f) {
            return;
        }
        boolean changed = logicalW != sidebarLogicalW || logicalH != sidebarLogicalH
                || scale != sidebarScale;
        sidebarLogicalW = logicalW;
        sidebarLogicalH = logicalH;
        sidebarScale = scale;
        javax.swing.UiScale.setScale(scale);
        if (changed) {
            JComponent root = sidebarRoot;
            if (root != null) {
                root.setSize(logicalW, logicalH);
                root.invalidate();
            }
            sidebarDirty();
        }
    }

    public static int sidebarLogicalWidth() {
        return sidebarLogicalW;
    }

    public static int sidebarLogicalHeight() {
        return sidebarLogicalH;
    }

    public static boolean isSidebarVisible() {
        return sidebarVisible;
    }

    /** Opens/closes the drawer: activates or deactivates the selected plugin panel. */
    public static void setSidebarVisible(boolean visible) {
        Runnable task = () -> {
            sidebarVisible = visible;
            SidebarActivation activation = sidebarActivation;
            if (activation != null) {
                try {
                    activation.sidebarVisibilityChanged(visible);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            sidebarDirty();
        };
        if (SwingEdt.isDispatchThread()) {
            task.run();
        } else {
            SwingEdt.post(task);
        }
    }

    /** Paints the sidebar (and its popups) into {@code argb}; dispatch thread only. */
    public static void paintSidebar(final int[] argb, final int deviceW, final int deviceH) {
        if (argb == null || deviceW <= 0 || deviceH <= 0) {
            return;
        }
        if (!SwingEdt.isDispatchThread()) {
            SwingEdt.invokeAndWait(() -> paintSidebar(argb, deviceW, deviceH));
            return;
        }
        stagePixels = argb;
        stageWidth = deviceW;
        stageHeight = deviceH;
        stageScale = sidebarScale;
        stageIsWindow = false;
        java.util.Arrays.fill(argb, 0, Math.min(argb.length, deviceW * deviceH), STAGE_BACKGROUND);
        JComponent root = sidebarRoot;
        if (root == null) {
            PopupLayer.paint(new java.awt.Graphics2D(argb, deviceW, deviceH));
            return;
        }
        int w = sidebarLogicalW > 0 ? sidebarLogicalW : root.getWidth();
        int h = sidebarLogicalH > 0 ? sidebarLogicalH : root.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (root.getWidth() != w || root.getHeight() != h) {
            root.setSize(w, h);
        }
        long t0 = System.nanoTime();
        java.awt.Graphics2D g = new java.awt.Graphics2D(argb, deviceW, deviceH);
        g.scale(sidebarScale, sidebarScale);
        g.clipRect(0, 0, w, h);
        long t1 = System.nanoTime();
        try {
            root.validate();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        long t2 = System.nanoTime();
        try {
            root.paint(g);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        long t3a = System.nanoTime();
        PopupLayer.paint(g);
        long t3 = System.nanoTime();
        if (!DEBUG_PAINT) {
            return;
        }
        SIDEBAR_PAINTS++;
        // Throttled device profiling: how often the stage paints, what it costs and which
        // component keeps asking for it (a self-sustaining repaint loop shows up here).
        if (SIDEBAR_PAINTS <= 40 || SIDEBAR_PAINTS % 30 == 0) {
            long filled = java.awt.Graphics.FILLED_PIXELS.getAndSet(0);
            System.err.println("sidebar paint #" + SIDEBAR_PAINTS
                + " setup=" + (t1 - t0) / 1000000 + "ms validate=" + (t2 - t1) / 1000000
                + "ms paint=" + (t3a - t2) / 1000000 + "ms popups=" + (t3 - t3a) / 1000000
                + "ms filled=" + filled + " oversized=" + oversizedSummary()
                + " " + paintCallsSummary()
                + " " + javax.swing.RepaintManager.dirtySummary(4));
            javax.swing.RepaintManager.resetDirtyStats();
        }
    }

    /** Marks the sidebar layer dirty via the host (thread-safe). */
    public static void sidebarDirty() {
        Host h = host;
        if (h != null) {
            h.sidebarDirty();
        }
    }

    // ------------------------------------------------------------ windows

    public static void registerWindow(Window window) {
        windowDirty();
    }

    public static void unregisterWindow(Window window) {
        windowDirty();
    }

    public static void windowDirty() {
        Host h = host;
        if (h != null) {
            h.windowDirty();
        }
    }

    public static boolean hasVisibleWindow() {
        return Window.topMostWindow() != null;
    }

    /** Size of the top-most window including the core-drawn title bar, or null. */
    public static int[] windowLogicalSize() {
        Window w = Window.topMostWindow();
        if (w == null) {
            return null;
        }
        int cw = Math.max(1, w.getWidth());
        int ch = Math.max(1, w.getHeight());
        return new int[] {cw, ch + WINDOW_TITLE_HEIGHT};
    }

    /** Scale the app renders the window layer at. */
    public static void setWindowBounds(int logicalW, int logicalH, float scale) {
        if (logicalW <= 0 || logicalH <= 0 || scale <= 0f) {
            return;
        }
        windowLogicalW = logicalW;
        windowLogicalH = logicalH;
        windowScale = scale;
    }

    public static float windowScale() {
        return windowScale;
    }

    /** Paints the top-most window (title bar, content, popups); dispatch thread only. */
    public static void paintWindow(final int[] argb, final int deviceW, final int deviceH) {
        if (argb == null || deviceW <= 0 || deviceH <= 0) {
            return;
        }
        if (!SwingEdt.isDispatchThread()) {
            SwingEdt.invokeAndWait(() -> paintWindow(argb, deviceW, deviceH));
            return;
        }
        stagePixels = argb;
        stageWidth = deviceW;
        stageHeight = deviceH;
        stageScale = windowScale;
        stageIsWindow = true;
        java.util.Arrays.fill(argb, 0, Math.min(argb.length, deviceW * deviceH), 0);
        Window w = Window.topMostWindow();
        if (w == null) {
            return;
        }
        int cw = Math.max(1, w.getWidth());
        int ch = Math.max(1, w.getHeight());
        java.awt.Graphics2D g = new java.awt.Graphics2D(argb, deviceW, deviceH);
        g.scale(windowScale, windowScale);
        g.clipRect(0, 0, cw, ch + WINDOW_TITLE_HEIGHT);
        // Frame background, then the content, then the title bar on top.
        g.setColor(new java.awt.Color(0xFF1E1E1E));
        g.fillRect(0, 0, cw, ch + WINDOW_TITLE_HEIGHT);
        javax.swing.JRootPane rootPane = w.getRootPane();
        if (rootPane != null) {
            if (rootPane.getWidth() != cw || rootPane.getHeight() != ch) {
                rootPane.setSize(cw, ch);
            }
            java.awt.Graphics2D content = (java.awt.Graphics2D) g.create();
            content.translate(0, WINDOW_TITLE_HEIGHT);
            content.clipRect(0, 0, cw, ch);
            paintTree(rootPane, content);
            content.dispose();
        }
        paintTitleBar(g, w, cw);
        PopupLayer.paint(g);
    }

    private static void paintTitleBar(java.awt.Graphics2D g, Window w, int width) {
        java.awt.Color bar = javax.swing.UIManager.getColor("MenuBar.background");
        g.setColor(bar != null ? bar : new java.awt.Color(0xFF3C3C3C));
        g.fillRect(0, 0, width, WINDOW_TITLE_HEIGHT);
        g.setColor(new java.awt.Color(0xFF171717));
        g.fillRect(0, WINDOW_TITLE_HEIGHT - 1, width, 1);
        String title = w.getTitle();
        if (title == null || title.isEmpty()) {
            title = w.getClass().getSimpleName();
        }
        java.awt.Font font = javax.swing.UIManager.getFont("TitlePane.font");
        if (font == null) {
            font = new java.awt.Font("Dialog", java.awt.Font.BOLD, 12);
        }
        g.setFont(font);
        g.setColor(new java.awt.Color(0xFFC6C6C6));
        g.drawString(title, 6, WINDOW_TITLE_HEIGHT - 7);
        // Close glyph: an x in the right corner.
        int cx = width - WINDOW_TITLE_HEIGHT + 6;
        int cy = 6;
        int size = WINDOW_TITLE_HEIGHT - 12;
        g.setColor(new java.awt.Color(0xFFC6C6C6));
        g.drawLine(cx, cy, cx + size, cy + size);
        g.drawLine(cx + size, cy, cx, cy + size);
    }

    // ------------------------------------------------------------ painting

    /** Validates (once) and paints a stage root's subtree. */
    private static void paintTree(Component root, java.awt.Graphics2D g) {
        try {
            if (root instanceof Container) {
                ((Container) root).validate();
            }
            root.paint(g);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Immediate repaint of a component's rectangle into the stage target of the last
     * paint (the JDK's {@code paintImmediately}); used by the colour picker while dragging.
     */
    public static void paintImmediately(JComponent c, int x, int y, int w, int h) {
        if (c == null || w <= 0 || h <= 0) {
            return;
        }
        if (!SwingEdt.isDispatchThread()) {
            SwingEdt.post(() -> paintImmediately(c, x, y, w, h));
            return;
        }
        if (stagePixels == null) {
            // Nothing painted yet: a normal dirty mark will do.
            c.repaint();
            return;
        }
        Point origin = c.getLocationOnScreen();
        java.awt.Graphics2D g = new java.awt.Graphics2D(stagePixels, stageWidth, stageHeight);
        g.scale(stageScale, stageScale);
        g.clipRect(origin.x + x, origin.y + y, w, h);
        java.awt.Graphics2D gc = (java.awt.Graphics2D) g.create();
        gc.translate(origin.x, origin.y);
        gc.clipRect(x, y, w, h);
        try {
            c.paint(gc);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        gc.dispose();
        if (stageIsWindow) {
            windowDirty();
        } else {
            sidebarDirty();
        }
    }

    // ------------------------------------------------------------ input

    public static void dispatchMouse(final int x, final int y, final int id, final int button,
                                     final int clickCount, final int modifiers, final long when) {
        SwingEdt.post(() -> dispatchMouseOnDispatchThread(x, y, id, button, clickCount, modifiers, when));
    }

    private static void dispatchMouseOnDispatchThread(int x, int y, int id, int button,
                                                      int clickCount, int modifiers, long when) {
        noteMousePosition(x, y);
        java.awt.MouseInfo.setPointerLocation(x, y);
        Window window = Window.topMostWindow();
        boolean windowLayer = window != null;
        // The title bar belongs to the window chrome, not to its content.
        if (windowLayer && y < WINDOW_TITLE_HEIGHT) {
            if (id == MouseEvent.MOUSE_CLICKED && x >= window.getWidth() - WINDOW_TITLE_HEIGHT) {
                window.closeRequested();
            }
            return;
        }
        Component target = hitTest(x, y, windowLayer);
        if (target == null) {
            return;
        }
        if (id == MouseEvent.MOUSE_PRESSED) {
            lastPressX = x;
            lastPressY = y;
        }
        pointerDispatch = true;
        try {
            dispatchMouseAt(target, x, y, windowLayer, id, button, clickCount, modifiers, when);
        } finally {
            pointerDispatch = false;
        }
    }

    private static void dispatchMouseAt(Component target, int x, int y, boolean windowLayer,
                                        int id, int button, int clickCount, int modifiers, long when) {
        Point p = pointIn(target, x, y, windowLayer);
        boolean popupTrigger = button == MouseEvent.BUTTON3
                && (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_CLICKED);
        MouseEvent e = new MouseEvent(target, id, when, modifiers, p.x, p.y, clickCount, popupTrigger, button);
        updateHover(target, p, when, modifiers, id);
        try {
            target.dispatchEvent(e);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void dispatchWheel(final int x, final int y, final int wheelRotation,
                                     final int clickCount, final int modifiers, final long when) {
        SwingEdt.post(() -> {
            noteMousePosition(x, y);
            java.awt.MouseInfo.setPointerLocation(x, y);
            Window window = Window.topMostWindow();
            boolean windowLayer = window != null;
            Component target = hitTest(x, y, windowLayer);
            if (target == null) {
                return;
            }
            Point p = pointIn(target, x, y, windowLayer);
            MouseWheelEvent e = new MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, when, modifiers,
                    p.x, p.y, clickCount, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, wheelRotation);
            try {
                target.dispatchEvent(e);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static void dispatchKey(final int id, final int keyCode, final char keyChar,
                                   final int modifiers, final long when) {
        SwingEdt.post(() -> {
            java.awt.Component focus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focus == null) {
                focus = stageRoot();
            }
            if (focus == null) {
                return;
            }
            KeyEvent e = new KeyEvent(focus, id, when, modifiers, keyCode, keyChar);
            try {
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(e);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private static Component stageRoot() {
        Window w = Window.topMostWindow();
        if (w != null) {
            return w.getRootPane() != null ? w.getRootPane() : w;
        }
        return sidebarRoot;
    }

    /** The deepest component under the given stage point (popups first). */
    private static Component hitTest(int x, int y, boolean windowLayer) {
        PopupLayer.Entry popup = PopupLayer.topMostAt(x, y);
        if (popup != null) {
            if (popup.component instanceof Container) {
                return pointIn((Container) popup.component, x - popup.x, y - popup.y, windowLayer);
            }
            return popup.component;
        }
        Container root;
        int rx = x;
        int ry = y;
        if (windowLayer) {
            Window w = Window.topMostWindow();
            if (w == null) {
                return null;
            }
            root = w.getRootPane();
            if (root == null) {
                return null;
            }
            ry = y - WINDOW_TITLE_HEIGHT;
            if (ry < 0) {
                return root;
            }
        } else {
            root = sidebarRoot;
        }
        if (root == null) {
            return null;
        }
        return pointIn(root, rx, ry, windowLayer);
    }

    /** The deepest visible component at a point given in {@code root}'s coordinates. */
    private static Component pointIn(Container root, int x, int y, boolean windowLayer) {
        Component hit = root.locate(x, y);
        return hit == null ? root : hit;
    }

    /** Converts a stage point into the component's own coordinate space. */
    private static Point pointIn(Component target, int stageX, int stageY, boolean windowLayer) {
        Point origin = target.getLocationOnScreen();
        int offsetY = windowLayer ? WINDOW_TITLE_HEIGHT : 0;
        return new Point(stageX - origin.x, stageY - offsetY - origin.y);
    }

    /** Sends ENTERED/EXITED to the components the pointer left or entered. */
    private static void updateHover(Component target, Point p, long when, int modifiers, int id) {
        if (hoverTarget == target) {
            return;
        }
        Component old = hoverTarget;
        hoverTarget = target;
        if (old != null && inChain(target, old) == false) {
            for (Component c = old; c != null && !inChain(target, c); c = c.getParent()) {
                dispatchEnterExit(c, MouseEvent.MOUSE_EXITED, when, modifiers);
            }
        }
        List<Component> entered = new ArrayList<>();
        for (Component c = target; c != null && !inChain(old, c); c = c.getParent()) {
            entered.add(c);
        }
        for (int i = entered.size() - 1; i >= 0; i--) {
            dispatchEnterExit(entered.get(i), MouseEvent.MOUSE_ENTERED, when, modifiers);
        }
    }

    private static boolean inChain(Component maybeAncestor, Component c) {
        if (maybeAncestor == null || c == null) {
            return false;
        }
        for (Component p = c; p != null; p = p.getParent()) {
            if (p == maybeAncestor) {
                return true;
            }
        }
        return false;
    }

    private static void dispatchEnterExit(Component c, int id, long when, int modifiers) {
        Point p = pointIn(c, mouseStageX, mouseStageY, Window.topMostWindow() != null);
        c.dispatchEvent(new MouseEvent(c, id, when, modifiers, p.x, p.y, 0, false, MouseEvent.NOBUTTON));
    }

    private static int mouseStageX;
    private static int mouseStageY;

    // ------------------------------------------------------------ helpers

    /** The pointer position in the given component's coordinates, or null. */
    public static Point mousePositionIn(Component c) {
        Window w = Window.topMostWindow();
        boolean windowLayer = w != null;
        Point inStage = new Point(mouseStageX, mouseStageY);
        Point origin = c.getLocationOnScreen();
        return new Point(inStage.x - origin.x,
                inStage.y - (windowLayer ? WINDOW_TITLE_HEIGHT : 0) - origin.y);
    }

    /** Remembers the pointer in stage coordinates; {@link #mousePositionIn} uses it. */
    public static void noteMousePosition(int x, int y) {
        mouseStageX = x;
        mouseStageY = y;
    }

    /** The tooltip text at a sidebar stage point, or null. */
    public static String tooltipAt(int x, int y) {
        Window w = Window.topMostWindow();
        boolean windowLayer = w != null;
        Component target = hitTest(x, y, windowLayer);
        if (target == null) {
            return null;
        }
        Point p = pointIn(target, x, y, windowLayer);
        MouseEvent event = new MouseEvent(target, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
                0, p.x, p.y, 0, false, MouseEvent.NOBUTTON);
        for (Component c = target; c != null; c = c.getParent()) {
            if (c instanceof JComponent) {
                String tip = ((JComponent) c).getToolTipText(event);
                if (tip != null && !tip.isEmpty()) {
                    return tip;
                }
            }
        }
        return null;
    }

    /**
     * True when the component under the given stage point is inside a scroll pane whose view
     * is larger than its viewport — the app turns a one-finger drag into wheel notches there
     * instead of into a mouse drag (a touch device has no scroll wheel, and RuneLite's panels
     * do not use drag gestures).
     */
    public static boolean isScrollableAt(int x, int y) {
        Window w = Window.topMostWindow();
        Component target = hitTest(x, y, w != null);
        for (Component c = target; c != null; c = c.getParent()) {
            if (c instanceof javax.swing.JTabbedPane) {
                javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) c;
                if (tabs.isStripScrollable()) {
                    return true;
                }
            }
            if (c instanceof javax.swing.JViewport) {
                javax.swing.JViewport viewport = (javax.swing.JViewport) c;
                Component view = viewport.getView();
                if (view != null) {
                    return view.getHeight() > viewport.getHeight() + 1
                        || view.getWidth() > viewport.getWidth() + 1;
                }
            }
        }
        return false;
    }

    /** True when the focus owner is a text component (the keyboard bar types into it). */
    public static boolean focusOwnerIsTextInput() {
        Component owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner instanceof javax.swing.text.JTextComponent;
    }

    /**
     * Called by the focus manager when the focus owner changes: the keyboard bar appears
     * only when the focus moved to a text component from a pointer press (a tap), and
     * disappears when the focus leaves one. Programmatic focus (a panel focusing its search
     * field on activation) no longer raises the keyboard.
     */
    public static void focusOwnerChanged(Component oldOwner, Component newOwner) {
        Host h = host;
        if (h == null) {
            return;
        }
        boolean textNow = newOwner instanceof javax.swing.text.JTextComponent;
        boolean textBefore = oldOwner instanceof javax.swing.text.JTextComponent;
        if (textNow && !textBefore) {
            h.textInputRequested(pointerDispatch && pointLiesOn(newOwner, lastPressX, lastPressY));
        } else if (!textNow && textBefore) {
            h.textInputRequested(false);
        }
    }

    /** Whether the last pointer press landed on (inside) the given component. */
    private static boolean pointLiesOn(Component component, int stageX, int stageY) {
        if (component == null || component.getWidth() <= 0 || component.getHeight() <= 0) {
            return false;
        }
        Point origin = component.getLocationOnScreen();
        int offsetY = Window.topMostWindow() != null ? WINDOW_TITLE_HEIGHT : 0;
        return stageX >= origin.x && stageY >= origin.y + offsetY
            && stageX < origin.x + component.getWidth()
            && stageY < origin.y + offsetY + component.getHeight();
    }

    /** True while {@link #dispatchMouse} is delivering an event. */
    public static boolean isPointerDispatch() {
        return pointerDispatch;
    }

    /** Notifies the app that the focus moved to/from a text component. */
    public static void textInputFocusChanged() {
        Host h = host;
        if (h != null) {
            h.textInputRequested(pointerDispatch && focusOwnerIsTextInput());
        }
    }

    public static void postToast(String message) {
        if (message == null) {
            return;
        }
        Host h = host;
        if (h != null) {
            h.toast(message);
        }
    }

    public static void closeSidebarRequested() {
        Host h = host;
        if (h != null) {
            h.closeSidebarRequested();
        }
    }

    /** Logs once per feature and tells the user the feature is not available here. */
    public static void notifyUnsupported(String feature) {
        if (feature == null || !REPORTED_UNSUPPORTED.add(feature)) {
            return;
        }
        System.err.println("Unsupported on this port: " + feature);
        postToast("not supported on mobile: " + feature);
    }

    public static Set<String> unsupportedFeatures() {
        return java.util.Collections.unmodifiableSet(REPORTED_UNSUPPORTED);
    }
}

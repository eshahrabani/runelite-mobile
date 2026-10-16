package java.awt;

import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;

/**
 * AWT {@code Window} on this port: a <em>virtual</em> window.
 *
 * <p>There is no native window manager, so a visible window is painted by the app into
 * the stage bitmap ({@code UiBridge.paintWindow}) and its input is routed back into the
 * component tree. The class still owns the parts of the JDK contract the Swing shell
 * uses: a {@link JRootPane} hierarchy ({@link RootPaneContainer}), an owner, a size and
 * location, visibility, {@code toFront}/{@code dispose}, and the window listener sets.
 *
 * <p>{@code setVisible(true)} registers the window with
 * {@link org.runelite.mobile.bridge.UiBridge}, which is what makes the app show the
 * window layer; {@code dispose()} unregisters it.
 */
public class Window extends Container implements RootPaneContainer {
    /** Windows in show order; the last one is the top-most (see UiBridge). */
    private static final List<Window> OPEN_WINDOWS = new ArrayList<>();

    private final Window owner;
    private JRootPane rootPane;
    private Container contentPane;
    private Component glassPane;
    private JLayeredPane layeredPane;
    private boolean undecorated;
    private boolean resizable = true;
    private boolean alwaysOnTop;
    private float opacity = 1f;
    private String title = "";
    private int defaultCloseOperation = javax.swing.WindowConstants.DISPOSE_ON_CLOSE;
    private int state = 0;
    private boolean disposable;
    private java.awt.image.BufferedImage iconImage;
    private final List<WindowListener> windowListeners = new ArrayList<>();
    private final List<WindowStateListener> windowStateListeners = new ArrayList<>();
    private final List<WindowFocusListener> windowFocusListeners = new ArrayList<>();

    public Window() {
        this(null);
    }

    public Window(Window owner) {
        this.owner = owner;
        this.rootPane = new JRootPane();
        this.layeredPane = rootPane.getLayeredPane();
        this.contentPane = rootPane.getContentPane();
        this.glassPane = rootPane.getGlassPane();
        this.layeredPane.setBounds(0, 0, 0, 0);
        super.add(rootPane);
    }

    /** The window that owns this one, or null for a top-level window. */
    public Window getOwner() {
        return owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    // ------------------------------------------------------- RootPaneContainer

    @Override
    public JRootPane getRootPane() {
        return rootPane;
    }

    public Container getContentPane() {
        return contentPane;
    }

    public void setContentPane(Container contentPane) {
        if (contentPane == null) {
            throw new IllegalArgumentException("contentPane must not be null");
        }
        this.contentPane = contentPane;
        rootPane.setContentPane(contentPane);
    }

    public Component getGlassPane() {
        return glassPane;
    }

    public void setGlassPane(Component glassPane) {
        this.glassPane = glassPane;
        rootPane.setGlassPane(glassPane);
    }

    public JLayeredPane getLayeredPane() {
        return layeredPane;
    }

    public void setLayeredPane(JLayeredPane layeredPane) {
        this.layeredPane = layeredPane;
        rootPane.setLayeredPane(layeredPane);
    }

    public void setJMenuBar(javax.swing.JMenuBar menu) {
        rootPane.setJMenuBar(menu);
    }

    public javax.swing.JMenuBar getJMenuBar() {
        return rootPane.getJMenuBar();
    }

    // ------------------------------------------------------------- geometry

    public void pack() {
        Dimension pref = rootPane.getPreferredSize();
        if (pref == null || pref.width <= 0 || pref.height <= 0) {
            pref = new Dimension(200, 100);
        }
        setSize(pref.width + insulationWidth(), pref.height + insulationHeight());
        validate();
    }

    private int insulationWidth() {
        return 0;
    }

    private int insulationHeight() {
        return 0;
    }

    /**
     * JDK semantics: centre on the owner when there is one (clamped to the screen),
     * otherwise centre on the screen.
     */
    public void setLocationRelativeTo(Component c) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = getWidth() > 0 ? getWidth() : 200;
        int h = getHeight() > 0 ? getHeight() : 100;
        if (c == null) {
            setLocation((screen.width - w) / 2, (screen.height - h) / 2);
            return;
        }
        Point ownerLoc = c.getLocationOnScreen();
        int x = ownerLoc.x + (c.getWidth() - w) / 2;
        int y = ownerLoc.y + (c.getHeight() - h) / 2;
        x = Math.max(0, Math.min(x, Math.max(0, screen.width - w)));
        y = Math.max(0, Math.min(y, Math.max(0, screen.height - h)));
        setLocation(x, y);
    }

    // -------------------------------------------------------------- visibility

    @Override
    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            // Still make sure the window layer knows about it (first show of a window
            // created visible by a subclass before registration).
            if (visible && !OPEN_WINDOWS.contains(this)) {
                register();
            }
            return;
        }
        super.setVisible(visible);
        if (visible) {
            valid = false;
            validate();
            fireWindowEvent(WindowEvent.WINDOW_OPENED);
            fireWindowEvent(WindowEvent.WINDOW_ACTIVATED);
            List<Window> open = OPEN_WINDOWS;
            synchronized (open) {
                open.remove(this);
                open.add(this);
            }
            register();
            requestFocusIfPossible();
        } else {
            fireWindowEvent(WindowEvent.WINDOW_DEACTIVATED);
            fireWindowEvent(WindowEvent.WINDOW_CLOSING);
            synchronized (OPEN_WINDOWS) {
                OPEN_WINDOWS.remove(this);
            }
            org.runelite.mobile.bridge.UiBridge.windowDirty();
        }
    }

    private void register() {
        org.runelite.mobile.bridge.UiBridge.registerWindow(this);
    }

    private void requestFocusIfPossible() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().setActiveWindow(this);
    }

    public void toFront() {
        synchronized (OPEN_WINDOWS) {
            OPEN_WINDOWS.remove(this);
            OPEN_WINDOWS.add(this);
        }
        fireWindowEvent(WindowEvent.WINDOW_ACTIVATED);
        org.runelite.mobile.bridge.UiBridge.windowDirty();
    }

    public void dispose() {
        setVisible(false);
        synchronized (OPEN_WINDOWS) {
            OPEN_WINDOWS.remove(this);
        }
        org.runelite.mobile.bridge.UiBridge.unregisterWindow(this);
        fireWindowEvent(WindowEvent.WINDOW_CLOSED);
    }

    /** All currently shown windows, bottom-most first. */
    public static Window[] getWindows() {
        synchronized (OPEN_WINDOWS) {
            return OPEN_WINDOWS.toArray(new Window[0]);
        }
    }

    /** The top-most visible window, or null. */
    public static Window topMostWindow() {
        synchronized (OPEN_WINDOWS) {
            for (int i = OPEN_WINDOWS.size() - 1; i >= 0; i--) {
                Window w = OPEN_WINDOWS.get(i);
                if (w.isVisible() && w.getWidth() > 0 && w.getHeight() > 0) {
                    return w;
                }
            }
        }
        return null;
    }

    public boolean isAlwaysOnTop() {
        return alwaysOnTop;
    }

    public void setAlwaysOnTop(boolean alwaysOnTop) {
        this.alwaysOnTop = alwaysOnTop;
    }

    public boolean isAlwaysOnTopSupported() {
        // No window manager: nothing can be raised above anything.
        return false;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        if (opacity < 0f || opacity > 1f) {
            throw new IllegalArgumentException("opacity must be in [0,1]");
        }
        this.opacity = opacity;
    }

    public boolean isUndecorated() {
        return undecorated;
    }

    public void setUndecorated(boolean undecorated) {
        this.undecorated = undecorated;
    }

    public boolean isResizable() {
        return resizable;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    public void setDefaultCloseOperation(int operation) {
        this.defaultCloseOperation = operation;
    }

    public int getExtendedState() {
        return state;
    }

    public void setExtendedState(int state) {
        this.state = state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public void setIconImage(java.awt.Image image) {
        if (image instanceof java.awt.image.BufferedImage) {
            this.iconImage = (java.awt.image.BufferedImage) image;
        }
    }

    public void setIconImages(List<? extends java.awt.Image> icons) {
        if (icons != null && !icons.isEmpty() && icons.get(0) instanceof java.awt.image.BufferedImage) {
            this.iconImage = (java.awt.image.BufferedImage) icons.get(0);
        }
    }

    // -------------------------------------------------------------- listeners

    public synchronized void addWindowListener(WindowListener l) {
        windowListeners.add(l);
    }

    public synchronized void removeWindowListener(WindowListener l) {
        windowListeners.remove(l);
    }

    public synchronized WindowListener[] getWindowListeners() {
        return windowListeners.toArray(new WindowListener[0]);
    }

    public synchronized void addWindowStateListener(WindowStateListener l) {
        windowStateListeners.add(l);
    }

    public synchronized void removeWindowStateListener(WindowStateListener l) {
        windowStateListeners.remove(l);
    }

    public void addWindowFocusListener(WindowFocusListener l) {
        synchronized (windowFocusListeners) {
            windowFocusListeners.add(l);
        }
    }

    public void removeWindowFocusListener(WindowFocusListener l) {
        synchronized (windowFocusListeners) {
            windowFocusListeners.remove(l);
        }
    }

    /**
     * Delivers a window event to the listeners the port's window layer cares about.
     * A {@code WINDOW_CLOSING} with no listener closes the window the way the default
     * close operation says (this is what makes the title-bar close glyph work).
     */
    public void dispatchWindowEvent(WindowEvent e) {
        processWindowEvent(e);
    }

    protected void processWindowEvent(WindowEvent e) {
        WindowListener[] listeners;
        synchronized (this) {
            listeners = windowListeners.toArray(new WindowListener[0]);
        }
        for (WindowListener l : listeners) {
            switch (e.getID()) {
                case WindowEvent.WINDOW_OPENED:
                    l.windowOpened(e);
                    break;
                case WindowEvent.WINDOW_CLOSING:
                    l.windowClosing(e);
                    break;
                case WindowEvent.WINDOW_CLOSED:
                    l.windowClosed(e);
                    break;
                case WindowEvent.WINDOW_ICONIFIED:
                    l.windowIconified(e);
                    break;
                case WindowEvent.WINDOW_DEICONIFIED:
                    l.windowDeiconified(e);
                    break;
                case WindowEvent.WINDOW_ACTIVATED:
                    l.windowActivated(e);
                    break;
                case WindowEvent.WINDOW_DEACTIVATED:
                    l.windowDeactivated(e);
                    break;
                default:
                    break;
            }
        }
        int id = e.getID();
        if (id == WindowEvent.WINDOW_CLOSING && listeners.length == 0) {
            if (defaultCloseOperation == javax.swing.WindowConstants.DISPOSE_ON_CLOSE) {
                dispose();
            }
        }
    }

    void fireWindowEvent(int id) {
        WindowEvent e = new WindowEvent(this, id);
        processWindowEvent(e);
        if (id == WindowEvent.WINDOW_STATE_CHANGED) {
            WindowStateListener[] listeners;
            synchronized (this) {
                listeners = windowStateListeners.toArray(new WindowStateListener[0]);
            }
            for (WindowStateListener l : listeners) {
                l.windowStateChanged(e);
            }
        }
    }

    /** The window layer's close glyph calls this. */
    public void closeRequested() {
        if (defaultCloseOperation == javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE) {
            return;
        }
        WindowListener[] listeners;
        synchronized (this) {
            listeners = windowListeners.toArray(new WindowListener[0]);
        }
        for (WindowListener l : listeners) {
            l.windowClosing(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        }
        if (!isVisible()) {
            return;
        }
        if (defaultCloseOperation == javax.swing.WindowConstants.HIDE_ON_CLOSE) {
            setVisible(false);
        } else {
            dispose();
        }
    }

    public boolean isDisposable() {
        return disposable;
    }

    public void setDisposable(boolean disposable) {
        this.disposable = disposable;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[title=" + title + ",x=" + x + ",y=" + y
                + ",w=" + width + ",h=" + height + ",visible=" + visible + "]";
    }
}

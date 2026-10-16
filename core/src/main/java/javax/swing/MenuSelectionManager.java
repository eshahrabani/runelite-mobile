package javax.swing;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.runelite.mobile.bridge.PopupLayer;
import org.runelite.mobile.bridge.UiBridge;

/**
 * Swing's {@code MenuSelectionManager}: holds the path of menu elements that are
 * currently open and dismisses them again.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the JDK
 * class carried real behaviour: the open path and the "click anywhere else and the menu
 * closes" grab. {@link JPopupMenu} publishes itself here when it becomes visible, so an
 * open menu is exactly {@code defaultManager().getSelectedPath().length > 0}.
 *
 * <p>How the port replaces the desktop mouse grab: the JDK's manager registers an
 * {@code AWTEventListener} with the toolkit and sees every event in the process before
 * any component does. The port's toolkit has no event-listener broadcast (only the
 * per-component listeners), so a {@link JPopupMenu} that becomes visible is announced to
 * the manager, which installs a mouse (and key) listener on the stage roots the menu
 * lives in — the invoker's top-level container, the sidebar root and the top-most
 * window's root pane. Every press that reaches those roots was hit-tested
 * <em>outside</em> the open popups ({@code PopupLayer} is consulted before the component
 * tree), so {@link #clearSelectedPath()} runs: the open popup is notified through
 * {@link MenuElement#menuSelectionChanged(boolean)} and hides itself. {@code ESC} does
 * the same. The event is not consumed — the tap that dismisses a menu still does what it
 * was aimed at, which is also what a desktop lightweight popup does.
 *
 * <p>Port deviation: the JDK routes mouse events through the whole selection path with
 * screen-coordinate hit-testing and lets each element decide. The port's elements are
 * reached directly ({@code UiBridge} dispatches to the deepest component under the
 * point, popups first, and a {@link JMenuItem} fires its own action), so
 * {@link #processMouseEvent} only has to decide between "inside the path" and
 * "dismiss"; {@link #processKeyEvent} forwards to the path like the JDK does.
 */
public class MenuSelectionManager {

    private static final MenuSelectionManager DEFAULT_MANAGER = new MenuSelectionManager();

    private final List<ChangeListener> changeListeners = new ArrayList<>();
    private final MouseWatcher mouseWatcher = new MouseWatcher();
    private final KeyWatcher keyWatcher = new KeyWatcher();
    private final List<Component> watchedRoots = new ArrayList<>();
    private MenuElement[] selectedPath = new MenuElement[0];

    protected MenuSelectionManager() {
    }

    /** The one manager for the whole process (the JDK's singleton). */
    public static MenuSelectionManager defaultManager() {
        return DEFAULT_MANAGER;
    }

    /** The open menu path, root first; empty when no menu is open. */
    public MenuElement[] getSelectedPath() {
        return selectedPath;
    }

    /**
     * Replaces the open menu path, notifying the elements that leave it
     * ({@code menuSelectionChanged(false)}) and those that enter it ({@code true}) before
     * the change listeners run — the JDK's order, which is what lets a popup hide itself
     * when another menu opens on top of it.
     */
    public void setSelectedPath(MenuElement[] path) {
        if (path == null) {
            path = new MenuElement[0];
        }
        if (path.length == 0 && selectedPath.length == 0) {
            updateStageWatcher(path);
            return;
        }
        int i;
        int newCount = path.length;
        int oldCount = selectedPath.length;
        for (i = 0; i < newCount && i < oldCount; i++) {
            if (selectedPath[i] != path[i]) {
                break;
            }
        }
        for (int j = i; j < oldCount; j++) {
            selectedPath[j].menuSelectionChanged(false);
        }
        for (int j = i; j < newCount; j++) {
            path[j].menuSelectionChanged(true);
        }
        selectedPath = path;
        updateStageWatcher(path);
        fireStateChanged();
    }

    /** Closes every open menu (the JDK's cover for {@code setSelectedPath(new MenuElement[0])}). */
    public void clearSelectedPath() {
        setSelectedPath(new MenuElement[0]);
    }

    public void addChangeListener(ChangeListener listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(ChangeListener listener) {
        changeListeners.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return changeListeners.toArray(new ChangeListener[0]);
    }

    /**
     * Routes a mouse event through the open path. Elements get the event first; a press
     * that nothing in the path consumed and that did not land on an open popup dismisses
     * the path (the desktop grab behaviour — see the class comment).
     */
    public void processMouseEvent(MouseEvent event) {
        if (event == null) {
            return;
        }
        MenuElement[] path = selectedPath;
        for (int i = path.length - 1; i >= 0; i--) {
            path[i].processMouseEvent(event, path, this);
            if (event.isConsumed()) {
                return;
            }
        }
        dismissOnOutsidePress(event);
    }

    /** Routes a key event through the open path, top-most element first. */
    public void processKeyEvent(KeyEvent event) {
        if (event == null) {
            return;
        }
        MenuElement[] path = selectedPath;
        for (int i = path.length - 1; i >= 0; i--) {
            path[i].processKeyEvent(event, path, this);
            if (event.isConsumed()) {
                return;
            }
        }
    }

    /** True when {@code c} is one of the components of the open menu path (the JDK API). */
    public boolean isComponentPartOfCurrentMenu(Component c) {
        if (c == null || selectedPath.length == 0) {
            return false;
        }
        return isComponentPartOfCurrentMenu(selectedPath[0], c);
    }

    private static boolean isComponentPartOfCurrentMenu(MenuElement root, Component c) {
        if (root == null) {
            return false;
        }
        if (root.getComponent() == c) {
            return true;
        }
        for (MenuElement child : root.getSubElements()) {
            if (isComponentPartOfCurrentMenu(child, c)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ internals

    private void dismissOnOutsidePress(MouseEvent event) {
        int id = event.getID();
        if (id != MouseEvent.MOUSE_PRESSED && id != MouseEvent.MOUSE_CLICKED) {
            // Hover, enter/exit and drag never close a menu: a desktop popup survives the
            // pointer leaving it.
            return;
        }
        if (selectedPath.length == 0 || event.isConsumed()) {
            return;
        }
        Component source = event.getComponent();
        if (source == null) {
            return;
        }
        // Bubbling keeps the event's source and its source-relative coordinates, so the
        // stage point can be recovered exactly from the deepest target.
        Point stage = stageLocation(source, event.getX(), event.getY());
        if (PopupLayer.topMostAt(stage.x, stage.y) != null) {
            // The press landed on a popup (or on one that opened while this event was
            // being dispatched): it is the popup's business, not a dismissal.
            return;
        }
        clearSelectedPath();
    }

    private void updateStageWatcher(MenuElement[] path) {
        List<Component> roots = new ArrayList<>();
        if (path.length > 0) {
            Component popupComponent = path[0].getComponent();
            if (popupComponent instanceof JPopupMenu) {
                // The popup itself is not in the component tree, so watch where its
                // invoker is staged.
                Component invoker = ((JPopupMenu) popupComponent).getInvoker();
                if (invoker != null) {
                    addRoot(roots, topMost(invoker));
                }
            }
            addRoot(roots, topMost(popupComponent));
            addRoot(roots, UiBridge.sidebarRoot());
            java.awt.Window window = java.awt.Window.topMostWindow();
            if (window != null) {
                addRoot(roots, window.getRootPane() != null ? window.getRootPane() : window);
            }
        }
        for (Component root : watchedRoots) {
            if (!roots.contains(root)) {
                root.removeMouseListener(mouseWatcher);
                root.removeKeyListener(keyWatcher);
            }
        }
        for (Component root : roots) {
            if (!watchedRoots.contains(root)) {
                root.addMouseListener(mouseWatcher);
                root.addKeyListener(keyWatcher);
            }
        }
        watchedRoots.clear();
        watchedRoots.addAll(roots);
    }

    private static void addRoot(List<Component> roots, Component root) {
        if (root != null && !roots.contains(root)) {
            roots.add(root);
        }
    }

    private static Component topMost(Component c) {
        Component top = c;
        while (top != null && top.getParent() != null) {
            top = top.getParent();
        }
        return top;
    }

    private void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : new ArrayList<>(changeListeners)) {
            listener.stateChanged(event);
        }
    }

    /**
     * The stage point of a point given in {@code c}'s coordinates.
     *
     * <p>{@code getLocationOnScreen()} accumulates the parent chain without the window's
     * title bar (the port's {@code Window} paints its content below a
     * {@link UiBridge#WINDOW_TITLE_HEIGHT} strip), so when the component is staged
     * inside the top-most window the title height is added back here. {@link JPopupMenu}
     * uses the same helper for {@code show()}, which is why it lives on the manager.
     */
    static Point stageLocation(Component c, int x, int y) {
        Point origin = c.getLocationOnScreen();
        int titleOffset = 0;
        if (java.awt.Window.topMostWindow() != null && isInWindow(c)) {
            titleOffset = UiBridge.WINDOW_TITLE_HEIGHT;
        }
        return new Point(origin.x + x, origin.y + titleOffset + y);
    }

    private static boolean isInWindow(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof java.awt.Window) {
                return true;
            }
        }
        return false;
    }

    /** Listener installed on the stage roots while a menu is open; see the class doc. */
    private final class MouseWatcher extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent event) {
            dismissOnOutsidePress(event);
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            dismissOnOutsidePress(event);
        }
    }

    /** The key half of {@link Watcher}: {@code ESC} closes whatever is open. */
    private final class KeyWatcher extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE && selectedPath.length > 0) {
                clearSelectedPath();
            }
        }
    }
}

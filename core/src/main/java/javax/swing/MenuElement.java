package javax.swing;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Swing's menu-element protocol: one node of an open menu path.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop}: the
 * generated stub declared an empty interface, so nothing could be added to or removed
 * from a menu path and RuneLite's popup menus ({@code ClientUI}'s tab-strip right-click
 * menu, {@code PluginListItem}'s label menus, {@code IconTextField}'s suggestion list)
 * could never be dismissed. {@link MenuSelectionManager} keeps the open path and routes
 * the events a desktop window system would send to the menu, and every element
 * ({@link JPopupMenu}, {@link JMenuItem}, {@link JMenu}, {@link JMenuBar}) implements
 * this interface exactly as the JDK declares it — the five methods, no more.
 */
public interface MenuElement {

    /**
     * Processes a mouse event that the manager routed into this element, where
     * {@code path} is the selection path that starts at the root element and ends with
     * this one.
     */
    void processMouseEvent(MouseEvent event, MenuElement[] path, MenuSelectionManager manager);

    /** Processes a key event that the manager routed into this element. */
    void processKeyEvent(KeyEvent event, MenuElement[] path, MenuSelectionManager manager);

    /**
     * Informs the element that it was added to ({@code true}) or removed from
     * ({@code false}) the current menu path. An open popup that is no longer part of the
     * path hides itself here — that is how an outside tap, {@code ESC} and
     * {@code MenuSelectionManager.clearSelectedPath()} close the port's menus.
     */
    void menuSelectionChanged(boolean isIncluded);

    /** The sub-elements of this element: a popup menu's items, a menu's submenu. */
    MenuElement[] getSubElements();

    /** The AWT component that paints this element. */
    Component getComponent();
}

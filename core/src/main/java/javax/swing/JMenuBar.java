package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Swing's {@code JMenuBar}: a horizontal strip of {@link JMenu}s.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the
 * generated stub laid nothing out: RuneLite puts a menu bar into some windows, and the
 * port's window host needs it to paint, size and open its menus.
 *
 * <p>Implemented as the JDK's {@code BasicMenuBarUI}: the bar fills with
 * {@code MenuBar.background} (falling back to {@code Menu.background} and then the panel
 * colour), lays its menus out left to right at their preferred widths, and forwards a
 * tap to the menu under the point — a menu that is already open and is tapped again
 * closes, which is {@link JMenu#setPopupMenuVisible}'s toggle.
 *
 * <p>Port deviation: the JDK's menu bar is driven by a {@code SingleSelectionModel} and
 * a {@code MenuKeyListener} chain (arrow keys move the selection, the selection opens
 * menus). The port has neither class in its runtime, so the menu bar keeps the
 * selection implicit: the menus hold their own popups, arrow keys move between the menus
 * in this bar, and {@code ESC} closes whatever is open.
 */
public class JMenuBar extends JComponent implements javax.accessibility.Accessible, MenuElement {

    /** The bar's height when it has no menu with a taller preferred size. */
    private static final int DEFAULT_HEIGHT = 24;

    public JMenuBar() {
        super();
        setLayout(new MenuBarLayout());
        setOpaque(true);
    }

    /** Adds a menu and returns it (the JDK's typed cover for {@code Container.add}). */
    public JMenu add(JMenu menu) {
        super.add(menu);
        return menu;
    }

    /** The menu at {@code index}; every child of a menu bar is a menu. */
    public JMenu getMenu(int index) {
        if (index < 0 || index >= getComponentCount()) {
            return null;
        }
        Component component = getComponent(index);
        return component instanceof JMenu ? (JMenu) component : null;
    }

    public int getMenuCount() {
        return getComponentCount();
    }

    /** The menu under a point in the bar, or null. */
    public JMenu menuAt(int x, int y) {
        for (int i = 0; i < getComponentCount(); i++) {
            Component component = getComponent(i);
            if (component instanceof JMenu && component.isVisible() && component.contains(x, y)) {
                return (JMenu) component;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBarBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** The bar's fill colour ({@code MenuBar.background} in the port's theme). */
    private Color getBarBackground() {
        Color color = UIManager.getColor("MenuBar.background");
        if (color == null) {
            color = UIManager.getColor("Menu.background");
        }
        if (color == null) {
            color = getBackground();
        }
        return color != null ? color : Color.LIGHT_GRAY;
    }

    // ------------------------------------------------------------ MenuElement

    @Override
    public MenuElement[] getSubElements() {
        MenuElement[] elements = new MenuElement[getComponentCount()];
        for (int i = 0; i < elements.length; i++) {
            Component component = getComponent(i);
            elements[i] = component instanceof MenuElement ? (MenuElement) component : null;
        }
        return elements;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void menuSelectionChanged(boolean isIncluded) {
        // The menus own their popups: leaving the bar closes each menu through its own
        // menuSelectionChanged, which JMenu implements.
    }

    /** Opens a menu whose label was hit by the manager's routed event. */
    @Override
    public void processMouseEvent(MouseEvent event, MenuElement[] path, MenuSelectionManager manager) {
        if (event.getID() != MouseEvent.MOUSE_PRESSED && event.getID() != MouseEvent.MOUSE_CLICKED) {
            return;
        }
        Component source = event.getComponent();
        JMenu menu = source instanceof JMenu ? (JMenu) source : menuAt(event.getX(), event.getY());
        if (menu != null && menu.isEnabled()) {
            menu.setPopupMenuVisible(!menu.isPopupMenuVisible());
            event.consume();
        }
    }

    /** {@code LEFT}/{@code RIGHT} move between the menus, {@code ESC} closes the path. */
    @Override
    public void processKeyEvent(KeyEvent event, MenuElement[] path, MenuSelectionManager manager) {
        if (event.getID() != KeyEvent.KEY_PRESSED) {
            return;
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_ESCAPE) {
            manager.clearSelectedPath();
            event.consume();
            return;
        }
        if (code != KeyEvent.VK_LEFT && code != KeyEvent.VK_RIGHT) {
            return;
        }
        int open = -1;
        for (int i = 0; i < getComponentCount(); i++) {
            JMenu menu = getMenu(i);
            if (menu != null && menu.isPopupMenuVisible()) {
                open = i;
                break;
            }
        }
        if (open < 0) {
            return;
        }
        int next = code == KeyEvent.VK_RIGHT
                ? (open + 1) % Math.max(1, getComponentCount())
                : (open - 1 + getComponentCount()) % Math.max(1, getComponentCount());
        JMenu menu = getMenu(next);
        if (menu != null) {
            setPopupMenuVisible(open, false);
            menu.setPopupMenuVisible(true);
            event.consume();
        }
    }

    private void setPopupMenuVisible(int index, boolean visible) {
        JMenu menu = getMenu(index);
        if (menu != null) {
            menu.setPopupMenuVisible(visible);
        }
    }

    /**
     * The bar's layout: menus left to right at their preferred widths, the bar's height
     * (or the tallest menu when the bar is thinner than its content).
     */
    private static final class MenuBarLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int width = insets.left + insets.right;
            int height = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component child = parent.getComponent(i);
                Dimension pref = child.getPreferredSize();
                width += pref.width;
                height = Math.max(height, pref.height);
            }
            return new Dimension(width, insets.top + Math.max(DEFAULT_HEIGHT, height) + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int x = insets.left;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component child = parent.getComponent(i);
                if (!child.isVisible()) {
                    continue;
                }
                int width = child.getPreferredSize().width;
                child.setBounds(x, insets.top, width, height);
                x += width;
            }
        }
    }
}

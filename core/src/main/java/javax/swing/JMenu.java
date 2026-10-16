package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Swing's {@code JMenu}: a menu bar entry (or a submenu) that owns a {@link JPopupMenu}.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and core had
 * no {@code JMenu} at all. The port runs RuneLite's own {@code ClientToolbarPanel}, which
 * builds its navigation-button menus out of {@link JMenuItem}s and can put a
 * {@link JMenuBar} into a window; this class implements the single menu level the port
 * uses: a label in a menu bar that opens its popup below itself, items that delegate to
 * the popup, and a popup that closes when the path leaves it.
 *
 * <p>Port deviations, all deliberate:
 * <ul>
 * <li>The label is inherited from {@link JMenuItem} (the JDK does the same); the drop
 *     arrow and the mnemonic underline the JDK paints are not drawn — the port's menus
 *     are tapped, not keyboard-driven, and the item's own {@code getUIClassID()} is
 *     {@code "MenuItemUI"} so nothing would draw them anyway.</li>
 * <li>A submenu opens <em>below</em> its label in a menu bar and to the <em>right</em> of
 *     it elsewhere, which is what a tap can reach on a phone; the JDK's
 *     placement-dependent geometry is not reproduced.</li>
 * <li>{@code setPopupMenuVisible(true)} requires the menu to be showing (the JDK rule):
 *     otherwise the popup would be placed at a position nothing has laid out yet.</li>
 * </ul>
 *
 * <p>Unlike the JDK, {@code JMenu} here is not a {@code MenuElement} node that the
 * selection path walks into: the port's popups are dispatched directly through the
 * popup layer, so a menu's popup appears in the path on its own.
 */
public class JMenu extends JMenuItem {

    private final JPopupMenu popupMenu = new JPopupMenu();

    public JMenu() {
        super();
        installPopup();
    }

    public JMenu(String text) {
        super(text);
        installPopup();
    }

    private void installPopup() {
        popupMenu.setInvoker(this);
        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                firePropertyChange("popupMenuVisible", Boolean.FALSE, Boolean.TRUE);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // The JDK keeps the menu label highlighted only while its popup is open.
                setArmed(false);
                firePropertyChange("popupMenuVisible", Boolean.TRUE, Boolean.FALSE);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                setArmed(false);
            }
        });
        setOpaque(false);
    }

    /** The popup this menu opens; its items are the menu's items (the JDK shape). */
    public JPopupMenu getPopupMenu() {
        return popupMenu;
    }

    // ------------------------------------------------------------- contents

    public JMenuItem add(JMenuItem item) {
        popupMenu.add(item);
        return item;
    }

    public JMenuItem add(String text) {
        return add(new JMenuItem(text));
    }

    /** Adds any component to the popup (a separator, a panel, a list). */
    public Component add(Component component) {
        popupMenu.add(component);
        return component;
    }

    public void addSeparator() {
        popupMenu.addSeparator();
    }

    /** Items in this menu (the JDK's count is the popup's, not the container's). */
    public int getItemCount() {
        return popupMenu.getComponentCount();
    }

    public JMenuItem getItem(int index) {
        Component component = popupMenu.getComponent(index);
        return component instanceof JMenuItem ? (JMenuItem) component : null;
    }

    public int getMenuComponentCount() {
        return popupMenu.getComponentCount();
    }

    public Component getMenuComponent(int index) {
        return popupMenu.getComponent(index);
    }

    public void remove(JMenuItem item) {
        popupMenu.remove(item);
    }

    @Override
    public void remove(int index) {
        popupMenu.remove(index);
    }

    @Override
    public void removeAll() {
        popupMenu.removeAll();
    }

    // ---------------------------------------------------------------- popup

    public boolean isPopupMenuVisible() {
        return popupMenu.isVisible();
    }

    /**
     * Opens or closes the menu's popup.
     *
     * <p>The JDK refuses to show a menu that is not showing itself; that rule is kept,
     * because {@link JPopupMenu#show} converts the invoker's position with
     * {@code getLocationOnScreen()} and an unlaid-out menu has no position.
     */
    public void setPopupMenuVisible(boolean visible) {
        if (visible == popupMenu.isVisible()) {
            return;
        }
        if (visible && !isShowing()) {
            return;
        }
        if (visible) {
            popupMenu.show(this, 0, getHeight());
        } else {
            popupMenu.setVisible(false);
        }
    }

    /** A tap on a menu label toggles its popup instead of firing an action. */
    @Override
    public void doClick() {
        setPopupMenuVisible(!popupMenu.isVisible());
    }

    @Override
    public void menuSelectionChanged(boolean isIncluded) {
        super.menuSelectionChanged(isIncluded);
        if (!isIncluded && popupMenu.isVisible()) {
            popupMenu.setVisible(false);
        }
    }

    /** A menu in a bar paints the bar's colour, so the bar looks like one strip. */
    @Override
    protected Color getItemBackground() {
        if (getParent() instanceof JMenuBar) {
            Color color = UIManager.getColor("MenuBar.background");
            if (color != null) {
                return color;
            }
        }
        return super.getItemBackground();
    }

    /** The menu's sub-element is its popup; the popup holds the items. */
    @Override
    public MenuElement[] getSubElements() {
        return new MenuElement[] {popupMenu};
    }

    /** {@code ESC} closes an open submenu; {@code ENTER}/{@code SPACE} opens it. */
    @Override
    public void processKeyEvent(KeyEvent event, MenuElement[] path, MenuSelectionManager manager) {
        if (event.getID() != KeyEvent.KEY_PRESSED || !isEnabled()) {
            return;
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_ESCAPE && popupMenu.isVisible()) {
            setPopupMenuVisible(false);
            event.consume();
        } else if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
            setPopupMenuVisible(true);
            event.consume();
        }
    }

    /** A press on the label of a menu that is already open closes it again. */
    @Override
    public void processMouseEvent(MouseEvent event, MenuElement[] path, MenuSelectionManager manager) {
        super.processMouseEvent(event, path, manager);
        if (event.getID() == MouseEvent.MOUSE_PRESSED && popupMenu.isVisible()) {
            setPopupMenuVisible(false);
        }
    }
}

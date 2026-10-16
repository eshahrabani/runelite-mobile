package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.runelite.mobile.bridge.PopupLayer;
import org.runelite.mobile.bridge.UiBridge;

/**
 * Swing's {@code JPopupMenu}: the menu shown by a tap (RuneLite's tab-strip right-click
 * menu, the navigation-button menus, the plugin-list label menus, the suggestion list of
 * an {@code IconTextField}).
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the
 * generated stub showed nothing: the port has no native popup windows, so a popup is a
 * component registered in {@code org.runelite.mobile.bridge.PopupLayer} — the stage's
 * overlay stack, painted after the base layer and consulted first when hit-testing.
 *
 * <p>Showing and hiding, in the port's terms:
 * <ol>
 * <li>{@link #show(Component, int, int)} places the popup: the invoker-relative point is
 *     converted to stage coordinates ({@code getLocationOnScreen()} plus the window title
 *     height when the invoker is staged inside a window), clamped so the popup stays
 *     inside the stage, and the popup is sized to its preferred size and laid out.</li>
 * <li>{@link #setVisible(boolean)} with {@code true} publishes the popup in
 *     {@link MenuSelectionManager}'s path — which closes whatever menu was open, because
 *     the manager tells the previous element that it left the path — and hands the
 *     {@link PopupFactory} a popup that registers in {@code PopupLayer}.</li>
 * <li>Dismissal happens through the manager, which is why the popup is in the path:
 *     a press that reaches the stage root ({@code PopupLayer} is hit-tested first, so
 *     such a press is outside every popup) or {@code ESC} calls
 *     {@code clearSelectedPath()}, {@link #menuSelectionChanged(boolean)} is told the
 *     popup left the path, and this class hides itself. Tapping an item fires the item's
 *     action and hides the menu from {@link JMenuItem#doClick()}; a tap on the popup's
 *     own padding clears the selection without firing anything.</li>
 * </ol>
 *
 * <p>Port deviations from the JDK, all deliberate:
 * <ul>
 * <li>The JDK's popup consists of a {@code PopupMenuUI} delegate, a
 *     {@code SingleSelectionModel} and a {@code JWindow}. The port has no
 *     {@code PopupMenuUI}/{@code SingleSelectionModel} in its runtime, so this class
 *     paints its background/border itself (through {@code JComponent}'s default
 *     painting), holds the keyboard selection in an {@code int}, and keeps its children
 *     in a vertical stack layout of its own instead of {@code DefaultMenuLayout}.</li>
 * <li>A popup that would leave the stage is clamped back inside it rather than flipped
 *     to the other side of its invoker: the port's stage is a phone-sized rectangle and
 *     a flipped menu would cover the control that opened it.</li>
 * <li>{@link #pack()} also validates a popup that is not shown, which the JDK skips.</li>
 * <li>{@code add(Action)}/{@code insert(Action, int)} are not offered: they need
 *     {@code JMenuItem.setAction}, which the port's {@code AbstractButton} gains with
 *     its own slice. RuneLite's menus use {@code add(JMenuItem)} and
 *     {@code add(String)} — see {@code PluginListItem}, {@code ClientToolbarPanel} and
 *     {@code ClientUI}.</li>
 * </ul>
 */
public class JPopupMenu extends JComponent implements javax.accessibility.Accessible, MenuElement {

    /** The height of a separator row. */
    private static final int SEPARATOR_HEIGHT = 5;

    private String label;
    private boolean lightWeightPopup;
    private boolean borderPainted = true;
    private Component invoker;
    private Popup popup;
    private int desiredLocationX;
    private int desiredLocationY;
    private int selectedIndex = -1;
    private Component previousFocusOwner;
    private final List<PopupMenuListener> popupMenuListeners = new ArrayList<>();

    /** Hides the popup when the manager takes it out of the open path. */
    private final ChangeListener pathListener = this::pathChanged;

    public JPopupMenu() {
        this(null);
    }

    public JPopupMenu(String label) {
        super();
        this.label = label;
        setLayout(new MenuLayout());
        setOpaque(true);
        Color background = resolveBackground();
        if (background != null) {
            setBackground(background);
        }
        Object border = UIManager.getBorder("PopupMenu.border");
        if (border instanceof javax.swing.border.Border) {
            setBorder((javax.swing.border.Border) border);
        } else {
            Color borderColor = UIManager.getColor("Component.borderColor");
            setBorder(BorderFactory.createLineBorder(borderColor != null ? borderColor : Color.BLACK));
        }
        // The port's default: a popup is painted inside the stage, so it is always the
        // light-weight kind. RuneLite turns the default off at start-up (ClientUI), which
        // is remembered here and reported by isLightWeightPopupEnabled().
        lightWeightPopup = PopupFactory.isDefaultLightWeightPopupEnabled();
    }

    /**
     * The port's own constructor form (the JDK has no light-weight flag argument):
     * {@code lightWeight} seeds the per-popup flag.
     */
    public JPopupMenu(boolean lightWeight) {
        this((String) null);
        this.lightWeightPopup = lightWeight;
    }

    private static Color resolveBackground() {
        Color color = UIManager.getColor("PopupMenu.background");
        if (color == null) {
            color = UIManager.getColor("Menu.background");
        }
        if (color == null) {
            color = UIManager.getColor("Panel.background");
        }
        return color;
    }

    // ----------------------------------------------------------- light weight

    public boolean isLightWeightPopupEnabled() {
        return lightWeightPopup;
    }

    public void setLightWeightPopupEnabled(boolean enabled) {
        lightWeightPopup = enabled;
    }

    /** Sets the process-wide default for popups created later (the JDK API). */
    public static void setDefaultLightWeightPopupEnabled(boolean enabled) {
        PopupFactory.setDefaultLightWeightPopupEnabled(enabled);
    }

    public static boolean getDefaultLightWeightPopupEnabled() {
        return PopupFactory.isDefaultLightWeightPopupEnabled();
    }

    // ------------------------------------------------------------------ model

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        String old = this.label;
        this.label = label;
        firePropertyChange("label", old, label);
        invalidate();
        repaint();
    }

    /** Appends a menu item and returns it (the JDK API). */
    public JMenuItem add(JMenuItem menuItem) {
        super.add(menuItem);
        return menuItem;
    }

    /** Creates a menu item with the given text and appends it. */
    public JMenuItem add(String text) {
        return add(new JMenuItem(text));
    }

    /** Appends a separator row. */
    public void addSeparator() {
        add(new Separator());
    }

    /**
     * Inserts a component at {@code index}, the JDK's algorithm: the tail is detached and
     * re-appended, so the layout manager sees a plain append for every component.
     */
    public void insert(Component component, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index less than zero.");
        }
        int count = getComponentCount();
        if (index > count) {
            index = count;
        }
        List<Component> tail = new ArrayList<>();
        for (int i = index; i < count; i++) {
            tail.add(getComponent(index));
            remove(index);
        }
        add(component);
        for (Component component1 : tail) {
            add(component1);
        }
    }

    @Override
    public void remove(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("index less than zero.");
        }
        if (position > getComponentCount() - 1) {
            throw new IllegalArgumentException("index greater than the number of items.");
        }
        if (position == selectedIndex) {
            selectedIndex = -1;
        } else if (position < selectedIndex) {
            selectedIndex--;
        }
        super.remove(position);
        revalidate();
        repaint();
    }

    @Override
    public void removeAll() {
        selectedIndex = -1;
        super.removeAll();
        revalidate();
        repaint();
    }

    /** @deprecated the JDK replaced it with {@code Container.getComponent(int)}. */
    @Deprecated
    public Component getComponentAtIndex(int index) {
        return index >= 0 && index < getComponentCount() ? getComponent(index) : null;
    }

    public int getComponentIndex(Component component) {
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) == component) {
                return i;
            }
        }
        return -1;
    }

    /** True when the event is a popup trigger (the JDK's cover for the platform check). */
    public boolean isPopupTrigger(MouseEvent event) {
        return event != null && event.isPopupTrigger();
    }

    // -------------------------------------------------------------- selection

    /**
     * Moves the keyboard selection. The JDK exposes this through a
     * {@code SingleSelectionModel}; that class is not part of the port's runtime, so the
     * index lives here and {@link #setSelected(Component)} is provided for the JDK's
     * method name.
     */
    public void setSelectedIndex(int index) {
        if (index < -1 || index >= getComponentCount()) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        selectedIndex = index;
        armItems();
        repaint();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Selects a component of the popup (the JDK API). */
    public void setSelected(Component selected) {
        setSelectedIndex(getComponentIndex(selected));
    }

    private void armItems() {
        for (int i = 0; i < getComponentCount(); i++) {
            Component child = getComponent(i);
            if (child instanceof JMenuItem) {
                ((JMenuItem) child).setArmed(i == selectedIndex);
            }
        }
    }

    /** The selected item, or null. */
    public JMenuItem getSelectedItem() {
        Component selected = getComponentAtIndex(selectedIndex);
        return selected instanceof JMenuItem ? (JMenuItem) selected : null;
    }

    private void moveSelection(int delta) {
        int count = getComponentCount();
        if (count == 0) {
            return;
        }
        int index = selectedIndex;
        for (int i = 0; i < count; i++) {
            index = ((index + delta) % count + count) % count;
            Component child = getComponent(index);
            if (child instanceof JMenuItem && child.isEnabled() && child.isVisible()) {
                setSelectedIndex(index);
                return;
            }
        }
    }

    // ----------------------------------------------------------- listeners

    public void addPopupMenuListener(PopupMenuListener listener) {
        if (listener != null && !popupMenuListeners.contains(listener)) {
            popupMenuListeners.add(listener);
        }
    }

    public void removePopupMenuListener(PopupMenuListener listener) {
        popupMenuListeners.remove(listener);
    }

    public PopupMenuListener[] getPopupMenuListeners() {
        return popupMenuListeners.toArray(new PopupMenuListener[0]);
    }

    protected void firePopupMenuWillBecomeVisible() {
        PopupMenuEvent event = new PopupMenuEvent(this);
        for (PopupMenuListener listener : new ArrayList<>(popupMenuListeners)) {
            listener.popupMenuWillBecomeVisible(event);
        }
    }

    protected void firePopupMenuWillBecomeInvisible() {
        PopupMenuEvent event = new PopupMenuEvent(this);
        for (PopupMenuListener listener : new ArrayList<>(popupMenuListeners)) {
            listener.popupMenuWillBecomeInvisible(event);
        }
    }

    protected void firePopupMenuCanceled() {
        PopupMenuEvent event = new PopupMenuEvent(this);
        for (PopupMenuListener listener : new ArrayList<>(popupMenuListeners)) {
            listener.popupMenuCanceled(event);
        }
    }

    // ---------------------------------------------------------- invoker/location

    public Component getInvoker() {
        return invoker;
    }

    public void setInvoker(Component invoker) {
        if (this.invoker == invoker) {
            return;
        }
        this.invoker = invoker;
        invalidate();
        repaint();
    }

    /**
     * Sets the popup's stage position; a visible popup is re-shown there (the JDK
     * re-shows rather than moving, because its popup is a window).
     */
    public void setLocation(int x, int y) {
        int oldX = desiredLocationX;
        int oldY = desiredLocationY;
        desiredLocationX = x;
        desiredLocationY = y;
        if (popup != null && (x != oldX || y != oldY)) {
            showPopup();
        }
    }

    /** Shows the menu at {@code (x, y)} relative to {@code invoker} (the JDK API). */
    public void show(Component invoker, int x, int y) {
        setInvoker(invoker);
        Point stage = invoker != null
                ? MenuSelectionManager.stageLocation(invoker, x, y)
                : new Point(x, y);
        setLocation(stage.x, stage.y);
        setVisible(true);
    }

    // -------------------------------------------------------------- visibility

    /** True while the popup is in the popup layer (the JDK's definition). */
    @Override
    public boolean isVisible() {
        return popup != null;
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible == isVisible()) {
            return;
        }
        MenuSelectionManager manager = MenuSelectionManager.defaultManager();
        if (visible) {
            super.setVisible(true);
            previousFocusOwner =
                    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            // Publishing in the path first: the manager tells the previously open popup
            // that it left the path, which is how only one menu is ever open.
            manager.setSelectedPath(new MenuElement[] {this});
            manager.addChangeListener(pathListener);
            firePopupMenuWillBecomeVisible();
            showPopup();
            // Keys (ESC/UP/DOWN/ENTER) belong to the popup while it is open; a device
            // with a keyboard sends them to the focus owner.
            if (isFocusable()) {
                requestFocusInWindow();
            }
            firePropertyChange("visible", Boolean.FALSE, Boolean.TRUE);
        } else {
            firePopupMenuWillBecomeInvisible();
            manager.removeChangeListener(pathListener);
            if (popup != null) {
                popup.hide();
                popup = null;
            }
            super.setVisible(false);
            selectedIndex = -1;
            armItems();
            if (manager.isComponentPartOfCurrentMenu(this)) {
                manager.clearSelectedPath();
            }
            if (previousFocusOwner != null) {
                Component owner = previousFocusOwner;
                previousFocusOwner = null;
                owner.requestFocus();
            }
            firePropertyChange("visible", Boolean.TRUE, Boolean.FALSE);
        }
    }

    /** Called by the manager when another menu took over the open path. */
    private void pathChanged(ChangeEvent event) {
        MenuSelectionManager manager = MenuSelectionManager.defaultManager();
        if (isVisible() && !manager.isComponentPartOfCurrentMenu(this)) {
            setVisible(false);
            firePopupMenuCanceled();
        }
    }

    /** Sizes the popup to its preferred size and shows it in the popup layer. */
    private void showPopup() {
        PopupFactory factory = PopupFactory.getSharedInstance();
        factory.setPopupType(isLightWeightPopupEnabled()
                ? PopupFactory.LIGHT_WEIGHT_POPUP : PopupFactory.HEAVY_WEIGHT_POPUP);
        Dimension preferred = getPreferredSize();
        Point location = clampToStage(desiredLocationX, desiredLocationY, preferred);
        desiredLocationX = location.x;
        desiredLocationY = location.y;
        setSize(preferred.width, preferred.height);
        validate();
        if (popup != null) {
            popup.hide();
            popup = null;
        }
        popup = factory.getPopup(invoker, this, location.x, location.y);
        popup.show();
        factory.setPopupType(PopupFactory.LIGHT_WEIGHT_POPUP);
        repaint();
    }

    /** Keeps the popup inside the stage it is painted into (see the class comment). */
    private Point clampToStage(int x, int y, Dimension size) {
        Point stage = stageSize();
        int maxX = Math.max(0, stage.x - size.width);
        int maxY = Math.max(0, stage.y - size.height);
        return new Point(Math.min(Math.max(0, x), maxX), Math.min(Math.max(0, y), maxY));
    }

    /** The logical size of the stage the popup is painted into. */
    private Point stageSize() {
        if (invoker != null && isStagedInWindow(invoker)) {
            int[] windowSize = UiBridge.windowLogicalSize();
            if (windowSize != null) {
                return new Point(windowSize[0], windowSize[1]);
            }
        }
        return new Point(UiBridge.sidebarLogicalWidth(), UiBridge.sidebarLogicalHeight());
    }

    private static boolean isStagedInWindow(Component component) {
        for (Component p = component; p != null; p = p.getParent()) {
            if (p instanceof java.awt.Window) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lays the popup out at its preferred size. The JDK only packs a visible popup; the
     * port also validates a hidden one, so a caller can size a menu before showing it.
     */
    public void pack() {
        if (popup != null) {
            Dimension preferred = getPreferredSize();
            if (preferred.width != getWidth() || preferred.height != getHeight()) {
                showPopup();
            } else {
                validate();
            }
        } else {
            setSize(getPreferredSize());
            invalidate();
            validate();
        }
    }

    /** Forces the popup's size (the JDK API). */
    public void setPopupSize(Dimension size) {
        Dimension old = getPreferredSize();
        setPreferredSize(size);
        if (popup != null && !old.equals(getPreferredSize())) {
            showPopup();
        }
    }

    public void setPopupSize(int width, int height) {
        setPopupSize(new Dimension(width, height));
    }

    /** The margin the JDK derives from the border'; the port's border insets. */
    public Insets getMargin() {
        return getInsets();
    }

    public boolean isBorderPainted() {
        return borderPainted;
    }

    public void setBorderPainted(boolean painted) {
        borderPainted = painted;
        repaint();
    }

    @Override
    protected void paintBorder(Graphics g) {
        if (borderPainted) {
            super.paintBorder(g);
        }
    }

    // ------------------------------------------------------------- MenuElement

    /** A popup's items are directly in the path, so it has no sub-elements. */
    @Override
    public MenuElement[] getSubElements() {
        return new MenuElement[0];
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /** Leaving the open path closes an open popup — see the class comment. */
    @Override
    public void menuSelectionChanged(boolean isIncluded) {
        if (!isIncluded && isVisible()) {
            setVisible(false);
        }
    }

    @Override
    public void processMouseEvent(MouseEvent event, MenuElement[] path, MenuSelectionManager manager) {
        if (event.getComponent() != this) {
            // An item's event bubbling up: its own handler owns it.
            return;
        }
        processMouseEvent(event);
    }

    @Override
    public void processKeyEvent(KeyEvent event, MenuElement[] path, MenuSelectionManager manager) {
        if (!event.isConsumed()) {
            processKeyEvent(event);
        }
    }

    // ----------------------------------------------------------- input handling

    @Override
    protected void processMouseEvent(MouseEvent event) {
        if (event.getComponent() == this
                && event.getID() == MouseEvent.MOUSE_PRESSED
                && event.getButton() == MouseEvent.BUTTON1) {
            // A press on the popup's own padding clears the keyboard selection; items
            // handle their own presses (their events bubble here afterwards).
            setSelectedIndex(-1);
        }
        super.processMouseEvent(event);
    }

    /** {@code ESC} closes, {@code UP}/{@code DOWN} move the selection, {@code ENTER} fires it. */
    @Override
    protected void processKeyEvent(KeyEvent event) {
        super.processKeyEvent(event);
        if (event.isConsumed() || event.getID() != KeyEvent.KEY_PRESSED) {
            return;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.VK_ESCAPE:
                setVisible(false);
                event.consume();
                break;
            case KeyEvent.VK_DOWN:
                moveSelection(1);
                event.consume();
                break;
            case KeyEvent.VK_UP:
                moveSelection(-1);
                event.consume();
                break;
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SPACE:
                JMenuItem selected = getSelectedItem();
                if (selected != null) {
                    selected.doClick();
                }
                event.consume();
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ layout

    /**
     * The port's replacement for the JDK's {@code DefaultMenuLayout} (a {@code BoxLayout}
     * subclass installed by {@code BasicPopupMenuUI}): the children stack vertically at
     * their preferred heights and are stretched to the popup's inner width, which is what
     * makes every row of a menu the same width. {@code IconTextField} replaces this layout
     * with a {@code BorderLayout} for its suggestion list, which keeps working because
     * {@code Container.add(Component, Object)} forwards the constraints.
     */
    private static final class MenuLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int width = 0;
            int height = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component child = parent.getComponent(i);
                if (!child.isVisible()) {
                    continue;
                }
                Dimension size = child.getPreferredSize();
                width = Math.max(width, size.width);
                height += size.height;
            }
            return new Dimension(insets.left + width + insets.right,
                    insets.top + height + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int innerWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);
            int y = insets.top;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component child = parent.getComponent(i);
                if (!child.isVisible()) {
                    continue;
                }
                int height = child.getPreferredSize().height;
                child.setBounds(insets.left, y, innerWidth, height);
                y += height;
            }
        }
    }

    /**
     * A separator row. The JDK's {@code JPopupMenu.Separator} extends {@code JSeparator},
     * which the port does not have; this paints the same single line.
     */
    public static class Separator extends JComponent {

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) {
                return super.getPreferredSize();
            }
            return new Dimension(0, SEPARATOR_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(dividerColor());
            int y = getHeight() / 2;
            g.drawLine(0, y, getWidth(), y);
        }

        private Color dividerColor() {
            Color color = UIManager.getColor("MenuItem.separatorColor");
            if (color == null) {
                color = UIManager.getColor("Component.borderColor");
            }
            return color != null ? color : Color.DARK_GRAY;
        }
    }
}

package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Swing's {@code JTabbedPane}: the sidebar's tab strip.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the
 * generated stub was inert. This is the widget RuneLite's own {@code ClientUI} builds on:
 * {@code new JTabbedPane(JTabbedPane.RIGHT)} holds one tab per plugin panel (icon only,
 * {@code insertTab(null, icon, panel, tooltip, index)}), the tab rectangles from
 * {@link #getBoundsAt(int)} are hit-tested for the strip's right-click popup, and the
 * navigation strip is anchored through the client properties
 * {@code "JTabbedPane.leadingComponent"} / {@code "JTabbedPane.trailingComponent"} — the
 * same two string literals FlatLaf uses, so the unmodified host works unchanged.
 *
 * <p><b>Geometry.</b> The strip sits on the requested side (only {@link #RIGHT} and
 * {@link #TOP} are laid out; every other placement falls back to {@code TOP}) and
 * everything below is inside the pane's border insets:
 * <ul>
 * <li><b>Strip thickness</b> (width for {@code RIGHT}, height for {@code TOP}) is
 *     {@value #TAB_AREA_PAD} px of tab-area padding around the largest tab plus the
 *     leading/trailing component's preferred thickness. An icon-only vertical tab is
 *     {@code 2 + 5 + 16 + 5 + 2 = 30} px wide (tab insets from
 *     {@code TabbedPane.tabInsets}, tab height from {@code TabbedPane.tabHeight}), which
 *     is the ~30 px strip the port's 272 px sidebar is sized for (242 px panel + 30).</li>
 * <li><b>Tab rects</b> are {@code TabbedPane.tabHeight} (26) long along the strip and
 *     the strip's inner thickness across it; the icon (and the title, for {@code TOP}) is
 *     centred in the rect. A {@code TOP} tab's length is its icon + gap + text width plus
 *     the tab insets, so {@link #getBoundsAt(int)} reports real per-tab rectangles.</li>
 * <li><b>Leading component</b> ({@code "JTabbedPane.leadingComponent"}): placed at the
 *     <em>start</em> of the strip — the top for {@code RIGHT}, the left for {@code TOP} —
 *     stretched to the strip's full thickness, and stretched along the strip to the space
 *     the tabs and the trailing component leave over (never below its preferred length).
 *     {@code ClientToolbarPanel.createSidebarPanel()} is a {@code BorderLayout} panel with
 *     the toolbar pinned {@code SOUTH}, so its buttons land at the bottom of the strip's
 *     leading area.</li>
 * <li><b>Trailing component</b> ({@code "JTabbedPane.trailingComponent"}, the key the
 *     unmodified {@code ClientUI} uses): placed flush at the <em>end</em> of the strip —
 *     the bottom of a {@code RIGHT} column — stretched to the strip's full thickness and
 *     keeping its own preferred length, with the tabs above it. That is upstream
 *     RuneLite's placement: the panel-less navigation buttons and the close-sidebar
 *     toggle sit at the very bottom of the strip.</li>
 * <li><b>Content</b>: the selected tab's component fills everything the strip leaves;
 *     with no selection ({@code getSelectedIndex() == -1}, the sidebar's initial state)
 *     no content is shown.</li>
 * <li>Only the selected tab paints {@code TabbedPane.selectedTabBackground}; the tab
 *     under the pointer paints {@code TabbedPane.tabBackground} brightened (the theme
 *     seeds both as {@code ColorScheme.DARKER_GRAY_HOVER_COLOR}); unselected tabs paint
 *     {@code TabbedPane.tabBackground}.</li>
 * </ul>
 *
 * <p><b>Port deviations from the JDK</b>, all deliberate:
 * <ul>
 * <li>Inserting the first tab <em>keeps</em> the selection at {@code -1} when it was
 *     {@code -1}. The JDK selects index 0 there; the port's sidebar host needs the
 *     "open the drawer with nothing selected" state (the plan calls it out), and it also
 *     means inserting the sidebar's tabs fires no {@code ChangeEvent} until something is
 *     actually selected. Every other selection rule is the JDK's, including the
 *     index-shift when a tab is inserted at or before the selection and the selection
 *     fix-up after a removal.</li>
 * <li>Placements other than {@code RIGHT} fall back to {@code TOP} instead of throwing:
 *     the port has no {@code LEFT}/{@code BOTTOM} strip and a client that asks for one
 *     should still see its panel.</li>
 * <li>Vertical tabs paint their title on one line, centred and clipped to the tab rect.
 *     The port's text rasteriser cannot rotate glyphs (see {@code Graphics.drawString}),
 *     so the JDK's rotated title is not reproduced; the sidebar's tabs are icon-only
 *     ({@code title == null}) and set their tooltip instead, which
 *     {@link #getToolTipText(MouseEvent)} serves to the host's long-press.</li>
 * <li>The tab layout policy is stored and reported but the strip is always a single run
 *     (no wrapping to a second row and no scroll buttons): the sidebar strip is one
 *     column of 26 px icons and a phone drawer never has room for a second run.</li>
 * <li>The strip's thickness above is fixed by the port rather than asked of a UI
 *     delegate, because the port has no {@code TabbedPaneUI}; the value is the one
 *     FlatLaf produces for {@code tabInsets: 2,5,2,5; tabHeight: 26} with icon-only
 *     tabs.</li>
 * </ul>
 */
public class JTabbedPane extends JComponent implements javax.accessibility.Accessible, javax.swing.SwingConstants {

    /** Tabs are laid out in one run (stored for API completeness; see the class doc). */
    public static final int WRAP_TAB_LAYOUT = 0;
    /** Tabs are laid out in one run (stored for API completeness; see the class doc). */
    public static final int SCROLL_TAB_LAYOUT = 1;

    /** FlatLaf's literal for the component before the tabs; the host sets exactly this. */
    private static final String LEADING_COMPONENT_KEY = "JTabbedPane.leadingComponent";
    /** FlatLaf's literal for the component after the tabs; upstream RuneLite uses this. */
    private static final String TRAILING_COMPONENT_KEY = "JTabbedPane.trailingComponent";

    /** Tab-area padding on the strip's cross axis (see the class doc's geometry). */
    private static final int TAB_AREA_PAD = 2;
    /** Tab height when {@code TabbedPane.tabHeight} is not in the defaults table. */
    private static final int DEFAULT_TAB_HEIGHT = 26;
    /** Gap between a tab's icon and its title. */
    private static final int ICON_TEXT_GAP = 4;

    /** One tab: everything {@code insertTab} takes plus its enabled flag. */
    private static final class Page {
        private String title;
        private Icon icon;
        private Component component;
        private String tip;
        private boolean enabled = true;

        Page(String title, Icon icon, Component component, String tip) {
            this.title = title != null ? title : "";
            this.icon = icon;
            this.component = component;
            this.tip = tip;
        }
    }

    /** The computed strip: the strip rect, the content rect, tab rects and the slots. */
    private static final class Strip {
        private int stripX;
        private int stripY;
        private int stripWidth;
        private int stripHeight;
        private int contentX;
        private int contentY;
        private int contentWidth;
        private int contentHeight;
        private Rectangle[] tabs = new Rectangle[0];
        private Rectangle leading;
        private Rectangle trailing;
    }

    private final List<Page> pages = new ArrayList<>();
    private final List<ChangeListener> changeListeners = new ArrayList<>();
    private int selectedIndex = -1;
    private int tabPlacement = TOP;
    private int tabLayoutPolicy = WRAP_TAB_LAYOUT;
    private int hoverIndex = -1;
    private Component leadingComponent;
    /** How far the tab strip is scrolled when the tabs do not fit (0 = from the top). */
    private int stripOffset;
    private Component trailingComponent;

    public JTabbedPane() {
        super();
    }

    public JTabbedPane(int tabPlacement) {
        super();
        this.tabPlacement = normalisePlacement(tabPlacement);
    }

    public JTabbedPane(int tabPlacement, int tabLayoutPolicy) {
        this(tabPlacement);
        this.tabLayoutPolicy = tabLayoutPolicy;
    }

    private static int normalisePlacement(int placement) {
        return placement == RIGHT ? RIGHT : TOP;
    }

    // ----------------------------------------------------------------- tabs

    /**
     * Inserts a tab, following the JDK: an existing tab for the same component is
     * replaced, the component is added as a child (invisible until it is selected), and
     * a selection at or after the insertion point shifts by one. The one deviation is
     * the first-insert rule in the class doc: a {@code -1} selection stays {@code -1}.
     */
    public void insertTab(String title, Icon icon, Component component, String tip, int index) {
        if (index < 0 || index > pages.size()) {
            throw new IndexOutOfBoundsException("index: " + index + ", tab count: " + pages.size());
        }
        int newIndex = index;
        int existing = indexOfComponent(component);
        if (component != null && existing != -1) {
            removeTabAt(existing);
            if (newIndex > existing) {
                newIndex--;
            }
        }
        int previousSelection = selectedIndex;
        pages.add(newIndex, new Page(title, icon, component, tip));
        if (component != null) {
            addImpl(component, null, -1);
            component.setVisible(false);
        }
        if (previousSelection >= 0 && previousSelection >= newIndex) {
            selectedIndex = previousSelection + 1;
            refreshSelection();
            fireStateChanged();
        }
        invalidate();
        revalidate();
        repaint();
    }

    public void addTab(String title, Icon icon, Component component, String tip) {
        insertTab(title, icon, component, tip, pages.size());
    }

    public void addTab(String title, Icon icon, Component component) {
        insertTab(title, icon, component, null, pages.size());
    }

    public void addTab(String title, Component component) {
        insertTab(title, null, component, null, pages.size());
    }

    /** The JDK's cover: a tab titled by the component's name. */
    public Component add(Component component) {
        addTab(component != null ? component.getName() : null, component);
        return component;
    }

    /** The JDK's cover: a tab with a title. */
    public Component add(String title, Component component) {
        addTab(title, component);
        return component;
    }

    public Component add(Component component, int index) {
        insertTab(component != null ? component.getName() : null, null, component, null, index);
        return component;
    }

    public void add(String title, Component component, int index) {
        insertTab(title, null, component, null, index);
    }

    /** Removes the tab at {@code index}, fixing up the selection like the JDK. */
    public void removeTabAt(int index) {
        checkIndex(index);
        Page page = pages.get(index);
        Component component = page.component;
        int previousSelection = selectedIndex;
        pages.remove(index);
        if (component != null) {
            // The JDK re-shows the component before detaching it, so a panel that is
            // moved to another container is not left hidden.
            component.setVisible(true);
            super.remove(component);
        }
        if (previousSelection > index) {
            selectedIndex = previousSelection - 1;
            refreshSelection();
            fireStateChanged();
        } else if (previousSelection >= pages.size() && previousSelection >= 0) {
            selectedIndex = previousSelection - 1;
            refreshSelection();
            fireStateChanged();
        } else if (index == previousSelection) {
            // The same index now names the following tab: tell the listeners.
            refreshSelection();
            fireStateChanged();
        }
        invalidate();
        revalidate();
        repaint();
    }

    public void remove(int index) {
        removeTabAt(index);
    }

    @Override
    public void remove(Component component) {
        int index = indexOfComponent(component);
        if (index != -1) {
            removeTabAt(index);
            return;
        }
        // Container#remove(comp) would route through JTabbedPane#remove(int); remove the
        // child (a leading/trailing strip component) directly, as the JDK does.
        Component[] children = getComponents();
        for (int i = 0; i < children.length; i++) {
            if (children[i] == component) {
                super.remove(i);
                break;
            }
        }
        revalidate();
        repaint();
    }

    @Override
    public void removeAll() {
        selectedIndex = -1;
        int count = getTabCount();
        for (int i = count - 1; i >= 0; i--) {
            removeTabAt(i);
        }
        invalidate();
        revalidate();
        repaint();
    }

    public int getTabCount() {
        return pages.size();
    }

    /** The component of the tab at {@code index} (the JDK's tab-indexed accessor). */
    public Component getComponentAt(int index) {
        checkIndex(index);
        return pages.get(index).component;
    }

    /** Port alias for {@link #getComponentAt(int)}: "the thing at this tab". */
    public Component getTabAt(int index) {
        return getComponentAt(index);
    }

    /** Replaces a tab's component, keeping the tab's title/icon/tooltip (the JDK API). */
    public void setComponentAt(int index, Component component) {
        checkIndex(index);
        Page page = pages.get(index);
        if (page.component == component) {
            return;
        }
        Component previous = page.component;
        boolean selected = index == selectedIndex;
        page.component = component;
        if (previous != null) {
            previous.setVisible(false);
            super.remove(previous);
        }
        if (component != null) {
            addImpl(component, null, -1);
            component.setVisible(selected);
        }
        refreshSelection();
        invalidate();
        revalidate();
        repaint();
    }

    public String getTitleAt(int index) {
        checkIndex(index);
        return pages.get(index).title;
    }

    public void setTitleAt(int index, String title) {
        checkIndex(index);
        String old = pages.get(index).title;
        pages.get(index).title = title != null ? title : "";
        firePropertyChange("titleAt", old, pages.get(index).title);
        revalidate();
        repaint();
    }

    public Icon getIconAt(int index) {
        checkIndex(index);
        return pages.get(index).icon;
    }

    public void setIconAt(int index, Icon icon) {
        checkIndex(index);
        pages.get(index).icon = icon;
        revalidate();
        repaint();
    }

    public String getToolTipTextAt(int index) {
        checkIndex(index);
        return pages.get(index).tip;
    }

    public void setToolTipTextAt(int index, String toolTipText) {
        checkIndex(index);
        pages.get(index).tip = toolTipText;
    }

    public boolean isEnabledAt(int index) {
        checkIndex(index);
        return pages.get(index).enabled;
    }

    public void setEnabledAt(int index, boolean enabled) {
        checkIndex(index);
        pages.get(index).enabled = enabled;
        repaint();
    }

    /** The tab index of a component, or -1 (the JDK API). */
    public int indexOfComponent(Component component) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).component == component) {
                return i;
            }
        }
        return -1;
    }

    /** The first tab with that title, or -1 (the JDK API). */
    public int indexOfTab(String title) {
        for (int i = 0; i < pages.size(); i++) {
            String text = pages.get(i).title;
            if (text == null ? title == null : text.equals(title)) {
                return i;
            }
        }
        return -1;
    }

    /** The first tab with that icon, or -1 (the JDK API). */
    public int indexOfTab(Icon icon) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).icon == icon) {
                return i;
            }
        }
        return -1;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("index: " + index + ", tab count: " + pages.size());
        }
    }

    /** The tab whose rectangle contains the point, or -1 (the JDK API). */
    public int indexAtLocation(int x, int y) {
        Strip strip = computeStrip();
        for (int i = 0; i < strip.tabs.length; i++) {
            if (strip.tabs[i] != null && strip.tabs[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The rectangle of a tab in this pane's coordinates, or null when {@code index} is
     * out of range (the JDK's UI returns null there too). The sidebar host hit-tests
     * these for its right-click popup, so they are the real tab rectangles.
     */
    public Rectangle getBoundsAt(int index) {
        Strip strip = computeStrip();
        if (index < 0 || index >= strip.tabs.length || strip.tabs[index] == null) {
            return null;
        }
        return new Rectangle(strip.tabs[index]);
    }

    /** Serves the hovered tab's tooltip, which is how the host's long-press identifies one. */
    @Override
    public String getToolTipText(MouseEvent event) {
        if (event != null) {
            int index = indexAtLocation(event.getX(), event.getY());
            if (index != -1) {
                String tip = pages.get(index).tip;
                if (tip != null) {
                    return tip;
                }
            }
        }
        return super.getToolTipText(event);
    }

    // ------------------------------------------------------------ selection

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Selects a tab, or none with {@code -1}. No event fires when nothing changes. */
    public void setSelectedIndex(int index) {
        if (index != -1) {
            checkIndex(index);
        }
        if (selectedIndex == index) {
            return;
        }
        selectedIndex = index;
        refreshSelection();
        invalidate();
        revalidate();
        repaint();
        fireStateChanged();
    }

    public Component getSelectedComponent() {
        return selectedIndex == -1 ? null : pages.get(selectedIndex).component;
    }

    public void setSelectedComponent(Component component) {
        int index = indexOfComponent(component);
        if (index == -1) {
            throw new IllegalArgumentException("component not found in tabbed pane");
        }
        setSelectedIndex(index);
    }

    /** Makes exactly the selected tab's component visible (the JDK's visible-component rule). */
    private void refreshSelection() {
        for (int i = 0; i < pages.size(); i++) {
            Component component = pages.get(i).component;
            if (component != null) {
                component.setVisible(i == selectedIndex);
            }
        }
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

    /** Fires one {@code ChangeEvent} to every registered listener. */
    protected void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : new ArrayList<>(changeListeners)) {
            listener.stateChanged(event);
        }
    }

    // ------------------------------------------------------------ placement

    public int getTabPlacement() {
        return tabPlacement;
    }

    /** Only {@code RIGHT} keeps a vertical strip; every other value means {@code TOP}. */
    public void setTabPlacement(int tabPlacement) {
        int placement = normalisePlacement(tabPlacement);
        if (this.tabPlacement == placement) {
            return;
        }
        int old = this.tabPlacement;
        this.tabPlacement = placement;
        firePropertyChange("tabPlacement", old, placement);
        revalidate();
        repaint();
    }

    public int getTabLayoutPolicy() {
        return tabLayoutPolicy;
    }

    /** Stored and reported; the port always lays the tabs out in one run. */
    public void setTabLayoutPolicy(int tabLayoutPolicy) {
        if (this.tabLayoutPolicy == tabLayoutPolicy) {
            return;
        }
        int old = this.tabLayoutPolicy;
        this.tabLayoutPolicy = tabLayoutPolicy;
        firePropertyChange("tabLayoutPolicy", old, tabLayoutPolicy);
        revalidate();
        repaint();
    }

    private boolean isVertical() {
        return tabPlacement == RIGHT;
    }

    // ------------------------------------------------- leading/trailing slots

    /**
     * Adopts a new leading/trailing component as soon as its client property is set, so
     * the host can set it right after constructing the pane and have it laid out by the
     * next validation.
     */
    @Override
    public void putClientProperty(Object key, Object value) {
        super.putClientProperty(key, value);
        if (LEADING_COMPONENT_KEY.equals(key) || TRAILING_COMPONENT_KEY.equals(key)) {
            syncStripComponents();
            revalidate();
            repaint();
        }
    }

    private Component stripComponent(String key) {
        Object value = getClientProperty(key);
        return value instanceof Component ? (Component) value : null;
    }

    /** Adds/removes the leading and trailing components so the children match the properties. */
    private void syncStripComponents() {
        Component leading = stripComponent(LEADING_COMPONENT_KEY);
        if (leading != leadingComponent) {
            if (leadingComponent != null) {
                super.remove(leadingComponent);
            }
            leadingComponent = leading;
            if (leading != null) {
                addImpl(leading, null, -1);
            }
        }
        Component trailing = stripComponent(TRAILING_COMPONENT_KEY);
        if (trailing != trailingComponent) {
            if (trailingComponent != null) {
                super.remove(trailingComponent);
            }
            trailingComponent = trailing;
            if (trailing != null) {
                addImpl(trailing, null, -1);
            }
        }
    }

    /** The leading component, or null (also readable through its client property). */
    public Component getLeadingComponent() {
        return leadingComponent;
    }

    /** The trailing component, or null (also readable through its client property). */
    public Component getTrailingComponent() {
        return trailingComponent;
    }

    // -------------------------------------------------------------- geometry

    private int tabHeight() {
        int height = UIManager.getInt("TabbedPane.tabHeight");
        return height > 0 ? height : DEFAULT_TAB_HEIGHT;
    }

    private Insets tabInsets() {
        Object value = UIManager.get("TabbedPane.tabInsets");
        if (value instanceof Insets) {
            return (Insets) value;
        }
        return new Insets(2, 5, 2, 5);
    }

    private FontMetrics tabFontMetrics(Font font) {
        return font != null ? getFontMetrics(font) : null;
    }

    /** The tab's length along the strip axis. */
    private int tabLength(int index) {
        if (isVertical()) {
            return tabHeight();
        }
        Page page = pages.get(index);
        Insets insets = tabInsets();
        if (page.icon == null && (page.title == null || page.title.isEmpty())) {
            return insets.left + insets.right + tabHeight();
        }
        int width = insets.left + insets.right;
        if (page.icon != null) {
            width += page.icon.getIconWidth();
        }
        if (page.title != null && !page.title.isEmpty()) {
            FontMetrics fm = tabFontMetrics(getFont());
            if (page.icon != null) {
                width += ICON_TEXT_GAP;
            }
            width += fm != null ? fm.stringWidth(page.title) : page.title.length() * 7;
        }
        return Math.max(width, tabHeight());
    }

    /** The tab's size across the strip axis. */
    private int tabCrossSize(int index) {
        if (!isVertical()) {
            return tabHeight();
        }
        Page page = pages.get(index);
        Insets insets = tabInsets();
        int cross = page.icon != null ? page.icon.getIconWidth() : 0;
        return Math.max(tabHeight(), cross + insets.left + insets.right);
    }

    /** The strip's thickness: the widest tab (or slot) plus the tab-area padding. */
    /**
     * The strip's cross-axis thickness: the tabs' cross size plus the padding.
     *
     * <p>Deliberately <em>not</em> derived from the leading/trailing components' preferred
     * sizes: those components are laid out <em>inside</em> the strip, so feeding their
     * preferred width back into the strip's thickness makes the strip grow on every layout
     * pass (observed on the device as the sidebar strip creeping leftwards over the panel).
     */
    private int stripThickness() {
        int cross = tabHeight();
        for (int i = 0; i < pages.size(); i++) {
            cross = Math.max(cross, tabCrossSize(i));
        }
        return cross + 2 * TAB_AREA_PAD;
    }

    private int slotLength(Component slot) {
        if (slot == null || !slot.isVisible()) {
            return 0;
        }
        Dimension pref = slot.getPreferredSize();
        return isVertical() ? pref.height : pref.width;
    }

    /** Lays out the strip: see the class doc for every rule this implements. */
    private Strip computeStrip() {
        Strip strip = new Strip();
        Insets insets = getInsets();
        int width = Math.max(0, getWidth() - insets.left - insets.right);
        int height = Math.max(0, getHeight() - insets.top - insets.bottom);
        boolean vertical = isVertical();
        int thickness = Math.min(stripThickness(), vertical ? width : height);

        if (vertical) {
            strip.stripX = insets.left + Math.max(0, width - thickness);
            strip.stripY = insets.top;
            strip.stripWidth = thickness;
            strip.stripHeight = height;
            strip.contentX = insets.left;
            strip.contentY = insets.top;
            strip.contentWidth = Math.max(0, width - thickness);
            strip.contentHeight = height;
        } else {
            strip.stripX = insets.left;
            strip.stripY = insets.top;
            strip.stripWidth = width;
            strip.stripHeight = thickness;
            strip.contentX = insets.left;
            strip.contentY = insets.top + thickness;
            strip.contentWidth = width;
            strip.contentHeight = Math.max(0, height - thickness);
        }

        int stripLength = vertical ? strip.stripHeight : strip.stripWidth;
        int tabsLength = 0;
        int[] lengths = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            lengths[i] = tabLength(i);
            tabsLength += lengths[i];
        }
        int trailingLength = slotLength(trailingComponent);
        int leadingLength = slotLength(leadingComponent);
        if (leadingLength > 0) {
            // Stretched along the strip to what the tabs and the trailing slot leave over.
            leadingLength = Math.max(leadingLength, stripLength - tabsLength - trailingLength);
            leadingLength = Math.min(leadingLength, Math.max(0, stripLength - trailingLength));
        }
        int trailingStart = Math.max(leadingLength + tabsLength, stripLength - trailingLength);
        if (trailingStart + trailingLength > stripLength) {
            trailingStart = Math.max(0, stripLength - trailingLength);
        }

        strip.leading = leadingLength > 0
                ? (vertical
                        ? new Rectangle(strip.stripX, strip.stripY, strip.stripWidth, leadingLength)
                        : new Rectangle(strip.stripX, strip.stripY, leadingLength, strip.stripHeight))
                : null;
        strip.trailing = trailingLength > 0
                ? (vertical
                        ? new Rectangle(strip.stripX, strip.stripY + trailingStart, strip.stripWidth, trailingLength)
                        : new Rectangle(strip.stripX + trailingStart, strip.stripY, trailingLength, strip.stripHeight))
                : null;

        strip.tabs = new Rectangle[pages.size()];
        int available = Math.max(0, stripLength - leadingLength - trailingLength);
        int overflow = Math.max(0, tabsLength - available);
        if (stripOffset > overflow) {
            stripOffset = overflow;
        }
        if (stripOffset < 0) {
            stripOffset = 0;
        }
        int cursor = leadingLength - stripOffset;
        int innerCross = Math.max(0, thickness - 2 * TAB_AREA_PAD);
        for (int i = 0; i < pages.size(); i++) {
            if (vertical) {
                strip.tabs[i] = new Rectangle(strip.stripX + TAB_AREA_PAD, strip.stripY + cursor,
                        innerCross, lengths[i]);
            } else {
                strip.tabs[i] = new Rectangle(strip.stripX + cursor, strip.stripY + TAB_AREA_PAD,
                        lengths[i], innerCross);
            }
            cursor += lengths[i];
        }
        return strip;
    }

    @Override
    public void doLayout() {
        syncStripComponents();
        Strip strip = computeStrip();
        if (leadingComponent != null && strip.leading != null) {
            leadingComponent.setBounds(strip.leading);
        }
        if (trailingComponent != null && strip.trailing != null) {
            trailingComponent.setBounds(strip.trailing);
        }
        for (int i = 0; i < pages.size(); i++) {
            Component component = pages.get(i).component;
            if (component == null) {
                continue;
            }
            if (i == selectedIndex) {
                component.setBounds(strip.contentX, strip.contentY,
                        strip.contentWidth, strip.contentHeight);
            } else {
                // Out of the way: a zero-sized child is skipped when painting and hit
                // testing, which is how the JDK keeps the other tabs from showing.
                component.setBounds(0, 0, 0, 0);
            }
        }
    }

    private Dimension contentSize(boolean preferred) {
        Component component = getSelectedComponent();
        if (component == null) {
            return new Dimension(0, 0);
        }
        return preferred ? component.getPreferredSize() : component.getMinimumSize();
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Insets insets = getInsets();
        Dimension content = contentSize(true);
        int thickness = stripThickness();
        int along = 0;
        for (int i = 0; i < pages.size(); i++) {
            along += tabLength(i);
        }
        along += slotLength(leadingComponent) + slotLength(trailingComponent);
        if (isVertical()) {
            return new Dimension(insets.left + thickness + content.width + insets.right,
                    insets.top + Math.max(along, content.height) + insets.bottom);
        }
        return new Dimension(insets.left + Math.max(along, content.width) + insets.right,
                insets.top + thickness + content.height + insets.bottom);
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        Insets insets = getInsets();
        Dimension content = contentSize(false);
        int thickness = stripThickness();
        if (isVertical()) {
            return new Dimension(insets.left + thickness + content.width + insets.right,
                    insets.top + Math.max(1, content.height) + insets.bottom);
        }
        return new Dimension(insets.left + Math.max(1, content.width) + insets.right,
                insets.top + thickness + content.height + insets.bottom);
    }

    // --------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Strip strip = computeStrip();
        g.setColor(tabBackground());
        g.fillRect(strip.stripX, strip.stripY, strip.stripWidth, strip.stripHeight);
        for (int i = 0; i < strip.tabs.length; i++) {
            Rectangle bounds = strip.tabs[i];
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                continue;
            }
            Color background = null;
            if (i == selectedIndex) {
                background = selectedTabBackground();
            } else if (i == hoverIndex) {
                background = hoverTabBackground();
            }
            if (background != null) {
                g.setColor(background);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            paintTab(g, i, bounds);
        }
    }

    /** Paints one tab's icon and title inside its rectangle. */
    private void paintTab(Graphics g, int index, Rectangle bounds) {
        Page page = pages.get(index);
        boolean enabled = page.enabled && isEnabled();
        Font font = getFont();
        String title = page.title != null && !page.title.isEmpty() ? page.title : null;
        Graphics tab = g.create();
        tab.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
        tab.setFont(font);
        FontMetrics fm = font != null ? tab.getFontMetrics(font) : null;
        Insets insets = tabInsets();
        int iconWidth = page.icon != null ? page.icon.getIconWidth() : 0;
        int iconHeight = page.icon != null ? page.icon.getIconHeight() : 0;
        int textHeight = title != null && fm != null ? fm.getHeight() : 0;
        int textWidth = title != null && fm != null ? fm.stringWidth(title) : 0;
        Color foreground = enabled
                ? tabForeground()
                : JMenuItem.blended(tabForeground(), tabBackground());
        tab.setColor(foreground);

        if (isVertical()) {
            // The strip runs vertically and is only as wide as a tab is tall, so the tab
            // shows its icon centred. The title is not rotated away here: it stays the
            // tab's tooltip (which the host's long-press also surfaces).
            if (page.icon != null) {
                page.icon.paintIcon(this, tab, bounds.x + (bounds.width - iconWidth) / 2,
                        bounds.y + (bounds.height - iconHeight) / 2);
            }
        } else {
            int contentWidth = iconWidth + (title != null && page.icon != null ? ICON_TEXT_GAP : 0) + textWidth;
            int x = bounds.x + Math.max(insets.left, (bounds.width - contentWidth) / 2);
            if (page.icon != null) {
                page.icon.paintIcon(this, tab, x, bounds.y + (bounds.height - iconHeight) / 2);
                x += iconWidth + (title != null ? ICON_TEXT_GAP : 0);
            }
            if (title != null && fm != null) {
                tab.drawString(title, x, bounds.y + (bounds.height + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
        tab.dispose();
    }

    private Color tabBackground() {
        Color color = UIManager.getColor("TabbedPane.tabBackground");
        if (color == null) {
            color = UIManager.getColor("Panel.background");
        }
        return color != null ? color : new Color(0xFF282828);
    }

    private Color selectedTabBackground() {
        Color color = UIManager.getColor("TabbedPane.selectedTabBackground");
        return color != null ? color : tabBackground().brighter();
    }

    private Color hoverTabBackground() {
        Color color = UIManager.getColor("TabbedPane.hoverTabBackground");
        return color != null ? color : selectedTabBackground();
    }

    private Color tabForeground() {
        Color color = UIManager.getColor("TabbedPane.foreground");
        if (color == null) {
            color = getForeground();
        }
        if (color == null) {
            color = UIManager.getColor("Component.foreground");
        }
        return color != null ? color : Color.LIGHT_GRAY;
    }

    // ------------------------------------------------------ mouse/hover state

    /** True when the tabs do not fit the strip and it can be scrolled (wheel/drag). */
    public boolean isStripScrollable() {
        Strip strip = computeStrip();
        int length = 0;
        for (int i = 0; i < strip.tabs.length; i++) {
            if (strip.tabs[i] != null) {
                length += isVertical() ? strip.tabs[i].height : strip.tabs[i].width;
            }
        }
        int available = (isVertical() ? strip.stripHeight : strip.stripWidth)
            - slotLength(leadingComponent) - slotLength(trailingComponent);
        return length > available;
    }

    /** Scrolls the tab strip by {@code notches} wheel notches (negative = towards the top). */
    public void scrollStrip(int notches) {
        stripOffset += notches * tabHeight();
        repaint();
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent event) {
        // Over the strip the wheel scrolls the tabs themselves (a hub install can add more
        // tabs than fit); anywhere else the event bubbles as usual.
        Strip strip = computeStrip();
        Rectangle tab = strip.tabs.length > 0 ? strip.tabs[0] : null;
        boolean overStrip = isVertical()
            ? (event.getX() >= strip.stripX && event.getX() < strip.stripX + strip.stripWidth)
            : (event.getY() >= strip.stripY && event.getY() < strip.stripY + strip.stripHeight);
        if (overStrip && isStripScrollable()) {
            scrollStrip(event.getWheelRotation() > 0 ? 1 : -1);
            event.consume();
            return;
        }
        super.processMouseWheelEvent(event);
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
        int id = event.getID();
        if (id == MouseEvent.MOUSE_EXITED) {
            setHoverIndex(-1);
        } else {
            updateHover(event.getX(), event.getY());
            if (id == MouseEvent.MOUSE_PRESSED && SwingUtilities.isLeftMouseButton(event)) {
                // The JDK selects a tab on press; without this the strip only highlights
                // (hover) and the content never changes.
                int index = indexAtLocation(event.getX(), event.getY());
                if (index >= 0) {
                    setSelectedIndex(index);
                }
            }
        }
        super.processMouseEvent(event);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_MOVED) {
            updateHover(event.getX(), event.getY());
        }
        super.processMouseMotionEvent(event);
    }

    private void updateHover(int x, int y) {
        setHoverIndex(contains(x, y) ? indexAtLocation(x, y) : -1);
    }

    private void setHoverIndex(int index) {
        if (hoverIndex == index) {
            return;
        }
        hoverIndex = index;
        repaint();
    }

    /** The tab under the pointer, or -1. */
    public int getHoverIndex() {
        return hoverIndex;
    }
}

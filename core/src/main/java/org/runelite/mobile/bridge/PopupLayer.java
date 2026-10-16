package org.runelite.mobile.bridge;

import java.awt.Component;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The stage overlay: in-stage popups painted above the sidebar or the top-most window.
 *
 * <p>On a desktop AWT popups are native windows ({@code PopupFactory} creates a
 * heavyweight frame when the popup does not fit into its parent). The port has no native
 * windows for popups, so a menu, a combo-box drop-down, a tooltip or the colour picker
 * registers here with a position in stage-logical coordinates, and
 * {@link UiBridge#paintSidebar}/{@link UiBridge#paintWindow} paint the stack after the
 * base layer. Hit-testing asks this class first, so a popup always wins over the content
 * underneath, exactly like a desktop popup captures the mouse.
 *
 * <p>All state is confined to the Swing dispatch thread; the accessors from other threads
 * only read the size/snapshot.
 */
public final class PopupLayer {

    /** One popup: a component plus its top-left corner in stage-logical coordinates. */
    public static final class Entry {
        public final Component component;
        public final int x;
        public final int y;

        Entry(Component component, int x, int y) {
            this.component = component;
            this.x = x;
            this.y = y;
        }

        public int getWidth() {
            return component.getWidth();
        }

        public int getHeight() {
            return component.getHeight();
        }
    }

    /** Bottom-to-top paint order; the last entry is the top-most popup. */
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private PopupLayer() {
    }

    public static synchronized void show(Component component, int x, int y) {
        hide(component);
        ENTRIES.add(new Entry(component, x, y));
    }

    public static synchronized void hide(Component component) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (ENTRIES.get(i).component == component) {
                ENTRIES.remove(i);
            }
        }
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }

    public static synchronized boolean isEmpty() {
        return ENTRIES.isEmpty();
    }

    public static synchronized List<Entry> entries() {
        return new ArrayList<>(ENTRIES);
    }

    /** Top-most entry containing the point, or null. */
    public static synchronized Entry topMostAt(int x, int y) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            if (x >= e.x && y >= e.y && x < e.x + e.getWidth() && y < e.y + e.getHeight()) {
                return e;
            }
        }
        return null;
    }

    /** Paints every entry, translated to its stage position (caller already scaled). */
    public static synchronized void paint(Graphics2D g) {
        for (Entry e : ENTRIES) {
            if (!e.component.isVisible()) {
                continue;
            }
            Graphics2D gc = (Graphics2D) g.create();
            gc.translate(e.x, e.y);
            gc.clipRect(0, 0, Math.max(0, e.getWidth()), Math.max(0, e.getHeight()));
            e.component.paint(gc);
            gc.dispose();
        }
    }
}

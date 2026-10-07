package org.runelite.mobile.host;

import java.util.ArrayList;
import java.util.List;

/**
 * In-process registry that connects RuneLite's {@code ClientToolbar} to the native side
 * panel.
 *
 * <p>On the desktop, {@code ClientToolbar.addNavigation(button)} puts an icon into the
 * Swing toolbar and {@code openPanel(button)} swaps the toolbar's panel. On this port the
 * toolbar shim ({@code net.runelite.client.ui.ClientToolbar}) has no Swing counterpart, so
 * it records the navigation buttons here instead and the {@link
 * org.runelite.mobile.SidePanel} observes them: a plugin that registered a panel shows up
 * in the Plugins tab with a "panel not available on mobile" note and its config opens in
 * the Config tab (plan D6).
 *
 * <p>All methods are called on the UI thread (plugin lifecycle runs there), so the
 * registry needs no locking beyond the list copy.
 */
public final class PluginPanelRegistry {

    /** Implemented by the side panel; called on the UI thread. */
    public interface Listener {
        void navigationAdded(String name);

        void navigationRemoved(String name);

        void panelOpened(String name);
    }

    private static final List<Object> NAVIGATION = new ArrayList<>();
    private static volatile Listener listener;

    private PluginPanelRegistry() {
    }

    public static void setListener(Listener l) {
        listener = l;
        if (l != null) {
            for (String name : names()) {
                l.navigationAdded(name);
            }
        }
    }

    /** Display name for a navigation button (its tooltip, or a fallback). */
    public static String nameOf(Object button) {
        if (button == null) {
            return "plugin panel";
        }
        try {
            Object tooltip = button.getClass().getMethod("getTooltip").invoke(button);
            if (tooltip instanceof String && !((String) tooltip).isEmpty()) {
                return (String) tooltip;
            }
        } catch (Throwable ignored) {
            // shim without a tooltip getter: fall through to the class name
        }
        return button.getClass().getSimpleName();
    }

    public static synchronized void add(Object button) {
        if (button == null || NAVIGATION.contains(button)) {
            return;
        }
        NAVIGATION.add(button);
        Listener l = listener;
        if (l != null) {
            l.navigationAdded(nameOf(button));
        }
    }

    public static synchronized void remove(Object button) {
        if (button == null || !NAVIGATION.remove(button)) {
            return;
        }
        Listener l = listener;
        if (l != null) {
            l.navigationRemoved(nameOf(button));
        }
    }

    public static synchronized void open(Object button) {
        Listener l = listener;
        if (l != null) {
            l.panelOpened(nameOf(button));
        }
    }

    /** Names of the currently registered navigation buttons, in registration order. */
    public static synchronized List<String> names() {
        List<String> out = new ArrayList<>(NAVIGATION.size());
        for (Object button : NAVIGATION) {
            out.add(nameOf(button));
        }
        return out;
    }

    public static synchronized int size() {
        return NAVIGATION.size();
    }
}

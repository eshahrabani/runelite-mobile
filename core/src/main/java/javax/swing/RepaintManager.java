package javax.swing;

import java.awt.Component;
import java.awt.Rectangle;
import org.runelite.mobile.bridge.UiBridge;

/**
 * Swing {@code RepaintManager}: the port's repaint coalescer.
 *
 * <p>There is no offscreen double-buffer machinery to run here — the stage's paint roots
 * (the sidebar and the virtual windows) are painted by the app on demand — so the
 * manager's job is to answer one question: <em>which layer does this component belong
 * to?</em> A component whose ancestor is the sidebar root or a visible window marks that
 * layer dirty, which tells the app to re-render it. A component with no paint root (the
 * game client's own components) is ignored, which is what keeps
 * {@code Component.repaint()} a no-op there.
 */
public class RepaintManager {
    private static RepaintManager current = new RepaintManager();

    private boolean doubleBufferingEnabled = true;

    public static RepaintManager currentManager(Component c) {
        return current;
    }

    public static RepaintManager currentManager(JComponent c) {
        return current;
    }

    public static void setCurrentManager(RepaintManager aRepaintManager) {
        if (aRepaintManager != null) {
            current = aRepaintManager;
        }
    }

    public static RepaintManager currentManager() {
        return current;
    }

    public boolean isDoubleBufferingEnabled() {
        return doubleBufferingEnabled;
    }

    public void setDoubleBufferingEnabled(boolean flag) {
        this.doubleBufferingEnabled = flag;
    }

    /** Debug: how many dirty marks each component class produced (device profiling). */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> DIRTY_SOURCES =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicInteger DIRTY_TOTAL =
        new java.util.concurrent.atomic.AtomicInteger();

    /** Debug: the busiest dirty sources, as {@code class=count} pairs. */
    public static String dirtySummary(int limit) {
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicInteger>> entries =
            new java.util.ArrayList<>(DIRTY_SOURCES.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder sb = new StringBuilder("marks=").append(DIRTY_TOTAL.get());
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            sb.append(' ').append(entries.get(i).getKey()).append('=')
                .append(entries.get(i).getValue().get());
        }
        return sb.toString();
    }

    /** Debug: clears the counters after a report. */
    public static void resetDirtyStats() {
        DIRTY_SOURCES.clear();
        DIRTY_TOTAL.set(0);
    }

    /** Marks the paint root of {@code c} dirty (no-op when it has none). */
    public void markDirty(Component c, Rectangle r) {
        if (c == null) {
            return;
        }
        DIRTY_TOTAL.incrementAndGet();
        DIRTY_SOURCES.computeIfAbsent(c.getClass().getSimpleName(),
            key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        Component root = c;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        if (root instanceof java.awt.Window) {
            if (root.isVisible()) {
                UiBridge.windowDirty();
            }
        } else if (UiBridge.isSidebarRoot(root)) {
            UiBridge.sidebarDirty();
        }
    }

    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        markDirty(c, new Rectangle(x, y, w, h));
    }

    public void addInvalidComponent(JComponent invalidComponent) {
        markDirty(invalidComponent, null);
    }

    public void markCompletelyDirty(JComponent aComponent) {
        markDirty(aComponent, new Rectangle(0, 0, aComponent.getWidth(), aComponent.getHeight()));
    }

    public void markCompletelyClean(JComponent aComponent) {
    }

    /** Nothing is double-buffered, so there are no dirty regions to flush. */
    public void paintDirtyRegions() {
    }

    /**
     * Coalesces a revalidation: the stage's paint path validates its root before
     * painting, so marking the layer dirty is enough — and it is what makes a
     * {@code revalidate()} end in a laid-out, painted component.
     */
    public void deferValidate(Component c) {
        markDirty(c, null);
    }

    /** Paints a rectangle of {@code c} right away into the current stage buffer. */
    public void paintImmediately(JComponent c, int x, int y, int w, int h) {
        UiBridge.paintImmediately(c, x, y, w, h);
    }
}

package java.awt.dnd;

import java.awt.Component;

/**
 * Hand-written {@code java.awt.dnd.DragSource}.
 *
 * <p>The port has no native drag-and-drop and no {@code SystemFlavorMap}: a
 * {@code DragSource} here is a placeholder that keeps the client's references resolvable
 * and its thresholds sane. {@link #getDragThreshold()} returning the desktop default
 * (3) is what matters — RuneLite's {@code DragAndDropReorderPane} uses it as the
 * pixel distance after which a press turns into a reorder drag, and that reorder runs
 * entirely through plain mouse events, never through {@link DragGestureRecognizer}.
 */
public class DragSource {

    /** The desktop's drag threshold in pixels. */
    private static final int DRAG_THRESHOLD = 3;

    private static final DragSource DEFAULT = new DragSource();

    public DragSource() {
    }

    public static DragSource getDefaultDragSource() {
        return DEFAULT;
    }

    /** No drag images exist here, and nothing asks for one at runtime. */
    public static boolean isDragImageSupported() {
        return false;
    }

    public static int getDragThreshold() {
        return DRAG_THRESHOLD;
    }

    /**
     * Creates a recogniser for {@code c}.
     *
     * <p>It stores its listeners and never fires: the port has no gesture detection, and
     * the one client that builds one (an AWT drag-reorder panel) drives the reorder from
     * mouse events. Registering a listener therefore has no observable effect beyond
     * keeping a reference.
     */
    public DragGestureRecognizer createDefaultDragGestureRecognizer(Component c, int actions,
                                                                   DragGestureListener dgl) {
        return new DragGestureRecognizer(this, c, actions, dgl);
    }
}

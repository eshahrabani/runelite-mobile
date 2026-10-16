package java.awt.dnd;

import java.util.EventListener;

/**
 * Hand-written {@code java.awt.dnd.DragGestureListener}: the callback a
 * {@link DragGestureRecognizer} fires on the platform's drag gesture.
 *
 * <p>It is never fired by this port — see {@link DragGestureRecognizer} — but the type
 * must exist because {@code DragSource.createDefaultDragGestureRecognizer} takes one.
 */
public interface DragGestureListener extends EventListener {

    void dragGestureRecognized(DragGestureEvent dge);
}

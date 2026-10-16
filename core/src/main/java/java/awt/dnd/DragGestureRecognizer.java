package java.awt.dnd;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hand-written {@code java.awt.dnd.DragGestureRecognizer}.
 *
 * <p>On the desktop AWT watches the component's mouse stream and fires
 * {@link DragGestureListener#dragGestureRecognized} once the gesture crosses the drag
 * threshold. This port has no native drag-and-drop: the recogniser registers itself with
 * the component (so a caller that adds its own listeners keeps the same ordering) and
 * <em>never</em> fires by itself. RuneLite's {@code DragAndDropReorderPane} reorders
 * through plain mouse events, which is exactly why the recogniser may stay inert.
 *
 * <p>Deviations from the JDK: the class is concrete rather than abstract (the port has
 * one recogniser, so there is nothing to specialise) and it does not implement
 * {@code Serializable}.
 */
public class DragGestureRecognizer implements MouseListener, MouseMotionListener {

    private final DragSource dragSource;
    private int sourceActions;
    private final List<DragGestureListener> listeners = new ArrayList<>();
    private Component component;
    private MouseEvent triggerEvent;

    protected DragGestureRecognizer(DragSource ds, Component c, int srcActions, DragGestureListener dgl) {
        this.dragSource = ds != null ? ds : DragSource.getDefaultDragSource();
        this.sourceActions = srcActions;
        if (dgl != null) {
            listeners.add(dgl);
        }
        if (c != null) {
            setComponent(c);
        }
    }

    public DragSource getDragSource() {
        return dragSource;
    }

    public Component getComponent() {
        return component;
    }

    public void setComponent(Component c) {
        if (component != null) {
            component.removeMouseListener(this);
            component.removeMouseMotionListener(this);
        }
        component = c;
        if (component != null) {
            component.addMouseListener(this);
            component.addMouseMotionListener(this);
        }
    }

    public int getSourceActions() {
        return sourceActions;
    }

    public void setSourceActions(int actions) {
        this.sourceActions = actions;
    }

    public List<DragGestureListener> getDragGestureListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public void addDragGestureListener(DragGestureListener dgl) {
        if (dgl != null && !listeners.contains(dgl)) {
            listeners.add(dgl);
        }
    }

    public void removeDragGestureListener(DragGestureListener dgl) {
        listeners.remove(dgl);
    }

    /** The event that started the gesture; always {@code null} on this port. */
    public MouseEvent getTriggerEvent() {
        return triggerEvent;
    }

    /** No gesture state exists to reset; provided for API compatibility. */
    public void resetRecognizer() {
        triggerEvent = null;
    }

    /**
     * Notifies the registered listeners. The default recogniser never calls this (so no
     * listener ever runs), but a subclass may.
     */
    protected void fireDragGestureRecognized(int dragAction, Point p) {
        if (listeners.isEmpty()) {
            return;
        }
        DragGestureEvent event = new DragGestureEvent(this, dragAction, p, null);
        for (DragGestureListener listener : new ArrayList<>(listeners)) {
            listener.dragGestureRecognized(event);
        }
    }

    // ------------------------------------------------ the listened-to mouse stream

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }
}

package java.awt.dnd;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hand-written {@code java.awt.dnd.DragGestureEvent}.
 *
 * <p>The callback argument of {@link DragGestureListener#dragGestureRecognized}. It never
 * reaches a listener on this port (see {@link DragGestureRecognizer}), so only the
 * accessors the JDK documents are provided, reading the values a real recogniser would
 * have passed in.
 */
public class DragGestureEvent {

    private final DragGestureRecognizer recognizer;
    private final int action;
    private final Point origin;
    private final List<InputEvent> events;

    public DragGestureEvent(DragGestureRecognizer dgr, int act, Point ori,
                            List<? extends InputEvent> evs) {
        this.recognizer = dgr;
        this.action = act;
        this.origin = ori == null ? new Point() : ori;
        this.events = evs == null
                ? Collections.<InputEvent>emptyList()
                : new ArrayList<InputEvent>(evs);
    }

    public DragGestureRecognizer getSourceAsDragGestureRecognizer() {
        return recognizer;
    }

    public java.awt.Component getComponent() {
        return recognizer == null ? null : recognizer.getComponent();
    }

    public DragSource getDragSource() {
        return recognizer == null ? null : recognizer.getDragSource();
    }

    public int getDragAction() {
        return action;
    }

    public Point getDragOrigin() {
        return new Point(origin);
    }

    public java.util.Iterator<InputEvent> iterator() {
        return events.iterator();
    }

    public Object[] toArray() {
        return events.toArray();
    }

    public Object[] toArray(Object[] array) {
        return events.toArray(array);
    }
}

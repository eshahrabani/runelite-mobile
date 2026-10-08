package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

/**
 * AWT Shape compatibility stub for Android runtime.
 *
 * <p>Every method is a {@code default} so the geometry classes only override
 * what they can answer exactly. The overlay surface only needs bounding boxes
 * and hit testing; path iteration and stroked outlines are not part of the
 * interface.
 */
public interface Shape {
    /** Bounding box, or null when the implementor cannot produce one. */
    default Rectangle getBounds() {
        return null;
    }

    default Rectangle2D getBounds2D() {
        return null;
    }

    default boolean contains(double x, double y) {
        return false;
    }

    /** Hit test for a point; null is not contained. */
    default boolean contains(java.awt.geom.Point2D p) {
        return p != null && contains(p.getX(), p.getY());
    }

    default boolean contains(int x, int y) {
        return contains((double) x, (double) y);
    }

    default boolean contains(Rectangle2D r) {
        return getBounds2D() != null && getBounds2D().contains(r);
    }

    default boolean intersects(double x, double y, double w, double h) {
        Rectangle2D b = getBounds2D();
        return b != null && b.intersects(x, y, w, h);
    }

    /** Hit test for a rectangle; null never intersects. */
    default boolean intersects(Rectangle2D r) {
        return r != null && intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    default boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }

    default PathIterator getPathIterator(AffineTransform at) {
        return null;
    }

    default PathIterator getPathIterator(AffineTransform at, double flatness) {
        return null;
    }
}

package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * AWT geom.Area compatibility stub for Android runtime.
 *
 * <p>Modelled as a list of added shapes minus a list of subtracted shapes:
 * {@link #contains(double, double)} is true when some added shape contains the
 * point and no subtracted shape does, and drawing visits the added outlines.
 * Exact boolean path arithmetic (curved intersections, winding) is not
 * implemented — approximate outlines are an accepted fidelity limit of the
 * mobile shape surface.
 */
public class Area implements Shape {
    private final List<Shape> added = new ArrayList<>();
    private final List<Shape> subtracted = new ArrayList<>();

    public Area() {
    }

    public Area(Shape s) {
        if (s instanceof Area) {
            Area a = (Area) s;
            added.addAll(a.added);
            subtracted.addAll(a.subtracted);
        } else if (s != null) {
            added.add(s);
        }
    }

    public void add(Area area) {
        if (area == null) {
            return;
        }
        added.addAll(area.added);
        subtracted.addAll(area.subtracted);
    }

    public void subtract(Area area) {
        if (area == null) {
            return;
        }
        subtracted.addAll(area.added);
    }

    public void intersect(Area area) {
        if (area == null || added.isEmpty()) {
            added.clear();
            return;
        }
        Rectangle a = area.getBounds();
        for (int i = added.size() - 1; i >= 0; i--) {
            Rectangle b = added.get(i).getBounds();
            if (b == null || a == null || !a.intersects(b)) {
                added.remove(i);
            }
        }
    }

    public void exclusiveOr(Area area) {
        add(area);
    }

    public void reset() {
        added.clear();
        subtracted.clear();
    }

    public boolean isEmpty() {
        return added.isEmpty();
    }

    @Override
    public boolean contains(double x, double y) {
        if (!anyContains(added, x, y)) {
            return false;
        }
        return !anyContains(subtracted, x, y);
    }

    @Override
    public boolean contains(int x, int y) {
        return contains((double) x, (double) y);
    }

    private static boolean anyContains(List<Shape> shapes, double x, double y) {
        for (int i = 0; i < shapes.size(); i++) {
            if (shapes.get(i).contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Rectangle getBounds() {
        Rectangle r = null;
        for (int i = 0; i < added.size(); i++) {
            Rectangle b = added.get(i).getBounds();
            if (b == null) {
                continue;
            }
            r = r == null ? new Rectangle(b) : union(r, b);
        }
        return r == null ? new Rectangle() : r;
    }

    @Override
    public Rectangle2D getBounds2D() {
        Rectangle r = getBounds();
        return new Rectangle2D.Double(r.x, r.y, r.width, r.height);
    }

    private static Rectangle union(Rectangle a, Rectangle b) {
        int x1 = Math.min(a.x, b.x);
        int y1 = Math.min(a.y, b.y);
        int x2 = Math.max(a.x + a.width, b.x + b.width);
        int y2 = Math.max(a.y + a.height, b.y + b.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    /** Added shapes, in add order; used by the rasteriser to fill the area. */
    public List<Shape> getShapes() {
        return added;
    }

    /** Shapes subtracted from the union; used to skip their outlines. */
    public List<Shape> getSubtractedShapes() {
        return subtracted;
    }
}

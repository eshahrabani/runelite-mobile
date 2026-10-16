package java.awt;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * AWT Rectangle compatibility stub for Android runtime.
 *
 * <p>Implements {@link Shape} so the rasteriser can dispatch on it directly.
 */
public class Rectangle implements Shape {
    public static final int OUT_LEFT = 1;
    public static final int OUT_TOP = 2;
    public static final int OUT_RIGHT = 4;
    public static final int OUT_BOTTOM = 8;

    public int x;
    public int y;
    public int width;
    public int height;

    public Rectangle() {}

    public Rectangle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rectangle(Dimension d) {
        this(0, 0, d.width, d.height);
    }

    public Rectangle(Point p) {
        this(p.x, p.y, 0, 0);
    }

    public Rectangle(Point p, Dimension d) {
        this(p.x, p.y, d.width, d.height);
    }

    public Rectangle(Rectangle r) {
        this(r.x, r.y, r.width, r.height);
    }

    /** The union of this rectangle and {@code r} (the JDK's {@code union}). */
    public Rectangle union(Rectangle r) {
        if (r == null) {
            return new Rectangle(this);
        }
        long tx1 = Math.min(x, r.x);
        long ty1 = Math.min(y, r.y);
        long tx2 = Math.max((long) x + width, (long) r.x + r.width);
        long ty2 = Math.max((long) y + height, (long) r.y + r.height);
        return new Rectangle((int) tx1, (int) ty1, (int) (tx2 - tx1), (int) (ty2 - ty1));
    }

    /** The largest rectangle contained in both this and {@code r}. */
    public Rectangle intersection(Rectangle r) {
        if (r == null) {
            return new Rectangle(this);
        }
        long tx1 = Math.max(x, r.x);
        long ty1 = Math.max(y, r.y);
        long tx2 = Math.min((long) x + width, (long) r.x + r.width);
        long ty2 = Math.min((long) y + height, (long) r.y + r.height);
        if (tx2 < tx1 || ty2 < ty1) {
            return new Rectangle((int) tx1, (int) ty1, 0, 0);
        }
        return new Rectangle((int) tx1, (int) ty1, (int) (tx2 - tx1), (int) (ty2 - ty1));
    }

    public Rectangle createIntersection(java.awt.geom.Rectangle2D r) {
        if (r == null) {
            return new Rectangle(this);
        }
        return intersection(new Rectangle((int) Math.floor(r.getX()), (int) Math.floor(r.getY()),
                (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight())));
    }

    public Rectangle createUnion(java.awt.geom.Rectangle2D r) {
        if (r == null) {
            return new Rectangle(this);
        }
        return union(new Rectangle((int) Math.floor(r.getX()), (int) Math.floor(r.getY()),
                (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight())));
    }

    /** Grows this rectangle so it contains the given rectangle. */
    public void add(Rectangle r) {
        if (r != null) {
            Rectangle u = union(r);
            setBounds(u.x, u.y, u.width, u.height);
        }
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    @Override
    public Rectangle2D getBounds2D() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double getMaxX() {
        return x + width;
    }

    public double getMaxY() {
        return y + height;
    }

    public Point getLocation() {
        return new Point(x, y);
    }

    public Dimension getSize() {
        return new Dimension(width, height);
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setLocation(Point p) {
        setLocation(p.x, p.y);
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setSize(Dimension d) {
        setSize(d.width, d.height);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setBounds(Rectangle r) {
        setBounds(r.x, r.y, r.width, r.height);
    }

    public void setRect(double x, double y, double width, double height) {
        this.x = (int) x;
        this.y = (int) y;
        this.width = (int) width;
        this.height = (int) height;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    public void translate(int dx, int dy) {
        this.x += dx;
        this.y += dy;
    }

    public void grow(int h, int v) {
        x -= h;
        y -= v;
        width += h * 2;
        height += v * 2;
    }

    public void add(Point p) {
        add(p.x, p.y);
    }

    public void add(int newx, int newy) {
        int x1 = Math.min(x, newx);
        int y1 = Math.min(y, newy);
        int x2 = Math.max(x + width, newx);
        int y2 = Math.max(y + height, newy);
        x = x1;
        y = y1;
        width = x2 - x1;
        height = y2 - y1;
    }

    @Override
    public boolean contains(double x, double y) {
        return contains((int) x, (int) y);
    }

    @Override
    public boolean contains(int x, int y) {
        return x >= this.x && y >= this.y && x < this.x + width && y < this.y + height;
    }

    public boolean contains(Point p) {
        return contains(p.x, p.y);
    }

    public boolean contains(Rectangle r) {
        return contains(r.x, r.y) && contains(r.x + r.width, r.y + r.height);
    }

    public boolean intersects(Rectangle r) {
        return r.width > 0 && r.height > 0 && width > 0 && height > 0
                && r.x < x + width && r.x + r.width > x
                && r.y < y + height && r.y + r.height > y;
    }

    public int outcode(double px, double py) {
        int out = 0;
        if (width <= 0) {
            out |= 5;
        } else if (px < x) {
            out |= 1;
        } else if (px > x + width) {
            out |= 4;
        }
        if (height <= 0) {
            out |= 10;
        } else if (py < y) {
            out |= 2;
        } else if (py > y + height) {
            out |= 8;
        }
        return out;
    }

    public int outcode(Point2D p) {
        return outcode(p.getX(), p.getY());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Rectangle)) {
            return false;
        }
        Rectangle r = (Rectangle) obj;
        return x == r.x && y == r.y && width == r.width && height == r.height;
    }

    @Override
    public int hashCode() {
        return ((x * 31 + y) * 31 + width) * 31 + height;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height + "]";
    }
}

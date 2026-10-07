package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/**
 * AWT geom.Rectangle2D compatibility stub for Android runtime.
 */
public abstract class Rectangle2D implements Shape {
    public abstract double getX();

    public abstract double getY();

    public abstract double getWidth();

    public abstract double getHeight();

    /**
     * Resets the rectangle. Concrete subclasses override this; the base
     * implementation is a no-op so minimal anonymous subclasses still link.
     */
    public void setRect(double x, double y, double w, double h) {
    }

    public void setFrame(double x, double y, double w, double h) {
        setRect(x, y, w, h);
    }

    public void setFrameFromDiagonal(double x1, double y1, double x2, double y2) {
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        setRect(x, y, Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    public double getMinX() {
        return getX();
    }

    public double getMinY() {
        return getY();
    }

    public double getMaxX() {
        return getX() + getWidth();
    }

    public double getMaxY() {
        return getY() + getHeight();
    }

    public double getCenterX() {
        return getX() + getWidth() / 2.0;
    }

    public double getCenterY() {
        return getY() + getHeight() / 2.0;
    }

    public boolean isEmpty() {
        return getWidth() <= 0.0 || getHeight() <= 0.0;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) Math.floor(getX()), (int) Math.floor(getY()),
                (int) Math.ceil(getWidth()), (int) Math.ceil(getHeight()));
    }

    @Override
    public Rectangle2D getBounds2D() {
        return new Double(getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public boolean contains(double x, double y) {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0.0 || h <= 0.0) {
            return false;
        }
        double x0 = getX();
        double y0 = getY();
        return x >= x0 && y >= y0 && x < x0 + w && y < y0 + h;
    }

    @Override
    public boolean contains(Rectangle2D r) {
        return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    public boolean contains(double x, double y, double w, double h) {
        if (isEmpty() || w <= 0.0 || h <= 0.0) {
            return false;
        }
        double x0 = getX();
        double y0 = getY();
        return x >= x0 && y >= y0 && (x + w) <= x0 + getWidth() && (y + h) <= y0 + getHeight();
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        if (isEmpty() || w <= 0.0 || h <= 0.0) {
            return false;
        }
        double x0 = getX();
        double y0 = getY();
        return x + w > x0 && y + h > y0 && x < x0 + getWidth() && y < y0 + getHeight();
    }

    public boolean intersects(Rectangle2D r) {
        return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    public boolean contains(java.awt.geom.Point2D p) {
        return contains(p.getX(), p.getY());
    }

    public int outcode(double x, double y) {
        int out = 0;
        if (getWidth() <= 0.0) {
            out |= java.awt.Rectangle.OUT_LEFT | java.awt.Rectangle.OUT_RIGHT;
        } else if (x < getX()) {
            out |= java.awt.Rectangle.OUT_LEFT;
        } else if (x > getMaxX()) {
            out |= java.awt.Rectangle.OUT_RIGHT;
        }
        if (getHeight() <= 0.0) {
            out |= java.awt.Rectangle.OUT_TOP | java.awt.Rectangle.OUT_BOTTOM;
        } else if (y < getY()) {
            out |= java.awt.Rectangle.OUT_TOP;
        } else if (y > getMaxY()) {
            out |= java.awt.Rectangle.OUT_BOTTOM;
        }
        return out;
    }

    public int outcode(Point2D p) {
        return outcode(p.getX(), p.getY());
    }

    public Rectangle2D createIntersection(Rectangle2D r) {
        double x1 = Math.max(getX(), r.getX());
        double y1 = Math.max(getY(), r.getY());
        double x2 = Math.min(getMaxX(), r.getMaxX());
        double y2 = Math.min(getMaxY(), r.getMaxY());
        return new Double(x1, y1, Math.max(0.0, x2 - x1), Math.max(0.0, y2 - y1));
    }

    public Rectangle2D createUnion(Rectangle2D r) {
        double x1 = Math.min(getX(), r.getX());
        double y1 = Math.min(getY(), r.getY());
        double x2 = Math.max(getMaxX(), r.getMaxX());
        double y2 = Math.max(getMaxY(), r.getMaxY());
        return new Double(x1, y1, x2 - x1, y2 - y1);
    }

    public PathIterator getPathIterator(AffineTransform at) {
        return null;
    }

    public PathIterator getPathIterator(AffineTransform at, double flatness) {
        return null;
    }

    /** Origin at (0,0) accumulating the union of the added rectangle. */
    public void add(double newx, double newy) {
        double x1 = Math.min(getMinX(), newx);
        double x2 = Math.max(getMaxX(), newx);
        double y1 = Math.min(getMinY(), newy);
        double y2 = Math.max(getMaxY(), newy);
        setRect(x1, y1, x2 - x1, y2 - y1);
    }

    public void add(Rectangle2D r) {
        double x1 = Math.min(getMinX(), r.getMinX());
        double x2 = Math.max(getMaxX(), r.getMaxX());
        double y1 = Math.min(getMinY(), r.getMinY());
        double y2 = Math.max(getMaxY(), r.getMaxY());
        setRect(x1, y1, x2 - x1, y2 - y1);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[x=" + getX() + ",y=" + getY()
                + ",w=" + getWidth() + ",h=" + getHeight() + "]";
    }

    public static class Double extends Rectangle2D {
        public double x;
        public double y;
        public double width;
        public double height;

        public Double() {
        }

        public Double(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public double getWidth() {
            return width;
        }

        @Override
        public double getHeight() {
            return height;
        }

        @Override
        public void setRect(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }
    }

    public static class Float extends Rectangle2D {
        public float x;
        public float y;
        public float width;
        public float height;

        public Float() {
        }

        public Float(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public double getWidth() {
            return width;
        }

        @Override
        public double getHeight() {
            return height;
        }

        @Override
        public void setRect(double x, double y, double w, double h) {
            this.x = (float) x;
            this.y = (float) y;
            this.width = (float) w;
            this.height = (float) h;
        }
    }
}

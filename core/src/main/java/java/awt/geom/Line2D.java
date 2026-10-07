package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/**
 * AWT geom.Line2D compatibility stub for Android runtime.
 */
public abstract class Line2D implements Shape {

    protected Line2D() {
    }

    public abstract double getX1();

    public abstract double getY1();

    public abstract double getX2();

    public abstract double getY2();

    public abstract void setLine(double x1, double y1, double x2, double y2);

    @Override
    public Rectangle getBounds() {
        int x = (int) Math.floor(Math.min(getX1(), getX2()));
        int y = (int) Math.floor(Math.min(getY1(), getY2()));
        int w = (int) Math.ceil(Math.abs(getX2() - getX1()));
        int h = (int) Math.ceil(Math.abs(getY2() - getY1()));
        return new Rectangle(x, y, w, h);
    }

    @Override
    public Rectangle2D getBounds2D() {
        double x = Math.min(getX1(), getX2());
        double y = Math.min(getY1(), getY2());
        return new Rectangle2D.Double(x, y, Math.abs(getX2() - getX1()), Math.abs(getY2() - getY1()));
    }

    @Override
    public boolean contains(double x, double y) {
        double x1 = getX1();
        double y1 = getY1();
        double x2 = getX2();
        double y2 = getY2();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0.0) {
            return x == x1 && y == y1;
        }
        double t = ((x - x1) * dx + (y - y1) * dy) / lenSq;
        if (t < 0.0 || t > 1.0) {
            return false;
        }
        double px = x1 + t * dx;
        double py = y1 + t * dy;
        return Math.abs(x - px) < 0.5 && Math.abs(y - py) < 0.5;
    }

    public static class Double extends Line2D {
        public double x1;
        public double y1;
        public double x2;
        public double y2;

        public Double() {
        }

        public Double(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public double getX1() {
            return x1;
        }

        @Override
        public double getY1() {
            return y1;
        }

        @Override
        public double getX2() {
            return x2;
        }

        @Override
        public double getY2() {
            return y2;
        }

        @Override
        public void setLine(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    public static class Float extends Line2D {
        public float x1;
        public float y1;
        public float x2;
        public float y2;

        public Float() {
        }

        public Float(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public double getX1() {
            return x1;
        }

        @Override
        public double getY1() {
            return y1;
        }

        @Override
        public double getX2() {
            return x2;
        }

        @Override
        public double getY2() {
            return y2;
        }

        @Override
        public void setLine(double x1, double y1, double x2, double y2) {
            this.x1 = (float) x1;
            this.y1 = (float) y1;
            this.x2 = (float) x2;
            this.y2 = (float) y2;
        }
    }
}

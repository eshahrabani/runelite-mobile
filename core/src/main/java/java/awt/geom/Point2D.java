package java.awt.geom;

/**
 * AWT geom.Point2D compatibility stub for Android runtime.
 */
public abstract class Point2D {
    public abstract double getX();

    public abstract double getY();

    public abstract void setLocation(double x, double y);

    public void setLocation(Point2D p) {
        setLocation(p.getX(), p.getY());
    }

    public double distanceSq(double px, double py) {
        double dx = getX() - px;
        double dy = getY() - py;
        return dx * dx + dy * dy;
    }

    public double distance(double px, double py) {
        return Math.sqrt(distanceSq(px, py));
    }

    public double distance(Point2D pt) {
        return distance(pt.getX(), pt.getY());
    }

    @Override
    public String toString() {
        return "Point2D[" + getX() + ", " + getY() + "]";
    }

    public static class Double extends Point2D {
        public double x;
        public double y;

        public Double() {
        }

        public Double(double x, double y) {
            this.x = x;
            this.y = y;
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
        public void setLocation(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Float extends Point2D {
        public float x;
        public float y;

        public Float() {
        }

        public Float(float x, float y) {
            this.x = x;
            this.y = y;
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
        public void setLocation(double x, double y) {
            this.x = (float) x;
            this.y = (float) y;
        }
    }
}

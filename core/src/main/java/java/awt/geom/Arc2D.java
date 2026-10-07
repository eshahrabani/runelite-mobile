package java.awt.geom;

/**
 * AWT geom.Arc2D compatibility stub for Android runtime.
 *
 * <p>Holds the framing rectangle plus start angle (degrees, counter-clockwise
 * from the positive X axis) and angular extent. The rasteriser approximates the
 * curve with a polyline, so this is exact enough for the overlay arcs.
 */
public abstract class Arc2D extends Rectangle2D {
    public static final int OPEN = 0;
    public static final int CHORD = 1;
    public static final int PIE = 2;

    protected int type;
    protected double start;
    protected double extent;

    protected Arc2D(int type) {
        this.type = type;
    }

    public static final int OPEN_TYPE = OPEN;

    public int getArcType() {
        return type;
    }

    public double getAngleStart() {
        return start;
    }

    public double getAngleExtent() {
        return extent;
    }

    public void setAngleStart(double start) {
        this.start = start;
    }

    public void setAngleExtent(double extent) {
        this.extent = extent;
    }

    public void setArc(double x, double y, double w, double h, double start, double extent, int type) {
        setFrame(x, y, w, h);
        this.start = start;
        this.extent = extent;
        this.type = type;
    }

    public static class Double extends Arc2D {
        private double x;
        private double y;
        private double width;
        private double height;

        public Double() {
            super(OPEN);
        }

        public Double(double x, double y, double w, double h, double start, double extent, int type) {
            super(type);
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.start = start;
            this.extent = extent;
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

        public void setFrame(double x, double y, double w, double h) {
            setRect(x, y, w, h);
        }
    }

    public static class Float extends Arc2D {
        private float x;
        private float y;
        private float width;
        private float height;

        public Float(int type) {
            super(type);
        }

        public Float(float x, float y, float w, float h, float start, float extent, int type) {
            super(type);
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.start = start;
            this.extent = extent;
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

        public void setFrame(double x, double y, double w, double h) {
            setRect(x, y, w, h);
        }
    }
}

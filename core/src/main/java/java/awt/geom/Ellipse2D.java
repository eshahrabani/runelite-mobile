package java.awt.geom;

/**
 * AWT geom.Ellipse2D compatibility stub for Android runtime.
 */
public abstract class Ellipse2D extends Rectangle2D {

    protected Ellipse2D() {
    }

    @Override
    public boolean contains(double x, double y) {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0.0 || h <= 0.0) {
            return false;
        }
        double rx = w / 2.0;
        double ry = h / 2.0;
        double dx = (x - (getX() + rx)) / rx;
        double dy = (y - (getY() + ry)) / ry;
        return dx * dx + dy * dy <= 1.0;
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return super.intersects(x, y, w, h);
    }

    public static class Double extends Ellipse2D {
        private double x;
        private double y;
        private double width;
        private double height;

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

        public void setFrame(double x, double y, double w, double h) {
            setRect(x, y, w, h);
        }
    }

    public static class Float extends Ellipse2D {
        private float x;
        private float y;
        private float width;
        private float height;

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

        public void setFrame(float x, float y, float w, float h) {
            setRect(x, y, w, h);
        }
    }
}

package java.awt.geom;

/**
 * AWT geom.RoundRectangle2D compatibility stub for Android runtime.
 */
public abstract class RoundRectangle2D extends Rectangle2D {

    protected RoundRectangle2D() {
    }

    public abstract double getArcWidth();

    public abstract double getArcHeight();

    public void setRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        setRect(x, y, w, h);
    }

    public static class Double extends RoundRectangle2D {
        public double x;
        public double y;
        public double width;
        public double height;
        public double arcwidth;
        public double archeight;

        public Double() {
        }

        public Double(double x, double y, double w, double h, double arcw, double arch) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.arcwidth = arcw;
            this.archeight = arch;
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
        public double getArcWidth() {
            return arcwidth;
        }

        @Override
        public double getArcHeight() {
            return archeight;
        }

        @Override
        public void setRect(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override
        public void setRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.arcwidth = arcWidth;
            this.archeight = arcHeight;
        }
    }

    public static class Float extends RoundRectangle2D {
        public float x;
        public float y;
        public float width;
        public float height;
        public float arcwidth;
        public float archeight;

        public Float() {
        }

        public Float(float x, float y, float w, float h, float arcw, float arch) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.arcwidth = arcw;
            this.archeight = arch;
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
        public double getArcWidth() {
            return arcwidth;
        }

        @Override
        public double getArcHeight() {
            return archeight;
        }

        @Override
        public void setRect(double x, double y, double w, double h) {
            this.x = (float) x;
            this.y = (float) y;
            this.width = (float) w;
            this.height = (float) h;
        }

        @Override
        public void setRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
            this.x = (float) x;
            this.y = (float) y;
            this.width = (float) w;
            this.height = (float) h;
            this.arcwidth = (float) arcWidth;
            this.archeight = (float) arcHeight;
        }
    }
}

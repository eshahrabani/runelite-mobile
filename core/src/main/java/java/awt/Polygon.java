package java.awt;

import java.awt.geom.Rectangle2D;

/**
 * AWT Polygon compatibility stub for Android runtime.
 */
public class Polygon implements Shape {
    public int npoints;
    public int[] xpoints;
    public int[] ypoints;

    public Polygon() {
        xpoints = new int[4];
        ypoints = new int[4];
    }

    public Polygon(int[] xpoints, int[] ypoints, int npoints) {
        this.xpoints = xpoints;
        this.ypoints = ypoints;
        this.npoints = npoints;
    }

    public void addPoint(int x, int y) {
        if (npoints >= xpoints.length) {
            int newLength = Math.max(xpoints.length * 2, npoints + 1);
            int[] tmp = new int[newLength];
            System.arraycopy(xpoints, 0, tmp, 0, npoints);
            xpoints = tmp;
            tmp = new int[newLength];
            System.arraycopy(ypoints, 0, tmp, 0, npoints);
            ypoints = tmp;
        }
        xpoints[npoints] = x;
        ypoints[npoints] = y;
        npoints++;
    }

    @Override
    public Rectangle getBounds() {
        if (npoints == 0) {
            return new Rectangle();
        }
        int minX = xpoints[0];
        int maxX = xpoints[0];
        int minY = ypoints[0];
        int maxY = ypoints[0];
        for (int i = 1; i < npoints; i++) {
            minX = Math.min(minX, xpoints[i]);
            maxX = Math.max(maxX, xpoints[i]);
            minY = Math.min(minY, ypoints[i]);
            maxY = Math.max(maxY, ypoints[i]);
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    @Override
    public Rectangle2D getBounds2D() {
        Rectangle b = getBounds();
        return new Rectangle2D.Double(b.x, b.y, b.width, b.height);
    }

    @Override
    public boolean contains(double x, double y) {
        if (npoints < 3) {
            return false;
        }
        boolean inside = false;
        for (int i = 0, j = npoints - 1; i < npoints; j = i++) {
            if ((ypoints[i] > y) != (ypoints[j] > y)
                    && x < (double) (xpoints[j] - xpoints[i]) * (y - ypoints[i]) / (ypoints[j] - ypoints[i]) + xpoints[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public boolean contains(int x, int y) {
        return contains((double) x, (double) y);
    }

    public boolean contains(Point p) {
        return contains(p.x, p.y);
    }
}

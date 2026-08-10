package java.awt;

/**
 * AWT Polygon compatibility stub for Android runtime.
 */
public class Polygon implements Shape {
    public int npoints;
    public int[] xpoints = new int[0];
    public int[] ypoints = new int[0];

    public Polygon() {}

    public Polygon(int[] xpoints, int[] ypoints, int npoints) {
        this.xpoints = xpoints;
        this.ypoints = ypoints;
        this.npoints = npoints;
    }

    public void addPoint(int x, int y) {
        int newNpoints = npoints + 1;
        int[] tmp = new int[newNpoints];
        System.arraycopy(xpoints, 0, tmp, 0, npoints);
        tmp[npoints] = x;
        xpoints = tmp;
        tmp = new int[newNpoints];
        System.arraycopy(ypoints, 0, tmp, 0, npoints);
        tmp[npoints] = y;
        ypoints = tmp;
        npoints = newNpoints;
    }

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
}

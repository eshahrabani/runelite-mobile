package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/**
 * AWT geom.GeneralPath compatibility stub for Android runtime.
 *
 * <p>Only straight-line paths are stored ({@code moveTo}/{@code lineTo} and
 * {@code closePath}); curves degrade to the current point, which is the
 * documented fidelity limit of the mobile shape surface. The overlay renderer
 * uses GeneralPath exclusively for perspective grid polylines.
 */
public class GeneralPath implements Shape {
    public static final int WIND_NON_ZERO = 1;
    public static final int WIND_EVEN_ODD = 0;

    private float[] xs = new float[8];
    private float[] ys = new float[8];
    private boolean[] moves = new boolean[8];
    private int count;

    public GeneralPath() {
    }

    public GeneralPath(int windingRule) {
    }

    public GeneralPath(Shape s) {
        if (s instanceof GeneralPath) {
            GeneralPath p = (GeneralPath) s;
            ensure(p.count);
            System.arraycopy(p.xs, 0, xs, 0, p.count);
            System.arraycopy(p.ys, 0, ys, 0, p.count);
            System.arraycopy(p.moves, 0, moves, 0, p.count);
            count = p.count;
        } else if (s != null) {
            Rectangle b = s.getBounds();
            if (b != null) {
                moveTo(b.x, b.y);
                lineTo(b.x + b.width, b.y);
                lineTo(b.x + b.width, b.y + b.height);
                lineTo(b.x, b.y + b.height);
                closePath();
            }
        }
    }

    private void ensure(int extra) {
        if (count + extra <= xs.length) {
            return;
        }
        int cap = Math.max(xs.length * 2, count + extra);
        float[] nx = new float[cap];
        float[] ny = new float[cap];
        boolean[] nm = new boolean[cap];
        System.arraycopy(xs, 0, nx, 0, count);
        System.arraycopy(ys, 0, ny, 0, count);
        System.arraycopy(moves, 0, nm, 0, count);
        xs = nx;
        ys = ny;
        moves = nm;
    }

    public void moveTo(float x, float y) {
        ensure(1);
        xs[count] = x;
        ys[count] = y;
        moves[count] = true;
        count++;
    }

    public void lineTo(float x, float y) {
        ensure(1);
        xs[count] = x;
        ys[count] = y;
        moves[count] = false;
        count++;
    }

    public void closePath() {
        if (count > 0) {
            lineTo(xs[count - 1], ys[count - 1]);
        }
    }

    public void reset() {
        count = 0;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int getPointCount() {
        return count;
    }

    public float getX(int i) {
        return xs[i];
    }

    public float getY(int i) {
        return ys[i];
    }

    /** True when point {@code i} starts a new subpath. */
    public boolean isMoveTo(int i) {
        return moves[i];
    }

    @Override
    public Rectangle getBounds() {
        if (count == 0) {
            return new Rectangle();
        }
        float minX = xs[0];
        float maxX = xs[0];
        float minY = ys[0];
        float maxY = ys[0];
        for (int i = 1; i < count; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return new Rectangle((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY));
    }

    @Override
    public Rectangle2D getBounds2D() {
        Rectangle b = getBounds();
        return new Rectangle2D.Double(b.x, b.y, b.width, b.height);
    }

    @Override
    public boolean contains(double x, double y) {
        boolean inside = false;
        for (int i = 0, j = count - 1; i < count; j = i++) {
            if ((ys[i] > y) != (ys[j] > y)
                    && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                inside = !inside;
            }
        }
        return inside;
    }
}

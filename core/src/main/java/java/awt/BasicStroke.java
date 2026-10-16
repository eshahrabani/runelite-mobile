package java.awt;

import java.util.Arrays;

/**
 * The port's {@code java.awt.BasicStroke}.
 *
 * <p>Hand-written because the desktop {@code java.awt} is absent: overlay code
 * calls {@code getStroke()}/{@code setStroke} and constructs strokes to change
 * the line width, and {@code Graphics2D.draw} honours the width on this runtime
 * (it traces that many parallel lines, adds a disc for round caps/joins and
 * extends a square cap by half the width — see {@code Graphics.drawLineWidth}).
 *
 * <p>Documented deviations:
 * <ul>
 *   <li>a dash array is recorded and reported but renders solid (nothing in the
 *       client sets one);</li>
 *   <li>{@code createStrokedShape} is not implemented — the stubbed
 *       {@link Stroke} is a marker interface because no shipped code calls it;
 *       the rasteriser strokes the shape directly instead.</li>
 * </ul>
 */
public class BasicStroke implements Stroke {
    public static final int JOIN_MITER = 0;
    public static final int JOIN_ROUND = 1;
    public static final int JOIN_BEVEL = 2;

    public static final int CAP_BUTT = 0;
    public static final int CAP_ROUND = 1;
    public static final int CAP_SQUARE = 2;

    private final float width;
    private final int endCap;
    private final int lineJoin;
    private final float miterLimit;
    private final float[] dash;
    private final float dashPhase;

    public BasicStroke() {
        this(1.0f, CAP_SQUARE, JOIN_MITER, 10.0f, null, 0.0f);
    }

    public BasicStroke(float width) {
        this(width, CAP_SQUARE, JOIN_MITER, 10.0f, null, 0.0f);
    }

    public BasicStroke(float width, int cap, int join) {
        this(width, cap, join, 10.0f, null, 0.0f);
    }

    public BasicStroke(float width, int cap, int join, float miterlimit) {
        this(width, cap, join, miterlimit, null, 0.0f);
    }

    public BasicStroke(float width, int cap, int join, float miterlimit, float[] dash, float dashphase) {
        if (width < 0.0f) {
            throw new IllegalArgumentException("negative width");
        }
        if (cap != CAP_BUTT && cap != CAP_ROUND && cap != CAP_SQUARE) {
            throw new IllegalArgumentException("illegal end cap value");
        }
        if (join != JOIN_MITER && join != JOIN_ROUND && join != JOIN_BEVEL) {
            throw new IllegalArgumentException("illegal line join value");
        }
        // The JDK does not validate the miter limit in the constructor.
        if (dash != null) {
            if (dashphase < 0.0f) {
                throw new IllegalArgumentException("negative dash phase");
            }
            boolean allZero = true;
            for (int i = 0; i < dash.length; i++) {
                if (dash[i] < 0.0f) {
                    throw new IllegalArgumentException("negative dash length");
                }
                allZero &= dash[i] == 0.0f;
            }
            if (allZero) {
                throw new IllegalArgumentException("dash lengths all zero");
            }
        }
        this.width = width;
        this.endCap = cap;
        this.lineJoin = join;
        this.miterLimit = miterlimit;
        this.dash = dash == null ? null : dash.clone();
        this.dashPhase = dashphase;
    }

    public float getLineWidth() {
        return width;
    }

    public int getEndCap() {
        return endCap;
    }

    public int getLineJoin() {
        return lineJoin;
    }

    public float getMiterLimit() {
        return miterLimit;
    }

    public float getDashPhase() {
        return dashPhase;
    }

    public float[] getDashArray() {
        return dash == null ? null : dash.clone();
    }

    @Override
    public int hashCode() {
        int hash = Float.floatToIntBits(width);
        hash = hash * 31 + endCap;
        hash = hash * 31 + lineJoin;
        hash = hash * 31 + Float.floatToIntBits(miterLimit);
        if (dash != null) {
            hash = hash * 31 + Float.floatToIntBits(dashPhase);
            for (int i = 0; i < dash.length; i++) {
                hash = hash * 31 + Float.floatToIntBits(dash[i]);
            }
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BasicStroke)) {
            return false;
        }
        BasicStroke s = (BasicStroke) obj;
        return width == s.width && endCap == s.endCap && lineJoin == s.lineJoin
                && miterLimit == s.miterLimit && dashPhase == s.dashPhase
                && Arrays.equals(dash, s.dash);
    }

    @Override
    public String toString() {
        return "BasicStroke[width=" + width + ",cap=" + endCap + ",join=" + lineJoin
                + ",miterlimit=" + miterLimit + ",dash=" + Arrays.toString(dash)
                + ",dashphase=" + dashPhase + "]";
    }
}

package java.awt;

/**
 * AWT BasicStroke compatibility stub for Android runtime.
 *
 * <p>Only the line width is honoured (shape drawing is always 1px lines, so the
 * width is stored for {@code getStroke().getLineWidth()} round-trips); caps,
 * joins, miter limit and dashes are recorded for compatibility.
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

    public BasicStroke(float width, int cap, int join, float miterlimit, float[] dash, float dashphase) {
        if (width < 0.0f) {
            throw new IllegalArgumentException("negative width");
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
}

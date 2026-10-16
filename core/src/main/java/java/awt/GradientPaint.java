package java.awt;

/**
 * The port's {@code java.awt.GradientPaint}.
 *
 * <p>Hand-written because RuneLite paints panels, bars and infobox backgrounds with
 * it and the rasteriser has to produce the ramp itself.
 *
 * <p>Stores the two colours, the two endpoints and the cyclic flag; the fill
 * paths rasterise the gradient along the transformed endpoints exactly as the
 * JDK describes (linear, cyclic when {@link #isCyclic()}). Documented deviation:
 * the stop fractions of the eight-argument constructor are ignored — this port
 * only implements the two-stop ramp, so a caller passing explicit fractions gets
 * the {0, 1} ramp and {@link #getFractions()} reports that.
 */
public class GradientPaint implements Paint {
    private final float x1;
    private final float y1;
    private final Color color1;
    private final float x2;
    private final float y2;
    private final Color color2;
    private final boolean cyclic;
    private final float[] fractions;

    public GradientPaint(float x1, float y1, Color color1, float x2, float y2, Color color2) {
        this(x1, y1, color1, x2, y2, color2, false);
    }

    public GradientPaint(float x1, float y1, Color color1, float x2, float y2, Color color2, boolean cyclic) {
        if (color1 == null || color2 == null) {
            throw new NullPointerException("colors must not be null");
        }
        this.x1 = x1;
        this.y1 = y1;
        this.color1 = color1;
        this.x2 = x2;
        this.y2 = y2;
        this.color2 = color2;
        this.cyclic = cyclic;
        this.fractions = new float[] {0.0f, 1.0f};
    }

    public GradientPaint(float x1, float y1, Color color1, float x2, float y2, Color color2,
                         boolean cyclic, float[] fractions) {
        this(x1, y1, color1, x2, y2, color2, cyclic);
    }

    public float getPoint1X() {
        return x1;
    }

    public float getPoint1Y() {
        return y1;
    }

    public Color getColor1() {
        return color1;
    }

    public float getPoint2X() {
        return x2;
    }

    public float getPoint2Y() {
        return y2;
    }

    public Color getColor2() {
        return color2;
    }

    public boolean isCyclic() {
        return cyclic;
    }

    public float[] getFractions() {
        return fractions.clone();
    }
}

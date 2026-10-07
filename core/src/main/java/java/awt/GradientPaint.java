package java.awt;

/**
 * AWT GradientPaint compatibility stub for Android runtime.
 *
 * <p>Stores the two colours and endpoints. The mobile surface does not rasterise
 * gradients (there is no linear-gradient compositor in {@code Graphics2D}); a
 * gradient paint is accepted by {@link Graphics2D#setPaint} but the last solid
 * colour remains in effect.
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

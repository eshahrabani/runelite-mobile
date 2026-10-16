package java.awt;

/**
 * The port's {@code java.awt.AlphaComposite}.
 *
 * <p>Hand-written, because {@code Graphics2D.setComposite} has to change what is
 * drawn: {@code ImageUtil.recolorImage} tints a sprite through
 * {@code AlphaComposite.SrcAtop} and the client alpha-fades icons through
 * {@code getInstance(SRC_OVER, alpha)}, so the rules are applied by the rasteriser
 * rather than ignored.
 *
 * <p>Carries the Porter-Duff rule and the extra alpha multiplier that
 * {@link Graphics2D#setComposite} installs. {@link Graphics#blendPixel} applies
 * every declared rule, which is what {@code ImageUtil.recolorImage} needs from
 * {@code SrcAtop}: colour only where the destination is already opaque, its
 * alpha preserved.
 */
public final class AlphaComposite implements Composite {
    /*
     * The numeric rule values are the ones the desktop JDK uses (verified against
     * java.awt.AlphaComposite on JDK 17), NOT the values this stub previously
     * carried: getInstance(int, float) is called with the constant inlined at the
     * call site, so the pre-compiled client's
     * {@code AlphaComposite.getInstance(3, alpha)} must mean SRC_OVER. The old
     * 4/SRC_OVER numbering silently selected DST and drew nothing.
     */
    public static final int CLEAR = 1;
    public static final int SRC = 2;
    public static final int DST = 9;
    public static final int SRC_OVER = 3;
    public static final int DST_OVER = 4;
    public static final int SRC_IN = 5;
    public static final int DST_IN = 6;
    public static final int SRC_OUT = 7;
    public static final int DST_OUT = 8;
    public static final int SRC_ATOP = 10;
    public static final int DST_ATOP = 11;
    public static final int XOR = 12;

    public static final AlphaComposite Clear = new AlphaComposite(CLEAR, 1.0f);
    public static final AlphaComposite Src = new AlphaComposite(SRC, 1.0f);
    public static final AlphaComposite Dst = new AlphaComposite(DST, 1.0f);
    public static final AlphaComposite SrcOver = new AlphaComposite(SRC_OVER, 1.0f);
    public static final AlphaComposite DstOver = new AlphaComposite(DST_OVER, 1.0f);
    public static final AlphaComposite SrcIn = new AlphaComposite(SRC_IN, 1.0f);
    public static final AlphaComposite DstIn = new AlphaComposite(DST_IN, 1.0f);
    public static final AlphaComposite SrcOut = new AlphaComposite(SRC_OUT, 1.0f);
    public static final AlphaComposite DstOut = new AlphaComposite(DST_OUT, 1.0f);
    public static final AlphaComposite SrcAtop = new AlphaComposite(SRC_ATOP, 1.0f);
    public static final AlphaComposite DstAtop = new AlphaComposite(DST_ATOP, 1.0f);
    public static final AlphaComposite Xor = new AlphaComposite(XOR, 1.0f);

    private final int rule;
    private final float alpha;

    private AlphaComposite(int rule, float alpha) {
        if (alpha < 0.0f || alpha > 1.0f) {
            throw new IllegalArgumentException("alpha must be in [0,1]");
        }
        this.rule = rule;
        this.alpha = alpha;
    }

    public static AlphaComposite getInstance(int rule) {
        return getInstance(rule, 1.0f);
    }

    public static AlphaComposite getInstance(int rule, float alpha) {
        // Every value of CLEAR..XOR is a rule, so the range check is exact.
        if (rule < CLEAR || rule > XOR) {
            throw new IllegalArgumentException("unknown rule");
        }
        return new AlphaComposite(rule, alpha);
    }

    public AlphaComposite derive(int rule) {
        return this.rule == rule ? this : new AlphaComposite(rule, this.alpha);
    }

    public AlphaComposite derive(float alpha) {
        return this.alpha == alpha ? this : new AlphaComposite(this.rule, alpha);
    }

    public int getRule() {
        return rule;
    }

    public float getAlpha() {
        return alpha;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AlphaComposite)) {
            return false;
        }
        AlphaComposite other = (AlphaComposite) obj;
        return rule == other.rule && Float.floatToIntBits(alpha) == Float.floatToIntBits(other.alpha);
    }

    @Override
    public int hashCode() {
        return rule * 31 + Float.floatToIntBits(alpha);
    }

    @Override
    public String toString() {
        return "AlphaComposite[" + rule + ", " + alpha + "]";
    }
}

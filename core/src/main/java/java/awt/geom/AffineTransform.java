package java.awt.geom;

/**
 * AWT AffineTransform compatibility stub for Android runtime.
 *
 * <p>Stores the six matrix elements of the standard 2D affine transform and
 * implements the composition operations the RuneLite overlay renderer uses
 * ({@code translate}, {@code rotate}, {@code scale}) plus the point mapping and
 * inverse needed by {@link java.awt.image.AffineTransformOp}. There is no
 * general matrix display class; this is the complete surface.
 */
public class AffineTransform {
    private double m00;
    private double m10;
    private double m01;
    private double m11;
    private double m02;
    private double m12;

    public AffineTransform() {
        m00 = 1.0;
        m11 = 1.0;
    }

    public AffineTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        this.m00 = m00;
        this.m10 = m10;
        this.m01 = m01;
        this.m11 = m11;
        this.m02 = m02;
        this.m12 = m12;
    }

    public AffineTransform(AffineTransform tx) {
        this.m00 = tx.m00;
        this.m10 = tx.m10;
        this.m01 = tx.m01;
        this.m11 = tx.m11;
        this.m02 = tx.m02;
        this.m12 = tx.m12;
    }

    public void setToIdentity() {
        m00 = 1.0;
        m10 = 0.0;
        m01 = 0.0;
        m11 = 1.0;
        m02 = 0.0;
        m12 = 0.0;
    }

    public void setTransform(AffineTransform tx) {
        this.m00 = tx.m00;
        this.m10 = tx.m10;
        this.m01 = tx.m01;
        this.m11 = tx.m11;
        this.m02 = tx.m02;
        this.m12 = tx.m12;
    }

    public boolean isIdentity() {
        return m00 == 1.0 && m11 == 1.0 && m01 == 0.0 && m10 == 0.0 && m02 == 0.0 && m12 == 0.0;
    }

    /** True when the transform only shifts the origin (no rotation/scale/shear). */
    public boolean isTranslationOnly() {
        return m00 == 1.0 && m11 == 1.0 && m01 == 0.0 && m10 == 0.0;
    }

    public double getScaleX() {
        return m00;
    }

    public double getScaleY() {
        return m11;
    }

    public double getShearX() {
        return m01;
    }

    public double getShearY() {
        return m10;
    }

    public double getTranslateX() {
        return m02;
    }

    public double getTranslateY() {
        return m12;
    }

    public void translate(double tx, double ty) {
        m02 += tx * m00 + ty * m01;
        m12 += tx * m10 + ty * m11;
    }

    public void scale(double sx, double sy) {
        m00 *= sx;
        m01 *= sy;
        m10 *= sx;
        m11 *= sy;
    }

    public void rotate(double theta) {
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double n00 = m00 * cos + m01 * sin;
        double n01 = -m00 * sin + m01 * cos;
        double n10 = m10 * cos + m11 * sin;
        double n11 = -m10 * sin + m11 * cos;
        m00 = n00;
        m01 = n01;
        m10 = n10;
        m11 = n11;
    }

    /**
     * Rotates so that the vector {@code (vecx, vecy)} maps to the positive X
     * axis (the JDK 24 {@code rotate(double,double)} convenience the RuneLite
     * devtools overlay compiles against).
     */
    public void rotate(double vecx, double vecy) {
        if (vecx == 0.0 && vecy == 0.0) {
            return;
        }
        rotate(Math.atan2(vecy, vecx));
    }

    public void rotate(double theta, double anchorx, double anchory) {
        translate(anchorx, anchory);
        rotate(theta);
        translate(-anchorx, -anchory);
    }

    public void concatenate(AffineTransform tx) {
        double n00 = m00 * tx.m00 + m01 * tx.m10;
        double n01 = m00 * tx.m01 + m01 * tx.m11;
        double n02 = m00 * tx.m02 + m01 * tx.m12 + m02;
        double n10 = m10 * tx.m00 + m11 * tx.m10;
        double n11 = m10 * tx.m01 + m11 * tx.m11;
        double n12 = m10 * tx.m02 + m11 * tx.m12 + m12;
        m00 = n00;
        m01 = n01;
        m02 = n02;
        m10 = n10;
        m11 = n11;
        m12 = n12;
    }

    public AffineTransform createInverse() {
        double det = m00 * m11 - m01 * m10;
        if (det == 0.0) {
            throw new IllegalStateException("determinant is zero");
        }
        double id = 1.0 / det;
        return new AffineTransform(
                m11 * id,
                -m10 * id,
                -m01 * id,
                m00 * id,
                (m01 * m12 - m11 * m02) * id,
                (m10 * m02 - m00 * m12) * id);
    }

    public double getDeterminant() {
        return m00 * m11 - m01 * m10;
    }

    /** Maps {@code ptSrc[srcOff..]} through this transform into {@code ptDst[destOff..]}. */
    public double[] transform(double[] ptSrc, int srcOff, double[] ptDst, int destOff, int numPts) {
        for (int i = 0; i < numPts; i++) {
            double x = ptSrc[srcOff + i * 2];
            double y = ptSrc[srcOff + i * 2 + 1];
            ptDst[destOff + i * 2] = m00 * x + m01 * y + m02;
            ptDst[destOff + i * 2 + 1] = m10 * x + m11 * y + m12;
        }
        return ptDst;
    }

    /** Maps one point; returns {@code {x', y'}}. */
    public double[] transform(double x, double y) {
        return new double[] {m00 * x + m01 * y + m02, m10 * x + m11 * y + m12};
    }

    public Point2D transform(Point2D ptSrc, Point2D ptDst) {
        if (ptDst == null) {
            ptDst = new Point2D.Double();
        }
        double x = ptSrc.getX();
        double y = ptSrc.getY();
        ptDst.setLocation(m00 * x + m01 * y + m02, m10 * x + m11 * y + m12);
        return ptDst;
    }

    @Override
    public String toString() {
        return "AffineTransform[[" + m00 + ", " + m01 + ", " + m02 + "], ["
                + m10 + ", " + m11 + ", " + m12 + "]]";
    }
}

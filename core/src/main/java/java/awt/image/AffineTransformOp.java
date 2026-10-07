package java.awt.image;

import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

/**
 * AWT AffineTransformOp compatibility stub for Android runtime.
 *
 * <p>Nearest-neighbour resampling only (bilinear/cubic hint values are accepted
 * but mapped to nearest neighbour) which is enough for the RuneLite image
 * utilities that rotate sprites.
 */
public class AffineTransformOp {
    public static final int TYPE_NEAREST_NEIGHBOR = 1;
    public static final int TYPE_BILINEAR = 2;
    public static final int TYPE_BICUBIC = 3;

    private final AffineTransform transform;
    private final int interpolationType;

    public AffineTransformOp(AffineTransform xform, int interpolationType) {
        if (xform == null) {
            throw new IllegalArgumentException("xform must not be null");
        }
        this.transform = new AffineTransform(xform);
        this.interpolationType = interpolationType;
    }

    public AffineTransformOp(AffineTransform xform, RenderingHints hints) {
        this(xform, TYPE_NEAREST_NEIGHBOR);
    }

    public int getInterpolationType() {
        return interpolationType;
    }

    public AffineTransform getTransform() {
        return new AffineTransform(transform);
    }

    public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
        return new BufferedImage(src.getWidth(), src.getHeight(),
                src.hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    public BufferedImage filter(BufferedImage src, BufferedImage dst) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (dst == null) {
            dst = createCompatibleDestImage(src, null);
        }
        AffineTransform inverse = transform.createInverse();
        double[] pt = new double[2];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                inverse.transform(new double[] {x + 0.5, y + 0.5}, 0, pt, 0, 1);
                int sx = (int) Math.floor(pt[0]);
                int sy = (int) Math.floor(pt[1]);
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
                    dst.setRGB(x, y, 0);
                } else {
                    dst.setRGB(x, y, src.getRGB(sx, sy));
                }
            }
        }
        return dst;
    }
}

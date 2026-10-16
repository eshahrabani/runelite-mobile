package java.awt;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
 * The port's {@code java.awt.Image}.
 *
 * <p>Hand-written, because the client hands plain images around (the game frame, the
 * applet bridge, {@code ImageUtil.flipImage}'s target) and the pixels are what this
 * runtime draws from — there is no image producer behind them.
 *
 * <p>A plain image is a non-premultiplied ARGB {@code int[]} plus a size. The
 * {@code hasAlpha} flag distinguishes opaque sources (the game frame) from
 * sources that need blending ({@link java.awt.image.BufferedImage} sprites);
 * it is set from the colour model of a {@code BufferedImage} and defaults to
 * false here.
 *
 * <p>{@link #getScaledInstance} is implemented here (not by a platform image
 * producer): it honours the JDK's {@code -1} = "preserve the aspect ratio"
 * convention and returns a {@link BufferedImage}. {@code SCALE_SMOOTH} selects
 * bilinear sampling, every other hint (including {@code SCALE_AREA_AVERAGING})
 * nearest — the JDK's exact kernels for the other hints are unspecified.
 */
public class Image {
    protected int[] pixels;
    protected int width;
    protected int height;

    /** True when {@link Graphics#drawImage} must blend this source per pixel. */
    public boolean hasAlpha;

    /** Default scaling hint, as in the JDK. */
    public static final int SCALE_DEFAULT = 1;
    /** Replicate the pixels rather than smoothing them (nearest here). */
    public static final int SCALE_FAST = 2;
    /**
     * Smooth scaling; maps to bilinear sampling.
     *
     * <p>Value 4 as in the JDK. {@code SCALE_REPLICATE} is the JDK's 8 (not 3 —
     * the plan's value was a typo) so a caller compiled against the desktop JDK
     * passes the hint this runtime recognises.
     */
    public static final int SCALE_SMOOTH = 4;
    /** Replicate rows/columns; nearest sampling here. */
    public static final int SCALE_REPLICATE = 8;
    /** Area averaging; nearest sampling here (documented approximation). */
    public static final int SCALE_AREA_AVERAGING = 16;

    public Image() {}

    public Image(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth(ImageObserver observer) {
        return width;
    }

    public int getHeight(ImageObserver observer) {
        return height;
    }

    public int[] getPixels() {
        return pixels;
    }

    /**
     * Returns a graphics context bound to this image's pixels. A
     * {@link Graphics2D} is returned because
     * {@code net.runelite.client.callback.Hooks} casts the result to
     * {@code Graphics2D} to render overlays into the game frame.
     */
    public Graphics getGraphics() {
        return new Graphics2D(this);
    }

    /**
     * Returns a scaled copy of this image.
     *
     * @param width the target width, or {@code -1} to derive it from the height
     * @param height the target height, or {@code -1} to derive it from the width
     * @param hints one of the {@code SCALE_*} constants ({@code SCALE_SMOOTH} is
     *        the only one that changes the sampling filter)
     * @return {@code this} when both axes are {@code -1}, otherwise an
     *         ARGB (or RGB, for an opaque source) {@link BufferedImage}
     */
    public Image getScaledInstance(int width, int height, int hints) {
        if (width == -1 && height == -1) {
            return this;
        }
        int w = width;
        int h = height;
        if (w == -1) {
            w = Math.max(1, (int) Math.round((double) h * this.width / this.height));
        } else if (h == -1) {
            h = Math.max(1, (int) Math.round((double) w * this.height / this.width));
        }
        w = Math.max(1, w);
        h = Math.max(1, h);
        BufferedImage out = new BufferedImage(w, h,
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        if ((hints & SCALE_SMOOTH) != 0 || hints == SCALE_SMOOTH) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g.drawImage(this, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    public void flush() {
    }
}

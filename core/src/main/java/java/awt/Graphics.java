package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
import org.runelite.mobile.bridge.TextBridge;

/**
 * The port's {@code java.awt.Graphics}.
 *
 * <p>Hand-written, because the RuneLite overlay renderer draws straight into the game
 * frame through this class: there is no desktop toolkit behind it, only a pixel array,
 * so every primitive is rasterised here.
 *
 * <p>Draws directly into a non-premultiplied ARGB {@code int[]} supplied by the
 * caller (the game frame, a {@code BufferedImage}'s raster or the applet bridge).
 * Destination alpha is ignored for colour arithmetic and preserved on write, so
 * drawing over the game frame (whose pixels carry alpha 0) blends visually
 * instead of making overlays opaque.
 *
 * <p><b>Transform.</b> Every primitive maps its geometry through
 * {@link AffineTransform}. When the transform is the identity or a pure
 * translation — which is what the game's per-frame work uses — the primitives
 * take the original integer paths and no object is allocated per call; a
 * general transform maps points into device space (and image draws sample the
 * source through the inverse transform).
 *
 * <p><b>Clip.</b> {@link #setClip} replaces the clip and {@link #clip}
 * intersects into it, both in user space; rasterisation uses the device-space
 * bounding box ({@link #clipBounds}). As in the JDK, getClip returns a rectangle
 * for rectangular clips and the set shape for a shape clip.
 *
 * <p><b>Composite.</b> {@link AlphaComposite}'s Porter-Duff rules are applied
 * per written pixel by {@link #blendPixel}.
 *
 * <p><b>Paint.</b> A {@link GradientPaint} is rasterised along its two
 * transformed endpoints in the fill paths; any other non-colour paint falls back
 * to the last solid colour.
 *
 * <p><b>Text.</b> Glyph rasterisation lives in the platform renderer behind
 * {@link TextBridge}; the device baseline and the effective scale come from the
 * transform. A clip that would cut the run (or a non-default composite) is
 * honoured by rasterising into a scratch buffer first, so the common case
 * composites straight into the destination with no allocation.
 *
 * <p>{@link #drawImage} keeps branch-free fast paths for opaque sources — a 1:1
 * {@code System.arraycopy} per row and a divide-free nearest-neighbour
 * accumulator for scaled draws — because it is the per-frame hot path for the
 * full 765x503 frame. Sources with {@link Image#hasAlpha}, and any draw with a
 * non-default {@link AlphaComposite}, take the per-pixel blend path instead.
 */
public class Graphics {
    protected int[] destPixels;
    protected int destWidth;
    protected int destHeight;

    /** Current colour as 0xAARRGGBB. */
    protected int colorARGB = 0xFF000000;
    protected Font font;
    protected Color background;
    protected AffineTransform transform = new AffineTransform();
    /** User-space clip shape as set; informational (rasterisation uses clipBounds). */
    protected Shape clipShape;
    /** Device-space clip bounding box, null meaning the whole surface. */
    protected Rectangle clipBounds;
    /** Extra alpha multiplier from an AlphaComposite, 0..255. */
    protected int compositeAlpha = 255;
    /** Porter-Duff rule of the active AlphaComposite; SRC_OVER until one is set. */
    protected int compositeRule = AlphaComposite.SRC_OVER;
    /** True when the destination pixels themselves carry meaningful alpha. */
    protected boolean destHasAlpha;
    /** Set when a GradientPaint is the active paint; null for solid colours. */
    protected GradientPaint gradientPaint;
    /** Active KEY_INTERPOLATION hint, or null; drives bilinear scaled image draws. */
    protected Object interpolationHint;

    // Device-space gradient axis, refreshed by syncPaint() at the start of a draw.
    private double gradX0;
    private double gradY0;
    private double gradDx;
    private double gradDy;
    private double gradLen2;
    private boolean gradCyclic;
    private int gradArgb1;
    private int gradArgb2;

    public Graphics() {
    }

    public Graphics(Image image) {
        if (image != null) {
            this.destPixels = image.getPixels();
            this.destWidth = image.getWidth();
            this.destHeight = image.getHeight();
            this.destHasAlpha = image.hasAlpha;
        }
    }

    public Graphics(int[] destPixels, int destWidth, int destHeight) {
        this.destPixels = destPixels;
        this.destWidth = destWidth;
        this.destHeight = destHeight;
    }

    // ---------------------------------------------------------------- state

    public void setColor(Color c) {
        if (c != null) {
            colorARGB = c.getRGB();
        }
        // The JDK's setColor is setPaint(Color): a gradient stops being active.
        gradientPaint = null;
    }

    public Color getColor() {
        return new Color(colorARGB, true);
    }

    public void setFont(Font f) {
        this.font = f;
    }

    public Font getFont() {
        return font;
    }

    public Color getBackground() {
        return background;
    }

    public void setBackground(Color c) {
        this.background = c;
    }

    public AffineTransform getTransform() {
        return new AffineTransform(transform);
    }

    public void setTransform(AffineTransform tx) {
        this.transform = tx == null ? new AffineTransform() : new AffineTransform(tx);
    }

    public void translate(int x, int y) {
        transform.translate(x, y);
    }

    public void translate(double x, double y) {
        transform.translate(x, y);
    }

    public FontMetrics getFontMetrics() {
        return getFontMetrics(font);
    }

    public FontMetrics getFontMetrics(Font f) {
        return new FontMetrics(f != null ? f : new Font("Dialog", Font.PLAIN, 12));
    }

    // ----------------------------------------------------------------- clip

    public void setClip(int x, int y, int width, int height) {
        setClip(new Rectangle(x, y, width, height));
    }

    /** Replaces the user-space clip (JDK semantics: not intersected with the old one). */
    public void setClip(Shape clip) {
        this.clipShape = clip;
        this.clipBounds = clip == null ? null : deviceBoundsOf(clip);
    }

    /**
     * @return the user-space clip: the shape that was set, the rectangle when the
     *         intersection of rectangles is still a rectangle, and null when the
     *         clip was cleared. A clip intersected with a non-rectangular shape is
     *         reported as the inverse-mapped device box, which is conservative but
     *         round-trips through {@link #setClip}.
     */
    public Shape getClip() {
        return clipShape;
    }

    /** Intersects the clip with the given rectangle (user space, as the JDK does). */
    public void clipRect(int x, int y, int width, int height) {
        clip(new Rectangle(x, y, width, height));
    }

    /** Intersects the clip with the given user-space shape. */
    public void clip(Shape s) {
        if (s == null) {
            return;
        }
        Rectangle b = deviceBoundsOf(s);
        Shape prev = clipShape;
        if (clipBounds == null) {
            clipBounds = b;
        } else if (b != null) {
            int x1 = Math.max(clipBounds.x, b.x);
            int y1 = Math.max(clipBounds.y, b.y);
            int x2 = Math.min(clipBounds.x + clipBounds.width, b.x + b.width);
            int y2 = Math.min(clipBounds.y + clipBounds.height, b.y + b.height);
            clipBounds = x2 > x1 && y2 > y1 ? new Rectangle(x1, y1, x2 - x1, y2 - y1)
                    : new Rectangle(0, 0, 0, 0);
        }
        if (prev instanceof Rectangle && s instanceof Rectangle) {
            // The common case (a component clip inside its parent's clip): exact.
            clipShape = ((Rectangle) prev).intersection((Rectangle) s);
        } else if (prev == null) {
            clipShape = s;
        } else {
            clipShape = userSpaceBox(clipBounds);
        }
    }

    /** Maps a device box back to a user-space rectangle (conservative under rotation). */
    private Rectangle2D userSpaceBox(Rectangle device) {
        if (device == null) {
            return null;
        }
        try {
            AffineTransform inv = transform.createInverse();
            return new Rectangle2D.Double(
                    inv.getScaleX() * device.x + inv.getShearX() * device.y + inv.getTranslateX(),
                    inv.getShearY() * device.x + inv.getScaleY() * device.y + inv.getTranslateY(),
                    Math.abs(inv.getScaleX()) * device.width,
                    Math.abs(inv.getScaleY()) * device.height);
        } catch (RuntimeException e) {
            return new Rectangle2D.Double(device.x, device.y, device.width, device.height);
        }
    }

    /**
     * @return the device-space clip box.
     *
     * <p>Deviation: the JDK returns null when no clip was ever set; this stub has
     * always reported the whole surface instead, and the injected client relies on
     * that, so the behaviour is kept.
     */
    public Rectangle getClipBounds() {
        if (clipBounds != null) {
            return new Rectangle(clipBounds);
        }
        return new Rectangle(0, 0, destWidth, destHeight);
    }

    public void dispose() {
        // No-op on purpose: disposing the cached Hooks graphics must not wipe
        // the frame pixels it is bound to.
    }

    /** A copy of this context sharing the same destination pixels (the JDK's create). */
    public Graphics create() {
        Graphics2D g = new Graphics2D(destPixels, destWidth, destHeight);
        g.colorARGB = colorARGB;
        g.font = font;
        g.background = background;
        g.transform = new AffineTransform(transform);
        g.clipShape = clipShape;
        g.clipBounds = clipBounds == null ? null : new Rectangle(clipBounds);
        g.compositeAlpha = compositeAlpha;
        g.compositeRule = compositeRule;
        g.destHasAlpha = destHasAlpha;
        g.gradientPaint = gradientPaint;
        g.interpolationHint = interpolationHint;
        return g;
    }

    // ---------------------------------------------------- transform helpers

    /** The JDK's zero-transform fast path: identity or a pure translation. */
    protected final boolean translationOnly() {
        // isTranslationOnly() already accepts the identity matrix; the explicit
        // isIdentity() keeps the fast-path condition readable at the call sites.
        return transform.isIdentity() || transform.isTranslationOnly();
    }

    protected final int translateX() {
        return (int) Math.round(transform.getTranslateX());
    }

    protected final int translateY() {
        return (int) Math.round(transform.getTranslateY());
    }

    /** Device x of a user-space point, rounded to the nearest pixel. */
    protected final int deviceX(double x, double y) {
        AffineTransform t = transform;
        return (int) Math.round(t.getScaleX() * x + t.getShearX() * y + t.getTranslateX());
    }

    /** Device y of a user-space point, rounded to the nearest pixel. */
    protected final int deviceY(double x, double y) {
        AffineTransform t = transform;
        return (int) Math.round(t.getShearY() * x + t.getScaleY() * y + t.getTranslateY());
    }

    /** True when the transform has no rotation or shear, only scale and translation. */
    protected final boolean axisAligned() {
        return transform.getShearX() == 0.0 && transform.getShearY() == 0.0;
    }

    /** True when no clip is set or the clip covers the whole surface. */
    protected final boolean clipIsWholeSurface() {
        return clipBounds == null
                || (clipBounds.x <= 0 && clipBounds.y <= 0
                    && clipBounds.x + clipBounds.width >= destWidth
                    && clipBounds.y + clipBounds.height >= destHeight);
    }

    /** The stroke of the sub-class, or null for a plain Graphics (1px legacy drawing). */
    protected BasicStroke strokeOrNull() {
        return null;
    }

    // ------------------------------------------------------------- drawing

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (destPixels == null) {
            return;
        }
        syncPaint();
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            drawLineWidth(x1 + tx, y1 + ty, x2 + tx, y2 + ty, colorARGB);
        } else {
            drawLineWidth(deviceX(x1, y1), deviceY(x1, y1), deviceX(x2, y2), deviceY(x2, y2), colorARGB);
        }
    }

    public void drawLineD(double x1, double y1, double x2, double y2) {
        if (destPixels == null) {
            return;
        }
        syncPaint();
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            drawLineWidth((int) Math.round(x1) + tx, (int) Math.round(y1) + ty,
                    (int) Math.round(x2) + tx, (int) Math.round(y2) + ty, colorARGB);
        } else {
            drawLineWidth(deviceX(x1, y1), deviceY(x1, y1), deviceX(x2, y2), deviceY(x2, y2), colorARGB);
        }
    }

    /** Draws every segment of an open polyline (device coordinates). */
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        if (xPoints == null || yPoints == null || nPoints < 2 || destPixels == null) {
            return;
        }
        syncPaint();
        int[] xs = new int[nPoints];
        int[] ys = new int[nPoints];
        mapPoints(xPoints, yPoints, nPoints, xs, ys);
        strokePolylineDevice(xs, ys, nPoints, false, colorARGB);
    }

    public void fillRect(int x, int y, int width, int height) {
        if (destPixels == null || width <= 0 || height <= 0) {
            return;
        }
        syncPaint();
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            fillRectDevice(x + tx, y + ty, width, height, colorARGB);
        } else {
            fillUserQuad(x, y, width, height);
        }
    }

    public void fillRectD(double x, double y, double width, double height) {
        if (destPixels == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        syncPaint();
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            fillRectDevice((int) Math.floor(x) + tx, (int) Math.floor(y) + ty,
                    (int) Math.ceil(width), (int) Math.ceil(height), colorARGB);
        } else {
            fillUserQuad(x, y, width, height);
        }
    }

    /** Fills the transformed image of a user-space rectangle. */
    private void fillUserQuad(double x, double y, double width, double height) {
        int x0 = deviceX(x, y);
        int y0 = deviceY(x, y);
        int x1 = deviceX(x + width, y);
        int y1 = deviceY(x + width, y);
        int x2 = deviceX(x + width, y + height);
        int y2 = deviceY(x + width, y + height);
        int x3 = deviceX(x, y + height);
        int y3 = deviceY(x, y + height);
        fillPolygonDevice(new int[] {x0, x1, x2, x3}, new int[] {y0, y1, y2, y3}, 4, colorARGB);
    }

    public void drawRect(int x, int y, int width, int height) {
        drawRectD(x, y, width, height);
    }

    public void drawRectD(double x, double y, double width, double height) {
        if (destPixels == null || width < 0.0 || height < 0.0) {
            return;
        }
        syncPaint();
        double x2 = x + width;
        double y2 = y + height;
        drawLineD(x, y, x2, y);
        drawLineD(x2, y, x2, y2);
        drawLineD(x2, y2, x, y2);
        drawLineD(x, y2, x, y);
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        drawRoundRectD(x, y, width, height, arcWidth, arcHeight);
    }

    /** Double-precision form of {@link #drawRoundRect}; the same outline either way. */
    public void drawRoundRectD(double x, double y, double width, double height,
                               double arcWidth, double arcHeight) {
        if (destPixels == null || width < 0.0 || height < 0.0) {
            return;
        }
        syncPaint();
        double[] pts = roundRectOutline(x, y, width, height, arcWidth, arcHeight);
        int n = pts.length / 2;
        int[] xs = new int[n];
        int[] ys = new int[n];
        mapPointsD(pts, xs, ys);
        strokePolylineDevice(xs, ys, n, true, colorARGB);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        fillRoundRectD(x, y, width, height, arcWidth, arcHeight);
    }

    /** Double-precision form of {@link #fillRoundRect}. */
    public void fillRoundRectD(double x, double y, double width, double height,
                               double arcWidth, double arcHeight) {
        if (destPixels == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        syncPaint();
        fillPolygonD(roundRectOutline(x, y, width, height, arcWidth, arcHeight));
    }

    /**
     * Samples the outline of a rounded rectangle as {@code {x0, y0, x1, y1, ...}} in
     * user space, corner arcs included; the closing top edge is implicit.
     */
    private double[] roundRectOutline(double x, double y, double w, double h,
                                      double arcw, double arch) {
        if (arcw <= 0.0 || arch <= 0.0) {
            return new double[] {x, y, x + w, y, x + w, y + h, x, y + h};
        }
        double aw = Math.min(arcw / 2.0, w / 2.0);
        double ah = Math.min(arch / 2.0, h / 2.0);
        int steps = 6;
        double[] out = new double[(4 * steps + 4) * 2];
        int n = 0;
        // Top-right, bottom-right, bottom-left, top-left arcs, going clockwise in
        // screen coordinates (y grows downwards) from the end of the top edge.
        double[] cx = {x + w - aw, x + w - aw, x + aw, x + aw};
        double[] cy = {y + ah, y + h - ah, y + h - ah, y + ah};
        double[] a0 = {-Math.PI / 2.0, 0.0, Math.PI / 2.0, Math.PI};
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= steps; i++) {
                double a = a0[c] + (Math.PI / 2.0) * i / steps;
                out[n++] = cx[c] + aw * Math.cos(a);
                out[n++] = cy[c] + ah * Math.sin(a);
            }
        }
        return out;
    }

    /** Maps a user-space {@code {x0, y0, ...}} point list into device x/y arrays. */
    private void mapPointsD(double[] pts, int[] xs, int[] ys) {
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            xs[i] = deviceX(pts[i * 2], pts[i * 2 + 1]);
            ys[i] = deviceY(pts[i * 2], pts[i * 2 + 1]);
        }
    }

    /** Fills a user-space polygon given as {@code {x0, y0, x1, y1, ...}}. */
    private void fillPolygonD(double[] pts) {
        int n = pts.length / 2;
        int[] xs = new int[n];
        int[] ys = new int[n];
        mapPointsD(pts, xs, ys);
        fillPolygonDevice(xs, ys, n, colorARGB);
    }

    public void fillOval(int x, int y, int width, int height) {
        fillOvalD(x, y, width, height);
    }

    /**
     * Double-precision form of {@link #fillOval}: an axis-aligned ellipse goes
     * through the analytic scanline filler, a rotated or sheared one through the
     * mapped sample outline.
     */
    public void fillOvalD(double x, double y, double width, double height) {
        if (destPixels == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        syncPaint();
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            fillEllipseDevice((int) Math.floor(x) + tx, (int) Math.floor(y) + ty,
                    (int) Math.ceil(width), (int) Math.ceil(height), colorARGB);
        } else {
            int n = ellipseSteps(width, height);
            int[] xs = new int[n];
            int[] ys = new int[n];
            ellipseOutline(x, y, width, height, xs, ys);
            fillPolygonDevice(xs, ys, n, colorARGB);
        }
    }

    public void drawOval(int x, int y, int width, int height) {
        drawOvalD(x, y, width, height);
    }

    /** Double-precision form of {@link #drawOval}. */
    public void drawOvalD(double x, double y, double width, double height) {
        if (destPixels == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        syncPaint();
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            drawEllipseDevice((int) Math.floor(x) + tx, (int) Math.floor(y) + ty,
                    (int) Math.ceil(width), (int) Math.ceil(height), colorARGB);
        } else {
            int n = ellipseSteps(width, height);
            int[] xs = new int[n];
            int[] ys = new int[n];
            ellipseOutline(x, y, width, height, xs, ys);
            strokePolylineDevice(xs, ys, n, true, colorARGB);
        }
    }

    /** Number of samples used for a transformed ellipse outline. */
    private static int ellipseSteps(double width, double height) {
        return Math.max(16, (int) (4.0 * Math.max(width, height)));
    }

    /** Samples a user-space ellipse outline into device x/y arrays. */
    private void ellipseOutline(double x, double y, double w, double h, int[] xs, int[] ys) {
        int steps = xs.length;
        double rx = w / 2.0;
        double ry = h / 2.0;
        double cx = x + rx;
        double cy = y + ry;
        for (int i = 0; i < steps; i++) {
            double a = 2.0 * Math.PI * i / steps;
            double px = cx + rx * Math.cos(a);
            double py = cy + ry * Math.sin(a);
            xs[i] = deviceX(px, py);
            ys[i] = deviceY(px, py);
        }
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        drawArcD(x, y, width, height, startAngle, arcAngle);
    }

    /** Double-precision form of {@link #drawArc}. */
    public void drawArcD(double x, double y, double width, double height,
                         double startAngle, double arcAngle) {
        if (destPixels == null || arcAngle == 0.0) {
            return;
        }
        syncPaint();
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            drawArcDevice((int) Math.floor(x) + tx, (int) Math.floor(y) + ty,
                    (int) Math.ceil(width), (int) Math.ceil(height),
                    startAngle, arcAngle, false, colorARGB);
        } else {
            double[] pts = arcOutline(x, y, width, height, startAngle, arcAngle, false);
            int n = pts.length / 2;
            int[] xs = new int[n];
            int[] ys = new int[n];
            mapPointsD(pts, xs, ys);
            strokePolylineDevice(xs, ys, n, false, colorARGB);
        }
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        fillArcD(x, y, width, height, startAngle, arcAngle, true);
    }

    /**
     * Double-precision form of {@link #fillArc}; {@code pie} selects the pie
     * (centre-closed) form, {@code false} the chord-closed one that
     * {@code Arc2D.CHORD}/{@code Arc2D.OPEN} fills use.
     */
    public void fillArcD(double x, double y, double width, double height,
                         double startAngle, double arcAngle, boolean pie) {
        if (destPixels == null || arcAngle == 0.0) {
            return;
        }
        syncPaint();
        if (translationOnly() && pie) {
            int tx = translateX();
            int ty = translateY();
            drawArcDevice((int) Math.floor(x) + tx, (int) Math.floor(y) + ty,
                    (int) Math.ceil(width), (int) Math.ceil(height),
                    startAngle, arcAngle, true, colorARGB);
        } else {
            fillPolygonD(arcOutline(x, y, width, height, startAngle, arcAngle, pie));
        }
    }

    /** Samples an arc into user space; {@code pie} closes it through the centre. */
    private double[] arcOutline(double x, double y, double w, double h,
                                double startDeg, double extentDeg, boolean pie) {
        double rx = w / 2.0;
        double ry = h / 2.0;
        double cx = x + rx;
        double cy = y + ry;
        double a0 = Math.toRadians(startDeg);
        double a1 = Math.toRadians(startDeg + extentDeg);
        int steps = Math.max(8, (int) (Math.abs(extentDeg) / 3.0));
        double[] out = new double[(pie ? steps + 3 : steps + 1) * 2];
        int k = 0;
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (a1 - a0) * i / steps;
            out[k++] = cx + rx * Math.cos(a);
            out[k++] = cy - ry * Math.sin(a);
        }
        if (pie) {
            out[k++] = cx;
            out[k++] = cy;
            out[k++] = out[0];
            out[k] = out[1];
        }
        return out;
    }

    public void drawPolygon(Polygon p) {
        if (p == null || p.npoints < 2) {
            return;
        }
        drawPolygon(p.xpoints, p.ypoints, p.npoints);
    }

    public void fillPolygon(Polygon p) {
        if (p == null || p.npoints < 3) {
            return;
        }
        fillPolygon(p.xpoints, p.ypoints, p.npoints);
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (xPoints == null || yPoints == null || nPoints < 3 || destPixels == null) {
            return;
        }
        syncPaint();
        int[] xs = new int[nPoints];
        int[] ys = new int[nPoints];
        mapPoints(xPoints, yPoints, nPoints, xs, ys);
        fillPolygonDevice(xs, ys, nPoints, colorARGB);
    }

    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (xPoints == null || yPoints == null || nPoints < 2 || destPixels == null) {
            return;
        }
        syncPaint();
        int[] xs = new int[nPoints];
        int[] ys = new int[nPoints];
        mapPoints(xPoints, yPoints, nPoints, xs, ys);
        strokePolylineDevice(xs, ys, nPoints, true, colorARGB);
    }

    /** Maps user-space integer points to device space (identity or translation only). */
    private void mapPoints(int[] xPoints, int[] yPoints, int nPoints, int[] xs, int[] ys) {
        if (translationOnly()) {
            int tx = translateX();
            int ty = translateY();
            for (int i = 0; i < nPoints; i++) {
                xs[i] = xPoints[i] + tx;
                ys[i] = yPoints[i] + ty;
            }
        } else {
            for (int i = 0; i < nPoints; i++) {
                xs[i] = deviceX(xPoints[i], yPoints[i]);
                ys[i] = deviceY(xPoints[i], yPoints[i]);
            }
        }
    }

    public void clearRect(int x, int y, int width, int height) {
        if (destPixels == null || width <= 0 || height <= 0) {
            return;
        }
        int argb = background != null ? background.getRGB() : 0;
        int[] r = deviceBounds(x, y, width, height);
        int x0 = Math.max(0, r[0]);
        int y0 = Math.max(0, r[1]);
        int x1 = Math.min(destWidth, r[2]);
        int y1 = Math.min(destHeight, r[3]);
        if (clipBounds != null) {
            x0 = Math.max(x0, clipBounds.x);
            y0 = Math.max(y0, clipBounds.y);
            x1 = Math.min(x1, clipBounds.x + clipBounds.width);
            y1 = Math.min(y1, clipBounds.y + clipBounds.height);
        }
        for (int yy = y0; yy < y1; yy++) {
            int idx = yy * destWidth + x0;
            for (int xx = x0; xx < x1; xx++) {
                destPixels[idx++] = argb;
            }
        }
    }

    public void drawString(String str, int x, int y) {
        drawStringD(str, x, y);
    }

    public void drawString(String str, float x, float y) {
        drawStringD(str, x, y);
    }

    private void drawStringD(String str, double x, double y) {
        if (destPixels == null || str == null || str.isEmpty()) {
            return;
        }
        int dx;
        int dy;
        float scale;
        if (translationOnly()) {
            dx = (int) Math.round(x) + translateX();
            dy = (int) Math.round(y) + translateY();
            scale = 1.0f;
        } else {
            // The baseline moves with the transform; the glyph rasteriser needs the
            // effective scale separately (text is not rotated by this rasteriser).
            dx = deviceX(x, y);
            dy = deviceY(x, y);
            scale = (float) transform.getScaleX();
        }
        if (scale <= 0.0f) {
            return;
        }
        Object handle = font == null ? null : font.getTextRendererHandle();
        drawText(handle, str, dx, dy, scale);
    }

    // -------------------------------------------------------- composite/paint

    /** True when the composite is the plain opaque source-over used by the fast paths. */
    protected final boolean compositeIsPlainSrcOver() {
        return compositeRule == AlphaComposite.SRC_OVER && compositeAlpha == 255;
    }

    /** True when the composite writes the source pixel verbatim (SRC or opaque SRC_OVER). */
    protected final boolean compositeIsSourceCopy() {
        return compositeAlpha == 255
                && (compositeRule == AlphaComposite.SRC || compositeRule == AlphaComposite.SRC_OVER);
    }

    protected final boolean isBilinearInterpolation() {
        return interpolationHint == RenderingHints.VALUE_INTERPOLATION_BILINEAR;
    }

    /** Refreshes the device-space gradient axis; a no-op for solid colours. */
    private void syncPaint() {
        GradientPaint p = gradientPaint;
        if (p == null) {
            return;
        }
        AffineTransform t = transform;
        double x1 = p.getPoint1X();
        double y1 = p.getPoint1Y();
        double x2 = p.getPoint2X();
        double y2 = p.getPoint2Y();
        gradX0 = t.getScaleX() * x1 + t.getShearX() * y1 + t.getTranslateX();
        gradY0 = t.getShearY() * x1 + t.getScaleY() * y1 + t.getTranslateY();
        double dx = t.getScaleX() * x2 + t.getShearX() * y2 + t.getTranslateX();
        double dy = t.getShearY() * x2 + t.getScaleY() * y2 + t.getTranslateY();
        gradDx = dx - gradX0;
        gradDy = dy - gradY0;
        gradLen2 = gradDx * gradDx + gradDy * gradDy;
        gradCyclic = p.isCyclic();
        gradArgb1 = p.getColor1().getRGB();
        gradArgb2 = p.getColor2().getRGB();
    }

    /** The paint colour at a device pixel: the gradient, or the solid colour. */
    protected final int paintArgb(int deviceX, int deviceY, int solidArgb) {
        if (gradientPaint == null) {
            return solidArgb;
        }
        double t = 0.0;
        if (gradLen2 > 0.0) {
            t = ((deviceX + 0.5 - gradX0) * gradDx + (deviceY + 0.5 - gradY0) * gradDy) / gradLen2;
        }
        if (gradCyclic && gradLen2 > 0.0) {
            t = t - Math.floor(t);
        } else if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        int a1 = gradArgb1;
        int a2 = gradArgb2;
        int a = (int) (((a1 >>> 24) & 0xFF) + (((a2 >>> 24) & 0xFF) - ((a1 >>> 24) & 0xFF)) * t);
        int r = (int) (((a1 >>> 16) & 0xFF) + (((a2 >>> 16) & 0xFF) - ((a1 >>> 16) & 0xFF)) * t);
        int g = (int) (((a1 >>> 8) & 0xFF) + (((a2 >>> 8) & 0xFF) - ((a1 >>> 8) & 0xFF)) * t);
        int b = (int) ((a1 & 0xFF) + ((a2 & 0xFF) - (a1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // --------------------------------------------------------- image drawing

    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        if (img == null) {
            return false;
        }
        return drawImage(img, x, y, img.getWidth(), img.getHeight(), observer);
    }

    /**
     * Draws the source scaled into the rectangle {@code (x, y, width, height)}.
     *
     * <p>A negative {@code width}/{@code height} flips the image about the anchor,
     * the contract {@code ImageUtil.flipImage} relies on: the drawn rectangle then
     * spans {@code [x + width, x)} (respectively {@code [y + height, y)}).
     */
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        if (img == null || destPixels == null) {
            return false;
        }
        int[] srcPixels = img.getPixels();
        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        if (srcPixels == null || srcWidth <= 0 || srcHeight <= 0) {
            return true;
        }
        if (width == 0 || height == 0) {
            // The JDK draws nothing for a zero-size destination rectangle.
            return true;
        }
        if (translationOnly()) {
            // The game frame's per-frame path: integer destination, no allocation.
            int tx = translateX();
            int ty = translateY();
            drawImageDevice(srcPixels, srcWidth, srcHeight, img.hasAlpha, x + tx, y + ty, width, height);
            return true;
        }
        if (axisAligned()) {
            AffineTransform t = transform;
            double sx = t.getScaleX();
            double sy = t.getScaleY();
            double dx0 = sx * x + t.getTranslateX();
            double dx1 = sx * (x + width) + t.getTranslateX();
            double dy0 = sy * y + t.getTranslateY();
            double dy1 = sy * (y + height) + t.getTranslateY();
            double lo = Math.min(dx0, dx1);
            double hi = Math.max(dx0, dx1);
            int devX0 = (int) Math.round(lo);
            int devW = (int) Math.round(hi) - devX0;
            lo = Math.min(dy0, dy1);
            hi = Math.max(dy0, dy1);
            int devY0 = (int) Math.round(lo);
            int devH = (int) Math.round(hi) - devY0;
            if (devW == 0 || devH == 0) {
                return true;
            }
            drawImageDevice(srcPixels, srcWidth, srcHeight, img.hasAlpha, devX0, devY0, devW, devH);
            return true;
        }
        drawImageMapped(img, x, y, width, height);
        return true;
    }

    /**
     * Draws the source into the integer device rectangle
     * {@code (x, y, |width|, |height|)}, flipped when the size is negative.
     */
    private void drawImageDevice(int[] srcPixels, int srcWidth, int srcHeight, boolean srcHasAlpha,
                                 int x, int y, int width, int height) {
        boolean flipX = width < 0;
        boolean flipY = height < 0;
        int w = flipX ? -width : width;
        int h = flipY ? -height : height;
        int dstX0 = flipX ? x + width : x;
        int dstY0 = flipY ? y + height : y;
        int dstX1 = dstX0 + w;
        int dstY1 = dstY0 + h;

        int cx0 = 0;
        int cy0 = 0;
        int cx1 = destWidth;
        int cy1 = destHeight;
        if (clipBounds != null) {
            cx0 = Math.max(cx0, clipBounds.x);
            cy0 = Math.max(cy0, clipBounds.y);
            cx1 = Math.min(cx1, clipBounds.x + clipBounds.width);
            cy1 = Math.min(cy1, clipBounds.y + clipBounds.height);
        }
        int px0 = Math.max(dstX0, cx0);
        int py0 = Math.max(dstY0, cy0);
        int px1 = Math.min(dstX1, cx1);
        int py1 = Math.min(dstY1, cy1);
        if (px0 >= px1 || py0 >= py1) {
            return;
        }

        if (!srcHasAlpha && !flipX && !flipY
                && w == srcWidth && h == srcHeight && compositeIsSourceCopy()) {
            // 1:1 copy — the game's every-frame path (drawImage(img, 0, 0, null)
            // with the source already at the display size). One System.arraycopy
            // per row, a native runtime call even while the class is interpreted.
            int copyW = px1 - px0;
            for (int dy = py0; dy < py1; dy++) {
                System.arraycopy(srcPixels, (dy - y) * srcWidth + (px0 - x),
                                 destPixels, dy * destWidth + px0, copyW);
            }
            return;
        }
        scaleBlit(srcPixels, srcWidth, srcHeight, srcHasAlpha,
                dstX0, dstY0, w, h, px0, py0, px1, py1, flipX, flipY);
    }

    /**
     * Nearest-neighbour or bilinear scaling of the source into the destination
     * rectangle {@code [dstX0, dstX0 + w) x [dstY0, dstY0 + h)}, mirrored on an axis
     * whose size was negative, clipped to {@code [px0, px1) x [py0, py1)}.
     * Source row/column stepping uses accumulators instead of two divisions per
     * pixel; bilinear sampling is centre aligned.
     */
    private void scaleBlit(int[] srcPixels, int srcWidth, int srcHeight, boolean srcHasAlpha,
                           int dstX0, int dstY0, int w, int h,
                           int px0, int py0, int px1, int py1, boolean flipX, boolean flipY) {
        boolean bilinear = isBilinearInterpolation() && (w != srcWidth || h != srcHeight);
        boolean copy = !srcHasAlpha && compositeIsSourceCopy();
        int copyW = px1 - px0;
        int xDelta = px0 - dstX0;
        int destRow = py0 * destWidth + px0;
        for (int dy = py0; dy < py1; dy++) {
            int iy = dy - dstY0;
            int srcY = flipY ? (h - 1 - iy) * srcHeight / h : iy * srcHeight / h;
            int srcRow = srcY * srcWidth;
            int srcX = xDelta * srcWidth / w;
            int acc = xDelta * srcWidth % w;
            int mirrored = flipX ? srcWidth - 1 - srcX : srcX;
            double syf = 0.0;
            if (bilinear) {
                syf = (iy + 0.5) * srcHeight / (double) h;
                if (flipY) {
                    syf = srcHeight - syf;
                }
                syf -= 0.5;
            }
            for (int i = 0; i < copyW; i++) {
                int argb;
                if (bilinear) {
                    double sxf = (xDelta + i + 0.5) * srcWidth / (double) w;
                    if (flipX) {
                        sxf = srcWidth - sxf;
                    }
                    argb = sampleBilinear(srcPixels, srcWidth, srcHeight, sxf - 0.5, syf);
                } else {
                    argb = srcPixels[srcRow + mirrored];
                }
                int idx = destRow + i;
                // A source without an alpha channel is opaque (the JDK contract),
                // so its alpha byte — 0 for the rasteriser's ground pixels — must
                // not be read as transparency when blending.
                destPixels[idx] = copy ? argb
                        : blendPixel(destPixels[idx], srcHasAlpha ? argb : (argb | 0xFF000000));
                acc += srcWidth;
                while (acc >= w) {
                    acc -= w;
                    srcX++;
                    mirrored = flipX ? srcWidth - 1 - srcX : srcX;
                }
            }
            destRow += destWidth;
        }
    }

    /**
     * Generic transformed image draw: the destination quad is walked pixel by
     * pixel and each destination point is mapped back through the inverse
     * transform, so rotation and shear are handled (the JDK's own filter for the
     * exact kernel is unspecified; this is nearest or bilinear by hint).
     */
    private void drawImageMapped(Image img, int x, int y, int width, int height) {
        AffineTransform inv;
        try {
            inv = transform.createInverse();
        } catch (RuntimeException e) {
            return;
        }
        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        int[] srcPixels = img.getPixels();
        AffineTransform t = transform;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double ux = (i == 1 || i == 2) ? x + width : x;
            double uy = (i >= 2) ? y + height : y;
            double dx = t.getScaleX() * ux + t.getShearX() * uy + t.getTranslateX();
            double dy = t.getShearY() * ux + t.getScaleY() * uy + t.getTranslateY();
            minX = Math.min(minX, dx);
            maxX = Math.max(maxX, dx);
            minY = Math.min(minY, dy);
            maxY = Math.max(maxY, dy);
        }
        int cx0 = 0;
        int cy0 = 0;
        int cx1 = destWidth;
        int cy1 = destHeight;
        if (clipBounds != null) {
            cx0 = Math.max(cx0, clipBounds.x);
            cy0 = Math.max(cy0, clipBounds.y);
            cx1 = Math.min(cx1, clipBounds.x + clipBounds.width);
            cy1 = Math.min(cy1, clipBounds.y + clipBounds.height);
        }
        int px0 = Math.max((int) Math.floor(minX), cx0);
        int py0 = Math.max((int) Math.floor(minY), cy0);
        int px1 = Math.min((int) Math.ceil(maxX), cx1);
        int py1 = Math.min((int) Math.ceil(maxY), cy1);
        boolean bilinear = isBilinearInterpolation();
        boolean srcHasAlpha = img.hasAlpha;
        boolean copy = !srcHasAlpha && compositeIsSourceCopy();
        double im00 = inv.getScaleX();
        double im01 = inv.getShearX();
        double im02 = inv.getTranslateX();
        double im10 = inv.getShearY();
        double im11 = inv.getScaleY();
        double im12 = inv.getTranslateY();
        for (int dy = py0; dy < py1; dy++) {
            int row = dy * destWidth;
            for (int dx = px0; dx < px1; dx++) {
                double ux = im00 * (dx + 0.5) + im01 * (dy + 0.5) + im02;
                double uy = im10 * (dx + 0.5) + im11 * (dy + 0.5) + im12;
                double sxf = (ux - x) * srcWidth / width;
                double syf = (uy - y) * srcHeight / height;
                if (sxf < 0.0 || syf < 0.0 || sxf >= srcWidth || syf >= srcHeight) {
                    continue;
                }
                int argb = bilinear
                        ? sampleBilinear(srcPixels, srcWidth, srcHeight, sxf - 0.5, syf - 0.5)
                        : srcPixels[(int) syf * srcWidth + (int) sxf];
                int idx = row + dx;
                destPixels[idx] = copy ? argb
                        : blendPixel(destPixels[idx], srcHasAlpha ? argb : (argb | 0xFF000000));
            }
        }
    }

    /**
     * Bilinear sample at a fractional source position. Channels are interpolated
     * independently (including alpha) rather than premultiplied first — the JDK's
     * exact kernel is unspecified and this is the obvious thing.
     */
    private static int sampleBilinear(int[] src, int srcWidth, int srcHeight, double fx, double fy) {
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double tx = fx - x0;
        double ty = fy - y0;
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 >= srcWidth) {
            x1 = srcWidth - 1;
        }
        if (y1 >= srcHeight) {
            y1 = srcHeight - 1;
        }
        int p00 = src[y0 * srcWidth + x0];
        int p10 = src[y0 * srcWidth + x1];
        int p01 = src[y1 * srcWidth + x0];
        int p11 = src[y1 * srcWidth + x1];
        double w00 = (1.0 - tx) * (1.0 - ty);
        double w10 = tx * (1.0 - ty);
        double w01 = (1.0 - tx) * ty;
        double w11 = tx * ty;
        int a = (int) (((p00 >>> 24) & 0xFF) * w00 + ((p10 >>> 24) & 0xFF) * w10
                + ((p01 >>> 24) & 0xFF) * w01 + ((p11 >>> 24) & 0xFF) * w11);
        int r = (int) (((p00 >>> 16) & 0xFF) * w00 + ((p10 >>> 16) & 0xFF) * w10
                + ((p01 >>> 16) & 0xFF) * w01 + ((p11 >>> 16) & 0xFF) * w11);
        int g = (int) (((p00 >>> 8) & 0xFF) * w00 + ((p10 >>> 8) & 0xFF) * w10
                + ((p01 >>> 8) & 0xFF) * w01 + ((p11 >>> 8) & 0xFF) * w11);
        int b = (int) ((p00 & 0xFF) * w00 + (p10 & 0xFF) * w10
                + (p01 & 0xFF) * w01 + (p11 & 0xFF) * w11);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------ text

    /**
     * Draws a text run with its baseline at the device point {@code (x, y)}, the
     * glyphs rasterised {@code scale} times the font's logical size.
     *
     * <p>When the clip cannot cut the run and the composite is the default, the
     * renderer composites straight into the destination. Otherwise the run is
     * rasterised into a scratch buffer first so the clip box is honoured exactly.
     */
    private void drawText(Object handle, String str, int x, int y, float scale) {
        if (handle == null) {
            return;
        }
        if (compositeIsPlainSrcOver() && clipIsWholeSurface()) {
            TextBridge.drawString(handle, destPixels, destWidth, destHeight, str, x, y, colorARGB, false, scale);
            return;
        }
        // Measuring must use the device-resolution handle: the glyphs the renderer
        // is about to composite are scale times the logical size.
        Object measured = TextBridge.scaledFont(handle, scale);
        int sizePx = font != null ? font.getSize() : 12;
        int width = TextBridge.stringWidth(measured, str);
        int ascent = TextBridge.ascent(measured, sizePx);
        int descent = TextBridge.descent(measured, sizePx);
        int bx = x - 1;
        int by = y - ascent - 2;
        int bw = width + 3;
        int bh = ascent + descent + 4;
        if (compositeIsPlainSrcOver() && (clipBounds == null
                || (clipBounds.x <= bx && clipBounds.y <= by
                    && clipBounds.x + clipBounds.width >= bx + bw
                    && clipBounds.y + clipBounds.height >= by + bh))) {
            // The default composite and nothing to clip: composite straight in.
            TextBridge.drawString(handle, destPixels, destWidth, destHeight, str, x, y, colorARGB, false, scale);
            return;
        }
        int x0 = Math.max(bx, 0);
        int y0 = Math.max(by, 0);
        int x1 = Math.min(bx + bw, destWidth);
        int y1 = Math.min(by + bh, destHeight);
        if (clipBounds != null) {
            x0 = Math.max(x0, clipBounds.x);
            y0 = Math.max(y0, clipBounds.y);
            x1 = Math.min(x1, clipBounds.x + clipBounds.width);
            y1 = Math.min(y1, clipBounds.y + clipBounds.height);
        }
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        int cw = x1 - x0;
        int ch = y1 - y0;
        int[] scratch = new int[cw * ch];
        TextBridge.drawString(handle, scratch, cw, ch, str, x - x0, y - y0, colorARGB, false, scale);
        for (int row = 0; row < ch; row++) {
            int destRow = (y0 + row) * destWidth + x0;
            int srcRow = row * cw;
            for (int col = 0; col < cw; col++) {
                int s = scratch[srcRow + col];
                if ((s >>> 24) != 0) {
                    destPixels[destRow + col] = blendPixel(destPixels[destRow + col], s);
                }
            }
        }
    }

    // --------------------------------------------------------- device layer

    /**
     * Bounding box, in device coordinates, of the given user-space rectangle.
     * Returns {@code {x0, y0, x1, y1}} with x1/y1 exclusive.
     */
    protected int[] deviceBounds(int x, int y, int width, int height) {
        if (transform.isTranslationOnly()) {
            int tx = (int) Math.round(transform.getTranslateX());
            int ty = (int) Math.round(transform.getTranslateY());
            return new int[] {x + tx, y + ty, x + width + tx, y + height + ty};
        }
        return transformedBounds(new double[] {x, y, x + width, y + height});
    }

    /**
     * The device-space bounding box of the user-space rectangle {@code {x0, y0, x1, y1}} —
     * two corner points mapped through the transform (the array holds two points, not four).
     */
    protected int[] transformedBounds(double[] rect) {
        double[] out = new double[4];
        transform.transform(rect, 0, out, 0, 2);
        double minX = Math.min(out[0], out[2]);
        double maxX = Math.max(out[0], out[2]);
        double minY = Math.min(out[1], out[3]);
        double maxY = Math.max(out[1], out[3]);
        return new int[] {(int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY)};
    }

    /** Device-space bounding box of an arbitrary shape (uses its getBounds). */
    protected Rectangle deviceBoundsOf(Shape s) {
        Rectangle b;
        if (s instanceof Rectangle) {
            b = new Rectangle((Rectangle) s);
        } else if (s instanceof Rectangle2D) {
            b = ((Rectangle2D) s).getBounds();
        } else {
            b = s.getBounds();
        }
        if (b == null) {
            return null;
        }
        int[] r = deviceBounds(b.x, b.y, b.width, b.height);
        return new Rectangle(r[0], r[1], r[2] - r[0], r[3] - r[1]);
    }

    // ------------------------------------------------------ composite maths

    /**
     * Composites the source colour {@code argb} onto the destination pixel using
     * the active {@link AlphaComposite} rule and its extra alpha.
     *
     * <p>Deviation from the JDK: destination pixels of a surface that carries no
     * alpha channel are treated as opaque (alpha 255) and their alpha byte is
     * preserved, so drawing over the game frame blends visually instead of making
     * overlays opaque.
     */
    protected final int blendPixel(int dst, int argb) {
        int sa = (argb >>> 24) * compositeAlpha / 255;
        int rule = compositeRule;
        if (rule == AlphaComposite.SRC_OVER && compositeAlpha == 255) {
            if (sa <= 0) {
                return dst;
            }
            int srgb = argb & 0xFFFFFF;
            if (!destHasAlpha) {
                if (sa >= 255) {
                    return (dst & 0xFF000000) | srgb;
                }
                int dr0 = (dst >>> 16) & 0xFF;
                int dg0 = (dst >>> 8) & 0xFF;
                int db0 = dst & 0xFF;
                int r0 = (((srgb >>> 16) & 0xFF) * sa + dr0 * (255 - sa)) / 255;
                int g0 = (((srgb >>> 8) & 0xFF) * sa + dg0 * (255 - sa)) / 255;
                int b0 = ((srgb & 0xFF) * sa + db0 * (255 - sa)) / 255;
                return (dst & 0xFF000000) | (r0 << 16) | (g0 << 8) | b0;
            }
            int da0 = (dst >>> 24) & 0xFF;
            int outA0 = sa + da0 * (255 - sa) / 255;
            if (outA0 == 0) {
                return 0;
            }
            int sr0 = (srgb >>> 16) & 0xFF;
            int sg0 = (srgb >>> 8) & 0xFF;
            int sb0 = srgb & 0xFF;
            int dr1 = (dst >>> 16) & 0xFF;
            int dg1 = (dst >>> 8) & 0xFF;
            int db1 = dst & 0xFF;
            int r1 = (sr0 * sa + dr1 * da0 * (255 - sa) / 255) / outA0;
            int g1 = (sg0 * sa + dg1 * da0 * (255 - sa) / 255) / outA0;
            int b1 = (sb0 * sa + db1 * da0 * (255 - sa) / 255) / outA0;
            return (outA0 << 24) | (r1 << 16) | (g1 << 8) | b1;
        }
        return blendRule(dst, argb, sa);
    }

    /** Porter-Duff rules other than the plain source-over fast path. */
    private int blendRule(int dst, int argb, int sa) {
        int rule = compositeRule;
        if (rule == AlphaComposite.CLEAR) {
            return 0;
        }
        if (rule == AlphaComposite.DST) {
            return dst;
        }
        int da = destHasAlpha ? (dst >>> 24) & 0xFF : 255;
        if (rule == AlphaComposite.SRC_ATOP) {
            // The rule ImageUtil.recolorImage depends on: the destination alpha is
            // kept exactly (no rounding through the Porter-Duff table) and the
            // colour is written only where the destination is opaque.
            if (da == 0) {
                return destHasAlpha ? 0 : (dst & 0xFF000000);
            }
            int r = (sa * ((argb >>> 16) & 0xFF) + (255 - sa) * ((dst >>> 16) & 0xFF)) / 255;
            int g = (sa * ((argb >>> 8) & 0xFF) + (255 - sa) * ((dst >>> 8) & 0xFF)) / 255;
            int b = (sa * (argb & 0xFF) + (255 - sa) * (dst & 0xFF)) / 255;
            return destHasAlpha ? (da << 24) | (r << 16) | (g << 8) | b
                    : (dst & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
        int fa;
        int fb;
        switch (rule) {
            case AlphaComposite.SRC:
                fa = 255;
                fb = 0;
                break;
            case AlphaComposite.DST_OVER:
                fa = 255 - da;
                fb = 255;
                break;
            case AlphaComposite.SRC_IN:
                fa = da;
                fb = 0;
                break;
            case AlphaComposite.DST_IN:
                fa = 0;
                fb = sa;
                break;
            case AlphaComposite.SRC_OUT:
                fa = 255 - da;
                fb = 0;
                break;
            case AlphaComposite.DST_OUT:
                fa = 0;
                fb = 255 - sa;
                break;
            case AlphaComposite.DST_ATOP:
                fa = 255 - da;
                fb = sa;
                break;
            case AlphaComposite.XOR:
                fa = 255 - da;
                fb = 255 - sa;
                break;
            default:
                // Unreachable: every declared rule is handled above (SRC_OVER takes
                // the fast path). Kept as a documented fallback.
                fa = 255;
                fb = 255 - sa;
                break;
        }
        int wS = sa * fa / 255;
        int wD = da * fb / 255;
        int outA = wS + wD;
        int r = 0;
        int g = 0;
        int b = 0;
        if (outA > 0) {
            r = (wS * ((argb >>> 16) & 0xFF) + wD * ((dst >>> 16) & 0xFF)) / outA;
            g = (wS * ((argb >>> 8) & 0xFF) + wD * ((dst >>> 8) & 0xFF)) / outA;
            b = (wS * (argb & 0xFF) + wD * (dst & 0xFF)) / outA;
        }
        if (destHasAlpha) {
            return (outA << 24) | (r << 16) | (g << 8) | b;
        }
        return (dst & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // --------------------------------------------------------- rasterisation

    protected final void plot(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= destWidth || y >= destHeight) {
            return;
        }
        if (clipBounds != null && (x < clipBounds.x || y < clipBounds.y
                || x >= clipBounds.x + clipBounds.width || y >= clipBounds.y + clipBounds.height)) {
            return;
        }
        int idx = y * destWidth + x;
        destPixels[idx] = blendPixel(destPixels[idx], paintArgb(x, y, argb));
    }

    /** Device-debug counter: pixels written by fills (see UiBridge's paint profiling). */
    public static final java.util.concurrent.atomic.AtomicLong FILLED_PIXELS =
        new java.util.concurrent.atomic.AtomicLong();

    protected final void fillSpan(int x0, int x1, int y, int argb) {
        if (destPixels == null || y < 0 || y >= destHeight) {
            return;
        }
        if (x0 < 0) {
            x0 = 0;
        }
        if (x1 >= destWidth) {
            x1 = destWidth - 1;
        }
        if (clipBounds != null) {
            if (y < clipBounds.y || y >= clipBounds.y + clipBounds.height) {
                return;
            }
            if (x0 < clipBounds.x) {
                x0 = clipBounds.x;
            }
            int cx = clipBounds.x + clipBounds.width - 1;
            if (x1 > cx) {
                x1 = cx;
            }
        }
        if (x0 > x1) {
            return;
        }
        int idx = y * destWidth + x0;
        if (gradientPaint != null) {
            for (int x = x0; x <= x1; x++) {
                destPixels[idx] = blendPixel(destPixels[idx], paintArgb(x, y, argb));
                idx++;
            }
        } else if ((argb >>> 24) == 0xFF) {
            // Opaque fill: a plain store. blendPixel's multiply-add per pixel dominated the
            // sidebar's paint (RuneLite's panels are full of opaque rectangles).
            int count = x1 - x0 + 1;
            java.util.Arrays.fill(destPixels, idx, idx + count, argb);
        } else {
            for (int x = x0; x <= x1; x++) {
                destPixels[idx] = blendPixel(destPixels[idx], argb);
                idx++;
            }
        }
    }

    protected final void fillRectDevice(int x, int y, int w, int h, int argb) {
        if (destPixels == null || w <= 0 || h <= 0) {
            return;
        }
        if (clipBounds != null) {
            if (x < clipBounds.x) {
                w -= clipBounds.x - x;
                x = clipBounds.x;
            }
            if (y < clipBounds.y) {
                h -= clipBounds.y - y;
                y = clipBounds.y;
            }
            if (x + w > clipBounds.x + clipBounds.width) {
                w = clipBounds.x + clipBounds.width - x;
            }
            if (y + h > clipBounds.y + clipBounds.height) {
                h = clipBounds.y + clipBounds.height - y;
            }
        }
        for (int yy = y; yy < y + h; yy++) {
            fillSpan(x, x + w - 1, yy, argb);
        }
    }

    /** Draws one stroked segment in device space, honouring the subclass stroke. */
    protected final void drawLineWidth(int x1, int y1, int x2, int y2, int argb) {
        BasicStroke s = strokeOrNull();
        int w = s == null ? 1 : Math.max(1, Math.round(s.getLineWidth()));
        if (w <= 1) {
            drawLineDevice(x1, y1, x2, y2, argb);
            return;
        }
        int half = w / 2;
        int ax = x1;
        int ay = y1;
        int bx = x2;
        int by = y2;
        if (s.getEndCap() == BasicStroke.CAP_SQUARE) {
            // The square cap extends half the line width past each endpoint along
            // the dominant axis (a documented approximation of the real quad).
            if (Math.abs(by - ay) <= Math.abs(bx - ax)) {
                int step = bx >= ax ? half : -half;
                ax -= step;
                bx += step;
            } else {
                int step = by >= ay ? half : -half;
                ay -= step;
                by += step;
            }
        }
        if (Math.abs(by - ay) <= Math.abs(bx - ax)) {
            for (int i = -half; i <= w - 1 - half; i++) {
                drawLineDevice(ax, ay + i, bx, by + i, argb);
            }
        } else {
            for (int i = -half; i <= w - 1 - half; i++) {
                drawLineDevice(ax + i, ay, bx + i, by, argb);
            }
        }
        if (s.getEndCap() == BasicStroke.CAP_ROUND) {
            fillDiscDevice(ax, ay, w, argb);
            fillDiscDevice(bx, by, w, argb);
        }
    }

    /** A filled circle centred on a device point; used for round caps and joins. */
    protected final void fillDiscDevice(int cx, int cy, int diameter, int argb) {
        if (diameter <= 0) {
            return;
        }
        fillEllipseDevice(cx - diameter / 2, cy - diameter / 2, diameter, diameter, argb);
    }

    protected final void drawLineDevice(int x1, int y1, int x2, int y2, int argb) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            plot(x1, y1, argb);
            if (x1 == x2 && y1 == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    protected final void drawPolygonDevice(int[] xs, int[] ys, int n, int argb) {
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            drawLineDevice(xs[i], ys[i], xs[j], ys[j], argb);
        }
    }

    /**
     * Strokes a device-space polyline: every segment takes the current width, and
     * round joins put a cap disc on each vertex (a documented approximation of the
     * real mitered outline).
     */
    protected final void strokePolylineDevice(int[] xs, int[] ys, int n, boolean closed, int argb) {
        if (n < 2) {
            return;
        }
        BasicStroke s = strokeOrNull();
        int w = s == null ? 1 : Math.max(1, Math.round(s.getLineWidth()));
        if (w <= 1) {
            if (closed) {
                drawPolygonDevice(xs, ys, n, argb);
            } else {
                for (int i = 0; i < n - 1; i++) {
                    drawLineDevice(xs[i], ys[i], xs[i + 1], ys[i + 1], argb);
                }
            }
            return;
        }
        int segments = closed ? n : n - 1;
        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % n;
            drawLineWidth(xs[i], ys[i], xs[j], ys[j], argb);
        }
        if (s.getLineJoin() == BasicStroke.JOIN_ROUND) {
            for (int i = 0; i < n; i++) {
                fillDiscDevice(xs[i], ys[i], w, argb);
            }
        }
    }

    protected final void fillPolygonDevice(int[] xs, int[] ys, int n, int argb) {
        if (n < 3) {
            return;
        }
        int minY = ys[0];
        int maxY = ys[0];
        for (int i = 1; i < n; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        minY = Math.max(minY, clipBounds != null ? clipBounds.y : 0);
        maxY = Math.min(maxY, (clipBounds != null ? clipBounds.y + clipBounds.height : destHeight) - 1);
        double[] inter = new double[n];
        for (int y = minY; y <= maxY; y++) {
            int cnt = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int y1 = ys[i];
                int y2 = ys[j];
                if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
                    double t = (double) (y - y1) / (double) (y2 - y1);
                    inter[cnt++] = xs[i] + t * (xs[j] - xs[i]);
                }
            }
            if (cnt < 2) {
                continue;
            }
            java.util.Arrays.sort(inter, 0, cnt);
            for (int k = 0; k + 1 < cnt; k += 2) {
                fillSpan((int) Math.ceil(inter[k]), (int) Math.floor(inter[k + 1]), y, argb);
            }
        }
    }

    protected final void fillEllipseDevice(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        double rx = w / 2.0;
        double ry = h / 2.0;
        double cx = x + rx;
        double cy = y + ry;
        for (int yy = y; yy < y + h; yy++) {
            double dy = (yy + 0.5 - cy) / ry;
            double t = 1.0 - dy * dy;
            if (t < 0.0) {
                continue;
            }
            double dx = rx * Math.sqrt(t);
            fillSpan((int) Math.round(cx - dx), (int) Math.round(cx + dx) - 1, yy, argb);
        }
    }

    protected final void drawEllipseDevice(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        double rx = w / 2.0;
        double ry = h / 2.0;
        double cx = x + rx;
        double cy = y + ry;
        int steps = Math.max(16, Math.max(w, h) * 4);
        for (int i = 0; i < steps; i++) {
            double a = 2.0 * Math.PI * i / steps;
            plot((int) Math.round(cx + rx * Math.cos(a)), (int) Math.round(cy + ry * Math.sin(a)), argb);
        }
    }

    /**
     * Samples an arc/pie into a polyline. {@code fill} selects the filled pie
     * form (a closed polygon through the centre) rather than the outline.
     */
    protected final void drawArcDevice(int x, int y, int w, int h,
                                       double startDeg, double extentDeg, boolean fill, int argb) {
        if (w <= 0 || h <= 0 || extentDeg == 0.0) {
            return;
        }
        double rx = w / 2.0;
        double ry = h / 2.0;
        double cx = x + rx;
        double cy = y + ry;
        double a0 = Math.toRadians(startDeg);
        double a1 = Math.toRadians(startDeg + extentDeg);
        int steps = Math.max(8, (int) (Math.abs(extentDeg) / 3.0));
        double[] xs = new double[steps + 1];
        double[] ys = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (a1 - a0) * i / steps;
            xs[i] = cx + rx * Math.cos(a);
            ys[i] = cy - ry * Math.sin(a);
        }
        if (fill) {
            int n = steps + 3;
            int[] px = new int[n];
            int[] py = new int[n];
            for (int i = 0; i <= steps; i++) {
                px[i] = (int) Math.round(xs[i]);
                py[i] = (int) Math.round(ys[i]);
            }
            px[steps + 1] = (int) Math.round(cx);
            py[steps + 1] = (int) Math.round(cy);
            px[steps + 2] = (int) Math.round(xs[0]);
            py[steps + 2] = (int) Math.round(ys[0]);
            fillPolygonDevice(px, py, n, argb);
        } else {
            for (int i = 0; i < steps; i++) {
                drawLineDevice((int) Math.round(xs[i]), (int) Math.round(ys[i]),
                        (int) Math.round(xs[i + 1]), (int) Math.round(ys[i + 1]), argb);
            }
        }
    }
}

package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;
import org.runelite.mobile.bridge.TextBridge;

/**
 * AWT Graphics compatibility stub for Android runtime.
 *
 * <p>Draws directly into a non-premultiplied ARGB {@code int[]} supplied by the
 * caller (the game frame, a {@code BufferedImage}'s raster or the applet bridge).
 * Destination alpha is ignored for colour arithmetic and preserved on write, so
 * drawing over the game frame (whose pixels carry alpha 0) blends visually
 * instead of making overlays opaque.
 *
 * <p>{@link #drawImage} keeps two branch-free fast paths for opaque sources —
 * a 1:1 {@code System.arraycopy} per row and a divide-free nearest-neighbour
 * accumulator for scaled draws — because it is the per-frame hot path for the
 * full 765x503 frame. Only sources with {@link Image#hasAlpha} take the
 * per-pixel source-over blend path.
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
    /** User-space clip shape as set; informational (see clipBounds). */
    protected Shape clipShape;
    /** Device-space clip bounding box, null meaning the whole surface. */
    protected Rectangle clipBounds;
    /** Extra alpha multiplier from an AlphaComposite, 0..255. */
    protected int compositeAlpha = 255;
    /** True when the destination pixels themselves carry meaningful alpha. */
    protected boolean destHasAlpha;

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

    public void setClip(Shape clip) {
        this.clipShape = clip;
        this.clipBounds = clip == null ? null : deviceBoundsOf(clip);
    }

    public Shape getClip() {
        return clipShape;
    }

    public void clip(Shape s) {
        if (s == null) {
            return;
        }
        Rectangle b = deviceBoundsOf(s);
        clipShape = null;
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
    }

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

    // ------------------------------------------------------------- drawing

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (destPixels == null) {
            return;
        }
        if (transform.isTranslationOnly()) {
            int tx = (int) Math.round(transform.getTranslateX());
            int ty = (int) Math.round(transform.getTranslateY());
            drawLineDevice(x1 + tx, y1 + ty, x2 + tx, y2 + ty, colorARGB);
        } else {
            double[] p = new double[4];
            transform.transform(new double[] {x1, y1, x2, y2}, 0, p, 0, 2);
            drawLineDevice((int) Math.round(p[0]), (int) Math.round(p[1]),
                    (int) Math.round(p[2]), (int) Math.round(p[3]), colorARGB);
        }
    }

    public void drawLineD(double x1, double y1, double x2, double y2) {
        if (destPixels == null) {
            return;
        }
        double[] p = new double[4];
        transform.transform(new double[] {x1, y1, x2, y2}, 0, p, 0, 2);
        drawLineDevice((int) Math.round(p[0]), (int) Math.round(p[1]),
                (int) Math.round(p[2]), (int) Math.round(p[3]), colorARGB);
    }

    public void fillRect(int x, int y, int width, int height) {
        if (destPixels == null || width <= 0 || height <= 0) {
            return;
        }
        if (transform.isTranslationOnly()) {
            int tx = (int) Math.round(transform.getTranslateX());
            int ty = (int) Math.round(transform.getTranslateY());
            fillRectDevice(x + tx, y + ty, width, height, colorARGB);
        } else {
            int[] r = deviceBounds(x, y, width, height);
            fillRectDevice(r[0], r[1], r[2] - r[0], r[3] - r[1], colorARGB);
        }
    }

    public void fillRectD(double x, double y, double width, double height) {
        if (destPixels == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        int[] r = deviceBounds((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(width), (int) Math.ceil(height));
        fillRectDevice(r[0], r[1], r[2] - r[0], r[3] - r[1], colorARGB);
    }

    public void drawRect(int x, int y, int width, int height) {
        drawRectD(x, y, width, height);
    }

    public void drawRectD(double x, double y, double width, double height) {
        if (destPixels == null || width < 0.0 || height < 0.0) {
            return;
        }
        double x2 = x + width;
        double y2 = y + height;
        drawLineD(x, y, x2, y);
        drawLineD(x2, y, x2, y2);
        drawLineD(x2, y2, x, y2);
        drawLineD(x, y2, x, y);
    }

    public void fillOval(int x, int y, int width, int height) {
        if (destPixels == null || width <= 0 || height <= 0) {
            return;
        }
        int[] r = deviceBounds(x, y, width, height);
        fillEllipseDevice(r[0], r[1], r[2] - r[0], r[3] - r[1], colorARGB);
    }

    public void drawOval(int x, int y, int width, int height) {
        if (destPixels == null || width <= 0 || height <= 0) {
            return;
        }
        int[] r = deviceBounds(x, y, width, height);
        drawEllipseDevice(r[0], r[1], r[2] - r[0], r[3] - r[1], colorARGB);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (destPixels == null) {
            return;
        }
        int[] r = deviceBounds(x, y, width, height);
        drawArcDevice(r[0], r[1], r[2] - r[0], r[3] - r[1], startAngle, arcAngle, false, colorARGB);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (destPixels == null) {
            return;
        }
        int[] r = deviceBounds(x, y, width, height);
        drawArcDevice(r[0], r[1], r[2] - r[0], r[3] - r[1], startAngle, arcAngle, true, colorARGB);
    }

    public void drawPolygon(Polygon p) {
        if (p == null || p.npoints < 2) {
            return;
        }
        int n = p.npoints;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            int[] d = devicePoint(p.xpoints[i], p.ypoints[i]);
            xs[i] = d[0];
            ys[i] = d[1];
        }
        drawPolygonDevice(xs, ys, n, colorARGB);
    }

    public void fillPolygon(Polygon p) {
        if (p == null || p.npoints < 3) {
            return;
        }
        int n = p.npoints;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            int[] d = devicePoint(p.xpoints[i], p.ypoints[i]);
            xs[i] = d[0];
            ys[i] = d[1];
        }
        fillPolygonDevice(xs, ys, n, colorARGB);
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (xPoints == null || yPoints == null || nPoints < 3) {
            return;
        }
        int[] xs = new int[nPoints];
        int[] ys = new int[nPoints];
        for (int i = 0; i < nPoints; i++) {
            int[] d = devicePoint(xPoints[i], yPoints[i]);
            xs[i] = d[0];
            ys[i] = d[1];
        }
        fillPolygonDevice(xs, ys, nPoints, colorARGB);
    }

    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (xPoints == null || yPoints == null || nPoints < 2) {
            return;
        }
        int[] xs = new int[nPoints];
        int[] ys = new int[nPoints];
        for (int i = 0; i < nPoints; i++) {
            int[] d = devicePoint(xPoints[i], yPoints[i]);
            xs[i] = d[0];
            ys[i] = d[1];
        }
        drawPolygonDevice(xs, ys, nPoints, colorARGB);
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
        if (destPixels == null || str == null || str.isEmpty()) {
            return;
        }
        int dx = x;
        int dy = y;
        if (transform.isTranslationOnly()) {
            dx += (int) Math.round(transform.getTranslateX());
            dy += (int) Math.round(transform.getTranslateY());
        } else {
            double[] p = new double[2];
            transform.transform(new double[] {x, y}, 0, p, 0, 1);
            dx = (int) Math.round(p[0]);
            dy = (int) Math.round(p[1]);
        }
        Object handle = font == null ? null : font.getTextRendererHandle();
        TextBridge.drawString(handle, destPixels, destWidth, destHeight, str, dx, dy, colorARGB, false);
    }

    public void drawString(String str, float x, float y) {
        drawString(str, (int) Math.floor(x), (int) Math.floor(y));
    }

    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        if (img == null) {
            return false;
        }
        return drawImage(img, x, y, img.getWidth(), img.getHeight(), observer);
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        if (img == null || destPixels == null || img.getPixels() == null) return false;

        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        int[] srcPixels = img.getPixels();
        if (srcWidth <= 0 || srcHeight <= 0 || width <= 0 || height <= 0) return true;

        int dstX0 = Math.max(0, x);
        int dstY0 = Math.max(0, y);
        int dstX1 = Math.min(destWidth, x + width);
        int dstY1 = Math.min(destHeight, y + height);
        if (dstX0 >= dstX1 || dstY0 >= dstY1) return true;

        if (img.hasAlpha) {
            drawImageBlend(srcPixels, srcWidth, srcHeight, x, y, width, height,
                    dstX0, dstY0, dstX1, dstY1);
            return true;
        }

        if (width == srcWidth && height == srcHeight) {
            // 1:1 copy — the game's every-frame path (drawImage(img, 0, 0, null)
            // with the source already at the display size). One System.arraycopy
            // per row, a native runtime call even while the class is interpreted.
            int copyW = dstX1 - dstX0;
            for (int dy = dstY0; dy < dstY1; dy++) {
                System.arraycopy(srcPixels, (dy - y) * srcWidth + (dstX0 - x),
                                 destPixels, dy * destWidth + dstX0, copyW);
            }
            return true;
        }

        // Scaled (the splash logo): nearest-neighbour, stepping the source row
        // and column with accumulators instead of two divisions per pixel.
        int copyW = dstX1 - dstX0;
        int xDelta = dstX0 - x;
        int srcX0 = xDelta * srcWidth / width;
        int srcXAcc = xDelta * srcWidth % width;
        int yDelta = dstY0 - y;
        int srcY = yDelta * srcHeight / height;
        int srcYAcc = yDelta * srcHeight % height;
        int destRow = dstY0 * destWidth + dstX0;
        for (int dy = dstY0; dy < dstY1; dy++) {
            int srcRow = srcY * srcWidth;
            int srcX = srcX0;
            int acc = srcXAcc;
            for (int i = 0; i < copyW; i++) {
                destPixels[destRow + i] = srcPixels[srcRow + srcX];
                acc += srcWidth;
                while (acc >= width) {
                    acc -= width;
                    srcX++;
                }
            }
            destRow += destWidth;
            srcYAcc += srcHeight;
            while (srcYAcc >= height) {
                srcYAcc -= height;
                srcY++;
            }
        }
        return true;
    }

    /**
     * Per-pixel source-over copy for sources with alpha (sprites, icons,
     * overlay images). Mirrors the scaled accumulator walk but blends instead
     * of overwriting.
     */
    private void drawImageBlend(int[] srcPixels, int srcWidth, int srcHeight,
                                int x, int y, int width, int height,
                                int dstX0, int dstY0, int dstX1, int dstY1) {
        int copyW = dstX1 - dstX0;
        int xDelta = dstX0 - x;
        int srcX0 = xDelta * srcWidth / width;
        int srcXAcc = xDelta * srcWidth % width;
        int yDelta = dstY0 - y;
        int srcY = yDelta * srcHeight / height;
        int srcYAcc = yDelta * srcHeight % height;
        int destRow = dstY0 * destWidth + dstX0;
        for (int dy = dstY0; dy < dstY1; dy++) {
            int srcRow = srcY * srcWidth;
            int srcX = srcX0;
            int acc = srcXAcc;
            for (int i = 0; i < copyW; i++) {
                destPixels[destRow + i] = blendPixel(destPixels[destRow + i], srcPixels[srcRow + srcX]);
                acc += srcWidth;
                while (acc >= width) {
                    acc -= width;
                    srcX++;
                }
            }
            destRow += destWidth;
            srcYAcc += srcHeight;
            while (srcYAcc >= height) {
                srcYAcc -= height;
                srcY++;
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

    protected int[] transformedBounds(double[] rect) {
        double[] out = new double[8];
        transform.transform(rect, 0, out, 0, 4);
        double minX = Math.min(Math.min(out[0], out[2]), Math.min(out[4], out[6]));
        double maxX = Math.max(Math.max(out[0], out[2]), Math.max(out[4], out[6]));
        double minY = Math.min(Math.min(out[1], out[3]), Math.min(out[5], out[7]));
        double maxY = Math.max(Math.max(out[1], out[3]), Math.max(out[5], out[7]));
        return new int[] {(int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY)};
    }

    protected int[] devicePoint(int x, int y) {
        if (transform.isTranslationOnly()) {
            return new int[] {x + (int) Math.round(transform.getTranslateX()),
                    y + (int) Math.round(transform.getTranslateY())};
        }
        double[] p = new double[2];
        transform.transform(new double[] {x, y}, 0, p, 0, 1);
        return new int[] {(int) Math.round(p[0]), (int) Math.round(p[1])};
    }

    /** Device-space bounding box of an arbitrary shape (uses its getBounds). */
    protected Rectangle deviceBoundsOf(Shape s) {
        Rectangle b;
        if (s instanceof Rectangle) {
            b = new Rectangle((Rectangle) s);
        } else if (s instanceof java.awt.geom.Rectangle2D) {
            b = ((java.awt.geom.Rectangle2D) s).getBounds();
        } else {
            b = s.getBounds();
        }
        if (b == null) {
            return null;
        }
        int[] r = deviceBounds(b.x, b.y, b.width, b.height);
        return new Rectangle(r[0], r[1], r[2] - r[0], r[3] - r[1]);
    }

    protected final int blendPixel(int dst, int argb) {
        int sa = (argb >>> 24) * compositeAlpha / 255;
        if (sa <= 0) {
            return dst;
        }
        int srgb = argb & 0xFFFFFF;
        if (!destHasAlpha) {
            // Destination is opaque by contract (the game frame and the display
            // bridge): blend the RGB channels and preserve the destination alpha
            // byte, so an alpha-0 frame pixel keeps its alpha-0 presentation.
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
        int da = (dst >>> 24) & 0xFF;
        int outA = sa + da * (255 - sa) / 255;
        if (outA == 0) {
            return 0;
        }
        int sr = (srgb >>> 16) & 0xFF;
        int sg = (srgb >>> 8) & 0xFF;
        int sb = srgb & 0xFF;
        int dr = (dst >>> 16) & 0xFF;
        int dg = (dst >>> 8) & 0xFF;
        int db = dst & 0xFF;
        int r = (sr * sa + dr * da * (255 - sa) / 255) / outA;
        int g = (sg * sa + dg * da * (255 - sa) / 255) / outA;
        int b = (sb * sa + db * da * (255 - sa) / 255) / outA;
        return (outA << 24) | (r << 16) | (g << 8) | b;
    }

    protected final void plot(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= destWidth || y >= destHeight) {
            return;
        }
        if (clipBounds != null && (x < clipBounds.x || y < clipBounds.y
                || x >= clipBounds.x + clipBounds.width || y >= clipBounds.y + clipBounds.height)) {
            return;
        }
        int idx = y * destWidth + x;
        destPixels[idx] = blendPixel(destPixels[idx], argb);
    }

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
        for (int x = x0; x <= x1; x++) {
            destPixels[idx] = blendPixel(destPixels[idx], argb);
            idx++;
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

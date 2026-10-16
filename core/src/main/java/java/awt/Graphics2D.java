package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/**
 * The port's {@code java.awt.Graphics2D}.
 *
 * <p>Hand-written, because the Swing base classes and the overlay renderer save and
 * restore a Graphics2D's whole state (transform, clip, stroke, composite, paint,
 * hints) around every component and every overlay, and every {@link Shape} draw has
 * to end in the primitives of {@link Graphics}.
 *
 * <p>Extends {@link Graphics} with the state the RuneLite overlay renderer
 * saves and restores (transform, stroke, composite, paint, rendering hints,
 * background) and converts {@link Shape} draws into the primitive raster calls
 * of the base class, which maps them through the transform.
 *
 * <p><b>Stroke.</b> An integer line width is honoured by tracing that many
 * parallel lines (offset on the dominant axis), round caps and round joins add
 * a filled disc at the ends/vertices, square caps extend the segment by half the
 * width, and a dash array is ignored — dashes render solid (documented
 * deviation; nothing in the client sets one).
 *
 * <p><b>Paint.</b> A {@link GradientPaint} is rasterised by the base class;
 * any other {@link Paint} falls back to the last solid colour.
 *
 * <p>Anti-aliasing is not implemented for shapes: {@code KEY_ANTIALIASING} is
 * accepted and stored but geometry is rasterised with hard edges. Text is
 * anti-aliased by the platform text bridge.
 */
public class Graphics2D extends Graphics {
    private Stroke stroke = new BasicStroke(1.0f);
    private Composite composite;
    private Paint paint;
    private final RenderingHints hints = new RenderingHints();

    public Graphics2D() {
        super();
    }

    public Graphics2D(Image image) {
        super(image);
    }

    public Graphics2D(int[] destPixels, int destWidth, int destHeight) {
        super(destPixels, destWidth, destHeight);
    }

    // ------------------------------------------------------------- state

    @Override
    public void setColor(Color c) {
        super.setColor(c);
        if (c != null) {
            this.paint = c;
        }
    }

    public Paint getPaint() {
        return paint;
    }

    /**
     * Sets the fill source. A {@link Color} becomes the solid colour, a
     * {@link GradientPaint} is rasterised along its two points; any other
     * {@code Paint} keeps the last solid colour in effect (documented deviation:
     * no texture or radial paints exist in this runtime).
     */
    public void setPaint(Paint paint) {
        this.paint = paint;
        if (paint instanceof Color) {
            super.setColor((Color) paint);
        } else if (paint instanceof GradientPaint) {
            this.gradientPaint = (GradientPaint) paint;
        } else {
            this.gradientPaint = null;
        }
    }

    public Composite getComposite() {
        return composite;
    }

    public void setComposite(Composite comp) {
        this.composite = comp;
        if (comp instanceof AlphaComposite) {
            AlphaComposite ac = (AlphaComposite) comp;
            this.compositeRule = ac.getRule();
            int a = (int) Math.round(ac.getAlpha() * 255.0f);
            this.compositeAlpha = a < 0 ? 0 : (a > 255 ? 255 : a);
        } else {
            this.compositeRule = AlphaComposite.SRC_OVER;
            this.compositeAlpha = 255;
        }
    }

    public Stroke getStroke() {
        return stroke;
    }

    public void setStroke(Stroke s) {
        this.stroke = s == null ? new BasicStroke(1.0f) : s;
    }

    /** The active stroke, or null when it carries no width information. */
    @Override
    protected BasicStroke strokeOrNull() {
        return stroke instanceof BasicStroke ? (BasicStroke) stroke : null;
    }

    public RenderingHints getRenderingHints() {
        return hints;
    }

    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        if (hintKey != null) {
            hints.put(hintKey, hintValue);
            if (hintKey == RenderingHints.KEY_INTERPOLATION) {
                interpolationHint = hintValue;
            }
        }
    }

    public Object getRenderingHint(RenderingHints.Key hintKey) {
        return hints.get(hintKey);
    }

    public void setRenderingHints(Map<?, ?> hints) {
        this.hints.clear();
        addRenderingHints(hints);
    }

    public void addRenderingHints(Map<?, ?> hints) {
        if (hints == null) {
            return;
        }
        for (Map.Entry<?, ?> e : hints.entrySet()) {
            this.hints.put(e.getKey(), e.getValue());
        }
        this.interpolationHint = this.hints.get(RenderingHints.KEY_INTERPOLATION);
    }

    // --------------------------------------------------------- transform

    public void rotate(double theta) {
        transform.rotate(theta);
    }

    public void rotate(double theta, double anchorx, double anchory) {
        transform.rotate(theta, anchorx, anchory);
    }

    public void scale(double sx, double sy) {
        transform.scale(sx, sy);
    }

    public void shear(double shx, double shy) {
        transform.concatenate(new AffineTransform(1.0, shy, shx, 1.0, 0.0, 0.0));
    }

    /** Returns a copy of this context sharing the same destination pixels. */
    @Override
    public Graphics2D create() {
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
        g.stroke = stroke;
        g.composite = composite;
        g.paint = paint;
        g.hints.putAll(hints);
        return g;
    }

    // ------------------------------------------------------------ shapes

    public void draw(Shape s) {
        drawOrFill(s, false);
    }

    public void fill(Shape s) {
        drawOrFill(s, true);
    }

    public void fill3DRect(int x, int y, int width, int height, boolean raised) {
        fillRect(x, y, width, height);
    }

    public void draw3DRect(int x, int y, int width, int height, boolean raised) {
        drawRect(x, y, width, height);
    }

    private void drawOrFill(Shape s, boolean fill) {
        if (s == null || destPixels == null) {
            return;
        }
        if (s instanceof Line2D) {
            Line2D l = (Line2D) s;
            drawLineD(l.getX1(), l.getY1(), l.getX2(), l.getY2());
            return;
        }
        // The round/arc/ellipse shapes all extend Rectangle2D, so they must be
        // tested before it.
        if (s instanceof RoundRectangle2D) {
            RoundRectangle2D r = (RoundRectangle2D) s;
            if (fill) {
                fillRoundRectD(r.getX(), r.getY(), r.getWidth(), r.getHeight(),
                        r.getArcWidth(), r.getArcHeight());
            } else {
                drawRoundRectD(r.getX(), r.getY(), r.getWidth(), r.getHeight(),
                        r.getArcWidth(), r.getArcHeight());
            }
            return;
        }
        if (s instanceof Arc2D) {
            Arc2D a = (Arc2D) s;
            if (fill) {
                // PIE closes the arc through the centre, OPEN and CHORD with the
                // chord between its endpoints.
                fillArcD(a.getX(), a.getY(), a.getWidth(), a.getHeight(),
                        a.getAngleStart(), a.getAngleExtent(), a.getArcType() == Arc2D.PIE);
            } else {
                drawArcD(a.getX(), a.getY(), a.getWidth(), a.getHeight(),
                        a.getAngleStart(), a.getAngleExtent());
            }
            return;
        }
        if (s instanceof Ellipse2D) {
            Rectangle2D r = (Rectangle2D) s;
            if (fill) {
                fillOvalD(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            } else {
                drawOvalD(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            }
            return;
        }
        if (s instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) s;
            if (fill) {
                fillRectD(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            } else {
                drawRectD(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            }
            return;
        }
        if (s instanceof Rectangle) {
            Rectangle r = (Rectangle) s;
            if (fill) {
                fillRect(r.x, r.y, r.width, r.height);
            } else {
                drawRect(r.x, r.y, r.width, r.height);
            }
            return;
        }
        if (s instanceof Polygon) {
            if (fill) {
                fillPolygon((Polygon) s);
            } else {
                drawPolygon((Polygon) s);
            }
            return;
        }
        if (s instanceof GeneralPath) {
            pathOrFill((GeneralPath) s, fill);
            return;
        }
        if (s instanceof Area) {
            areaOrFill((Area) s, fill);
            return;
        }
        Rectangle b = s.getBounds();
        if (b == null) {
            return;
        }
        if (fill) {
            fillRect(b.x, b.y, b.width, b.height);
        } else {
            drawRect(b.x, b.y, b.width, b.height);
        }
    }

    /**
     * Draws or fills a {@link GeneralPath}'s sub-paths. The path stores only
     * move/line segments (no curve elements exist in this runtime), so each
     * sub-path is an exact polygon: filled separately, or stroked as a polyline.
     */
    private void pathOrFill(GeneralPath path, boolean fill) {
        int n = path.getPointCount();
        if (n == 0) {
            return;
        }
        int start = 0;
        for (int i = 1; i <= n; i++) {
            if (i == n || path.isMoveTo(i)) {
                int count = i - start;
                if (count >= (fill ? 3 : 2)) {
                    int[] xs = new int[count];
                    int[] ys = new int[count];
                    for (int k = 0; k < count; k++) {
                        xs[k] = deviceX(path.getX(start + k), path.getY(start + k));
                        ys[k] = deviceY(path.getX(start + k), path.getY(start + k));
                    }
                    if (fill) {
                        fillPolygonDevice(xs, ys, count, colorARGB);
                    } else {
                        strokePolylineDevice(xs, ys, count, true, colorARGB);
                    }
                }
                start = i;
            }
        }
    }

    /**
     * Fills or strokes an {@link Area}. The added outlines are drawn; for a fill
     * the subtracted outlines are cleared with the {@code CLEAR} rule, which is
     * the boolean difference the area models (an approximation: overlapping
     * added outlines are filled once each, and on a surface without alpha the
     * cleared pixels are transparent black).
     */
    private void areaOrFill(Area area, boolean fill) {
        java.util.List<Shape> shapes = area.getShapes();
        for (int i = 0; i < shapes.size(); i++) {
            drawOrFill(shapes.get(i), fill);
        }
        if (!fill) {
            return;
        }
        java.util.List<Shape> holes = area.getSubtractedShapes();
        if (holes.isEmpty()) {
            return;
        }
        int savedRule = compositeRule;
        compositeRule = AlphaComposite.CLEAR;
        for (int i = 0; i < holes.size(); i++) {
            drawOrFill(holes.get(i), true);
        }
        compositeRule = savedRule;
    }

}

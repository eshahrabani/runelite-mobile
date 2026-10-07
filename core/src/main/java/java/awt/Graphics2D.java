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
 * AWT Graphics2D compatibility stub for Android runtime.
 *
 * <p>Extends {@link Graphics} with the state the RuneLite overlay renderer
 * saves and restores (transform, stroke, composite, paint, rendering hints,
 * background) and converts {@link Shape} draws into the primitive raster calls
 * of the base class.
 *
 * <p>Anti-aliasing is not implemented for shapes: {@code KEY_ANTIALIASING} is
 * accepted and stored but geometry is rasterised with hard edges. Text is
 * anti-aliased by the platform text bridge. Gradients are accepted but not
 * rasterised (the last solid colour stays in effect).
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

    public void setPaint(Paint paint) {
        this.paint = paint;
        if (paint instanceof Color) {
            super.setColor((Color) paint);
        }
        // GradientPaint: recorded but not rasterised.
    }

    public Composite getComposite() {
        return composite;
    }

    public void setComposite(Composite comp) {
        this.composite = comp;
        if (comp instanceof AlphaComposite) {
            this.compositeAlpha = (int) Math.round(((AlphaComposite) comp).getAlpha() * 255.0f);
            if (this.compositeAlpha < 0) {
                this.compositeAlpha = 0;
            } else if (this.compositeAlpha > 255) {
                this.compositeAlpha = 255;
            }
        } else {
            this.compositeAlpha = 255;
        }
    }

    public Stroke getStroke() {
        return stroke;
    }

    public void setStroke(Stroke s) {
        this.stroke = s == null ? new BasicStroke(1.0f) : s;
    }

    public RenderingHints getRenderingHints() {
        return hints;
    }

    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        if (hintKey != null) {
            hints.put(hintKey, hintValue);
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
    public Graphics2D create() {
        Graphics2D g = new Graphics2D(destPixels, destWidth, destHeight);
        g.colorARGB = colorARGB;
        g.font = font;
        g.background = background;
        g.transform = new AffineTransform(transform);
        g.clipShape = clipShape;
        g.clipBounds = clipBounds == null ? null : new Rectangle(clipBounds);
        g.compositeAlpha = compositeAlpha;
        g.destHasAlpha = destHasAlpha;
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
        if (s instanceof Ellipse2D) {
            Rectangle2D r = (Rectangle2D) s;
            int[] b = deviceBounds((int) Math.floor(r.getX()), (int) Math.floor(r.getY()),
                    (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight()));
            if (fill) {
                fillEllipseDevice(b[0], b[1], b[2] - b[0], b[3] - b[1], colorARGB);
            } else {
                drawEllipseDevice(b[0], b[1], b[2] - b[0], b[3] - b[1], colorARGB);
            }
            return;
        }
        if (s instanceof Arc2D) {
            Arc2D a = (Arc2D) s;
            int[] b = deviceBounds((int) Math.floor(a.getX()), (int) Math.floor(a.getY()),
                    (int) Math.ceil(a.getWidth()), (int) Math.ceil(a.getHeight()));
            drawArcDevice(b[0], b[1], b[2] - b[0], b[3] - b[1],
                    a.getAngleStart(), a.getAngleExtent(), fill, colorARGB);
            return;
        }
        if (s instanceof RoundRectangle2D) {
            Rectangle2D r = (Rectangle2D) s;
            int[] b = deviceBounds((int) Math.floor(r.getX()), (int) Math.floor(r.getY()),
                    (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight()));
            if (fill) {
                fillRectDevice(b[0], b[1], b[2] - b[0], b[3] - b[1], colorARGB);
            } else {
                drawRectD(b[0], b[1], b[2] - b[0] - 1, b[3] - b[1] - 1);
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
            GeneralPath p = (GeneralPath) s;
            int n = p.getPointCount();
            if (n == 0) {
                return;
            }
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                int[] d = devicePoint((int) p.getX(i), (int) p.getY(i));
                xs[i] = d[0];
                ys[i] = d[1];
            }
            if (fill) {
                fillPolygonDevice(xs, ys, n, colorARGB);
            } else {
                drawPolygonDevice(xs, ys, n, colorARGB);
            }
            return;
        }
        if (s instanceof Area) {
            java.util.List<Shape> shapes = ((Area) s).getShapes();
            for (int i = 0; i < shapes.size(); i++) {
                drawOrFill(shapes.get(i), fill);
            }
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
}

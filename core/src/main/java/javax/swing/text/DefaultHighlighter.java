package javax.swing.text;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written {@code javax.swing.text.DefaultHighlighter}: the {@link Highlighter} that
 * {@link JTextComponent} installs by default.
 *
 * <p>Why hand-written: {@code getHighlighter()} must answer with a real object (RuneLite's
 * {@code JRichTextPane} calls {@code setHighlighter(null)} to turn the desktop selection
 * highlight off), and marks registered through the API have to be paintable so a search or a
 * link sweep shows up.
 *
 * <p>Deviation from the JDK: the port has no {@link View} hierarchy, so
 * {@link #paintLayeredHighlights} draws nothing (the plain {@link #paint(Graphics)} path
 * paints every mark). A mark whose endpoints are outside the component's visible text simply
 * paints nothing.
 */
public class DefaultHighlighter implements LayeredHighlighter {
    private static final Highlight[] EMPTY = new Highlight[0];

    /** The painter {@link #addHighlight} uses when the caller supplies none. */
    public static final HighlightPainter DefaultPainter = new DefaultHighlightPainter();

    private final List<HighlightInfo> highlights = new ArrayList<>();
    private JTextComponent component;
    private boolean drawsLayeredHighlights = true;

    public DefaultHighlighter() {
    }

    @Override
    public void install(JTextComponent c) {
        component = c;
        removeAllHighlights();
    }

    @Override
    public void deinstall(JTextComponent c) {
        removeAllHighlights();
        component = null;
    }

    @Override
    public Object addHighlight(int p0, int p1, HighlightPainter p) throws BadLocationException {
        if (p0 < 0) {
            throw new BadLocationException("Invalid start offset", p0);
        }
        HighlightInfo info = new HighlightInfo(p0, p1, p == null ? DefaultPainter : p);
        highlights.add(info);
        if (component != null) {
            component.repaint();
        }
        return info;
    }

    @Override
    public void removeHighlight(Object tag) {
        if (tag instanceof HighlightInfo && highlights.remove(tag) && component != null) {
            component.repaint();
        }
    }

    @Override
    public void removeAllHighlights() {
        if (!highlights.isEmpty()) {
            highlights.clear();
            if (component != null) {
                component.repaint();
            }
        }
    }

    @Override
    public void changeHighlight(Object tag, int p0, int p1) throws BadLocationException {
        if (!(tag instanceof HighlightInfo)) {
            return;
        }
        HighlightInfo info = (HighlightInfo) tag;
        info.start = p0;
        info.end = p1;
        if (component != null) {
            component.repaint();
        }
    }

    @Override
    public Highlight[] getHighlights() {
        return highlights.isEmpty() ? EMPTY : highlights.toArray(new Highlight[0]);
    }

    /** True when layered painting is enabled (see the class comment for the port caveat). */
    @Override
    public void paintLayeredHighlights(Graphics g, int p0, int p1, Shape viewBounds,
                                       JTextComponent editor, View view) {
        // No view tree on this port: the component paints its highlights in paintComponent.
    }

    @Override
    public void paint(Graphics g) {
        if (component == null || highlights.isEmpty()) {
            return;
        }
        Shape bounds = new Rectangle(0, 0, component.getWidth(), component.getHeight());
        for (int i = 0; i < highlights.size(); i++) {
            HighlightInfo info = highlights.get(i);
            info.painter.paint(g, info.start, info.end, bounds, component);
        }
    }

    /** True when marks are expected to be painted while views paint (ignored here). */
    public boolean getDrawsLayeredHighlights() {
        return drawsLayeredHighlights;
    }

    /** Enables/disables layered painting (stored; the port always paints through {@code paint}). */
    public void setDrawsLayeredHighlights(boolean newValue) {
        drawsLayeredHighlights = newValue;
    }

    /** The default mark painter: a translucent-ish solid fill of the marked range. */
    public static class DefaultHighlightPainter implements LayeredHighlighter.LayerPainter {
        private final Color color;

        public DefaultHighlightPainter() {
            this(null);
        }

        public DefaultHighlightPainter(Color c) {
            this.color = c;
        }

        /** The colour this painter fills with (null means the component's selection colour). */
        public Color getColor() {
            return color;
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            Rectangle r = c.modelToView(p0);
            Rectangle to = c.modelToView(p1);
            if (r == null || to == null || r.height == 0) {
                return;
            }
            Color old = g.getColor();
            g.setColor(selectionColor(c));
            if (r.y == to.y) {
                g.fillRect(r.x, r.y, Math.max(to.x - r.x, 0), r.height);
            } else {
                Rectangle clip = bounds == null ? null : bounds.getBounds();
                int width = clip == null ? c.getWidth() : clip.width;
                g.fillRect(r.x, r.y, Math.max(width - r.x, 0), r.height);
                g.fillRect(0, r.y + r.height, width, Math.max(to.y - r.y - r.height, 0));
                g.fillRect(0, to.y, to.x, to.height);
            }
            g.setColor(old);
        }

        @Override
        public Shape paintLayer(Graphics g, int p0, int p1, Shape viewBounds, JTextComponent editor, View view) {
            paint(g, p0, p1, viewBounds, editor);
            return viewBounds;
        }

        private Color selectionColor(JTextComponent c) {
            if (color != null) {
                return color;
            }
            return c.getSelectionColor();
        }
    }

    /** One mark: a range plus its painter, and the tag the API hands back. */
    private static final class HighlightInfo implements Highlight {
        private int start;
        private int end;
        private final HighlightPainter painter;

        HighlightInfo(int start, int end, HighlightPainter painter) {
            this.start = start;
            this.end = end;
            this.painter = painter;
        }

        @Override
        public int getStartOffset() {
            return start;
        }

        @Override
        public int getEndOffset() {
            return end;
        }

        @Override
        public HighlightPainter getPainter() {
            return painter;
        }
    }
}

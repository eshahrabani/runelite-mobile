package javax.swing.text;

import java.awt.Graphics;
import java.awt.Shape;

/**
 * Hand-written {@code javax.swing.text.LayeredHighlighter}: a {@link Highlighter} that can
 * paint individual highlights while a {@link View} is drawn.
 *
 * <p>Why hand-written: {@code JTextComponent.setHighlighter} installs one of these (the JDK's
 * default is a {@code DefaultHighlighter}), and the html/{@code JEditorPane} path checks for
 * it. The generated stub set had no such type.
 *
 * <p>Deviation from the JDK: this port has no view tree, so
 * {@link #paintLayeredHighlights} paints nothing and answers null. Callers that only query
 * the highlights still get the real ones.
 */
public interface LayeredHighlighter extends Highlighter {

    /** Paints the highlights that fall in {@code p0}..{@code p1} inside {@code view}. */
    void paintLayeredHighlights(Graphics g, int p0, int p1, Shape viewBounds, JTextComponent editor, View view);

    /** A highlight painter that can also paint through a view. */
    interface LayerPainter extends Highlighter.HighlightPainter {
        /** Paints the range inside {@code view}; returns the painted shape or null. */
        Shape paintLayer(Graphics g, int p0, int p1, Shape viewBounds, JTextComponent editor, View view);
    }
}
